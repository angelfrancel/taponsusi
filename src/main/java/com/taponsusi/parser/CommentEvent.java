package com.taponsusi.parser;

import java.time.Instant;

public record CommentEvent(
        String platform,
        String platformEventId,
        String platformUserId,
        Long sellerId,
        Long sessionId,
        String rawMessage,
        Instant sourceCreatedAt
) {

    /*
     * Preserves compatibility with older tests and callers.
     * New platform integrations should provide the source timestamp.
     */
    public CommentEvent(
            String platform,
            String platformEventId,
            String platformUserId,
            Long sellerId,
            Long sessionId,
            String rawMessage
    ) {
        this(
                platform,
                platformEventId,
                platformUserId,
                sellerId,
                sessionId,
                rawMessage,
                Instant.now()
        );
    }
}