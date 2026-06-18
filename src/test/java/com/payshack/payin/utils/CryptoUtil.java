package com.payshack.payin.utils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class CryptoUtil {

    private static final String AES_ALGO = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final byte[] IV = "1234567890123456".getBytes(StandardCharsets.UTF_8);

    private CryptoUtil() {}

    /**
     * SHA-256 hash of rawKey, returns first 32 hex chars (128-bit derived key for AES).
     */
    public static String deriveKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    public static String encrypt(String plainText, String rawKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    deriveKey(rawKey).getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(IV));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String encryptedText, String rawKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    deriveKey(rawKey).getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(IV));
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * HMAC-SHA256 using rawKey directly (not derived) — matches API contract.
     */
    public static String generateChecksum(String encryptedBody, String rawKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(rawKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hmac = mac.doFinal(encryptedBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hmac) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Checksum generation failed", e);
        }
    }
}
