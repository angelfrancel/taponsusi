package com.taponsusi.messaging;

public record OutboundMessageQueuedEvent(
        Long sellerId,
        Long messageId
) {
}