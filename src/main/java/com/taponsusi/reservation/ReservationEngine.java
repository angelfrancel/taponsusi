package com.taponsusi.reservation;

import com.taponsusi.common.idempotency.IdempotencyService;
import com.taponsusi.order.Order;
import com.taponsusi.parser.PurchaseIntent;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class ReservationEngine {

    private final IdempotencyService idempotencyService;
    private final ReservationTransactionExecutor transactionExecutor;

    public ReservationEngine(IdempotencyService idempotencyService,
                             ReservationTransactionExecutor transactionExecutor) {
        this.idempotencyService = idempotencyService;
        this.transactionExecutor = transactionExecutor;
    }

    public Optional<Order> reserve(String platform, String platformEventId,
                                   PurchaseIntent intent, Long inventoryItemId) {

        boolean isNew = idempotencyService.isNewEvent(platform, platformEventId);
        if (!isNew) {
            return Optional.empty();
        }

        return Optional.of(transactionExecutor.execute(intent, inventoryItemId));
    }
}