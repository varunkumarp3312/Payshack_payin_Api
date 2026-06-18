package com.payshack.payin.utils;

/**
 * Thread-safe store for sharing data across test methods within the same thread.
 * Use clear() in @After to avoid state leaking between tests.
 */
public final class TestContext {

    private TestContext() {}

    private static final ThreadLocal<String> orderId = new ThreadLocal<>();
    private static final ThreadLocal<String> txnRefId = new ThreadLocal<>();
    private static final ThreadLocal<String> paymentUrl = new ThreadLocal<>();
    private static final ThreadLocal<String> transactionStatus = new ThreadLocal<>();
    private static final ThreadLocal<String> utr = new ThreadLocal<>();
    private static final ThreadLocal<String> amount = new ThreadLocal<>();

    public static String getOrderId() { return orderId.get(); }
    public static void setOrderId(String value) { orderId.set(value); }

    public static String getTxnRefId() { return txnRefId.get(); }
    public static void setTxnRefId(String value) { txnRefId.set(value); }

    public static String getPaymentUrl() { return paymentUrl.get(); }
    public static void setPaymentUrl(String value) { paymentUrl.set(value); }

    public static String getTransactionStatus() { return transactionStatus.get(); }
    public static void setTransactionStatus(String value) { transactionStatus.set(value); }

    public static String getUtr() { return utr.get(); }
    public static void setUtr(String value) { utr.set(value); }

    public static String getAmount() { return amount.get(); }
    public static void setAmount(String value) { amount.set(value); }

    public static void clear() {
        orderId.remove();
        txnRefId.remove();
        paymentUrl.remove();
        transactionStatus.remove();
        utr.remove();
        amount.remove();
    }
}
