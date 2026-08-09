package com.taponsusi.parser;


public class PurchaseIntentRejectedException extends RuntimeException {

    private final String reasonCode;

    public PurchaseIntentRejectedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}