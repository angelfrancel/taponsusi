package com.taponsusi.inventory;

public class InvalidReservationCodeException
        extends RuntimeException {

    public InvalidReservationCodeException(String message) {
        super(message);
    }
}