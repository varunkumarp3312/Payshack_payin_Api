package com.payshack.payin.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshack.payin.models.InitiateIntentRequest;
import com.payshack.payin.utils.CallbackReceiver;
import com.payshack.payin.utils.ConfigReader;
import com.payshack.payin.utils.TelegramNotifier;
import com.payshack.payin.utils.TelegramNotifier.CallbackResult;
import com.payshack.payin.utils.TestContext;
import com.payshack.payin.utils.TestDataGenerator;
import io.restassured.response.Response;
import org.junit.Assert;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.payshack.payin.utils.TelegramNotifier.SEP;
import static com.payshack.payin.utils.TelegramNotifier.esc;

public class PayinTelegramFlowTest extends BaseTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String BOT_TOKEN = "8953397341:AAGopaDTZYju0tm_h7NQEAzi2SY7aklQBVc";
    private static final String CHAT_ID   = "-5554324456";

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT    = DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy");
    private static final DateTimeFormatter SUMMARY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private static final long PAID_BUTTON_TIMEOUT_SECONDS   = 600;
    private static final long MINUTES_REPLY_TIMEOUT_SECONDS = 120;
    private static final int  STATUS_RETRY_COUNT            = 2;
    private static final long STATUS_RETRY_INTERVAL_MS      = 25_000;

    @Test
    public void tc01_telegramConfirmedPaymentFlow() throws Exception {

        TelegramNotifier telegram = new TelegramNotifier(BOT_TOKEN, CHAT_ID);
        telegram.flushPendingUpdates();

        // Read callback config
        int    callbackPort        = Integer.parseInt(ConfigReader.get("callbackServerPort", "8089"));
        long   callbackWaitSeconds = Long.parseLong(ConfigReader.get("callbackWaitSeconds", "60"));
        String callbackUrl         = ConfigReader.get("callbackNgrokUrl").replaceAll("/$", "")
                                     + CallbackReceiver.CALLBACK_PATH;

        // Start local server — ngrok tunnels the public URL to this port
        CallbackReceiver callbackReceiver = new CallbackReceiver(callbackPort);
        log.info("Callback URL (register with provider): {}", callbackUrl);

        // UPI → result; insertion order preserved so the summary is chronological.
        // When a UPI appears whose key already exists → round-robin cycle complete → stop.
        LinkedHashMap<String, String> upiResults = new LinkedHashMap<>();

        try {
            while (true) {

                // ── 1. Initiate fresh payment intent ──────────────────────────
                String orderId = TestDataGenerator.generateOrderId();

                InitiateIntentRequest request = new InitiateIntentRequest()
                        .setOrderId(orderId)
                        .setAmount("100")
                        .setFirstName("pavan")
                        .setLastName("kumar")
                        .setEmail("pmkirru33@gmail.com")
                        .setPhone("8970411423")
                        .setUserIP("10.210.10.1")
                        .setUserId("dddd");

                Response initiateResponse = payinApi.initiateIntent(request);

                Assert.assertEquals("HTTP status should be 200", 200, initiateResponse.getStatusCode());
                Assert.assertEquals("statusCode mismatch", 200, initiateResponse.jsonPath().getInt("statusCode"));
                Assert.assertEquals("status mismatch", "OK", initiateResponse.jsonPath().getString("status"));
                Assert.assertTrue("success flag should be true", initiateResponse.jsonPath().getBoolean("success"));
                Assert.assertEquals("message mismatch", "Transaction Initiated Successfully",
                        initiateResponse.jsonPath().getString("message"));

                JsonNode initiateData = mapper.readTree(payinApi.decryptResponseData(initiateResponse));
                String paymentUrl = initiateData.path("paymentUrl").asText();
                String txnRefId   = initiateData.path("txnRefId").asText();

                Assert.assertFalse("paymentUrl should not be empty", paymentUrl.trim().isEmpty());
                Assert.assertFalse("txnRefId should not be empty", txnRefId.trim().isEmpty());

                TestContext.setOrderId(orderId);
                TestContext.setTxnRefId(txnRefId);
                TestContext.setPaymentUrl(paymentUrl);
                log.info("Intent initiated | orderId={} | txnRefId={}", orderId, txnRefId);

                // ── Round-robin guard — stop when a UPI repeats ───────────────
                String currentUpi = extractUpiId(paymentUrl);
                log.info("UPI ID in payment URL | orderId={} | upi={}", orderId, currentUpi);

                if (currentUpi != null && upiResults.containsKey(currentUpi)) {
                    log.warn("Round-robin complete — UPI {} seen again. Stopping.", currentUpi);
                    telegram.sendMessage(buildSummaryMessage(upiResults));
                    break;
                }
                // Register UPI; result will be filled in at the outcome point below
                if (currentUpi != null) upiResults.put(currentUpi, "🔄 In progress");

                // Drop any callbacks from previous iterations
                callbackReceiver.clearQueue();

                // ── 2. Post payment message to Telegram ───────────────────────
                int messageId = telegram.sendPaymentMessage(paymentUrl, orderId, txnRefId, callbackUrl);

                // ── 3. Wait for Paid or Not Now button click ──────────────────
                CallbackResult callback = telegram.waitForPaidCallback(PAID_BUTTON_TIMEOUT_SECONDS, orderId);

                if (callback == null) {
                    telegram.editMessageText(messageId, msgTimeout(orderId));
                    log.warn("Timed out waiting for button click | orderId={}. Re-initiating...", orderId);
                    if (currentUpi != null) upiResults.put(currentUpi, "⏰ TIMED OUT");
                    continue;
                }

                telegram.removeInlineKeyboard(messageId);
                telegram.answerCallbackQuery(callback.callbackQueryId,
                        callback.isNotNow ? "Ok! Tell me when to reschedule." : "Verifying payment...");

                // ── 4a. NOT NOW — ask for delay minutes and loop ──────────────
                if (callback.isNotNow) {
                    telegram.editMessageText(messageId, msgAskMinutes(orderId, callback.fromUser));

                    Integer minutes = telegram.waitForMinutesReply(MINUTES_REPLY_TIMEOUT_SECONDS);
                    if (minutes == null) {
                        telegram.editMessageText(messageId, msgNoReply(orderId));
                        log.warn("No minutes input received within {}s | orderId={}. Re-initiating...",
                                MINUTES_REPLY_TIMEOUT_SECONDS, orderId);
                        // Remove from tracking — test was not completed for this UPI
                        if (currentUpi != null) upiResults.remove(currentUpi);
                        continue;
                    }

                    String timeStr = ZonedDateTime.now(IST).plusMinutes(minutes).format(TIME_FMT);
                    telegram.editMessageText(messageId, msgRescheduled(orderId, callback.fromUser, minutes, timeStr));
                    log.info("Not Now by {}. Sleeping {} min until {}", callback.fromUser, minutes, timeStr);
                    // Remove from tracking — deferred, not completed; will be re-tested after sleep
                    if (currentUpi != null) upiResults.remove(currentUpi);
                    Thread.sleep((long) minutes * 60_000L);
                    continue;   // loop back → new orderId, new initiate, new message
                }

                // ── 4b. PAID — run status check ───────────────────────────────
                telegram.editMessageText(messageId, msgVerifying(orderId, callback.fromUser));
                log.info("Paid by {}. Running status check.", callback.fromUser);

                String finalStatus = runStatusCheckWithRetry(telegram, orderId, txnRefId, messageId, callback.fromUser);
                TestContext.setTransactionStatus(finalStatus);

                if (!"SUCCESS".equalsIgnoreCase(finalStatus)) {
                    telegram.editMessageText(messageId, msgFailure(orderId, txnRefId, finalStatus, callback.fromUser));
                    log.error("Test FAILED | orderId={} | status={}. Re-initiating...", orderId, finalStatus);
                    if (currentUpi != null) upiResults.put(currentUpi, "❌ FAILED: " + finalStatus);
                    continue;
                }

                // ── 5. Wait for provider callback ─────────────────────────────
                telegram.editMessageText(messageId,
                        msgWaitingCallback(orderId, txnRefId, callback.fromUser, callbackWaitSeconds));

                String payload = callbackReceiver.waitForCallback(callbackWaitSeconds);

                if (payload != null) {
                    String decryptedCallback;
                    String callbackStatus;
                    try {
                        decryptedCallback = payinApi.decryptCallbackPayload(payload);
                        JsonNode cbNode = mapper.readTree(decryptedCallback);
                        callbackStatus = cbNode.path("status").asText("UNKNOWN");
                        log.info("Decrypted callback | orderId={} | callbackStatus={}", orderId, callbackStatus);
                    } catch (Exception e) {
                        log.warn("Could not decrypt callback for orderId={}: {}", orderId, e.getMessage());
                        decryptedCallback = payload;
                        callbackStatus = "DECRYPT_FAILED";
                    }
                    telegram.editMessageText(messageId,
                            msgCallbackReceived(orderId, txnRefId, finalStatus, callback.fromUser,
                                    decryptedCallback, callbackStatus));
                    log.info("Test PASSED + callback received | orderId={}. Re-initiating...", orderId);
                    if (currentUpi != null)
                        upiResults.put(currentUpi, "✅ SUCCESS | Callback: " + callbackStatus);
                } else {
                    telegram.editMessageText(messageId,
                            msgCallbackNotReceived(orderId, txnRefId, finalStatus, callback.fromUser));
                    log.warn("SUCCESS but no callback received | orderId={}. Re-initiating...", orderId);
                    if (currentUpi != null) upiResults.put(currentUpi, "✅ SUCCESS (no callback)");
                }
            }

        } finally {
            callbackReceiver.stop();
        }
    }

    // ── Status check ─────────────────────────────────────────────────────────

    private String runStatusCheckWithRetry(TelegramNotifier telegram, String orderId,
                                           String txnRefId, int messageId,
                                           String confirmedBy) throws Exception {
        String status = doStatusCheck(orderId);
        log.info("Status check 1 | orderId={} | status={}", orderId, status);
        if (!isPendingStatus(status)) return status;

        for (int attempt = 1; attempt <= STATUS_RETRY_COUNT; attempt++) {
            telegram.editMessageText(messageId, msgPendingStatus(orderId, status, attempt));
            log.info("{} status. Waiting {}s before retry {}/{}...", 
                    status, STATUS_RETRY_INTERVAL_MS / 1_000, attempt, STATUS_RETRY_COUNT);
            Thread.sleep(STATUS_RETRY_INTERVAL_MS);

            status = doStatusCheck(orderId);
            log.info("Status check {} | orderId={} | status={}", attempt + 1, orderId, status);
            if (!isPendingStatus(status)) return status;
        }

        return status;
    }
    private String msgPendingStatus(String orderId, String status, int attempt) {
        String statusDisplay = "INITIATED".equalsIgnoreCase(status) ? "INITIATED" : "IN PROCESS";
        return "⏳ <b>STATUS : " + statusDisplay + "</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "🔁 <b>Retry</b>  ·  " + attempt + " / " + STATUS_RETRY_COUNT + "\n\n"
             + "🔄 <i>Still processing, please wait...</i>";
    }
    private boolean isPendingStatus(String status) {
        return "INITIATED".equalsIgnoreCase(status) || 
               "TRANSACTION IN PROCESS".equalsIgnoreCase(status);
    }

    private String doStatusCheck(String orderId) throws Exception {
        Response res = payinApi.checkStatus(orderId);

        Assert.assertEquals("Status check HTTP 200", 200, res.getStatusCode());
        Assert.assertEquals("Status API statusCode", 200, res.jsonPath().getInt("statusCode"));
        Assert.assertTrue("Status API success flag", res.jsonPath().getBoolean("success"));

        JsonNode data = resolveStatusData(res);
        String status = data.path("status").asText();

        TestContext.setTransactionStatus(status);
        TestContext.setTxnRefId(data.path("txnRefId").asText());
        TestContext.setUtr(data.path("utr").asText());
        TestContext.setAmount(data.path("amount").asText());

        log.info("Status={} | utr={} | amount={}", status, TestContext.getUtr(), TestContext.getAmount());
        return status;
    }

    private JsonNode resolveStatusData(Response response) throws Exception {
        Object dataField = response.jsonPath().get("data");
        if (dataField instanceof String) {
            return mapper.readTree(payinApi.decryptResponseData(response));
        }
        return mapper.readTree(mapper.writeValueAsString(dataField));
    }

    // ── Telegram message builders ─────────────────────────────────────────────

    private String msgVerifying(String orderId, String by) {
        return "🔄 <b>VERIFYING PAYMENT</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "👤 <b>Confirmed by</b>  ·  " + esc(by) + "\n\n"
             + "🔍 <i>Checking payment status, please wait...</i>";
    }

