package com.taponsusi.waitlist;

public class WaitlistOfferNotAcceptableException
        extends RuntimeException {

    public enum Reason {
        WRONG_BUYER,
        INVALID_STATE
    }

    private final Reason reason;

    public WaitlistOfferNotAcceptableException(
            String message
    ) {
        this(message, Reason.INVALID_STATE);
    }

    public WaitlistOfferNotAcceptableException(
            String message,
            Reason reason
    ) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
