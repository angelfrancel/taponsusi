package com.taponsusi.websocket;

import com.taponsusi.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService);
    }

    @Test
    void connectWithValidTokenSetsAuthenticatedPrincipal() {
        when(jwtService.isTokenValid("good-token")).thenReturn(true);
        when(jwtService.extractSellerId("good-token")).thenReturn(42L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true); // mirrors how the real STOMP framework builds inbound frames
        accessor.addNativeHeader("Authorization", "Bearer good-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, null);

        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal()).isEqualTo(42L);
    }

    @Test
    void connectWithMissingTokenIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer bad-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribingToOwnSellerTopicIsAllowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/sellers/42/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(42L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Should not throw.
        interceptor.preSend(message, null);
    }

    @Test
    void subscribingToAnotherSellersTopicIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/sellers/999/orders");
        accessor.setUser(new UsernamePasswordAuthenticationToken(42L, null, Collections.emptyList()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Authenticated as seller 42, trying to listen in on seller 999's
        // feed — this is the exact leak we're closing.
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribingWithoutHavingConnectedIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/sellers/42/orders");
        // No accessor.setUser(...) — simulates a malformed/bypassed CONNECT.
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
    }
}