package apiTests;

import static io.restassured.RestAssured.given;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import api_utilities.CommonMethods;
import api_utilities.ConfigReader;
import api_utilities.DecryptUtil;
import api_utilities.EncryptionUtil;
import api_utilities.TestDataStore;
import io.restassured.response.Response;

public class PayinInitiateIntentApiTest {

    String baseUrl = ConfigReader.getprop("apibaseUrl");

    String initiateEndpoint =
            "/payshack-payin/api/v1/payin/ext/txn/initiate-intent";

    String secretKey = ConfigReader.getprop("secreteKey");

    String clientId = ConfigReader.getprop("clientId");

    String encryptionKey = ConfigReader.getprop("encryptionkey");

    @Test
    public void verifyInitiatePayinTransactionWithValidPayload() {

        String orderId = CommonMethods.generateRandomOrderID();

        TestDataStore.orderId = orderId;

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        TestDataStore.orderId =
                decryptedJson.getString("orderId");

        TestDataStore.txnRefId =
                decryptedJson.getString("txnRefId");

        TestDataStore.paymentUrl =
                decryptedJson.getString("paymentUrl");

        System.out.println("OrderId    : " + TestDataStore.orderId);
        System.out.println("TxnRefId   : " + TestDataStore.txnRefId);
        System.out.println("PaymentUrl : " + TestDataStore.paymentUrl);
    }

    @Test
    public void verifyInitiateStatusCodeIs200() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        Assert.assertEquals(
                "HTTP status code mismatch",
                200,
                response.statusCode());
    }

    @Test
    public void verifyInitiateResponseMessage() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        Assert.assertEquals(
                "Transaction Initiated Successfully",
                response.jsonPath().getString("message"));
    }

    @Test
    public void verifyInitiateSuccessFlagIsTrue() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        Assert.assertTrue(
                "Success flag should be true",
                response.jsonPath().getBoolean("success"));
    }

    @Test
    public void verifyDecryptedResponseContainsOnlyExpectedFields() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        Set<String> expectedKeys =
                new HashSet<String>(
                        Arrays.asList(
                                "orderId",
                                "txnRefId",
                                "paymentUrl"));

        Assert.assertEquals(
                "Extra or missing fields found in decrypted response",
                expectedKeys,
                decryptedJson.keySet());
    }

    @Test
    public void verifyOrderIdMatchesRequestOrderId() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        Assert.assertEquals(
                "OrderId mismatch",
                orderId,
                decryptedJson.getString("orderId"));
    }

    @Test
    public void verifyTxnRefIdIsGenerated() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        Assert.assertFalse(
                "TxnRefId should not be empty",
                decryptedJson.getString("txnRefId").trim().isEmpty());
    }

    @Test
    public void verifyPaymentUrlIsGenerated() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        Assert.assertFalse(
                "PaymentUrl should not be empty",
                decryptedJson.getString("paymentUrl").trim().isEmpty());
    }

    @Test
    public void verifyPaymentUrlContainsUpiPay() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        String paymentUrl =
                decryptedJson.getString("paymentUrl");

        Assert.assertTrue(
                "PaymentUrl should contain upi://pay",
                paymentUrl.contains("upi://pay"));
    }

    @Test
    public void verifyPaymentUrlContainsTxnRefId() {

        String orderId = CommonMethods.generateRandomOrderID();

        JSONObject requestPayload = buildInitiatePayload(orderId);

        Response response = sendInitiateRequest(requestPayload);

        JSONObject decryptedJson =
                validateAndGetDecryptedInitiateResponse(response, orderId);

        String txnRefId =
                decryptedJson.getString("txnRefId");

        String paymentUrl =
                decryptedJson.getString("paymentUrl");

        Assert.assertTrue(
                "PaymentUrl should contain txnRefId",
                paymentUrl.contains(txnRefId));
        System.out.println(requestPayload);
    }

    public JSONObject buildInitiatePayload(String orderId) {

        JSONObject actualPayload = new JSONObject();

        actualPayload.put("orderId", orderId);
        actualPayload.put("amount", "100");
        actualPayload.put("firstName", "pavan");
        actualPayload.put("lastName", "kumar");
        actualPayload.put("email", "pmkirru33@gmail.com");
        actualPayload.put("phone", "8970411423");
        actualPayload.put("userIP", "10.210.10");
        actualPayload.put("userId", "dddd");

        return actualPayload;
    }

    public Response sendInitiateRequest(JSONObject actualPayload) {

        String encryptedBody =
                EncryptionUtil.encrypt(
                        actualPayload.toString(),
                        encryptionKey);

        String checksum =
                EncryptionUtil.getChecksum(
                        encryptedBody,
                        encryptionKey);

        JSONObject finalRequest = new JSONObject();

        finalRequest.put("checksum", checksum);
        finalRequest.put("encryptedBody", encryptedBody);

        Response response =
                given()
                    .baseUri(baseUrl)
                    .header("secret-key", secretKey)
                    .header("client-id", clientId)
                    .header("Content-Type", "application/json")
                    .body(finalRequest.toString())
                    .log().all()

                .when()
                    .post(initiateEndpoint)

                .then()
                    .log().all()
                    .extract()
                    .response();

        return response;
    }

    public JSONObject validateAndGetDecryptedInitiateResponse(
            Response response,
            String expectedOrderId) {

        Assert.assertEquals(
                "HTTP status code mismatch",
                200,
                response.statusCode());

        Assert.assertEquals(
                "API statusCode mismatch",
                200,
                response.jsonPath().getInt("statusCode"));

        Assert.assertEquals(
                "API status mismatch",
                "OK",
                response.jsonPath().getString("status"));

        Assert.assertEquals(
                "API message mismatch",
                "Transaction Initiated Successfully",
                response.jsonPath().getString("message"));

        Assert.assertTrue(
                "Success flag should be true",
                response.jsonPath().getBoolean("success"));

        Assert.assertNotNull(
                "Encrypted data should not be null",
                response.jsonPath().getString("data"));

        String encryptedResponseData =
                response.jsonPath().getString("data");

        String decryptedResponse =
                DecryptUtil.decrypt(
                        encryptedResponseData,
                        encryptionKey);

        System.out.println(
                "Initiate Decrypted Response is : "
                        + decryptedResponse);

        JSONObject decryptedJson =
                new JSONObject(decryptedResponse);

        Set<String> expectedKeys =
                new HashSet<String>(
                        Arrays.asList(
                                "orderId",
                                "txnRefId",
                                "paymentUrl"));

        Assert.assertEquals(
                "Extra or missing fields found in initiate response",
                expectedKeys,
                decryptedJson.keySet());

        Assert.assertEquals(
                "OrderId mismatch in initiate response",
                expectedOrderId,
                decryptedJson.getString("orderId"));

        Assert.assertFalse(
                "txnRefId should not be empty",
                decryptedJson.getString("txnRefId").trim().isEmpty());

        Assert.assertFalse(
                "paymentUrl should not be empty",
                decryptedJson.getString("paymentUrl").trim().isEmpty());

        return decryptedJson;
    }
}



//Valid initiate request
//Status code validation
//Message validation
//Success flag validation
//Decrypted response field validation
//No extra field validation
//OrderId validation
//TxnRefId validation
//PaymentUrl validation
//UPI URL validation
//TxnRefId inside payment URL validation