package com.payshack.payin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.payshack.payin.constants.ApiEndpoints;
import com.payshack.payin.models.InitiateIntentRequest;
import com.payshack.payin.models.StatusCheckRequest;
import com.payshack.payin.utils.ConfigReader;
import com.payshack.payin.utils.CryptoUtil;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;

public class PayinApiService {

    private static final Logger log = LoggerFactory.getLogger(PayinApiService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String baseUrl;
    private final String clientId;
    private final String secretKey;
    private final String encryptionKey;

    public PayinApiService() {
        this.baseUrl = ConfigReader.get("apibaseUrl");
        this.clientId = ConfigReader.get("clientId");
        this.secretKey = ConfigReader.get("secreteKey");
        this.encryptionKey = ConfigReader.get("encryptionkey");
    }

    public Response initiateIntent(InitiateIntentRequest request) {
        String body = buildEncryptedRequestBody(request);
        log.info("POST {} | orderId={}", ApiEndpoints.PAYIN_INITIATE_INTENT, request.getOrderId());

        return given()
                .baseUri(baseUrl)
                .header("client-id", clientId)
                .header("secret-key", secretKey)
                .contentType("application/json")
                .body(body)
                .log().all()
                .when()
                .post(ApiEndpoints.PAYIN_INITIATE_INTENT)
                .then()
                .log().all()
                .extract().response();
    }

    public Response checkStatus(String orderId) {
        StatusCheckRequest request = new StatusCheckRequest().setOrderId(orderId);
        String body = buildEncryptedRequestBody(request);
        log.info("POST {} | orderId={}", ApiEndpoints.PAYIN_STATUS_CHECK, orderId);

        return given()
                .baseUri(baseUrl)
                .header("client-id", clientId)
                .header("secret-key", secretKey)
                .contentType("application/json")
                .body(body)
//                .log().all()
                .when()
                .post(ApiEndpoints.PAYIN_STATUS_CHECK)
                .then()
//                .log().all()
                .extract().response();
    }

    /**
     * Decrypts the "data" field from an API response.
     * Returns the raw decrypted JSON string for the test to parse.
     */
    public String decryptResponseData(Response response) {
        String encryptedData = response.jsonPath().getString("data");
        if (encryptedData == null) {
            throw new RuntimeException("Response has no 'data' field: " + response.asString());
        }
        String decrypted = CryptoUtil.decrypt(encryptedData, encryptionKey);
        log.debug("Decrypted response: {}", decrypted);
        return decrypted;
    }

    /**
     * Decrypts the "encryptedData" field from a provider callback payload.
     * Returns the raw decrypted JSON string.
     */
    public String decryptCallbackPayload(String rawPayload) {
        try {
            JsonNode node = mapper.readTree(rawPayload);
            String encryptedData = node.path("encryptedData").asText();
            if (encryptedData.isEmpty()) {
                throw new RuntimeException("Callback has no 'encryptedData' field: " + rawPayload);
            }
            String decrypted = CryptoUtil.decrypt(encryptedData, encryptionKey);
            log.debug("Decrypted callback: {}", decrypted);
            return decrypted;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt callback payload", e);
        }
    }

    private String buildEncryptedRequestBody(Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            String encryptedBody = CryptoUtil.encrypt(json, encryptionKey);
            String checksum = CryptoUtil.generateChecksum(encryptedBody, encryptionKey);

            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("checksum", checksum);
            wrapper.put("encryptedBody", encryptedBody);
            return mapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build encrypted request body", e);
        }
    }
}
