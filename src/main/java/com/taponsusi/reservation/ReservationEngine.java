package com.taponsusi.reservation;

import com.taponsusi.common.idempotency.IdempotencyService;
import com.taponsusi.order.Order;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.websocket.ReservationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ReservationEngine {

    private final IdempotencyService idempotencyService;
    private final ReservationTransactionExecutor transactionExecutor;
    private final ReservationEventPublisher reservationEventPublisher;

    public ReservationEngine(
            IdempotencyService idempotencyService,
            ReservationTransactionExecutor transactionExecutor,
            ReservationEventPublisher reservationEventPublisher
    ) {
        this.idempotencyService = idempotencyService;
        this.transactionExecutor = transactionExecutor;
        this.reservationEventPublisher =
                reservationEventPublisher;
    }

    public ReservationOutcome decide(
            String platform,
            String platformEventId,
            PurchaseIntent intent,
            Long inventoryItemId
    ) {
        Optional<ReservationOutcome> processed =
                idempotencyService.executeIfNew(
                        platform,
                        platformEventId,
                        () -> transactionExecutor.execute(
                                platform,
                                platformEventId,
                                intent,
                                inventoryItemId
                        )
                );

        ReservationOutcome outcome =
                processed.orElseGet(
                        ReservationOutcome::duplicate
                );

        if (outcome.isReserved()) {
            reservationEventPublisher
                    .publishReservationCreated(
                            outcome.order()
                    );
        }

        return outcome;
    }

    /*
     * Compatibility method for current simulation, webhook, and tests.
     *
     * Existing callers still receive:
     * - an Order for a successful reservation;
     * - Optional.empty() for a duplicate event;
     * - OutOfStockException when the buyer was waitlisted.
     *
     * The waitlist transaction has already committed before the
     * OutOfStockException is raised here.
     */
    public Optional<Order> reserve(
            String platform,
            String platformEventId,
            PurchaseIntent intent,
            Long inventoryItemId
    ) {
        ReservationOutcome outcome = decide(
                platform,
                platformEventId,
                intent,
                inventoryItemId
        );

        if (outcome.isWaitlisted()) {
            throw new OutOfStockException(
                    "Item "
                            + inventoryItemId
                            + " is out of stock; buyer "
                            + intent.platformUserId()
                            + " was added to the waitlist"
            );
        }

        return outcome.optionalOrder();
    }
}