package com.taponsusi.reservation;

import com.taponsusi.livesession.LiveSessionStatus;

public class SessionNotLiveException extends RuntimeException {

    private final Long sessionId;
    private final LiveSessionStatus actualStatus;

    public SessionNotLiveException(
            Long sessionId,
            LiveSessionStatus actualStatus
    ) {
        super(
                "Live session "
                        + sessionId
                        + " is not accepting reservations because its status is "
                        + actualStatus
        );

        this.sessionId = sessionId;
        this.actualStatus = actualStatus;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public LiveSessionStatus getActualStatus() {
        return actualStatus;
    }
}