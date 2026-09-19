package com.taponsusi.waitlist;

import com.taponsusi.audit.AuditService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistResolutionServiceTest {

    private static final Long SELLER_ID = 101L;
    private static final Long SESSION_ID = 201L;
    private static final Long INVENTORY_ITEM_ID = 301L;
    private static final Long WAITLIST_ENTRY_ID = 401L;
    private static final String BUYER_ID = "BUYER-001";

    private WaitlistEntryRepository waitlistEntryRepository;
    private WaitlistOfferService waitlistOfferService;
    private TenantContext tenantContext;
    private AuditService auditService;
    private EntityManager entityManager;
    private WaitlistResolutionService service;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository =
                mock(WaitlistEntryRepository.class);

        waitlistOfferService =
                mock(WaitlistOfferService.class);

        tenantContext = mock(TenantContext.class);
        auditService = mock(AuditService.class);
        entityManager = mock(EntityManager.class);

        service = new WaitlistResolutionService(
                waitlistEntryRepository,
                new WaitlistStateMachine(),
                waitlistOfferService,
                tenantContext,
                auditService
        );

        ReflectionTestUtils.setField(
                service,
                "entityManager",
                entityManager
        );
    }

    @Test
    void declineReleasesStockAndAdvancesQueue() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry declinedEntry =
                createOfferedEntry(
                        BUYER_ID,
                        Instant.now().plusSeconds(600)
                );

        WaitlistEntry nextEntry =
                createWaitingEntry("BUYER-002");

        stubLockedOffer(item, declinedEntry);

        when(waitlistOfferService.offerNext(
                SELLER_ID,
                INVENTORY_ITEM_ID
        )).thenReturn(Optional.of(nextEntry));

        WaitlistResolutionResult result =
                service.declineOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        BUYER_ID
                );

        assertThat(result.resolvedEntry().getStatus())
                .isEqualTo(WaitlistStatus.DECLINED);

        assertThat(result.nextOffer())
                .contains(nextEntry);

        /*
         * The mock offer service does not deduct the restored unit,
         * so this directly verifies that the hold was released.
         */
        assertThat(item.getAvailableStock())
                .isEqualTo(1);

        verify(waitlistOfferService).offerNext(
                SELLER_ID,
                INVENTORY_ITEM_ID
        );
    }

    @Test
    void expirationReleasesStockAndAdvancesQueue() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry expiredEntry =
                createOfferedEntry(
                        BUYER_ID,
                        Instant.now().minusSeconds(1)
                );

        stubLockedOffer(item, expiredEntry);

        when(waitlistOfferService.offerNext(
                SELLER_ID,
                INVENTORY_ITEM_ID
        )).thenReturn(Optional.empty());

        WaitlistResolutionResult result =
                service.expireOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID
                );

        assertThat(result.resolvedEntry().getStatus())
                .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(result.nextOffer()).isEmpty();

        assertThat(item.getAvailableStock())
                .isEqualTo(1);
    }

    @Test
    void offerCannotExpireBeforeItsDeadline() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry activeEntry =
                createOfferedEntry(
                        BUYER_ID,
                        Instant.now().plusSeconds(600)
                );

        stubLockedOffer(item, activeEntry);

        assertThatThrownBy(() ->
                service.expireOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID
                )
        )
                .isInstanceOf(
                        WaitlistOfferNotAcceptableException.class
                )
                .hasMessageContaining("has not expired");

        assertThat(item.getAvailableStock())
                .isZero();

        verify(waitlistOfferService, never())
                .offerNext(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );
    }

    @Test
    void differentBuyerCannotDeclineOffer() {
        InventoryItem item = createItemWithHeldStock();
        WaitlistEntry activeEntry =
                createOfferedEntry(
                        BUYER_ID,
                        Instant.now().plusSeconds(600)
                );

        stubLockedOffer(item, activeEntry);

        assertThatThrownBy(() ->
                service.declineOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        "IMPERSONATOR"
                )
        )
                .isInstanceOf(
                        WaitlistOfferNotAcceptableException.class
                )
                .hasMessageContaining(
                        "does not own this offer"
                );

        assertThat(item.getAvailableStock())
                .isZero();

        verify(waitlistOfferService, never())
                .offerNext(
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                );
    }

    private void stubLockedOffer(
            InventoryItem item,
            WaitlistEntry entry
    ) {
        when(entityManager.find(
                InventoryItem.class,
                INVENTORY_ITEM_ID,
                LockModeType.PESSIMISTIC_WRITE
        )).thenReturn(item);

        when(waitlistEntryRepository
                .findByIdForPromotion(
                        WAITLIST_ENTRY_ID,
                        SELLER_ID,
                        INVENTORY_ITEM_ID
                ))
                .thenReturn(Optional.of(entry));
    }

    private InventoryItem createItemWithHeldStock() {
        InventoryItem item = new InventoryItem(
                SELLER_ID,
                SESSION_ID,
                "ITEM-CODE",
                "Test Item",
                1,
                5
        );

        item.setAvailableStock(0);
        return item;
    }

    private WaitlistEntry createOfferedEntry(
            String buyerPlatformId,
            Instant expiresAt
    ) {
        WaitlistEntry entry =
                createWaitingEntry(buyerPlatformId);

        ReflectionTestUtils.setField(
                entry,
                "id",
                WAITLIST_ENTRY_ID
        );

        entry.markOffered(
                Instant.now().minusSeconds(60),
                expiresAt,
                1
        );

        return entry;
    }

    private WaitlistEntry createWaitingEntry(
            String buyerPlatformId
    ) {
        return new WaitlistEntry(
                SELLER_ID,
                SESSION_ID,
                INVENTORY_ITEM_ID,
                "SIMULATION",
                "EVENT-" + buyerPlatformId,
                buyerPlatformId,
                1,
                Instant.now().minusSeconds(120)
        );
    }
}