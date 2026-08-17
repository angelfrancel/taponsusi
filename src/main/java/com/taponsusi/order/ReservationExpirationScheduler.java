package com.taponsusi.order;

import com.taponsusi.audit.AuditService;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Component
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final AuditService auditService;

    @Value("${taponsusi.reservation.safety-net-hours}")
    private long safetyNetHours;

    public ReservationExpirationScheduler(SellerRepository sellerRepository,
                                          OrderRepository orderRepository,
                                          OrderService orderService,
                                          AuditService auditService) {
        this.sellerRepository = sellerRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "300000")
    public void processExpirations() {
        List<Seller> allSellers = sellerRepository.findAll();

        for (Seller seller : allSellers) {
            try {
                expirePendingOrders(seller.getId());
                autoReleaseOverdueOrders(seller.getId());
            } catch (Exception e) {
                // One seller's bad data/edge case must not stop the
                // scheduler from processing every other seller.
                log.error("Expiration processing failed for seller {}", seller.getId(), e);
            }
        }
    }

    private void expirePendingOrders(Long sellerId) {
        List<Order> pastDue = orderRepository.findAllBySellerIdAndStatusAndExpiresAtBefore(
                sellerId, OrderStatus.PENDING, Instant.now());

        for (Order order : pastDue) {
            orderService.transitionStatusInternal(
                    order.getId(), OrderStatus.OVERDUE, sellerId, "EXPIRATION_SCHEDULER");
        }
    }

    private void autoReleaseOverdueOrders(Long sellerId) {
        Instant safetyNetThreshold = Instant.now().minus(safetyNetHours, ChronoUnit.HOURS);

        List<Order> pastSafetyNet = orderRepository.findAllBySellerIdAndStatusAndOverdueSinceBefore(
                sellerId, OrderStatus.OVERDUE, safetyNetThreshold);

        for (Order order : pastSafetyNet) {
            orderService.transitionStatusInternal(
                    order.getId(), OrderStatus.CANCELLED, sellerId, "AUTO_RELEASED_STALE_NO_RESPONSE");

            log.warn("AUTO_RELEASED_STALE_NO_RESPONSE: order {} for seller {} auto-cancelled "
                            + "after {} hours overdue with no seller action",
                    order.getId(), sellerId, safetyNetHours);
        }
    }
}