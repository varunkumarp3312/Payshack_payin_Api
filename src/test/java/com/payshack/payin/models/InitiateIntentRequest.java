package com.payshack.payin.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitiateIntentRequest {

    private String orderId;
    private String amount;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String userIP;
    private String userId;

    public String getOrderId() { return orderId; }
    public InitiateIntentRequest setOrderId(String orderId) { this.orderId = orderId; return this; }

    public String getAmount() { return amount; }
    public InitiateIntentRequest setAmount(String amount) { this.amount = amount; return this; }

    public String getFirstName() { return firstName; }
    public InitiateIntentRequest setFirstName(String firstName) { this.firstName = firstName; return this; }

    public String getLastName() { return lastName; }
    public InitiateIntentRequest setLastName(String lastName) { this.lastName = lastName; return this; }

    public String getEmail() { return email; }
    public InitiateIntentRequest setEmail(String email) { this.email = email; return this; }

    public String getPhone() { return phone; }
    public InitiateIntentRequest setPhone(String phone) { this.phone = phone; return this; }

    public String getUserIP() { return userIP; }
    public InitiateIntentRequest setUserIP(String userIP) { this.userIP = userIP; return this; }

    public String getUserId() { return userId; }
    public InitiateIntentRequest setUserId(String userId) { this.userId = userId; return this; }
}
