package com.taponsusi.inventory;

import com.taponsusi.common.exception.DuplicateResourceException;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.inventory.dto.CreateInventoryItemRequest;
import com.taponsusi.inventory.dto.InventoryItemResponse;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final int DEFAULT_MAX_QUANTITY_PER_BUYER = 10;

    private final InventoryItemRepository inventoryItemRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public InventoryService(InventoryItemRepository inventoryItemRepository,
                            LiveSessionRepository liveSessionRepository,
                            TenantContext tenantContext) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public InventoryItemResponse create(Long sessionId, CreateInventoryItemRequest request) {
        tenantContext.applyCurrentSeller(entityManager);
        Long sellerId = AuthenticatedSeller.currentSellerId();

        // Confirms the session exists AND belongs to this seller —
        // reuses LiveSessionRepository directly rather than the full
        // service, since we only need the ownership check here.
        liveSessionRepository.findByIdAndSellerId(sessionId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session not found: " + sessionId));

        String normalizedCode = normalize(request.reservationCode());

        if (inventoryItemRepository.existsBySellerIdAndSessionIdAndReservationCode(
                sellerId, sessionId, normalizedCode)) {
            throw new DuplicateResourceException(
                    "Reservation code '" + normalizedCode + "' already exists in this session");
        }

        Integer maxQty = request.maxQuantityPerBuyer() != null
                ? request.maxQuantityPerBuyer()
                : DEFAULT_MAX_QUANTITY_PER_BUYER;

        InventoryItem item = new InventoryItem(
                sellerId, sessionId, normalizedCode, request.name(), request.totalStock(), maxQty);

        InventoryItem saved = inventoryItemRepository.save(item);
        return InventoryItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listForSession(Long sessionId) {
        tenantContext.applyCurrentSeller(entityManager);
        Long sellerId = AuthenticatedSeller.currentSellerId();

        return inventoryItemRepository.findAllBySellerIdAndSessionId(sellerId, sessionId).stream()
                .map(InventoryItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getById(Long id) {
        tenantContext.applyCurrentSeller(entityManager);
        Long sellerId = AuthenticatedSeller.currentSellerId();

        InventoryItem item = inventoryItemRepository.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));

        return InventoryItemResponse.from(item);
    }

    /**
     * Trims, uppercases, and collapses whitespace only. Never
     * auto-corrects spelling. Reservation codes are deliberately
     * typo-sensitive.
     */
    private String normalize(String code) {
        return code.trim().toUpperCase().replaceAll("\\s+", " ");
    }
}