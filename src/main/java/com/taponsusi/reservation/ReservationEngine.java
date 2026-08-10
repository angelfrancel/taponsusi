package com.taponsusi.reservation;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.common.idempotency.IdempotencyService;
import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.parser.PurchaseIntent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class ReservationEngine {

    private final IdempotencyService idempotencyService;
    private final InventoryItemRepository inventoryItemRepository;
    private final OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${taponsusi.reservation.lock-timeout-ms}")
    private long lockTimeoutMs;

    public ReservationEngine(IdempotencyService idempotencyService,
                             InventoryItemRepository inventoryItemRepository,
                             OrderRepository orderRepository) {
        this.idempotencyService = idempotencyService;
        this.inventoryItemRepository = inventoryItemRepository;
        this.orderRepository = orderRepository;
    }


    public java.util.Optional<Order> reserve(String platform, String platformEventId,
                                             PurchaseIntent intent, Long inventoryItemId) {

        boolean isNew = idempotencyService.isNewEvent(platform, platformEventId);
        if (!isNew) {
            return java.util.Optional.empty(); // duplicate delivery, already handled
        }

        return java.util.Optional.of(reserveInTransaction(intent, inventoryItemId));
    }

    @Transactional
    protected Order reserveInTransaction(PurchaseIntent intent, Long inventoryItemId) {
        applyLockTimeout();

        InventoryItem item = lockInventoryItem(inventoryItemId);

        if (!item.getSellerId().equals(intent.sellerId())) {
            throw new ResourceNotFoundException("Inventory item not found: " + inventoryItemId);
        }
        if (!item.getReservationCode().equals(intent.reservationCode())) {
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
            throw new OutOfStockException(
                    "Buyer has already reached max_quantity_per_buyer for this item");
        }

        if (item.getAvailableStock() <= 0) {
            throw new OutOfStockException("Item " + inventoryItemId + " is out of stock");
        }

        int fulfilled = Math.min(newPortionRequested, item.getAvailableStock());

        decrementStock(item, fulfilled);

        Order order = new Order(
                intent.sellerId(), intent.sessionId(), inventoryItemId,
                intent.platformUserId(), newPortionRequested, fulfilled);

        return orderRepository.save(order);
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