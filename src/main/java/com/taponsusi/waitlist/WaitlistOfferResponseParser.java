package com.taponsusi.waitlist;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class WaitlistOfferResponseParser {

    public Optional<WaitlistOfferResponse> parse(
            String message
    ) {
        if (message == null
                || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = message
                .trim()
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "YES" ->
                    Optional.of(
                            WaitlistOfferResponse.ACCEPT
                    );

            case "NO" ->
                    Optional.of(
                            WaitlistOfferResponse.DECLINE
                    );

            default -> Optional.empty();
        };
    }
}