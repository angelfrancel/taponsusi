package com.taponsusi.order;

import com.taponsusi.audit.AuditService;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
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
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getSellerId().equals(sellerId))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        orderStateMachine.validate(oldStatus, newStatus);

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        auditService.record(sellerId, "ORDER_STATUS_CHANGED", "Order", orderId,
                oldStatus + " -> " + newStatus);

        return saved;
    }
}