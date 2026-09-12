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
    private final ReservationCodeGenerator reservationCodeGenerator;

    @PersistenceContext
    private EntityManager entityManager;

    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            LiveSessionRepository liveSessionRepository,
            TenantContext tenantContext,
            ReservationCodeGenerator reservationCodeGenerator
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.tenantContext = tenantContext;
        this.reservationCodeGenerator = reservationCodeGenerator;
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

        String normalizedCode = resolveReservationCode(
                sellerId,
                sessionId,
                request
        );

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

    private String resolveReservationCode(
            Long sellerId,
            Long sessionId,
            CreateInventoryItemRequest request
    ) {
        ReservationCodeMode codeMode =
                request.effectiveCodeMode();

        if (codeMode == ReservationCodeMode.AUTO) {
            if (hasText(request.reservationCode())) {
                throw new InvalidReservationCodeException(
                        "Do not provide a reservation code when codeMode is AUTO"
                );
            }

            return reservationCodeGenerator.generateUnique(
                    sellerId,
                    sessionId
            );
        }

        if (!hasText(request.reservationCode())) {
            throw new InvalidReservationCodeException(
                    "Reservation code is required when codeMode is MANUAL"
            );
        }

        String normalizedCode =
                request.reservationCode().trim().toUpperCase();

        if (!normalizedCode.matches("[A-Z0-9]+")) {
            throw new InvalidReservationCodeException(
                    "Reservation code may contain only letters and numbers"
            );
        }

        if (normalizedCode.length() > 20) {
            throw new InvalidReservationCodeException(
                    "Reservation code must not exceed 20 characters"
            );
        }

        return normalizedCode;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}