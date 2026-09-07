package com.taponsusi.messaging;

import com.taponsusi.order.Order;
import com.taponsusi.order.OrderRepository;
import com.taponsusi.reservationproof.ReservationProofService;
import com.taponsusi.reservationproof.dto.ReservationProofResponse;
import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class InvoiceRequestHandlerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private MessagingProvider messagingProvider;
    @Mock private ReservationProofService reservationProofService;
    @Mock private TenantContext tenantContext;
    @Mock private EntityManager entityManager;

    private InvoiceRequestHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InvoiceRequestHandler(orderRepository, messagingProvider, reservationProofService, tenantContext);
        ReflectionTestUtils.setField(handler, "entityManager", entityManager);
    }

    @Test
    void recognizesExactInvoiceKeywordOnly() {
        assertThat(handler.isInvoiceRequest("INVOICE")).isTrue();
        assertThat(handler.isInvoiceRequest("INVOICE PLEASE")).isFalse();
        assertThat(handler.isInvoiceRequest("SWERTE")).isFalse();
    }

    @Test
    void repliesWithNoReservationsMessageWhenBuyerHasNoOrders() {
        when(orderRepository.findAllBySellerIdAndSessionIdAndBuyerPlatformId(1L, 10L, "BUYER_X"))
                .thenReturn(List.of());

        handler.handle("SIMULATION", 1L, 10L, "BUYER_X");

        verify(messagingProvider).sendMessage(eq("SIMULATION"), eq("BUYER_X"), contains("don't see any reservations"));
        verifyNoInteractions(reservationProofService);
    }

    @Test
    void repliesWithAggregateSummaryAndProofTokensWhenOrdersExist() {
        Order order1 = new Order(1L, 10L, 100L, "BUYER_X", 2, 2);
        Order order2 = new Order(1L, 10L, 101L, "BUYER_X", 1, 1);

        when(orderRepository.findAllBySellerIdAndSessionIdAndBuyerPlatformId(1L, 10L, "BUYER_X"))
                .thenReturn(List.of(order1, order2));

        when(reservationProofService.getProofForOrder(eq(1L), any()))
                .thenReturn(new ReservationProofResponse(1L, null, 1L, "AAAA-BBBB-CCCC", "hash", Instant.now()));

        handler.handle("SIMULATION", 1L, 10L, "BUYER_X");

        verify(messagingProvider).sendMessage(eq("SIMULATION"), eq("BUYER_X"),
                argThat(message -> message.contains("AAAA-BBBB-CCCC") && message.contains("Total items reserved: 3")));
    }
}