//    private String msgInitiated(String orderId, int attempt) {
//        return "⏳ <b>STATUS : INITIATED</b>\n" + SEP + "\n\n"
//             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
//             + "🔁 <b>Retry</b>  ·  " + attempt + " / " + STATUS_RETRY_COUNT + "\n\n"
//             + "🔄 <i>Still testing, please wait...</i>";
//    }

    private String msgWaitingCallback(String orderId, String txnRefId, String by, long waitSec) {
        return "✅ <b>STATUS : SUCCESS</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "🔑 <b>TxnRef ID</b>\n<code>" + esc(txnRefId) + "</code>\n\n"
             + "📊 <b>Status</b>  ·  <code>SUCCESS</code>\n"
             + "💰 <b>Amount</b>  ·  ₹" + esc(TestContext.getAmount()) + "\n"
             + "🏦 <b>UTR</b>  ·  <code>" + esc(TestContext.getUtr()) + "</code>\n"
             + "👤 <b>Confirmed by</b>  ·  " + esc(by) + "\n\n"
             + SEP + "\n"
             + "📡 <i>Waiting for provider callback... (" + waitSec + "s)</i>";
    }

    private String msgCallbackReceived(String orderId, String txnRefId,
                                       String status, String by,
                                       String decryptedPayload, String callbackStatus) {
        String display = decryptedPayload.length() > 800
                ? decryptedPayload.substring(0, 800) + "..." : decryptedPayload;
        return "✅ <b>PAYMENT SUCCESS</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "🔑 <b>TxnRef ID</b>\n<code>" + esc(txnRefId) + "</code>\n\n"
             + "📊 <b>Status (API)</b>  ·  <code>" + esc(status) + "</code>\n"
             + "💰 <b>Amount</b>  ·  ₹" + esc(TestContext.getAmount()) + "\n"
             + "🏦 <b>UTR</b>  ·  <code>" + esc(TestContext.getUtr()) + "</code>\n"
             + "👤 <b>Confirmed by</b>  ·  " + esc(by) + "\n\n"
             + SEP + "\n"
             + "📡 <b>Callback Received</b>  ✅\n"
             + "📊 <b>Callback Status</b>  ·  <code>" + esc(callbackStatus) + "</code>\n\n"
             + "📄 <b>Decrypted Callback</b>\n<code>" + esc(display) + "</code>\n\n"
             + SEP + "\n"
             + "🎉 <b>Test case PASSED</b>\n"
             + "🚀 Volume test can now proceed.";
    }

    private String msgCallbackNotReceived(String orderId, String txnRefId,
                                          String status, String by) {
        return "✅ <b>PAYMENT SUCCESS</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "🔑 <b>TxnRef ID</b>\n<code>" + esc(txnRefId) + "</code>\n\n"
             + "📊 <b>Status</b>  ·  <code>" + esc(status) + "</code>\n"
             + "💰 <b>Amount</b>  ·  ₹" + esc(TestContext.getAmount()) + "\n"
             + "🏦 <b>UTR</b>  ·  <code>" + esc(TestContext.getUtr()) + "</code>\n"
             + "👤 <b>Confirmed by</b>  ·  " + esc(by) + "\n\n"
             + SEP + "\n"
             + "📡 <b>Callback</b>  ·  ❌ Not received\n"
             + "⚠️ <i>Please verify callback URL with the provider.</i>\n\n"
             + SEP + "\n"
             + "🎉 <b>Test case PASSED</b>  <i>(status check)</i>\n"
             + "🚀 Volume test can now proceed.";
    }

    private String msgFailure(String orderId, String txnRefId, String status, String by) {
        return "❌ <b>PAYMENT FAILED</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "🔑 <b>TxnRef ID</b>\n<code>" + esc(txnRefId) + "</code>\n\n"
             + "📊 <b>Status</b>  ·  <code>" + esc(status) + "</code>\n"
             + "💰 <b>Amount</b>  ·  ₹" + esc(TestContext.getAmount()) + "\n"
             + "👤 <b>Confirmed by</b>  ·  " + esc(by) + "\n\n"
             + SEP + "\n"
             + "⚠️ <b>Test is FAILED</b>\n"
             + "📞 Contact the provider and check.";
    }

    private String msgTimeout(String orderId) {
        return "⏰ <b>TIMED OUT</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>\n<code>" + esc(orderId) + "</code>\n\n"
             + "❌ No response received within " + (PAID_BUTTON_TIMEOUT_SECONDS / 60) + " minutes.\n"
             + "Please restart the test.";
    }

    private String msgAskMinutes(String orderId, String by) {
        return "⏰ <b>RESCHEDULE REQUEST</b>\n" + SEP + "\n\n"
             + "👤 <b>" + esc(by) + "</b> clicked <b>Not Now</b>\n\n"
             + "📋 <b>Order ID</b>  ·  <code>" + esc(orderId) + "</code>\n\n"
             + SEP + "\n"
             + "💬 Please reply with the <b>number of minutes</b> after which the test should run.\n\n"
             + "Example: <code>10</code>";
    }

    private String msgRescheduled(String orderId, String by, int minutes, String timeStr) {
        return "📅 <b>TEST RESCHEDULED</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>  ·  <code>" + esc(orderId) + "</code>\n"
             + "👤 <b>Rescheduled by</b>  ·  " + esc(by) + "\n"
             + "⏱ <b>Delay</b>  ·  " + minutes + " minute(s)\n"
             + "🕐 <b>Runs at</b>  ·  <b>" + esc(timeStr) + " IST</b>\n\n"
             + SEP + "\n"
             + "🔔 A new payment request will be posted automatically.";
    }

    private String msgNoReply(String orderId) {
        return "⚠️ <b>NO RESPONSE RECEIVED</b>\n" + SEP + "\n\n"
             + "📋 <b>Order ID</b>  ·  <code>" + esc(orderId) + "</code>\n\n"
             + "❌ No minutes input received within " + (MINUTES_REPLY_TIMEOUT_SECONDS / 60) + " minutes.\n"
             + "Test has been cancelled.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSummaryMessage(LinkedHashMap<String, String> upiResults) {
        String dateStr = ZonedDateTime.now(IST).format(SUMMARY_FMT);
        int pass = 0, fail = 0;
        StringBuilder rows = new StringBuilder();
        int idx = 1;
        for (Map.Entry<String, String> e : upiResults.entrySet()) {
            String result = e.getValue() != null ? e.getValue() : "❓ Unknown";
            rows.append(idx++).append(". <code>").append(esc(e.getKey())).append("</code>\n")
                .append("   ↳ ").append(result).append("\n\n");
            if (result.startsWith("✅")) pass++;
            else                         fail++;
        }
        return "📊 <b>TODAY'S TEST SUMMARY</b>\n" + SEP + "\n\n"
             + "📅 <b>Date</b>  ·  " + esc(dateStr) + " IST\n\n"
             + SEP + "\n\n"
             + rows
             + SEP + "\n"
             + "✅ <b>Passed</b>  ·  " + pass + "\n"
             + "❌ <b>Failed / Other</b>  ·  " + fail + "\n\n"
             + "🔄 <i>Round-robin cycle complete. Test execution stopped.</i>";
    }

    /** Extracts the {@code pa=} UPI VPA from a UPI intent / payment URL. */
    private static String extractUpiId(String paymentUrl) {
        if (paymentUrl == null || paymentUrl.isEmpty()) return null;
        int idx = paymentUrl.indexOf("pa=");
        if (idx < 0) return null;
        int start = idx + 3;
        int end   = paymentUrl.indexOf('&', start);
        return (end < 0 ? paymentUrl.substring(start) : paymentUrl.substring(start, end)).trim();
    }
}
