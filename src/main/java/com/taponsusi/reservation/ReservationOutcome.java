package com.taponsusi.reservation;

import com.taponsusi.order.Order;
import com.taponsusi.waitlist.WaitlistEntry;

import java.util.Optional;

public record ReservationOutcome(
        ReservationOutcomeType type,
        Order order,
        WaitlistEntry waitlistEntry
) {

    public ReservationOutcome {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Reservation outcome type is required"
            );
        }

        if (type == ReservationOutcomeType.RESERVED) {
            if (order == null || waitlistEntry != null) {
                throw new IllegalArgumentException(
                        "RESERVED outcome requires an order only"
                );
            }
        }

        if (type == ReservationOutcomeType.WAITLISTED) {
            if (waitlistEntry == null || order != null) {
                throw new IllegalArgumentException(
                        "WAITLISTED outcome requires a waitlist entry only"
                );
            }
        }

        if (type == ReservationOutcomeType.DUPLICATE) {
            if (order != null || waitlistEntry != null) {
                throw new IllegalArgumentException(
                        "DUPLICATE outcome cannot contain an order or waitlist entry"
                );
            }
        }
    }

    public static ReservationOutcome reserved(Order order) {
        return new ReservationOutcome(
                ReservationOutcomeType.RESERVED,
                order,
                null
        );
    }

    public static ReservationOutcome waitlisted(
            WaitlistEntry waitlistEntry
    ) {
        return new ReservationOutcome(
                ReservationOutcomeType.WAITLISTED,
                null,
                waitlistEntry
        );
    }

    public static ReservationOutcome duplicate() {
        return new ReservationOutcome(
                ReservationOutcomeType.DUPLICATE,
                null,
                null
        );
    }

    public Optional<Order> optionalOrder() {
        return Optional.ofNullable(order);
    }

    public Optional<WaitlistEntry> optionalWaitlistEntry() {
        return Optional.ofNullable(waitlistEntry);
    }

    public boolean isReserved() {
        return type == ReservationOutcomeType.RESERVED;
    }

    public boolean isWaitlisted() {
        return type == ReservationOutcomeType.WAITLISTED;
    }

    public boolean isDuplicate() {
        return type == ReservationOutcomeType.DUPLICATE;
    }
}