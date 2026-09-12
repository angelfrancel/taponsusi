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
        this.reservationEventPublisher = reservationEventPublisher;
    }

    public Optional<Order> reserve(
            String platform,
            String platformEventId,
            PurchaseIntent intent,
            Long inventoryItemId
    ) {
        Optional<Order> result = idempotencyService.executeIfNew(
                platform,
                platformEventId,
                () -> transactionExecutor.execute(
                        intent,
                        inventoryItemId
                )
        );

        result.ifPresent(
                reservationEventPublisher::publishReservationCreated
        );

        return result;
    }
}