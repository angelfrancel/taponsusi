package com.taponsusi.reservation;

import com.taponsusi.audit.AuditEvent;
import com.taponsusi.audit.AuditService;
import com.taponsusi.buyerreputation.BuyerReputationService;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.reservationproof.ReservationProofService;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
public class ReservationTransactionExecutor {

    private final InventoryItemRepository inventoryItemRepository;
    private final OrderRepository orderRepository;
    private final TenantContext tenantContext;
    private final AuditService auditService;
    private final ReservationProofService reservationProofService;
    private final BuyerReputationService buyerReputationService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${taponsusi.reservation.lock-timeout-ms}")
    private long lockTimeoutMs;

    @Value("${taponsusi.reservation.window-minutes}")
    private long windowMinutes;

    public ReservationTransactionExecutor(InventoryItemRepository inventoryItemRepository,
                                          OrderRepository orderRepository,
                                          TenantContext tenantContext,
                                          AuditService auditService, ReservationProofService reservationProofService, BuyerReputationService buyerReputationService) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.orderRepository = orderRepository;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
        this.reservationProofService = reservationProofService;
        this.buyerReputationService = buyerReputationService;
    }

    @Transactional
    public Order execute(PurchaseIntent intent, Long inventoryItemId) {
        tenantContext.applyCurrentSeller(entityManager, intent.sellerId());
        applyLockTimeout();

        InventoryItem item = lockInventoryItem(inventoryItemId);

        if (!item.getSellerId().equals(intent.sellerId())) {
            throw new ResourceNotFoundException("Inventory item not found: " + inventoryItemId);
        }
        if (!item.getReservationCode().equals(intent.reservationCode())) {
            auditService.record(intent.sellerId(), "RESERVATION_REJECTED_CODE_MISMATCH",
                    "InventoryItem", inventoryItemId,
                    "Buyer " + intent.platformUserId() + " requested code " + intent.reservationCode());
            throw new OutOfStockException(
                    "Reservation code mismatch for item " + inventoryItemId);
        }

        List<Order> existing = orderRepository.findAllBySellerIdAndInventoryItemIdAndBuyerPlatformId(
                intent.sellerId(), inventoryItemId, intent.platformUserId());

        int alreadyRequested = existing.stream()
                .mapToInt(Order::getRequestedQuantity)
                .sum();

        int totalRequested = alreadyRequested + intent.quantity();
        int cappedRequested = Math.min(totalRequested, item.getMaxQuantityPerBuyer());
        int newPortionRequested = Math.max(0, cappedRequested - alreadyRequested);

        if (newPortionRequested == 0) {
            auditService.record(intent.sellerId(), "MAX_QUANTITY_EXCEEDED",
                    "InventoryItem", inventoryItemId,
                    "Buyer " + intent.platformUserId() + " already at max_quantity_per_buyer cap");
            throw new OutOfStockException(
                    "Buyer has already reached max_quantity_per_buyer for this item");
        }

        if (item.getAvailableStock() <= 0) {
            auditService.record(intent.sellerId(), "RESERVATION_REJECTED_OUT_OF_STOCK",
                    "InventoryItem", inventoryItemId,
                    "Buyer " + intent.platformUserId() + " requested " + intent.quantity()
                            + ", available_stock was 0");
            throw new OutOfStockException("Item " + inventoryItemId + " is out of stock");
        }

        int fulfilled = Math.min(newPortionRequested, item.getAvailableStock());

        decrementStock(item, fulfilled);

        Order order = new Order(
                intent.sellerId(), intent.sessionId(), inventoryItemId,
                intent.platformUserId(), newPortionRequested, fulfilled);

        Order saved = orderRepository.save(order);
        reservationProofService.generateProof(saved);
        saved.setExpiresAt(saved.getCreatedAt().plus(windowMinutes, java.time.temporal.ChronoUnit.MINUTES));
        saved = orderRepository.save(saved);

        auditService.record(intent.sellerId(), "RESERVATION_CREATED",
                "Order", saved.getId(),
                "Buyer " + intent.platformUserId() + " reserved " + fulfilled
                        + " of code " + intent.reservationCode()
                        + (fulfilled < newPortionRequested ? " (partial fulfillment)" : ""));

        return saved;
    }

    private InventoryItem lockInventoryItem(Long inventoryItemId) {
        try {
            InventoryItem item = entityManager.find(
                    InventoryItem.class, inventoryItemId, LockModeType.PESSIMISTIC_WRITE);

            if (item == null) {
                throw new ResourceNotFoundException("Inventory item not found: " + inventoryItemId);
            }
            return item;
        } catch (PessimisticLockingFailureException | QueryTimeoutException e) {
            throw new ReservationContendedException(
                    "Could not acquire lock on inventory item " + inventoryItemId
                            + " within " + lockTimeoutMs + "ms");
        }
    }

    private void decrementStock(InventoryItem item, int amount) {
        entityManager.createQuery(
                        "UPDATE InventoryItem i SET i.availableStock = i.availableStock - :amount "
                                + "WHERE i.id = :id")
                .setParameter("amount", amount)
                .setParameter("id", item.getId())
                .executeUpdate();
    }

    private void applyLockTimeout() {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '" + lockTimeoutMs + "ms'")
                .executeUpdate();
    }
}