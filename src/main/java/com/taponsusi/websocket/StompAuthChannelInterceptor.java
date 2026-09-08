package com.taponsusi.websocket;

import com.taponsusi.security.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Enforces JWT auth at STOMP CONNECT and per-seller topic isolation at
 * STOMP SUBSCRIBE. Without this, the WebSocket handshake (permitted in
 * SecurityConfig so SockJS can complete it) would leave every seller's
 * reservation feed readable by anyone who guesses their sellerId.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);

        if (token == null || !jwtService.isTokenValid(token)) {
            throw new MessagingException("Missing or invalid token on WebSocket CONNECT");
        }

        Long sellerId = jwtService.extractSellerId(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(sellerId, null, Collections.emptyList()));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Long authenticatedSellerId = extractSellerId(accessor);

        if (destination == null || authenticatedSellerId == null
                || !destination.equals("/topic/sellers/" + authenticatedSellerId + "/orders")) {
            throw new MessagingException("Not authorized to subscribe to: " + destination);
        }
    }

    @Nullable
    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @Nullable
    private Long extractSellerId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof Long sellerId) {
            return sellerId;
        }
        return null;
    }
}