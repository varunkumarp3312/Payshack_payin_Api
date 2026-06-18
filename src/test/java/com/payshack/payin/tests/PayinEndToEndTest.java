package com.payshack.payin.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshack.payin.constants.TransactionStatus;
import com.payshack.payin.models.InitiateIntentRequest;
import com.payshack.payin.utils.TestContext;
import com.payshack.payin.utils.TestDataGenerator;
import io.restassured.response.Response;
import org.junit.Assert;
import org.junit.Test;

public class PayinEndToEndTest extends BaseTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void tc01_verifyInitiateAndStatusFlow() throws Exception {

        String orderId = TestDataGenerator.generateOrderId();

        InitiateIntentRequest request = new InitiateIntentRequest()
                .setOrderId(orderId)
                .setAmount("100")
                .setFirstName("pavan")
                .setLastName("kumar")
                .setEmail("pmkirru33@gmail.com")
                .setPhone("8970411423")
                .setUserIP("10.210.10")
                .setUserId("dddd");

        Response initiateResponse = payinApi.initiateIntent(request);

        Assert.assertEquals("HTTP status should be 200", 200, initiateResponse.getStatusCode());
        Assert.assertEquals("API statusCode mismatch", 200, initiateResponse.jsonPath().getInt("statusCode"));
        Assert.assertEquals("API status mismatch", "OK", initiateResponse.jsonPath().getString("status"));
        Assert.assertEquals(
                "message mismatch",
                "Transaction Initiated Successfully",
                initiateResponse.jsonPath().getString("message"));
        Assert.assertTrue("success flag should be true", initiateResponse.jsonPath().getBoolean("success"));

        String decryptedInitiateResponse = payinApi.decryptResponseData(initiateResponse);
        JsonNode initiateData = mapper.readTree(decryptedInitiateResponse);

        Assert.assertEquals("orderId mismatch", orderId, initiateData.path("orderId").asText());
        Assert.assertFalse(
                "txnRefId should not be empty",
                initiateData.path("txnRefId").asText().trim().isEmpty());
        Assert.assertFalse(
                "paymentUrl should not be empty",
                initiateData.path("paymentUrl").asText().trim().isEmpty());

        TestContext.setOrderId(initiateData.path("orderId").asText());
        TestContext.setTxnRefId(initiateData.path("txnRefId").asText());
        TestContext.setPaymentUrl(initiateData.path("paymentUrl").asText());

        log.info(
                "Initiated | orderId={} | txnRefId={}",
                TestContext.getOrderId(),
                TestContext.getTxnRefId());

        Assert.assertNotNull("OrderId should not be null before status check", orderId);
        Assert.assertFalse("OrderId should not be empty before status check", orderId.trim().isEmpty());

        log.info("Waiting 10s before status check...");
        Thread.sleep(10_000);

        Response statusResponse = payinApi.checkStatus(orderId);

        Assert.assertEquals("HTTP status should be 200", 200, statusResponse.getStatusCode());
        Assert.assertEquals("API statusCode mismatch", 200, statusResponse.jsonPath().getInt("statusCode"));
        Assert.assertEquals("API status mismatch", "OK", statusResponse.jsonPath().getString("status"));
        Assert.assertTrue("success flag should be true", statusResponse.jsonPath().getBoolean("success"));
        Assert.assertNotNull("response should contain data", statusResponse.jsonPath().get("data"));

        JsonNode statusData = resolveStatusData(statusResponse);

        String status = statusData.path("status").asText();

        log.info("Status | orderId={} | status={}", orderId, status);

        Assert.assertTrue(
                "Invalid transaction status: " + status,
                TransactionStatus.isValid(status));

        Assert.assertFalse(
                "txnRefId should not be empty",
                statusData.path("txnRefId").asText().trim().isEmpty());

        Assert.assertFalse(
                "utr should not be empty",
                statusData.path("utr").asText().trim().isEmpty());

        Assert.assertFalse(
                "amount should not be empty",
                statusData.path("amount").asText().trim().isEmpty());

        TestContext.setTransactionStatus(status);
        TestContext.setTxnRefId(statusData.path("txnRefId").asText());
        TestContext.setUtr(statusData.path("utr").asText());
        TestContext.setAmount(statusData.path("amount").asText());

        log.info(
                "Status={} | TxnRefId={} | UTR={} | Amount={}",
                TestContext.getTransactionStatus(),
                TestContext.getTxnRefId(),
                TestContext.getUtr(),
                TestContext.getAmount());
    }

    private JsonNode resolveStatusData(Response response) throws Exception {

        Object dataField = response.jsonPath().get("data");

        if (dataField instanceof String) {
            return mapper.readTree(payinApi.decryptResponseData(response));
        }

        return mapper.readTree(mapper.writeValueAsString(dataField));
    }
}