package com.taponsusi.waitlist;

import com.taponsusi.inventory.InventoryItem;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistOfferServiceTest {

    private static final Long SELLER_ID = 101L;
    private static final Long SESSION_ID = 201L;
    private static final Long INVENTORY_ITEM_ID = 301L;

    private WaitlistEntryRepository waitlistEntryRepository;
    private TenantContext tenantContext;
    private EntityManager entityManager;
    private WaitlistOfferService service;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository =
                mock(WaitlistEntryRepository.class);

        tenantContext = mock(TenantContext.class);
        entityManager = mock(EntityManager.class);

        service = new WaitlistOfferService(
                waitlistEntryRepository,
                new WaitlistStateMachine(),
                tenantContext,
                10
        );

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );
    }

    @Test
    void holdsAvailableStockForEarliestWaitingBuyer() {
        InventoryItem item = createItem(2);

        WaitlistEntry entry = createWaitlistEntry(
                "EARLIEST-EVENT",
                3
        );

        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        when(waitlistEntryRepository
                .existsBySellerIdAndInventoryItemIdAndStatus(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WaitlistStatus.OFFERED
                ))
                .thenReturn(false);

        when(waitlistEntryRepository
                .findNextWaitingForUpdate(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                ))
                .thenReturn(Optional.of(entry));

        Optional<WaitlistEntry> result =
                service.offerNext(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );

        assertThat(result).contains(entry);
        assertThat(entry.getStatus())
                .isEqualTo(WaitlistStatus.OFFERED);

        assertThat(entry.getOfferedQuantity())
                .isEqualTo(2);

        assertThat(entry.getOfferedAt())
                .isNotNull();

        assertThat(entry.getOfferExpiresAt())
                .isEqualTo(
                        entry.getOfferedAt()
                                .plusSeconds(600)
                );

        assertThat(item.getAvailableStock())
                .isZero();

        verify(tenantContext).applyCurrentSeller(
                entityManager,
                SELLER_ID
        );

        verify(entityManager).find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        );
    }

    @Test
    void doesNothingWhenNoStockIsAvailable() {
        InventoryItem item = createItem(0);

        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        Optional<WaitlistEntry> result =
                service.offerNext(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );

        assertThat(result).isEmpty();

        verify(waitlistEntryRepository, never())
                .findNextWaitingForUpdate(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );
    }

    @Test
    void doesNotCreateSecondActiveOffer() {
        InventoryItem item = createItem(2);

        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        when(waitlistEntryRepository
                .existsBySellerIdAndInventoryItemIdAndStatus(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WaitlistStatus.OFFERED
                ))
                .thenReturn(true);

        Optional<WaitlistEntry> result =
                service.offerNext(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );

        assertThat(result).isEmpty();
        assertThat(item.getAvailableStock())
                .isEqualTo(2);

        verify(waitlistEntryRepository, never())
                .findNextWaitingForUpdate(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );
    }

    private InventoryItem createItem(int availableStock) {
        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "ITEM-CODE",
                "Test Item",
                availableStock,
                5
        );

        item.setAvailableStock(availableStock);
        return item;
    }

    private WaitlistEntry createWaitlistEntry(
            String platformEventId,
            int requestedQuantity
    ) {
        return new WaitlistEntry(
                SELLER_ID,
                SESSION_ID,
                INVENTORY_ITEM_ID,
                "SIMULATION",
                platformEventId,
                "BUYER-001",
                requestedQuantity,
                Instant.parse("2026-09-18T10:00:00Z")
        );
    }
}