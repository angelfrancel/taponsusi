package com.taponsusi.parser;


public record CommentEvent(
        String platform,
        String platformEventId,
        String platformUserId,
        Long sellerId,
        Long sessionId,
        String rawMessage
) {
}