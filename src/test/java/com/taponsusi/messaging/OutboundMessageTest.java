package com.taponsusi.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundMessageTest {

    @Test
    void newMessageStartsPendingWithoutAttempts() {
        OutboundMessage message = createMessage();

        assertThat(message.getStatus())
                .isEqualTo(
                        OutboundMessageStatus.PENDING
                );

        assertThat(message.getAttemptCount())
                .isZero();

        assertThat(message.getSentAt())
                .isNull();

        assertThat(message.getLastError())
                .isNull();
    }

    @Test
    void successfulDeliveryMarksMessageSent() {
        OutboundMessage message = createMessage();

        Instant sentAt =
                Instant.parse("2026-09-19T10:00:00Z");

        message.markSent(sentAt);

        assertThat(message.getStatus())
                .isEqualTo(
                        OutboundMessageStatus.SENT
                );

        assertThat(message.getAttemptCount())
                .isEqualTo(1);

        assertThat(message.getSentAt())
                .isEqualTo(sentAt);

        assertThat(message.getLastError())
                .isNull();
    }

    @Test
    void failedDeliveryRecordsFailure() {
        OutboundMessage message = createMessage();

        message.markFailed(
                "Temporary provider failure"
        );

        assertThat(message.getStatus())
                .isEqualTo(
                        OutboundMessageStatus.FAILED
                );

        assertThat(message.getAttemptCount())
                .isEqualTo(1);

        assertThat(message.getLastError())
                .isEqualTo(
                        "Temporary provider failure"
                );

        assertThat(message.getSentAt())
                .isNull();
    }

    @Test
    void failureMessageIsLimitedToDatabaseLength() {
        OutboundMessage message = createMessage();

        message.markFailed("x".repeat(1200));

        assertThat(message.getLastError())
                .hasSize(1000);
    }

    private OutboundMessage createMessage() {
        return new OutboundMessage(
                101L,
                OutboundMessageType.WAITLIST_OFFER,
                "WAITLIST-OFFER:401",
                "SIMULATION",
                "BUYER-001",
                "The item is available again."
        );
    }
}