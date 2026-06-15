package api_utilities;

import java.util.Random;

public class CommonMethods {

    public static String generateRandomOrderID() {

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        StringBuilder orderId = new StringBuilder();

        Random random = new Random();

        for (int i = 0; i < 32; i++) {
            orderId.append(chars.charAt(random.nextInt(chars.length())));
        }

        return orderId.toString();
    }
}