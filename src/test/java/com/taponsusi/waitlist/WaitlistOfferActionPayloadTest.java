package com.taponsusi.waitlist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitlistOfferActionPayloadTest {

    private final WaitlistOfferActionPayload payloads =
            new WaitlistOfferActionPayload();

    @Test
    void createsAndParsesAcceptPayload() {
        String payload =
                payloads.createAcceptPayload(401L);

        assertThat(payload)
                .isEqualTo("WAITLIST_ACCEPT:401");

        assertThat(payloads.parse(payload))
                .contains(
                        new WaitlistOfferActionCommand(
                                WaitlistOfferActionType.ACCEPT,
                                401L
                        )
                );
    }

    @Test
    void createsAndParsesDeclinePayload() {
        String payload =
                payloads.createDeclinePayload(402L);

        assertThat(payload)
                .isEqualTo("WAITLIST_DECLINE:402");

        assertThat(payloads.parse(payload))
                .contains(
                        new WaitlistOfferActionCommand(
                                WaitlistOfferActionType.DECLINE,
                                402L
                        )
                );
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertThat(
                payloads.parse(
                        "  WAITLIST_ACCEPT:401  "
                )
        ).contains(
                new WaitlistOfferActionCommand(
                        WaitlistOfferActionType.ACCEPT,
                        401L
                )
        );
    }

    @Test
    void rejectsMalformedPayloads() {
        assertThat(payloads.parse(null)).isEmpty();
        assertThat(payloads.parse("")).isEmpty();
        assertThat(payloads.parse("WAITLIST_ACCEPT")).isEmpty();
        assertThat(payloads.parse("WAITLIST_ACCEPT:")).isEmpty();
        assertThat(payloads.parse("WAITLIST_ACCEPT:0")).isEmpty();
        assertThat(payloads.parse("WAITLIST_ACCEPT:-1")).isEmpty();
        assertThat(payloads.parse("WAITLIST_ACCEPT:ABC")).isEmpty();
        assertThat(payloads.parse("WAITLIST_UNKNOWN:401")).isEmpty();
        assertThat(payloads.parse("YES")).isEmpty();
        assertThat(payloads.parse("NO")).isEmpty();
    }

    @Test
    void rejectsModifiedOrConversationalPayloads() {
        assertThat(
                payloads.parse("WAITLIST_ACCEPT:401 PLEASE")
        ).isEmpty();

        assertThat(
                payloads.parse("CLICK WAITLIST_ACCEPT:401")
        ).isEmpty();

        assertThat(
                payloads.parse("waitlist_accept:401")
        ).isEmpty();
    }

    @Test
    void rejectsNumericOverflow() {
        assertThat(
                payloads.parse(
                        "WAITLIST_ACCEPT:999999999999999999999999"
                )
        ).isEmpty();
    }

    @Test
    void cannotCreatePayloadWithoutPersistedEntryId() {
        assertThatThrownBy(() ->
                payloads.createAcceptPayload(null)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Persisted waitlist entry ID"
                );

        assertThatThrownBy(() ->
                payloads.createDeclinePayload(0L)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Persisted waitlist entry ID"
                );
    }
}