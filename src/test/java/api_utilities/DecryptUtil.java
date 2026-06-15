package api_utilities;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DecryptUtil {

    private static final String IV = "1234567890123456";

    public static String decrypt(String encryptedResponseData, String key) {
        try {
            String derivedKey = EncryptionUtil.deriveKey(key);

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            derivedKey.getBytes(StandardCharsets.UTF_8),
                            "AES");

            IvParameterSpec ivSpec =
                    new IvParameterSpec(
                            IV.getBytes(StandardCharsets.UTF_8));

            Cipher cipher =
                    Cipher.getInstance("AES/CBC/PKCS5Padding");

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] decodedBytes =
                    Base64.getDecoder().decode(encryptedResponseData);

            byte[] decryptedBytes =
                    cipher.doFinal(decodedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}