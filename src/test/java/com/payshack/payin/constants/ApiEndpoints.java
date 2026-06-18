package com.payshack.payin.constants;

public final class ApiEndpoints {

    private ApiEndpoints() {}

    public static final String PAYIN_INITIATE_INTENT =
            "/payshack-payin/api/v1/payin/ext/txn/initiate-intent";

    public static final String PAYIN_STATUS_CHECK =
            "/indigate-payin-svc/api/v1/payin/ext/txn/status";
}
