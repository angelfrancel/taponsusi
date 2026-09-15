package com.taponsusi.parser;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseIntentSourceTimestampTest {

    @Test
    void parserPreservesPlatformSourceTimestamp() {
        PurchaseIntentParser parser =
                new PurchaseIntentParser();

        Instant sourceTime =
                Instant.parse("2026-09-14T02:30:45Z");

        PurchaseIntent intent = parser.parse(
                42L,
                100L,
                "buyer-123",
                "MINE SWERTE X2",
                sourceTime
        );

        assertThat(intent.reservationCode())
                .isEqualTo("SWERTE");

        assertThat(intent.quantity())
                .isEqualTo(2);

        assertThat(intent.sourceCreatedAt())
                .isEqualTo(sourceTime);
    }
}