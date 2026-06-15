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

public class PayinEndToEndApiTest {

    String baseUrl = ConfigReader.getprop("apibaseUrl");

    String initiateEndpoint =
            "/payshack-payin/api/v1/payin/ext/txn/initiate-intent";

    String statusEndpoint =
            "/indigate-payin-svc/api/v1/payin/ext/txn/status";

    String secretKey = ConfigReader.getprop("secreteKey");

    String clientId = ConfigReader.getprop("clientId");

    String encryptionKey = ConfigReader.getprop("encryptionkey");
    boolean success = false;

    @Test
    public void verifyPayinInitiateAndStatusCheckFlow() throws InterruptedException {

        String orderId = initiatePayinTransaction();
        verifyPayinStatus(orderId);

        
       
        
       
    }

    

    public String initiatePayinTransaction() {

        String orderId = CommonMethods.generateRandomOrderID();

        TestDataStore.orderId = orderId;

        JSONObject actualPayload = new JSONObject();

        actualPayload.put("orderId", orderId);
        actualPayload.put("amount", "1000");
        actualPayload.put("firstName", "pavan");
        actualPayload.put("lastName", "kumar");
        actualPayload.put("email", "pmkirru33@gmail.com");
        actualPayload.put("phone", "8970411423");
        actualPayload.put("userIP", "10.210.10");
        actualPayload.put("userId", "dddd");

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

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals(200, response.jsonPath().getInt("statusCode"));
        Assert.assertEquals("OK", response.jsonPath().getString("status"));
        Assert.assertEquals(
                "Transaction Initiated Successfully",
                response.jsonPath().getString("message"));
        Assert.assertTrue(response.jsonPath().getBoolean("success"));

        String encryptedResponseData =
                response.jsonPath().getString("data");

        String decryptedResponse =
                DecryptUtil.decrypt(
                        encryptedResponseData,
                        encryptionKey);

        System.out.println("Initiate Decrypted Response is : " + decryptedResponse);

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
                orderId,
                decryptedJson.getString("orderId"));

        Assert.assertFalse(
                "txnRefId should not be empty",
                decryptedJson.getString("txnRefId").trim().isEmpty());

        Assert.assertFalse(
                "paymentUrl should not be empty",
                decryptedJson.getString("paymentUrl").trim().isEmpty());

        TestDataStore.orderId =
                decryptedJson.getString("orderId");

        TestDataStore.txnRefId =
                decryptedJson.getString("txnRefId");

        TestDataStore.paymentUrl =
                decryptedJson.getString("paymentUrl");

        System.out.println("OrderId    : " + TestDataStore.orderId);
        System.out.println("TxnRefId   : " + TestDataStore.txnRefId);
        System.out.println("PaymentUrl : " + TestDataStore.paymentUrl);

        return TestDataStore.orderId;
    }
   

    public void verifyPayinStatus(String orderId) throws InterruptedException{
    	Thread.sleep(10000);
        JSONObject actualPayload = new JSONObject();

        actualPayload.put("orderId", orderId);

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
                    .post(statusEndpoint)
                    

                .then()
                    .log().all()
                    .extract()
                    .response();
        
        

        Assert.assertEquals(200, response.statusCode());

        JSONObject responseJson =
                new JSONObject(response.asString());

        Assert.assertEquals(200, responseJson.getInt("statusCode"));
        Assert.assertEquals("OK", responseJson.getString("status"));
        Assert.assertTrue(responseJson.getBoolean("success"));

        Assert.assertTrue(
                "Response data field is missing",
                responseJson.has("data"));

        Object dataObject =
                responseJson.get("data");

        JSONObject dataJson;

        if (dataObject instanceof JSONObject) {
            dataJson = responseJson.getJSONObject("data");
        } else {
            String decryptedStatusResponse =
                    DecryptUtil.decrypt(
                            responseJson.getString("data"),
                            encryptionKey);

            System.out.println(
                    "Status Decrypted Response is : "
                            + decryptedStatusResponse);

            dataJson =
                    new JSONObject(decryptedStatusResponse);
        }

        Set<String> expectedDataKeys =
                new HashSet<String>(
                        Arrays.asList(
                                "status",
                                "txnRefId",
                                "utr",
                                "orderId",
                                "amount"));

        Assert.assertEquals(
                "Extra or missing fields found in status data response",
                expectedDataKeys,
                dataJson.keySet());

        Set<String> allowedStatuses =
                new HashSet<String>(
                        Arrays.asList(
                                "INITIATED",
                                "SUCCESS",
                                "FAILED",
                                "PENDING",
                                "TAMPERED",
                                "DUPLICATE",
                                "EXPIRED",
                                "REFUNDED",
                                "TRANSACTION IN PROCESS",
                                "INCOMPLETE",
                                "HOLD"));

        String actualStatus =
                dataJson.getString("status");

        Assert.assertTrue(
                "Invalid transaction status : " + actualStatus,
                allowedStatuses.contains(actualStatus));

        Assert.assertEquals(
                "OrderId mismatch in status response",
                orderId,
                dataJson.getString("orderId"));

        Assert.assertNotNull(
                "txnRefId should not be null",
                dataJson.get("txnRefId"));

        Assert.assertNotNull(
                "utr should not be null",
                dataJson.get("utr"));

        Assert.assertNotNull(
                "amount should not be null",
                dataJson.get("amount"));

        TestDataStore.transactionStatus =
                actualStatus;

        TestDataStore.txnRefId =
                dataJson.getString("txnRefId");

        TestDataStore.utr =
                dataJson.get("utr").toString();

        TestDataStore.amount =
                dataJson.get("amount").toString();

        System.out.println("Status OrderId : " + dataJson.getString("orderId"));
        System.out.println("Status         : " + actualStatus);
        System.out.println("TxnRefId       : " + dataJson.getString("txnRefId"));
        System.out.println("UTR            : " + dataJson.get("utr"));
        System.out.println("Amount         : " + dataJson.get("amount"));
    }
}