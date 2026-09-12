package com.taponsusi.livesession;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.inventory.InventoryItemRepository;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveSessionPreflightServiceTest {

    private static final Long SELLER_ID = 42L;
    private static final Long SESSION_ID = 100L;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private EntityManager entityManager;

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private LiveSessionPreflightService preflightService;

    @BeforeEach
    void authenticateSeller() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        SELLER_ID,
                        null,
                        List.of()
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void simulationSessionWithSellableInventoryIsReady() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Friday Live Sale",
                "SIMULATION"
        );

        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "SWERTE",
                "Red Dress",
                5,
                1
        );

        mockSessionAndInventory(session, List.of(item));

        SessionPreflightResponse result =
                preflightService.evaluate(SESSION_ID);

        assertThat(result.ready()).isTrue();
        assertThat(result.checks())
                .allMatch(PreflightCheckResponse::passed);
    }

    @Test
    void sessionWithoutInventoryIsNotReady() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Friday Live Sale",
                "SIMULATION"
        );

        mockSessionAndInventory(session, List.of());

        SessionPreflightResponse result =
                preflightService.evaluate(SESSION_ID);

        assertThat(result.ready()).isFalse();
        assertThat(findCheck(result, "HAS_INVENTORY").passed())
                .isFalse();
        assertThat(findCheck(result, "HAS_AVAILABLE_STOCK").passed())
                .isFalse();
    }

    @Test
    void sessionWithoutAvailableStockIsNotReady() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Friday Live Sale",
                "SIMULATION"
        );

        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "SWERTE",
                "Red Dress",
                5,
                1
        );

        item.setAvailableStock(0);

        mockSessionAndInventory(session, List.of(item));

        SessionPreflightResponse result =
                preflightService.evaluate(SESSION_ID);

        assertThat(result.ready()).isFalse();
        assertThat(findCheck(result, "HAS_INVENTORY").passed())
                .isTrue();
        assertThat(findCheck(result, "HAS_AVAILABLE_STOCK").passed())
                .isFalse();
    }

    @Test
    void reservationCodeWithSpacesIsNotReady() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Friday Live Sale",
                "SIMULATION"
        );

        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "BAD CODE",
                "Red Dress",
                5,
                1
        );

        mockSessionAndInventory(session, List.of(item));

        SessionPreflightResponse result =
                preflightService.evaluate(SESSION_ID);

        assertThat(result.ready()).isFalse();
        assertThat(findCheck(
                result,
                "VALID_RESERVATION_CODES"
        ).passed()).isFalse();
    }

    @Test
    void facebookSessionWithoutLinkedPageIsNotReady() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Facebook Live Sale",
                "FACEBOOK"
        );

        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "SWERTE",
                "Red Dress",
                5,
                1
        );

        mockSessionAndInventory(session, List.of(item));

        SessionPreflightResponse result =
                preflightService.evaluate(SESSION_ID);

        assertThat(result.ready()).isFalse();
        assertThat(findCheck(
                result,
                "FACEBOOK_PAGE_LINKED"
        ).passed()).isFalse();
    }

    @Test
    void facebookSessionWithLinkedPageIsReady() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Facebook Live Sale",
                "FACEBOOK"
        );

        session.setFacebookPageId("page-123");

        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "SWERTE",
                "Red Dress",
                5,
                1
        );

        mockSessionAndInventory(session, List.of(item));

        SessionPreflightResponse result =
                preflightService.evaluate(SESSION_ID);

        assertThat(result.ready()).isTrue();
    }

    private void mockSessionAndInventory(
            LiveSession session,
            List<InventoryItem> inventory
    ) {
        when(liveSessionRepository.findByIdAndSellerId(
                SESSION_ID,
                SELLER_ID
        )).thenReturn(Optional.of(session));

        when(inventoryItemRepository.findAllBySellerIdAndSessionId(
                SELLER_ID,
                SESSION_ID
        )).thenReturn(inventory);
    }

    private PreflightCheckResponse findCheck(
            SessionPreflightResponse response,
            String code
    ) {
        return response.checks().stream()
                .filter(check -> check.code().equals(code))
                .findFirst()
                .orElseThrow();
    }
}