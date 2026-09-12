package com.taponsusi.reservation;

public class ReservationContendedException extends RuntimeException {

    public ReservationContendedException(String message) {
        super(message);
    }

    public ReservationContendedException(String message, Throwable cause) {
        super(message, cause);
    }
}