package com.taponsusi.reservation;

import com.taponsusi.buyerreputation.BuyerReputationService;
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
    private final BuyerReputationService buyerReputationService;
    private final ReservationEventPublisher reservationEventPublisher;

    public ReservationEngine(IdempotencyService idempotencyService,
                             ReservationTransactionExecutor transactionExecutor,
                             BuyerReputationService buyerReputationService,
                             ReservationEventPublisher reservationEventPublisher) {
        this.idempotencyService = idempotencyService;
        this.transactionExecutor = transactionExecutor;
        this.buyerReputationService = buyerReputationService;
        this.reservationEventPublisher = reservationEventPublisher;
    }

    public Optional<Order> reserve(String platform, String platformEventId,
                                   PurchaseIntent intent, Long inventoryItemId) {

        if (buyerReputationService.isBlocked(intent.sellerId(), intent.platformUserId())) {
            return Optional.empty();
        }

        boolean isNew = idempotencyService.isNewEvent(platform, platformEventId);
        if (!isNew) {
            return Optional.empty();
        }

        // execute() is @Transactional on a different bean — by the time
        // it returns here, the transaction has committed. Safe to push.
        Order order = transactionExecutor.execute(intent, inventoryItemId);
        reservationEventPublisher.publishReservationCreated(order);
        return Optional.of(order);
    }
}