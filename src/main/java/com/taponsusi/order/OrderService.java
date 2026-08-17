package com.taponsusi.order;

import com.taponsusi.audit.AuditService;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final TenantContext tenantContext;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderService(OrderRepository orderRepository, OrderStateMachine orderStateMachine,
                        TenantContext tenantContext, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.orderStateMachine = orderStateMachine;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
    }

    @Transactional
    public Order transitionStatus(Long orderId, OrderStatus newStatus) {
        Long sellerId = AuthenticatedSeller.currentSellerId();
        return transitionStatusInternal(orderId, newStatus, sellerId, "SELLER_ACTION");
    }

    /**
     * Shared by the authenticated-user path (above) and the
     * scheduler (which has no SecurityContext — see
     * ReservationExpirationScheduler). sellerId is always passed
     * explicitly, never re-derived from thread-local state.
     */
    @Transactional
    public Order transitionStatusInternal(Long orderId, OrderStatus newStatus, Long sellerId, String reason) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getSellerId().equals(sellerId))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        orderStateMachine.validate(oldStatus, newStatus);

        if (newStatus == OrderStatus.CANCELLED) {
            releaseStock(order);
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.OVERDUE) {
            order.setOverdueSince(java.time.Instant.now());
        }

        Order saved = orderRepository.save(order);

        auditService.record(sellerId, "ORDER_STATUS_CHANGED", "Order", orderId,
                oldStatus + " -> " + newStatus + " (" + reason + ")");

        return saved;
    }


    private void releaseStock(Order order) {
        InventoryItem item = entityManager.find(
                InventoryItem.class, order.getInventoryItemId(), LockModeType.PESSIMISTIC_WRITE);

        if (item != null) {
            item.setAvailableStock(item.getAvailableStock() + order.getQuantity());
        }
    }
}