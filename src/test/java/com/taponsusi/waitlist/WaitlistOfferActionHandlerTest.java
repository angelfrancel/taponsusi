package com.taponsusi.waitlist;

import com.taponsusi.order.Order;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
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

class WaitlistOfferActionHandlerTest {

    private static final Long SELLER_ID = 101L;
    private static final Long SESSION_ID = 201L;
    private static final Long INVENTORY_ITEM_ID = 301L;
    private static final Long WAITLIST_ENTRY_ID = 401L;
    private static final Long ORDER_ID = 501L;
    private static final String BUYER_ID = "BUYER-001";

    private WaitlistEntryRepository repository;
    private WaitlistPromotionService promotionService;
    private WaitlistResolutionService resolutionService;
    private TenantContext tenantContext;
    private EntityManager entityManager;
    private WaitlistOfferActionHandler handler;

    @BeforeEach
    void setUp() {
        repository =
                mock(WaitlistEntryRepository.class);

        promotionService =
                mock(WaitlistPromotionService.class);

        resolutionService =
                mock(WaitlistResolutionService.class);

        tenantContext = mock(TenantContext.class);
        entityManager = mock(EntityManager.class);

        handler = new WaitlistOfferActionHandler(
                new WaitlistOfferActionPayload(),
                repository,
                promotionService,
                resolutionService,
                tenantContext
        );

        ReflectionTestUtils.setField(
                handler,
                "entityManager",
                entityManager
        );
    }

    @Test
    void acceptPayloadPromotesExactOffer() {
        WaitlistEntry entry = createEntry();
        Order order = mock(Order.class);

        when(repository.findByIdAndSellerId(
                WAITLIST_ENTRY_ID,
                SELLER_ID
        )).thenReturn(Optional.of(entry));

        when(order.getId()).thenReturn(ORDER_ID);

        when(promotionService.acceptOffer(
                SELLER_ID,
                INVENTORY_ITEM_ID,
                WAITLIST_ENTRY_ID,
                BUYER_ID
        )).thenReturn(order);

        Optional<WaitlistOfferActionResult> result =
                handler.handle(
                        SELLER_ID,
                        BUYER_ID,
                        "WAITLIST_ACCEPT:401"
                );

        assertThat(result).contains(
                WaitlistOfferActionResult.accepted(
                        WAITLIST_ENTRY_ID,
                        ORDER_ID
                )
        );

        verify(promotionService).acceptOffer(
                SELLER_ID,
                INVENTORY_ITEM_ID,
                WAITLIST_ENTRY_ID,
                BUYER_ID
        );

        verify(resolutionService, never())
                .declineOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        BUYER_ID
                );
    }

    @Test
    void declinePayloadDeclinesExactOffer() {
        WaitlistEntry entry = createEntry();

        when(repository.findByIdAndSellerId(
                WAITLIST_ENTRY_ID,
                SELLER_ID
        )).thenReturn(Optional.of(entry));

        when(resolutionService.declineOffer(
                SELLER_ID,
                INVENTORY_ITEM_ID,
                WAITLIST_ENTRY_ID,
                BUYER_ID
        )).thenReturn(
                new WaitlistResolutionResult(
                        entry,
                        Optional.empty()
                )
        );

        Optional<WaitlistOfferActionResult> result =
                handler.handle(
                        SELLER_ID,
                        BUYER_ID,
                        "WAITLIST_DECLINE:401"
                );

        assertThat(result).contains(
                WaitlistOfferActionResult.declined(
                        WAITLIST_ENTRY_ID,
                        null
                )
        );

        verify(resolutionService).declineOffer(
                SELLER_ID,
                INVENTORY_ITEM_ID,
                WAITLIST_ENTRY_ID,
                BUYER_ID
        );

        verify(promotionService, never())
                .acceptOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        BUYER_ID
                );
    }

    @Test
    void malformedPayloadIsIgnored() {
        Optional<WaitlistOfferActionResult> result =
                handler.handle(
                        SELLER_ID,
                        BUYER_ID,
                        "UNRELATED_EVENT"
                );

        assertThat(result).isEmpty();

        verify(repository, never())
                .findByIdAndSellerId(
                        WAITLIST_ENTRY_ID,
                        SELLER_ID
                );
    }

    @Test
    void differentBuyerCannotUseOfferButton() {
        WaitlistEntry entry = createEntry();

        when(repository.findByIdAndSellerId(
                WAITLIST_ENTRY_ID,
                SELLER_ID
        )).thenReturn(Optional.of(entry));

        assertThatThrownBy(() ->
                handler.handle(
                        SELLER_ID,
                        "IMPERSONATOR",
                        "WAITLIST_ACCEPT:401"
                )
        )
                .isInstanceOf(
                        WaitlistOfferNotAcceptableException.class
                )
                .hasFieldOrPropertyWithValue(
                        "reason",
                        WaitlistOfferNotAcceptableException.Reason.WRONG_BUYER
                )
                .hasMessageContaining(
                        "does not own this offer"
                );

        verify(promotionService, never())
                .acceptOffer(
                        SELLER_ID,
                        INVENTORY_ITEM_ID,
                        WAITLIST_ENTRY_ID,
                        "IMPERSONATOR"
                );
    }

    private WaitlistEntry createEntry() {
        WaitlistEntry entry = new WaitlistEntry(
                SELLER_ID,
                SESSION_ID,
                INVENTORY_ITEM_ID,
                "SIMULATION",
                "EVENT-001",
                BUYER_ID,
                1,
                Instant.now().minusSeconds(60)
        );

        ReflectionTestUtils.setField(
                entry,
                "id",
                WAITLIST_ENTRY_ID
        );

        entry.markOffered(
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(600),
                1
        );

        return entry;
    }
}
