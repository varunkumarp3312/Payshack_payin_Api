package com.payshack.payin.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshack.payin.models.InitiateIntentRequest;
import com.payshack.payin.utils.TestContext;
import com.payshack.payin.utils.TestDataGenerator;
import io.restassured.response.Response;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PayinInitiateIntentTest extends BaseTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private InitiateIntentRequest defaultRequest() {
        return new InitiateIntentRequest()
                .setOrderId(TestDataGenerator.generateOrderId())
                .setAmount("100")
                .setFirstName("pavan")
                .setLastName("kumar")
                .setEmail("pmkirru33@gmail.com")
                .setPhone("8970411423")
                .setUserIP("217.165.72.64")
                .setUserId("ddrtyuytdd");
    }

    private JsonNode executeAndDecrypt(InitiateIntentRequest request) throws Exception {
        Response response = payinApi.initiateIntent(request);
        Assert.assertEquals("HTTP status should be 200", 200, response.getStatusCode());
        String decrypted = payinApi.decryptResponseData(response);
        log.debug("Decrypted initiate response: {}", decrypted);
        return mapper.readTree(decrypted);
    }

    // ─── Tests ──────────────────────────────────────────────────────────────────

    // Verifies that the API returns HTTP 200 for a valid initiate intent request
    @Test
    public void tc01_verifyHttpStatusCodeIs200() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC01 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);
        Assert.assertEquals("HTTP status code mismatch", 200, response.getStatusCode());
    }

    // Verifies that the statusCode field inside the response JSON body is 200
    @Test
    public void tc02_verifyResponseApiStatusCodeIs200() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC02 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);
        Assert.assertEquals("API statusCode mismatch", 200, response.jsonPath().getInt("statusCode"));
    }

    // Verifies that the status field in the response body is "OK"
    @Test
    public void tc03_verifyResponseStatusIsOK() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC03 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);
        Assert.assertEquals("API status mismatch", "OK", response.jsonPath().getString("status"));
    }

    // Verifies that the message field in the response is "Transaction Initiated Successfully"
    @Test
    public void tc04_verifyResponseMessage() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC04 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);
        Assert.assertEquals(
                "Transaction Initiated Successfully",
                response.jsonPath().getString("message"));
    }

    // Verifies that the success flag in the response is true for a valid request
    @Test
    public void tc05_verifySuccessFlagIsTrue() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC05 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);
        Assert.assertTrue("success flag should be true", response.jsonPath().getBoolean("success"));
    }

    // Verifies the decrypted response contains exactly the fields: orderId, txnRefId, paymentUrl — no more, no less
    @Test
    public void tc06_verifyDecryptedResponseContainsOnlyExpectedFields() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC06 Request Body: {}", mapper.writeValueAsString(request));
        JsonNode data = executeAndDecrypt(request);
        Set<String> expected = new HashSet<>(Arrays.asList("orderId", "txnRefId", "paymentUrl"));
        Set<String> actual = new HashSet<>();
        data.fieldNames().forEachRemaining(actual::add);
        Assert.assertEquals("Extra or missing fields in decrypted initiate response", expected, actual);
    }

    // Verifies that the orderId echoed in the decrypted response matches the orderId sent in the request
    @Test
    public void tc07_verifyOrderIdMatchesRequest() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC07 Request Body: {}", mapper.writeValueAsString(request));
        String expectedOrderId = request.getOrderId();
        JsonNode data = executeAndDecrypt(request);
        Assert.assertEquals("orderId mismatch", expectedOrderId, data.path("orderId").asText());
    }

    // Verifies that the txnRefId in the decrypted response is non-empty and stores it in TestContext for downstream tests
    @Test
    public void tc08_verifyTxnRefIdIsGenerated() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC08 Request Body: {}", mapper.writeValueAsString(request));
        JsonNode data = executeAndDecrypt(request);
        String txnRefId = data.path("txnRefId").asText();
        Assert.assertFalse("txnRefId should not be empty", txnRefId.trim().isEmpty());
        TestContext.setTxnRefId(txnRefId);
    }

    // Verifies that the paymentUrl in the decrypted response is non-empty and stores it in TestContext for downstream tests
    @Test
    public void tc09_verifyPaymentUrlIsGenerated() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC09 Request Body: {}", mapper.writeValueAsString(request));
        JsonNode data = executeAndDecrypt(request);
        String paymentUrl = data.path("paymentUrl").asText();
        Assert.assertFalse("paymentUrl should not be empty", paymentUrl.trim().isEmpty());
        TestContext.setPaymentUrl(paymentUrl);
    }

    // Verifies that the paymentUrl begins with the expected UPI deep-link scheme "upi://pay"
    @Test
    public void tc10_verifyPaymentUrlContainsUpiPay() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC10 Request Body: {}", mapper.writeValueAsString(request));
        JsonNode data = executeAndDecrypt(request);
        String paymentUrl = data.path("paymentUrl").asText();
        Assert.assertTrue("paymentUrl should contain 'upi://pay'", paymentUrl.contains("upi://pay"));
    }

    // Verifies that the paymentUrl does not expose the txnRefId in plain text within the URL
    @Test
    public void tc11_verifyPaymentUrlDoesNotContainTxnRefId() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC11 Request Body: {}", mapper.writeValueAsString(request));
        JsonNode data = executeAndDecrypt(request);
        String txnRefId = data.path("txnRefId").asText();
        String paymentUrl = data.path("paymentUrl").asText();
        Assert.assertTrue("paymentUrl should NOT contain txnRefId", paymentUrl.contains(txnRefId));
    }

    // Verifies a complete valid payload initiates a transaction and stores orderId, txnRefId, paymentUrl in TestContext
    @Test
    public void tc12_verifyFullValidPayloadStoresTransactionData() throws Exception {
        InitiateIntentRequest request = defaultRequest();
        log.info("TC12 Request Body: {}", mapper.writeValueAsString(request));
        JsonNode data = executeAndDecrypt(request);

        TestContext.setOrderId(data.path("orderId").asText());
        TestContext.setTxnRefId(data.path("txnRefId").asText());
        TestContext.setPaymentUrl(data.path("paymentUrl").asText());
        TestContext.setSharedOrderId(data.path("orderId").asText());

        log.info("OrderId    : {}", TestContext.getOrderId());
        log.info("TxnRefId   : {}", TestContext.getTxnRefId());
        log.info("PaymentUrl : {}", TestContext.getPaymentUrl());

        Assert.assertNotNull(TestContext.getOrderId());
        Assert.assertNotNull(TestContext.getTxnRefId());
        Assert.assertNotNull(TestContext.getPaymentUrl());
    }

    // ─── Negative Tests ─────────────────────────────────────────────────────────

    // Verifies the API returns HTTP 500 and rejects the transaction when amount exceeds 10000
    @Test
    public void tc13_verifyAmountOver10000IsRejected() throws Exception {
        InitiateIntentRequest request = defaultRequest().setAmount("10001");
        log.info("TC13 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);

        log.info("Amount > 10000 response: {}", response.asString());

        Assert.assertEquals("HTTP status code mismatch", 500, response.getStatusCode());
        Assert.assertEquals("API statusCode mismatch", 500, response.jsonPath().getInt("statusCode"));
        Assert.assertEquals("status mismatch", "Internal Server Error", response.jsonPath().getString("status"));
        Assert.assertEquals("message mismatch", "Amount should not be greater than 10000",
                response.jsonPath().getString("message"));
        Assert.assertFalse("success should be false for amount > 10000",
                response.jsonPath().getBoolean("success"));
    }

    // Verifies the API rejects a request where the email is missing the '@' symbol (invalid format)
    @Test
    public void tc14_verifyInvalidEmailFormatIsRejected() throws Exception {
        InitiateIntentRequest request = defaultRequest().setEmail("pmkirru33gmail.com");
        log.info("TC14 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);

        log.info("Invalid email response: {}", response.asString());
        Assert.assertFalse("success should be false for invalid email",
                response.jsonPath().getBoolean("success"));
        Assert.assertTrue("error message should mention email",
                response.jsonPath().getString("message").toLowerCase().contains("email"));
    }

    // Verifies the API rejects a phone number that exceeds 10 digits
    @Test
    public void tc15_verifyPhoneMoreThan10DigitsIsRejected() throws Exception {
        InitiateIntentRequest request = defaultRequest().setPhone("897041142312");
        log.info("TC15 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);

        log.info("Phone >10 digits response: {}", response.asString());
        Assert.assertFalse("success should be false for phone > 10 digits",
                response.jsonPath().getBoolean("success"));
        Assert.assertTrue("error message should mention phone",
                response.jsonPath().getString("message").toLowerCase().contains("phone"));
    }

    // Verifies the API rejects a phone number prefixed with a country code (+91)
    @Test
    public void tc16_verifyPhoneWithCountryCodeIsRejected() throws Exception {
        InitiateIntentRequest request = defaultRequest().setPhone("+918970411423");
        log.info("TC16 Request Body: {}", mapper.writeValueAsString(request));
        Response response = payinApi.initiateIntent(request);

        log.info("Phone with country code response: {}", response.asString());
        Assert.assertFalse("success should be false for phone with country code",
                response.jsonPath().getBoolean("success"));
        Assert.assertTrue("error message should mention phone",
                response.jsonPath().getString("message").toLowerCase().contains("phone"));
    }
}
