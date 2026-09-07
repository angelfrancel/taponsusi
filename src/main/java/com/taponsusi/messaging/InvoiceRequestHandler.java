package com.taponsusi.messaging;

import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.reservationproof.ReservationProofService;
import com.taponsusi.reservationproof.dto.ReservationProofResponse;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
public class InvoiceRequestHandler {

    private static final String INVOICE_KEYWORD = "INVOICE";

    private final OrderRepository orderRepository;
    private final MessagingProvider messagingProvider;
    private final ReservationProofService reservationProofService;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public InvoiceRequestHandler(OrderRepository orderRepository,
                                 MessagingProvider messagingProvider,
                                 ReservationProofService reservationProofService,
                                 TenantContext tenantContext) {
        this.orderRepository = orderRepository;
        this.messagingProvider = messagingProvider;
        this.reservationProofService = reservationProofService;
        this.tenantContext = tenantContext;
    }

    public boolean isInvoiceRequest(String normalizedMessage) {
        return INVOICE_KEYWORD.equals(normalizedMessage);
    }

    @Transactional(readOnly = true)
    public void handle(String platform, Long sellerId, Long sessionId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        List<Order> orders = orderRepository.findAllBySellerIdAndSessionIdAndBuyerPlatformId(
                sellerId, sessionId, buyerPlatformId);

        if (orders.isEmpty()) {
            messagingProvider.sendMessage(platform, buyerPlatformId,
                    "We don't see any reservations under your account for this session yet.");
            return;
        }

        StringBuilder reply = new StringBuilder("Here's your current reservation summary:\n");
        int totalQuantity = 0;

        for (Order order : orders) {
            totalQuantity += order.getQuantity();
            ReservationProofResponse proof = reservationProofService.getProofForOrder(sellerId, order.getId());
            reply.append("- Order #").append(order.getId())
                    .append(": qty ").append(order.getQuantity())
                    .append(" (").append(order.getStatus()).append(")")
                    .append(" — proof token ").append(proof.proofToken())
                    .append("\n");
        }

        reply.append("Total items reserved: ").append(totalQuantity);

        messagingProvider.sendMessage(platform, buyerPlatformId, reply.toString());
    }
}