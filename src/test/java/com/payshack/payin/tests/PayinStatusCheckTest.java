package com.payshack.payin.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshack.payin.constants.TransactionStatus;
import com.payshack.payin.utils.ConfigReader;
import io.restassured.response.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Tests run in alphabetical order (tc01 → tc12) to match report readability
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PayinStatusCheckTest extends BaseTest {

    // ─── Fields ─────────────────────────────────────────────────────────────────

    private static final ObjectMapper mapper = new ObjectMapper();

    // The orderId used for all status-check calls, loaded from config
    private String orderId;

    // ─── Setup ──────────────────────────────────────────────────────────────────

    @Before
    @Override
    public void setUp() {
        super.setUp();
        orderId = ConfigReader.get("statusOrderId");
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Calls the status-check API and returns the decrypted "data" node.
     * Handles both encrypted (String) and plain-JSON (Object) data fields.
     */
    private JsonNode fetchDecryptedData() throws Exception {
        Response response = payinApi.checkStatus(orderId);
        log.info("Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());
        Assert.assertEquals("HTTP status should be 200 before decryption", 200, response.getStatusCode());

        Object dataField = response.jsonPath().get("data");

        if (dataField instanceof String) {
            // Encrypted path: decrypt the Base64 cipher string and parse JSON
            String decrypted = payinApi.decryptResponseData(response);
            log.debug("Decrypted status response: {}", decrypted);
            return mapper.readTree(decrypted);
        }

        // Plain path: data is already a JSON object, just convert to JsonNode
        return mapper.readTree(response.jsonPath().getObject("data", Object.class).toString());
    }

    // ─── TC01 : HTTP Layer ───────────────────────────────────────────────────────

    // Verifies that the server returns HTTP 200 (not 4xx/5xx)
    @Test
    public void tc01_httpStatusCodeShouldBe200() {
        Response response = payinApi.checkStatus(orderId);
        log.info("TC01 Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());

        Assert.assertEquals("HTTP status code should be 200", 200, response.getStatusCode());
    }

    // ─── TC02 : Envelope — statusCode field ─────────────────────────────────────

    // Verifies that the JSON envelope's "statusCode" field equals 200
    @Test
    public void tc02_apiEnvelopeStatusCodeShouldBe200() {
        Response response = payinApi.checkStatus(orderId);
        log.info("TC02 Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());

        Assert.assertEquals("Envelope 'statusCode' should be 200",
                200, response.jsonPath().getInt("statusCode"));
    }

    // ─── TC03 : Envelope — status field ─────────────────────────────────────────

    // Verifies that the JSON envelope's "status" field equals "OK"
    @Test
    public void tc03_apiEnvelopeStatusShouldBeOK() {
        Response response = payinApi.checkStatus(orderId);
        log.info("TC03 Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());

        Assert.assertEquals("Envelope 'status' should be 'OK'",
                "OK", response.jsonPath().getString("status"));
    }

    // ─── TC04 : Envelope — success flag ─────────────────────────────────────────

    // Verifies that the JSON envelope's "success" flag is true
    @Test
    public void tc04_successFlagShouldBeTrue() {
        Response response = payinApi.checkStatus(orderId);
        log.info("TC04 Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());

        Assert.assertTrue("Envelope 'success' flag should be true",
                response.jsonPath().getBoolean("success"));
    }

    // ─── TC05 : Envelope — message field ────────────────────────────────────────

    // Verifies that the envelope contains a non-blank "message" field
    @Test
    public void tc05_messageShouldNotBeBlank() {
        Response response = payinApi.checkStatus(orderId);
        log.info("TC05 Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());
        String message = response.jsonPath().getString("message");

        Assert.assertNotNull("Envelope 'message' should not be null", message);
        Assert.assertFalse("Envelope 'message' should not be blank", message.trim().isEmpty());
    }

    // ─── TC06 : Envelope — data field present ───────────────────────────────────

    // Verifies that the envelope contains a "data" field (encrypted or plain)
    @Test
    public void tc06_envelopeShouldContainDataField() {
        Response response = payinApi.checkStatus(orderId);
        log.info("TC06 Status API raw response [HTTP {}]: {}", response.getStatusCode(), response.asString());

        Assert.assertNotNull("Envelope 'data' field should be present",
                response.jsonPath().get("data"));
    }

    // ─── TC07 : Decrypted data — exact field set ─────────────────────────────────

    // Verifies that the decrypted data contains exactly the expected fields — no extras, no missing
    // Expected: orderId, status, txnRefId, utr, amount
    @Test
    public void tc07_decryptedDataShouldHaveExactFields() throws Exception {
        JsonNode data = fetchDecryptedData();

        Set<String> expected = new HashSet<>(Arrays.asList("orderId", "status", "txnRefId", "utr", "amount"));
        Set<String> actual   = new HashSet<>();
        data.fieldNames().forEachRemaining(actual::add);

        Assert.assertEquals("Decrypted data has extra or missing fields", expected, actual);
    }

    // ─── TC08 : Decrypted data — orderId matches request ────────────────────────

    // Verifies that the orderId in the decrypted response matches the one we requested
    @Test
    public void tc08_decryptedOrderIdShouldMatchRequest() throws Exception {
        JsonNode data = fetchDecryptedData();

        Assert.assertEquals("Decrypted 'orderId' must match the requested orderId",
                orderId, data.path("orderId").asText());
    }

    // ─── TC09 : Decrypted data — status is a known enum value ───────────────────

    // Verifies that "status" is one of the allowed TransactionStatus values:
    // INITIATED, SUCCESS, FAILED, PENDING, TAMPERED, DUPLICATE,
    // EXPIRED, REFUNDED, TRANSACTION IN PROCESS, INCOMPLETE, HOLD
    @Test
    public void tc09_transactionStatusShouldBeAValidEnum() throws Exception {
        JsonNode data   = fetchDecryptedData();
        String   status = data.path("status").asText();

        log.info("Transaction status for orderId={} is '{}'", orderId, status);

        Assert.assertTrue(
                "'" + status + "' is not a valid TransactionStatus. " +
                "Allowed: INITIATED, SUCCESS, FAILED, PENDING, TAMPERED, DUPLICATE, " +
                "EXPIRED, REFUNDED, TRANSACTION IN PROCESS, INCOMPLETE, HOLD",
                TransactionStatus.isValid(status));
    }

    // ─── TC10 : Decrypted data — txnRefId is not blank ──────────────────────────

    // Verifies that the transaction reference ID returned by the gateway is non-empty
    @Test
    public void tc10_txnRefIdShouldNotBeBlank() throws Exception {
        JsonNode data = fetchDecryptedData();

        Assert.assertFalse("Decrypted 'txnRefId' should not be blank",
                data.path("txnRefId").asText().trim().isEmpty());
    }

    // ─── TC11 : Decrypted data — amount is not blank ────────────────────────────

    // Verifies that the amount field is present and holds a value
    @Test
    public void tc11_amountShouldNotBeBlank() throws Exception {
        JsonNode data = fetchDecryptedData();

        Assert.assertFalse("Decrypted 'amount' should not be blank",
                data.path("amount").asText().trim().isEmpty());
    }

    // ─── TC12 : Decrypted data — utr field exists ───────────────────────────────

    // Verifies that the "utr" (Unique Transaction Reference) field is present in the response.
    // utr will be empty for INITIATED/PENDING transactions but must always exist as a key.
    @Test
    public void tc12_utrFieldShouldExistInDecryptedData() throws Exception {
        JsonNode data = fetchDecryptedData();

        Assert.assertTrue("Decrypted data should contain 'utr' field", data.has("utr"));
    }
}
