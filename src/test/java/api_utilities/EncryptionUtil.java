package api_utilities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtil {

    private static final String IV = "1234567890123456";

    public static String deriveKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.substring(0, 32);

        } catch (Exception e) {
            throw new RuntimeException("Failed to derive key", e);
        }
    }

    public static String encrypt(String jsonPayload, String key) {
        try {
            String derivedKey = deriveKey(key);

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            derivedKey.getBytes(StandardCharsets.UTF_8),
                            "AES");

            IvParameterSpec ivSpec =
                    new IvParameterSpec(
                            IV.getBytes(StandardCharsets.UTF_8));

            Cipher cipher =
                    Cipher.getInstance("AES/CBC/PKCS5Padding");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            byte[] encrypted =
                    cipher.doFinal(
                            jsonPayload.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String getChecksum(String encryptedBody, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(
                            key.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256");

            mac.init(secretKeySpec);

            byte[] hmac =
                    mac.doFinal(
                            encryptedBody.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();

            for (byte b : hmac) {
                String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException("Checksum generation failed", e);
        }
    }
}