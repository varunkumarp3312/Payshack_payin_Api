package com.payshack.payin.utils;

import java.util.Random;

public final class TestDataGenerator {

    private static final String ORDER_ID_CHARS = "ABCDEFGHIJKLMNOP765ERTYGFRTY123456789";
    private static final int ORDER_ID_LENGTH = 20;
    private static final Random RANDOM = new Random();

    private TestDataGenerator() {}

    public static String generateOrderId() {
        StringBuilder sb = new StringBuilder(ORDER_ID_LENGTH);
        for (int i = 0; i < ORDER_ID_LENGTH; i++) {
            sb.append(ORDER_ID_CHARS.charAt(RANDOM.nextInt(ORDER_ID_CHARS.length())));
        }
        return sb.toString();
    }
}
