package com.taponsusi.inventory;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ReservationCodeGenerator {

    /*
     * Ambiguous characters are intentionally excluded:
     * I, O, 0, and 1.
     *
     * This makes generated codes easier to read while the seller
     * is presenting an item during a livestream.
     */
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 6;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final InventoryItemRepository inventoryItemRepository;
    private final SecureRandom random = new SecureRandom();

    public ReservationCodeGenerator(
            InventoryItemRepository inventoryItemRepository
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public String generateUnique(Long sellerId, Long sessionId) {
        for (int attempt = 0;
             attempt < MAX_GENERATION_ATTEMPTS;
             attempt++) {

            String candidate = generateCandidate();

            boolean alreadyExists =
                    inventoryItemRepository
                            .existsBySellerIdAndSessionIdAndReservationCode(
                                    sellerId,
                                    sessionId,
                                    candidate
                            );

            if (!alreadyExists) {
                return candidate;
            }
        }

        throw new ReservationCodeGenerationException();
    }

    private String generateCandidate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);

        for (int index = 0; index < CODE_LENGTH; index++) {
            int randomIndex = random.nextInt(ALPHABET.length());
            code.append(ALPHABET.charAt(randomIndex));
        }

        return code.toString();
    }
}