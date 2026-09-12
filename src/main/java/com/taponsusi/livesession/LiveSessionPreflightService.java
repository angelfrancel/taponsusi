package com.taponsusi.livesession;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LiveSessionPreflightService {

    private static final Set<String> SUPPORTED_PLATFORMS =
            Set.of("SIMULATION", "FACEBOOK");

    private static final Pattern VALID_RESERVATION_CODE =
            Pattern.compile("[A-Z0-9]+");

    private final TenantContext tenantContext;
    private final LiveSessionRepository liveSessionRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public LiveSessionPreflightService(
            TenantContext tenantContext,
            LiveSessionRepository liveSessionRepository,
            InventoryItemRepository inventoryItemRepository
    ) {
        this.tenantContext = tenantContext;
        this.liveSessionRepository = liveSessionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Transactional(readOnly = true)
    public SessionPreflightResponse evaluate(Long sessionId) {
        Long sellerId = AuthenticatedSeller.currentSellerId();
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        LiveSession session = liveSessionRepository
                .findByIdAndSellerId(sessionId, sellerId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Live session not found"));

        List<InventoryItem> inventory =
                inventoryItemRepository.findAllBySellerIdAndSessionId(
                        sellerId,
                        sessionId
                );

        List<PreflightCheckResponse> checks = new ArrayList<>();

        String platform = normalize(session.getPlatform());

        boolean supportedPlatform = SUPPORTED_PLATFORMS.contains(platform);

        checks.add(new PreflightCheckResponse(
                "SUPPORTED_PLATFORM",
                supportedPlatform,
                supportedPlatform
                        ? "Session platform is supported."
                        : "Select either SIMULATION or FACEBOOK as the session platform."
        ));

        boolean hasInventory = !inventory.isEmpty();

        checks.add(new PreflightCheckResponse(
                "HAS_INVENTORY",
                hasInventory,
                hasInventory
                        ? "Session contains inventory."
                        : "Add at least one inventory item before marking the session ready."
        ));

        boolean hasAvailableStock = inventory.stream()
                .anyMatch(item -> item.getAvailableStock() > 0);

        checks.add(new PreflightCheckResponse(
                "HAS_AVAILABLE_STOCK",
                hasAvailableStock,
                hasAvailableStock
                        ? "At least one inventory item has available stock."
                        : "At least one inventory item must have available stock."
        ));

        boolean validReservationCodes = !inventory.isEmpty()
                && inventory.stream()
                .allMatch(this::hasValidReservationCode);

        checks.add(new PreflightCheckResponse(
                "VALID_RESERVATION_CODES",
                validReservationCodes,
                validReservationCodes
                        ? "All reservation codes are valid."
                        : "Reservation codes may contain only letters and numbers, without spaces or punctuation."
        ));

        boolean facebookPageLinked =
                !"FACEBOOK".equals(platform)
                        || hasText(session.getFacebookPageId());

        checks.add(new PreflightCheckResponse(
                "FACEBOOK_PAGE_LINKED",
                facebookPageLinked,
                facebookPageLinked
                        ? "Facebook Page requirement is satisfied."
                        : "Link a Facebook Page before marking a Facebook session ready."
        ));

        boolean ready = checks.stream()
                .allMatch(PreflightCheckResponse::passed);

        return new SessionPreflightResponse(
                sessionId,
                ready,
                List.copyOf(checks)
        );
    }

    public void requireReady(Long sessionId) {
        SessionPreflightResponse preflight = evaluate(sessionId);

        if (!preflight.ready()) {
            throw new LiveSessionNotReadyException(preflight);
        }
    }

    private boolean hasValidReservationCode(InventoryItem item) {
        String code = normalize(item.getReservationCode());

        return !code.isBlank()
                && VALID_RESERVATION_CODE.matcher(code).matches();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}