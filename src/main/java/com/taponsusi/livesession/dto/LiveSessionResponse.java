package com.taponsusi.livesession.dto;

import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionStatus;

import java.time.Instant;

public record LiveSessionResponse(
        Long id,
        String title,
        String platform,
        LiveSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt
) {
    public static LiveSessionResponse from(LiveSession session) {
        return new LiveSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getPlatform(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt()
        );
    }
}