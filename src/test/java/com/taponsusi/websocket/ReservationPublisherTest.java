package com.taponsusi.websocket;

import com.taponsusi.order.Order;
import com.taponsusi.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;


@ExtendWith(MockitoExtension.class)
class ReservationEventPublisherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishesReservationCreatedToSellerScopedTopic() {
        ReservationEventPublisher publisher = new ReservationEventPublisher(messagingTemplate);
        Order order = new Order(42L, 10L, 100L, "BUYER_X", 1, 1);

        publisher.publishReservationCreated(order);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/sellers/42/orders"),
                argThat((ReservationEventPublisher.OrderEventMessage msg) ->
                        msg.eventType().equals("RESERVATION_CREATED")
                                && msg.order().buyerPlatformId().equals("BUYER_X")));
    }

    @Test
    void publishesOrderUpdatedToSellerScopedTopic() {
        ReservationEventPublisher publisher = new ReservationEventPublisher(messagingTemplate);
        Order order = new Order(42L, 10L, 100L, "BUYER_X", 1, 1);
        order.setStatus(OrderStatus.PAID);

        publisher.publishOrderUpdated(order);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/sellers/42/orders"),
                argThat((ReservationEventPublisher.OrderEventMessage msg) ->
                        msg.eventType().equals("ORDER_UPDATED")
                                && msg.order().status() == OrderStatus.PAID));
    }
}