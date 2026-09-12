package com.taponsusi.inventory;

import com.taponsusi.inventory.dto.CreateInventoryItemRequest;
import com.taponsusi.inventory.dto.InventoryItemResponse;
import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCodeModeTest {

    private static final Long SELLER_ID = 42L;
    private static final Long SESSION_ID = 100L;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private ReservationCodeGenerator reservationCodeGenerator;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private InventoryService inventoryService;

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
    void manualModeUsesSellerProvidedNormalizedCode() {
        mockOwnedSession();

        CreateInventoryItemRequest request =
                new CreateInventoryItemRequest(
                        ReservationCodeMode.MANUAL,
                        "swerte",
                        "Red Dress",
                        5,
                        1
                );

        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        InventoryItemResponse response =
                inventoryService.create(SESSION_ID, request);

        assertThat(response.reservationCode())
                .isEqualTo("SWERTE");

        verify(reservationCodeGenerator, never())
                .generateUnique(any(), any());
    }

    @Test
    void omittedModeRemainsBackwardCompatibleWithManualCode() {
        mockOwnedSession();

        CreateInventoryItemRequest request =
                new CreateInventoryItemRequest(
                        "swerte",
                        "Red Dress",
                        5,
                        1
                );

        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        InventoryItemResponse response =
                inventoryService.create(SESSION_ID, request);

        assertThat(response.reservationCode())
                .isEqualTo("SWERTE");
    }

    @Test
    void autoModeUsesGeneratedCode() {
        mockOwnedSession();

        CreateInventoryItemRequest request =
                new CreateInventoryItemRequest(
                        ReservationCodeMode.AUTO,
                        null,
                        "Red Dress",
                        5,
                        1
                );

        when(reservationCodeGenerator.generateUnique(
                SELLER_ID,
                SESSION_ID
        )).thenReturn("K7M4PX");

        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        InventoryItemResponse response =
                inventoryService.create(SESSION_ID, request);

        assertThat(response.reservationCode())
                .isEqualTo("K7M4PX");

        verify(reservationCodeGenerator)
                .generateUnique(SELLER_ID, SESSION_ID);
    }

    @Test
    void manualModeRequiresReservationCode() {
        mockOwnedSession();

        CreateInventoryItemRequest request =
                new CreateInventoryItemRequest(
                        ReservationCodeMode.MANUAL,
                        null,
                        "Red Dress",
                        5,
                        1
                );

        assertThatThrownBy(() ->
                inventoryService.create(SESSION_ID, request)
        )
                .isInstanceOf(
                        InvalidReservationCodeException.class
                )
                .hasMessageContaining(
                        "required when codeMode is MANUAL"
                );

        verify(inventoryItemRepository, never())
                .save(any());
    }

    @Test
    void autoModeRejectsSellerProvidedCode() {
        mockOwnedSession();

        CreateInventoryItemRequest request =
                new CreateInventoryItemRequest(
                        ReservationCodeMode.AUTO,
                        "SWERTE",
                        "Red Dress",
                        5,
                        1
                );

        assertThatThrownBy(() ->
                inventoryService.create(SESSION_ID, request)
        )
                .isInstanceOf(
                        InvalidReservationCodeException.class
                )
                .hasMessageContaining(
                        "Do not provide a reservation code"
                );

        verify(reservationCodeGenerator, never())
                .generateUnique(any(), any());

        verify(inventoryItemRepository, never())
                .save(any());
    }

    @Test
    void manualModeRejectsCodeWithSpaces() {
        mockOwnedSession();

        CreateInventoryItemRequest request =
                new CreateInventoryItemRequest(
                        ReservationCodeMode.MANUAL,
                        "BAD CODE",
                        "Red Dress",
                        5,
                        1
                );

        assertThatThrownBy(() ->
                inventoryService.create(SESSION_ID, request)
        )
                .isInstanceOf(
                        InvalidReservationCodeException.class
                )
                .hasMessageContaining(
                        "only letters and numbers"
                );

        verify(inventoryItemRepository, never())
                .save(any());
    }

    private void mockOwnedSession() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Friday Live Sale",
                "SIMULATION"
        );

        when(liveSessionRepository.findByIdAndSellerId(
                SESSION_ID,
                SELLER_ID
        )).thenReturn(Optional.of(session));
    }
}