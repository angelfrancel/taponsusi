package com.taponsusi.reservation;

import com.taponsusi.order.Order;
import com.taponsusi.waitlist.WaitlistEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationOutcomeTest {

    @Test
    void reservedOutcomeContainsOnlyOrder() {
        Order order = new Order(
                42L,
                100L,
                200L,
                "buyer-123",
                1,
                1
        );

        ReservationOutcome outcome =
                ReservationOutcome.reserved(order);

        assertThat(outcome.type())
                .isEqualTo(ReservationOutcomeType.RESERVED);

        assertThat(outcome.isReserved()).isTrue();
        assertThat(outcome.optionalOrder()).contains(order);
        assertThat(outcome.optionalWaitlistEntry()).isEmpty();
    }

    @Test
    void waitlistedOutcomeContainsOnlyWaitlistEntry() {
        WaitlistEntry entry = new WaitlistEntry(
                42L,
                100L,
                200L,
                "FACEBOOK",
                "comment-456",
                "buyer-123",
                1,
                Instant.parse("2026-09-16T01:00:00Z")
        );

        ReservationOutcome outcome =
                ReservationOutcome.waitlisted(entry);

        assertThat(outcome.type())
                .isEqualTo(
                        ReservationOutcomeType.WAITLISTED
                );

        assertThat(outcome.isWaitlisted()).isTrue();
        assertThat(outcome.optionalOrder()).isEmpty();
        assertThat(outcome.optionalWaitlistEntry())
                .contains(entry);
    }

    @Test
    void duplicateOutcomeContainsNeitherEntity() {
        ReservationOutcome outcome =
                ReservationOutcome.duplicate();

        assertThat(outcome.type())
                .isEqualTo(
                        ReservationOutcomeType.DUPLICATE
                );

        assertThat(outcome.isDuplicate()).isTrue();
        assertThat(outcome.optionalOrder()).isEmpty();
        assertThat(outcome.optionalWaitlistEntry()).isEmpty();
    }

    @Test
    void rejectsReservedOutcomeWithoutOrder() {
        assertThatThrownBy(() ->
                new ReservationOutcome(
                        ReservationOutcomeType.RESERVED,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires an order");
    }

    @Test
    void rejectsWaitlistedOutcomeWithoutEntry() {
        assertThatThrownBy(() ->
                new ReservationOutcome(
                        ReservationOutcomeType.WAITLISTED,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a waitlist");
    }

    @Test
    void rejectsDuplicateOutcomeContainingOrder() {
        Order order = new Order(
                42L,
                100L,
                200L,
                "buyer-123",
                1,
                1
        );

        assertThatThrownBy(() ->
                new ReservationOutcome(
                        ReservationOutcomeType.DUPLICATE,
                        order,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "cannot contain an order"
                );
    }
}