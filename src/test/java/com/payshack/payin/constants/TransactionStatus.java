package com.payshack.payin.constants;

public enum TransactionStatus {
    INITIATED,
    SUCCESS,
    FAILED,
    PENDING,
    TAMPERED,
    DUPLICATE,
    EXPIRED,
    REFUNDED,
    TRANSACTION_IN_PROCESS("TRANSACTION IN PROCESS"),
    INCOMPLETE,
    HOLD;

    private final String value;

    TransactionStatus() {
        this.value = name();
    }

    TransactionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static boolean isValid(String status) {
        if (status == null) return false;
        for (TransactionStatus ts : values()) {
            if (ts.getValue().equalsIgnoreCase(status.trim())) return true;
        }
        return false;
    }
}
