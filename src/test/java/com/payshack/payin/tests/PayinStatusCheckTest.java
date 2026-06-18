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

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PayinStatusCheckTest extends BaseTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private String orderId;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        orderId = ConfigReader.get("statusOrderId");
    }

    private JsonNode fetchAndDecryptStatus() throws Exception {
        Response response = payinApi.checkStatus(orderId);
        Assert.assertEquals("HTTP status should be 200", 200, response.getStatusCode());

        Object dataField = response.jsonPath().get("data");
        if (dataField instanceof String) {
            String decrypted = payinApi.decryptResponseData(response);
            log.debug("Decrypted status response: {}", decrypted);
            return mapper.readTree(decrypted);
        }
        // data is already a JSON object (plain, non-encrypted)
        return mapper.readTree(response.jsonPath().getObject("data", Object.class).toString());
    }

    // ─── Tests ──────────────────────────────────────────────────────────────────

    @Test
    public void tc01_verifyHttpStatusCodeIs200() {
        Response response = payinApi.checkStatus(orderId);
        Assert.assertEquals("HTTP status code mismatch", 200, response.getStatusCode());
    }

    @Test
    public void tc02_verifyResponseApiStatusCodeIs200() {
        Response response = payinApi.checkStatus(orderId);
        Assert.assertEquals("API statusCode mismatch", 200, response.jsonPath().getInt("statusCode"));
    }

    @Test
    public void tc03_verifyResponseStatusIsOK() {
        Response response = payinApi.checkStatus(orderId);
        Assert.assertEquals("API status mismatch", "OK", response.jsonPath().getString("status"));
    }

    @Test
    public void tc04_verifySuccessFlagIsTrue() {
        Response response = payinApi.checkStatus(orderId);
        Assert.assertTrue("success flag should be true", response.jsonPath().getBoolean("success"));
    }

    @Test
    public void tc05_verifyResponseContainsDataField() {
        Response response = payinApi.checkStatus(orderId);
        Assert.assertNotNull("response should contain 'data' field",
                response.jsonPath().get("data"));
    }

    @Test
    public void tc06_verifyDecryptedDataContainsOnlyExpectedFields() throws Exception {
        JsonNode data = fetchAndDecryptStatus();
        Set<String> expected = new HashSet<>(Arrays.asList("status", "txnRefId", "utr", "orderId", "amount"));
        Set<String> actual = new HashSet<>();
        data.fieldNames().forEachRemaining(actual::add);
        Assert.assertEquals("Extra or missing fields in decrypted status response", expected, actual);
    }

    @Test
    public void tc07_verifyOrderIdMatchesRequest() throws Exception {
        JsonNode data = fetchAndDecryptStatus();
        Assert.assertEquals("orderId mismatch", orderId, data.path("orderId").asText());
    }

    @Test
    public void tc08_verifyTransactionStatusIsValid() throws Exception {
        JsonNode data = fetchAndDecryptStatus();
        String status = data.path("status").asText();
        log.info("Transaction status for orderId {}: {}", orderId, status);
        Assert.assertTrue("Invalid transaction status: " + status, TransactionStatus.isValid(status));
    }

    @Test
    public void tc09_verifyTxnRefIdIsNotNull() throws Exception {
        JsonNode data = fetchAndDecryptStatus();
        Assert.assertFalse("txnRefId should not be empty",
                data.path("txnRefId").asText().trim().isEmpty());
    }

    @Test
    public void tc10_verifyAmountIsNotNull() throws Exception {
        JsonNode data = fetchAndDecryptStatus();
        Assert.assertFalse("amount should not be empty",
                data.path("amount").asText().trim().isEmpty());
    }
}
