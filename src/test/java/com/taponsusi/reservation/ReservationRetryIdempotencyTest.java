package com.taponsusi.reservation;

import com.taponsusi.order.Order;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.websocket.ReservationEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReservationRetryIdempotencyTest {

    @Autowired
    private ReservationEngine reservationEngine;

    @MockBean
    private ReservationTransactionExecutor transactionExecutor;

    @MockBean
    private ReservationEventPublisher reservationEventPublisher;

    @Test
    void retryableFailureDoesNotPermanentlyConsumePlatformEvent() {
        String platform = "SIMULATION";

        String platformEventId =
                "RETRYABLE_EVENT_" + UUID.randomUUID();

        Long sellerId = 9_000_001L;
        Long sessionId = 9_000_002L;
        Long inventoryItemId = 9_000_003L;

        PurchaseIntent intent = new PurchaseIntent(
                "RETRY_BUYER",
                sellerId,
                sessionId,
                "RETRY-CODE",
                1
        );

        Order successfulRetry = new Order(
                sellerId,
                sessionId,
                inventoryItemId,
                "RETRY_BUYER",
                1,
                1
        );

        ReservationOutcome successfulOutcome =
                ReservationOutcome.reserved(
                        successfulRetry
                );

        when(transactionExecutor.execute(
                platform,
                platformEventId,
                intent,
                inventoryItemId
        ))
                .thenThrow(
                        new ReservationContendedException(
                                "Temporary lock contention"
                        )
                )
                .thenReturn(successfulOutcome);

        assertThatThrownBy(() ->
                reservationEngine.reserve(
                        platform,
                        platformEventId,
                        intent,
                        inventoryItemId
                )
        ).isInstanceOf(
                ReservationContendedException.class
        );

        Optional<Order> retryResult =
                reservationEngine.reserve(
                        platform,
                        platformEventId,
                        intent,
                        inventoryItemId
                );

        assertThat(retryResult)
                .as(
                        "A retryable failure must not permanently consume the event"
                )
                .contains(successfulRetry);

        verify(transactionExecutor, times(2))
                .execute(
                        platform,
                        platformEventId,
                        intent,
                        inventoryItemId
                );

        verify(reservationEventPublisher, times(1))
                .publishReservationCreated(
                        successfulRetry
                );
    }
}