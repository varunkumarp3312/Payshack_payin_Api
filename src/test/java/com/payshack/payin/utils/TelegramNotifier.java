package com.payshack.payin.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private static final int LONG_POLL_SECONDS = 30;

    /** Visual separator line used in every styled message. */
    public static final String SEP = "━━━━━━━━━━━━━━━━━━━━━";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiBase;
    private final String chatId;

    /** Tracks the next update_id; updated by every polling call. */
    private int offset = 0;

    public TelegramNotifier(String botToken, String chatId) {
        this.apiBase = "https://api.telegram.org/bot" + botToken + "/";
        this.chatId = chatId;
    }

    /**
     * Escapes HTML special characters in dynamic content so it is safe to embed
     * inside HTML-formatted Telegram messages.
     */
    public static String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    /**
     * Drains all pending Telegram updates and advances the internal offset past
     * them. Call once at the very start of a test run.
     */
    public void flushPendingUpdates() throws Exception {
        ObjectNode params = mapper.createObjectNode();
        params.put("timeout", 0);
        params.put("limit", 100);

        JsonNode response = post("getUpdates", params);
        JsonNode updates = response.path("result");

        if (updates.size() == 0) {
            log.info("No pending Telegram updates to flush.");
            return;
        }

        int maxUpdateId = 0;
        for (JsonNode update : updates) {
            int id = update.path("update_id").asInt();
            if (id > maxUpdateId) maxUpdateId = id;
        }

        ObjectNode ackParams = mapper.createObjectNode();
        ackParams.put("offset", maxUpdateId + 1);
        ackParams.put("timeout", 0);
        ackParams.put("limit", 1);
        post("getUpdates", ackParams);

        offset = maxUpdateId + 1;
        log.info("Flushed {} stale update(s). Next offset = {}", updates.size(), offset);
    }

    /**
     * Sends the styled payment message with [Paid] and [Not Now] buttons.
     *
     * @param callbackUrl the URL the provider must POST callbacks to; shown in
     *                    the message so the group can verify the configuration.
     *                    Pass {@code null} or blank to omit the callback section.
     * @return Telegram message_id
     */
    public int sendPaymentMessage(String paymentUrl, String orderId,
                                  String txnRefId, String callbackUrl) throws Exception {
        String cbSection = (callbackUrl != null && !callbackUrl.isBlank())
            ? "\n📡 <b>Callback URL</b>  <i>(configure with provider)</i>\n"
              + "<code>" + esc(callbackUrl) + "</code>\n"
            : "";

        String text =
            "🔔 <b>NEW PAYMENT REQUEST</b>\n"
          + SEP + "\n\n"
          + "📋 <b>Order ID</b>\n"
          + "<code>" + esc(orderId) + "</code>\n\n"
          + "🔑 <b>TxnRef ID</b>\n"
          + "<code>" + esc(txnRefId) + "</code>\n\n"
          + "💳 <b>Payment URL</b>\n"
          + "<code>" + esc(paymentUrl) + "</code>\n"
          + cbSection + "\n"
          + SEP + "\n"
          + "✅ Tap <b>Paid</b> once payment is done\n"
          + "⏰ Tap <b>Not Now</b> to reschedule";

        ObjectNode paidBtn = mapper.createObjectNode();
        paidBtn.put("text", "✅  Paid");
        paidBtn.put("callback_data", "paid_" + orderId);

        ObjectNode notNowBtn = mapper.createObjectNode();
        notNowBtn.put("text", "⏰  Not Now");
        notNowBtn.put("callback_data", "notnow_" + orderId);

        ArrayNode row = mapper.createArrayNode();
        row.add(paidBtn);
        row.add(notNowBtn);

        ArrayNode keyboard = mapper.createArrayNode();
        keyboard.add(row);

        ObjectNode replyMarkup = mapper.createObjectNode();
        replyMarkup.set("inline_keyboard", keyboard);

        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.set("reply_markup", replyMarkup);

        JsonNode result = post("sendMessage", body);
        int messageId = result.path("result").path("message_id").asInt();
        log.info("Payment message sent, message_id={}", messageId);
        return messageId;
    }

    /**
     * Long-polls until someone clicks Paid or Not Now for this orderId,
     * or the timeout expires (returns null).
     */
    public CallbackResult waitForPaidCallback(long timeoutSeconds, String orderId) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        String paidData   = "paid_"   + orderId;
        String notNowData = "notnow_" + orderId;

        log.info("Waiting up to {}s for button click (orderId={})...", timeoutSeconds, orderId);

        while (System.currentTimeMillis() < deadline) {
            long remainingMs = deadline - System.currentTimeMillis();
            int pollSecs = (int) Math.min(remainingMs / 1_000L, LONG_POLL_SECONDS);
            if (pollSecs <= 0) break;

            ArrayNode allowedUpdates = mapper.createArrayNode();
            allowedUpdates.add("callback_query");

            ObjectNode params = mapper.createObjectNode();
            params.put("offset", offset);
            params.put("timeout", pollSecs);
            params.set("allowed_updates", allowedUpdates);

            JsonNode response = post("getUpdates", params);

            for (JsonNode update : response.path("result")) {
                offset = update.path("update_id").asInt() + 1;

                JsonNode cq = update.path("callback_query");
                if (cq.isMissingNode()) continue;

                String data     = cq.path("data").asText();
                String fromUser = cq.path("from").path("first_name").asText("User");
                String cbId     = cq.path("id").asText();

                if (paidData.equals(data)) {
                    log.info("'Paid' clicked by {} for orderId={}", fromUser, orderId);
                    return new CallbackResult(cbId, fromUser, false);
                }
                if (notNowData.equals(data)) {
                    log.info("'Not Now' clicked by {} for orderId={}", fromUser, orderId);
                    return new CallbackResult(cbId, fromUser, true);
                }
            }
        }

        log.warn("Timed out waiting for button click (orderId={}) after {}s", orderId, timeoutSeconds);
        return null;
    }

    /**
     * Listens for the next group text message containing a positive integer.
     * Continues from the offset left by {@link #waitForPaidCallback}.
     */
    public Integer waitForMinutesReply(long timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;

        log.info("Waiting up to {}s for user to reply with number of minutes...", timeoutSeconds);

        while (System.currentTimeMillis() < deadline) {
            long remainingMs = deadline - System.currentTimeMillis();
            int pollSecs = (int) Math.min(remainingMs / 1_000L, LONG_POLL_SECONDS);
            if (pollSecs <= 0) break;

            ArrayNode allowedUpdates = mapper.createArrayNode();
            allowedUpdates.add("message");

            ObjectNode params = mapper.createObjectNode();
            params.put("offset", offset);
            params.put("timeout", pollSecs);
            params.set("allowed_updates", allowedUpdates);

            JsonNode response = post("getUpdates", params);

            for (JsonNode update : response.path("result")) {
                offset = update.path("update_id").asInt() + 1;

                String text = update.path("message").path("text").asText("").trim();
                if (text.isEmpty()) continue;

                Matcher m = NUMBER_PATTERN.matcher(text);
                if (m.find()) {
                    int minutes = Integer.parseInt(m.group());
                    if (minutes > 0) {
                        log.info("User replied with {} minute(s)", minutes);
                        return minutes;
                    }
                }
            }
        }

        log.warn("Timed out waiting for minutes reply after {}s", timeoutSeconds);
        return null;
    }

    /**
     * Removes the inline keyboard from a message so buttons cannot be re-clicked.
     */
    public void removeInlineKeyboard(int messageId) {
        try {
            ObjectNode emptyMarkup = mapper.createObjectNode();
            emptyMarkup.set("inline_keyboard", mapper.createArrayNode());

            ObjectNode body = mapper.createObjectNode();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.set("reply_markup", emptyMarkup);

            post("editMessageReplyMarkup", body);
            log.info("Inline keyboard removed from message_id={}", messageId);
        } catch (Exception e) {
            log.warn("removeInlineKeyboard failed: {}", e.getMessage());
        }
    }

    /** Acknowledges a callback query. Silently ignored if the 10s window expired. */
    public void answerCallbackQuery(String callbackQueryId, String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("callback_query_id", callbackQueryId);
            body.put("text", text);
            post("answerCallbackQuery", body);
        } catch (Exception e) {
            log.warn("answerCallbackQuery failed (may have expired): {}", e.getMessage());
        }
    }

    /** Edits the HTML text of an existing message. Silently ignores "not modified" errors. */
    public void editMessageText(int messageId, String htmlText) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", htmlText);
        body.put("parse_mode", "HTML");
        try {
            post("editMessageText", body);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                log.debug("editMessageText skipped — content unchanged for message_id={}", messageId);
            } else {
                throw e;
            }
        }
    }

    private JsonNode post(String method, ObjectNode body) throws Exception {
        String json = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + method))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(LONG_POLL_SECONDS + 15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode result = mapper.readTree(response.body());

        if (!result.path("ok").asBoolean(false)) {
            throw new RuntimeException("Telegram API [" + method + "] error: " + response.body());
        }
        return result;
    }

    public static class CallbackResult {
        public final String callbackQueryId;
        public final String fromUser;
        /** true = "Not Now" clicked; false = "Paid" clicked. */
        public final boolean isNotNow;

        public CallbackResult(String callbackQueryId, String fromUser, boolean isNotNow) {
            this.callbackQueryId = callbackQueryId;
            this.fromUser = fromUser;
            this.isNotNow = isNotNow;
        }
    }
}
