package com.taponsusi.websocket;

import com.taponsusi.order.Order;
import com.taponsusi.order.dto.OrderResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReservationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public ReservationEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishReservationCreated(Order order) {
        publish(order.getSellerId(), new OrderEventMessage("RESERVATION_CREATED", OrderResponse.from(order)));
    }

    public void publishOrderUpdated(Order order) {
        publish(order.getSellerId(), new OrderEventMessage("ORDER_UPDATED", OrderResponse.from(order)));
    }

    private void publish(Long sellerId, OrderEventMessage message) {
        messagingTemplate.convertAndSend("/topic/sellers/" + sellerId + "/orders", message);
    }

    public record OrderEventMessage(String eventType, OrderResponse order) {
    }
}