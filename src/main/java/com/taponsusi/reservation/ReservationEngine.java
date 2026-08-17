package com.taponsusi.reservation;

import com.taponsusi.buyerreputation.BuyerReputationService;
import com.taponsusi.common.idempotency.IdempotencyService;
import com.taponsusi.order.Order;
import com.taponsusi.parser.PurchaseIntent;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class ReservationEngine {

    private final IdempotencyService idempotencyService;
    private final ReservationTransactionExecutor transactionExecutor;
    private final BuyerReputationService buyerReputationService;

    public ReservationEngine(IdempotencyService idempotencyService,
                             ReservationTransactionExecutor transactionExecutor, BuyerReputationService buyerReputationService) {
        this.idempotencyService = idempotencyService;
        this.transactionExecutor = transactionExecutor;
        this.buyerReputationService = buyerReputationService;
    }

    public Optional<Order> reserve(String platform, String platformEventId,
                                   PurchaseIntent intent, Long inventoryItemId) {

        if (buyerReputationService.isBlocked(intent.sellerId(), intent.platformUserId())) {
            return Optional.empty();
        }

        if (buyerReputationService.isBlocked(intent.sellerId(), intent.platformUserId())) {
            return Optional.empty(); // silently ignored, same as a duplicate — Section 8 doesn't
            // specify a distinct rejection signal for blocked buyers
        }

        boolean isNew = idempotencyService.isNewEvent(platform, platformEventId);
        if (!isNew) {
            return Optional.empty();
        }

        return Optional.of(transactionExecutor.execute(intent, inventoryItemId));
    }
}