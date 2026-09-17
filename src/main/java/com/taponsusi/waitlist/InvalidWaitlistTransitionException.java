package com.taponsusi.waitlist;

public class InvalidWaitlistTransitionException extends RuntimeException {

    public InvalidWaitlistTransitionException(
            WaitlistStatus currentStatus,
            WaitlistStatus requestedStatus
    ) {
        super(
                "Cannot transition waitlist entry from "
                        + currentStatus
                        + " to "
                        + requestedStatus
        );
    }
}