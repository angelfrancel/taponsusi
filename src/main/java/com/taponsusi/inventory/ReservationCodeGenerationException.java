package com.taponsusi.inventory;

public class ReservationCodeGenerationException extends RuntimeException {

    public ReservationCodeGenerationException() {
        super("Unable to generate a unique reservation code");
    }
}