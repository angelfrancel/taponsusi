package com.taponsusi.waitlist;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitlistOfferMetadataTest {

    @Test
    void recordsTheQuantityHeldForAnOffer() {
        WaitlistEntry entry = createEntry(3);

        Instant offeredAt =
                Instant.parse("2026-09-18T10:00:00Z");

        Instant offerExpiresAt =
                Instant.parse("2026-09-18T10:10:00Z");

        entry.markOffered(
                offeredAt,
                offerExpiresAt,
                1
        );

        assertThat(entry.getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);

        assertThat(entry.getOfferedAt())
                .isEqualTo(offeredAt);

        assertThat(entry.getOfferExpiresAt())
                .isEqualTo(offerExpiresAt);

        assertThat(entry.getOfferedQuantity())
                .isEqualTo(1);
    }

    @Test
    void offeredQuantityCannotExceedRequestedQuantity() {
        WaitlistEntry entry = createEntry(2);

        Instant offeredAt =
                Instant.parse("2026-09-18T10:00:00Z");

        assertThatThrownBy(() ->
                entry.markOffered(
                        offeredAt,
                        offeredAt.plusSeconds(600),
                        3
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "cannot exceed requested quantity"
                );
    }

    @Test
    void offerExpirationMustBeAfterOfferTime() {
        WaitlistEntry entry = createEntry(1);

        Instant offeredAt =
                Instant.parse("2026-09-18T10:00:00Z");

        assertThatThrownBy(() ->
                entry.markOffered(
                        offeredAt,
                        offeredAt,
                        1
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "expiration must be after"
                );
    }

    private WaitlistEntry createEntry(
            int requestedQuantity
    ) {
        return new WaitlistEntry(
                101L,
                201L,
                301L,
                "SIMULATION",
                "EVENT-001",
                "BUYER-001",
                requestedQuantity,
                Instant.parse("2026-09-18T09:59:00Z")
        );
    }
}