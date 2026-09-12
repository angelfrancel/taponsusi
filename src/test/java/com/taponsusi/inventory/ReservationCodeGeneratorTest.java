package com.taponsusi.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationCodeGeneratorTest {

    private static final Long SELLER_ID = 42L;
    private static final Long SESSION_ID = 100L;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void generatesSixCharacterReadableCode() {
        when(inventoryItemRepository
                .existsBySellerIdAndSessionIdAndReservationCode(
                        eq(SELLER_ID),
                        eq(SESSION_ID),
                        anyString()
                ))
                .thenReturn(false);

        ReservationCodeGenerator generator =
                new ReservationCodeGenerator(
                        inventoryItemRepository
                );

        String generatedCode =
                generator.generateUnique(
                        SELLER_ID,
                        SESSION_ID
                );

        assertThat(generatedCode)
                .hasSize(6)
                .matches("[A-HJ-NP-Z2-9]+")
                .doesNotContain("I", "O", "0", "1");
    }

    @Test
    void retriesWhenGeneratedCodeAlreadyExists() {
        when(inventoryItemRepository
                .existsBySellerIdAndSessionIdAndReservationCode(
                        eq(SELLER_ID),
                        eq(SESSION_ID),
                        anyString()
                ))
                .thenReturn(true, false);

        ReservationCodeGenerator generator =
                new ReservationCodeGenerator(
                        inventoryItemRepository
                );

        String generatedCode =
                generator.generateUnique(
                        SELLER_ID,
                        SESSION_ID
                );

        assertThat(generatedCode)
                .hasSize(6)
                .matches("[A-HJ-NP-Z2-9]+");
    }

    @Test
    void failsAfterTwentyCollisions() {
        when(inventoryItemRepository
                .existsBySellerIdAndSessionIdAndReservationCode(
                        eq(SELLER_ID),
                        eq(SESSION_ID),
                        anyString()
                ))
                .thenReturn(true);

        ReservationCodeGenerator generator =
                new ReservationCodeGenerator(
                        inventoryItemRepository
                );

        assertThatThrownBy(() ->
                generator.generateUnique(
                        SELLER_ID,
                        SESSION_ID
                )
        ).isInstanceOf(
                ReservationCodeGenerationException.class
        );
    }
}