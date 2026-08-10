package com.taponsusi.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class PurchaseIntentParser {

    private static final int ABSURD_QUANTITY_CEILING = 1000;

    // A "code" is a contiguous run of letters/digits. Matches things
    // like SWERTE, SWERTE2, 123.
    // Codes are typo-sensitive by design, not smart-matched.
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9]+");

    public PurchaseIntent parse(Long sellerId, Long sessionId,
                                String platformUserId, String rawMessage) {
        String normalized = normalize(rawMessage);

        if (normalized.isEmpty()) {
            throw new PurchaseIntentRejectedException(
                    "EMPTY_COMMENT", "Comment has no content after normalization");
        }

        java.util.List<String> codes = extractCandidateCodes(normalized);

        if (codes.isEmpty()) {
            throw new PurchaseIntentRejectedException(
                    "NO_CODE_FOUND", "No reservation code found in comment");
        }

        if (codes.size() > 1) {
            throw new PurchaseIntentRejectedException(
                    "MULTIPLE_CODES", "Comment references more than one code: " + codes);
        }

        int quantity = extractQuantity(normalized);

        if (quantity <= 0) {
            throw new PurchaseIntentRejectedException(
                    "INVALID_QUANTITY", "Quantity must be positive, got: " + quantity);
        }

        if (quantity > ABSURD_QUANTITY_CEILING) {
            throw new PurchaseIntentRejectedException(
                    "ABSURD_QUANTITY", "Quantity exceeds sanity ceiling: " + quantity);
        }

        return new PurchaseIntent(platformUserId, sellerId, sessionId, codes.get(0), quantity);
    }


    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    private java.util.List<String> extractCandidateCodes(String normalized) {
        java.util.List<String> found = new java.util.ArrayList<>();
        Matcher matcher = CODE_PATTERN.matcher(normalized);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }


    private int extractQuantity(String normalized) {
        Matcher qtyMatcher = Pattern.compile("X(\\d+)").matcher(normalized);
        if (qtyMatcher.find()) {
            return Integer.parseInt(qtyMatcher.group(1));
        }
        return 1;
    }
}