package com.taponsusi.livesession;

public class InvalidLiveSessionTransitionException
        extends RuntimeException {

    public InvalidLiveSessionTransitionException(
            LiveSessionStatus currentStatus,
            LiveSessionStatus requestedStatus
    ) {
        super(
                "Cannot transition live session from "
                        + currentStatus
                        + " to "
                        + requestedStatus
        );
    }
}