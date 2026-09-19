package com.taponsusi.messaging;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundMessageDispatcherTest {

    private static final Long SELLER_ID = 101L;
    private static final Long MESSAGE_ID = 201L;

    private OutboundMessageRepository repository;
    private MessagingProvider messagingProvider;
    private TenantContext tenantContext;
    private EntityManager entityManager;
    private OutboundMessageDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository =
                mock(OutboundMessageRepository.class);

        messagingProvider =
                mock(MessagingProvider.class);

        tenantContext = mock(TenantContext.class);
        entityManager = mock(EntityManager.class);

        dispatcher = new OutboundMessageDispatcher(
                repository,
                messagingProvider,
                tenantContext
        );

        ReflectionTestUtils.setField(
                dispatcher,
                "entityManager",
                entityManager
        );
    }

    @Test
    void sendsPendingMessageAndMarksItSent() {
        OutboundMessage message = createMessage();

        when(repository.findByIdForDelivery(
                MESSAGE_ID,
                SELLER_ID
        )).thenReturn(Optional.of(message));

        boolean delivered = dispatcher.dispatch(
                SELLER_ID,
                MESSAGE_ID
        );

        assertThat(delivered).isTrue();

        assertThat(message.getStatus())
                .isEqualTo(
                        OutboundMessageStatus.SENT
                );

        assertThat(message.getAttemptCount())
                .isEqualTo(1);

        assertThat(message.getSentAt()).isNotNull();

        verify(messagingProvider).sendMessage(
                "SIMULATION",
                "BUYER-001",
                "The item is available again."
        );
    }

    @Test
    void failedProviderMarksMessageFailed() {
        OutboundMessage message = createMessage();

        when(repository.findByIdForDelivery(
                MESSAGE_ID,
                SELLER_ID
        )).thenReturn(Optional.of(message));

        org.mockito.Mockito.doThrow(
                new RuntimeException(
                        "Provider unavailable"
                )
        ).when(messagingProvider).sendMessage(
                "SIMULATION",
                "BUYER-001",
                "The item is available again."
        );

        boolean delivered = dispatcher.dispatch(
                SELLER_ID,
                MESSAGE_ID
        );

        assertThat(delivered).isFalse();

        assertThat(message.getStatus())
                .isEqualTo(
                        OutboundMessageStatus.FAILED
                );

        assertThat(message.getAttemptCount())
                .isEqualTo(1);

        assertThat(message.getLastError())
                .isEqualTo("Provider unavailable");
    }

    @Test
    void alreadySentMessageIsNotSentAgain() {
        OutboundMessage message = createMessage();
        message.markSent(
                java.time.Instant.now()
        );

        when(repository.findByIdForDelivery(
                MESSAGE_ID,
                SELLER_ID
        )).thenReturn(Optional.of(message));

        boolean delivered = dispatcher.dispatch(
                SELLER_ID,
                MESSAGE_ID
        );

        assertThat(delivered).isFalse();

        verify(messagingProvider, never())
                .sendMessage(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    private OutboundMessage createMessage() {
        OutboundMessage message =
                new OutboundMessage(
                        SELLER_ID,
                        OutboundMessageType.WAITLIST_OFFER,
                        "WAITLIST-OFFER:401",
                        "SIMULATION",
                        "BUYER-001",
                        "The item is available again."
                );

        ReflectionTestUtils.setField(
                message,
                "id",
                MESSAGE_ID
        );

        return message;
    }
}