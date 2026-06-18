package com.payshack.payin.models;

public class StatusCheckRequest {

    private String orderId;

    public String getOrderId() { return orderId; }
    public StatusCheckRequest setOrderId(String orderId) { this.orderId = orderId; return this; }
}
