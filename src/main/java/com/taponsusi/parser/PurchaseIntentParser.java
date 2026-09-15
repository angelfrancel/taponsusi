package com.taponsusi.parser;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PurchaseIntentParser {

    private static final int ABSURD_QUANTITY_CEILING = 1000;

    /*
     * Accepted formats:
     *
     * SWERTE
     * MINE SWERTE
     * MINE! SWERTE
     * SWERTE 2
     * SWERTE X2
     * MINE SWERTE X2
     * MINE SWERTE X 2
     *
     * Group 1 is the reservation code.
     * Group 2 is the optional quantity.
     */
    private static final Pattern PURCHASE_PATTERN = Pattern.compile(
            "^(?:MINE!?\\s+)?"
                    + "([A-Z0-9]{1,20})"
                    + "(?:\\s+(?:X\\s*)?(\\d+))?$"
    );

    public PurchaseIntent parse(
            Long sellerId,
            Long sessionId,
            String platformUserId,
            String rawMessage
    ) {
        return parse(
                sellerId,
                sessionId,
                platformUserId,
                rawMessage,
                Instant.now()
        );
    }

    public PurchaseIntent parse(
            Long sellerId,
            Long sessionId,
            String platformUserId,
            String rawMessage,
            Instant sourceCreatedAt
    ) {
        if (sourceCreatedAt == null) {
            throw new IllegalArgumentException(
                    "Source creation time is required"
            );
        }

        String normalized = normalize(rawMessage);

        if (normalized.isEmpty()) {
            throw new PurchaseIntentRejectedException(
                    "EMPTY_COMMENT",
                    "Comment has no content after normalization"
            );
        }

        Matcher matcher = PURCHASE_PATTERN.matcher(normalized);

        if (!matcher.matches()) {
            throw new PurchaseIntentRejectedException(
                    "INVALID_FORMAT",
                    "Comment does not match a supported reservation format"
            );
        }

        String reservationCode = matcher.group(1);
        int quantity = parseQuantity(matcher.group(2));

        if (quantity <= 0) {
            throw new PurchaseIntentRejectedException(
                    "INVALID_QUANTITY",
                    "Quantity must be positive"
            );
        }

        if (quantity > ABSURD_QUANTITY_CEILING) {
            throw new PurchaseIntentRejectedException(
                    "ABSURD_QUANTITY",
                    "Quantity exceeds sanity ceiling: " + quantity
            );
        }

        return new PurchaseIntent(
                platformUserId,
                sellerId,
                sessionId,
                reservationCode,
                quantity,
                sourceCreatedAt
        );
    }

    private int parseQuantity(String quantityText) {
        if (quantityText == null) {
            return 1;
        }

        try {
            long parsedQuantity = Long.parseLong(quantityText);

            if (parsedQuantity > ABSURD_QUANTITY_CEILING) {
                throw new PurchaseIntentRejectedException(
                        "ABSURD_QUANTITY",
                        "Quantity exceeds sanity ceiling: "
                                + quantityText
                );
            }

            return (int) parsedQuantity;
        } catch (NumberFormatException exception) {
            throw new PurchaseIntentRejectedException(
                    "INVALID_QUANTITY",
                    "Quantity is not a valid number"
            );
        }
    }

    private String normalize(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }

        return rawMessage
                .trim()
                .toUpperCase()
                .replaceAll("\\s+", " ");
    }
}