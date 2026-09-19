package com.taponsusi.waitlist;

public class WaitlistOfferNotAcceptableException
        extends RuntimeException {

    public WaitlistOfferNotAcceptableException(
            String message
    ) {
        super(message);
    }
}