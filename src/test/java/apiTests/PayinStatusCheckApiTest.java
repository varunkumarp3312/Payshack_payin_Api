package apiTests;

import static io.restassured.RestAssured.given;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import api_utilities.ConfigReader;
import api_utilities.DecryptUtil;
import api_utilities.EncryptionUtil;
import api_utilities.TestDataStore;
import io.restassured.response.Response;

public class PayinStatusCheckApiTest {

    String baseUrl = ConfigReader.getprop("apibaseUrl");

    String endpoint = "/indigate-payin-svc/api/v1/payin/ext/txn/status";

    String secretKey = ConfigReader.getprop("secreteKey");

    String clientId = ConfigReader.getprop("clientId");

    String encryptionKey = ConfigReader.getprop("encryptionkey");

    @Test
    public void verifyPayinStatusCheckApi() {

        Assert.assertNotNull(
                "Order ID is null. Run Initiate Intent API first.",
                TestDataStore.orderId);

        JSONObject actualPayload = new JSONObject();

        actualPayload.put("orderId", TestDataStore.orderId);

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
                    .post(endpoint)

                .then()
                    .log().all()
                    .extract()
                    .response();

        Assert.assertEquals(200, response.statusCode());

        JSONObject responseJson =
                new JSONObject(response.asString());

        Assert.assertEquals(200, responseJson.getInt("statusCode"));
        Assert.assertEquals("OK", responseJson.getString("status"));
        Assert.assertEquals("Decrypted Successfully!", responseJson.getString("message"));
        Assert.assertTrue(responseJson.getBoolean("success"));

        Set<String> expectedMainKeys =
                new HashSet<String>(
                        Arrays.asList(
                                "timestamp",
                                "statusCode",
                                "status",
                                "message",
                                "success",
                                "data"));

        Assert.assertEquals(
                "Extra or missing keys found in main response",
                expectedMainKeys,
                responseJson.keySet());

        String encryptedResponseData =
                responseJson.getString("data");

        String decryptedResponse =
                DecryptUtil.decrypt(
                        encryptedResponseData,
                        encryptionKey);

        System.out.println("Decrypted Status Response is : " + decryptedResponse);

        JSONObject dataJson =
                new JSONObject(decryptedResponse);

        Set<String> expectedDataKeys =
                new HashSet<String>(
                        Arrays.asList(
                                "status",
                                "txnRefId",
                                "utr",
                                "orderId",
                                "amount"));

        Assert.assertEquals(
                "Extra or missing keys found in decrypted data response",
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
                "Order ID mismatch",
                TestDataStore.orderId,
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

        TestDataStore.txnRefId =
                dataJson.getString("txnRefId");

        System.out.println("Order ID   : " + dataJson.getString("orderId"));
        System.out.println("Status     : " + actualStatus);
        System.out.println("Txn Ref ID : " + dataJson.getString("txnRefId"));
        System.out.println("UTR        : " + dataJson.get("utr"));
        System.out.println("Amount     : " + dataJson.get("amount"));
    }
}