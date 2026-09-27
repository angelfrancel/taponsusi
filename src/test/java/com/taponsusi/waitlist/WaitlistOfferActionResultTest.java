package com.taponsusi.waitlist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistOfferActionResultTest {

    @Test
    void acceptedResultContainsCreatedOrder() {
        WaitlistOfferActionResult result =
                WaitlistOfferActionResult.accepted(
                        401L,
                        501L
                );

        assertThat(result.action())
                .isEqualTo(WaitlistOfferActionType.ACCEPT);
        assertThat(result.waitlistEntryId()).isEqualTo(401L);
        assertThat(result.createdOrderId()).isEqualTo(501L);
        assertThat(result.nextOfferedEntryId()).isNull();
    }

    @Test
    void declinedResultContainsNextOffer() {
        WaitlistOfferActionResult result =
                WaitlistOfferActionResult.declined(
                        401L,
                        402L
                );

        assertThat(result.action())
                .isEqualTo(WaitlistOfferActionType.DECLINE);
        assertThat(result.waitlistEntryId()).isEqualTo(401L);
        assertThat(result.createdOrderId()).isNull();
        assertThat(result.nextOfferedEntryId()).isEqualTo(402L);
    }
}