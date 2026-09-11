package com.taponsusi.order;

import com.taponsusi.security.TenantContext;
import com.taponsusi.websocket.ReservationEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Component
public class SellerExpirationProcessor {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final TenantContext tenantContext;
    private final ReservationEventPublisher reservationEventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${taponsusi.reservation.safety-net-hours}")
    private long safetyNetHours;

    public SellerExpirationProcessor(
            OrderRepository orderRepository,
            OrderService orderService,
            TenantContext tenantContext,
            ReservationEventPublisher reservationEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.tenantContext = tenantContext;
        this.reservationEventPublisher = reservationEventPublisher;
    }

    @Transactional
    public void processSeller(Long sellerId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        expirePendingOrders(sellerId);
        autoReleaseOverdueOrders(sellerId);
    }

    private void expirePendingOrders(Long sellerId) {
        List<Order> pastDue =
                orderRepository.findAllBySellerIdAndStatusAndExpiresAtBefore(
                        sellerId,
                        OrderStatus.PENDING,
                        Instant.now()
                );

        for (Order order : pastDue) {
            Order updated = orderService.transitionStatusInternal(
                    order.getId(),
                    OrderStatus.OVERDUE,
                    sellerId,
                    "EXPIRATION_SCHEDULER"
            );

            reservationEventPublisher.publishOrderUpdated(updated);
        }
    }

    private void autoReleaseOverdueOrders(Long sellerId) {
        Instant safetyNetThreshold = Instant.now()
                .minus(safetyNetHours, ChronoUnit.HOURS);

        List<Order> pastSafetyNet =
                orderRepository.findAllBySellerIdAndStatusAndOverdueSinceBefore(
                        sellerId,
                        OrderStatus.OVERDUE,
                        safetyNetThreshold
                );

        for (Order order : pastSafetyNet) {
            Order updated = orderService.transitionStatusInternal(
                    order.getId(),
                    OrderStatus.CANCELLED,
                    sellerId,
                    "AUTO_RELEASED_STALE_NO_RESPONSE"
            );

            reservationEventPublisher.publishOrderUpdated(updated);
        }
    }
}