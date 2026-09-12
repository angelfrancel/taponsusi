package com.taponsusi.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseIntentParserContractTest {

    private static final Long SELLER_ID = 42L;
    private static final Long SESSION_ID = 100L;
    private static final String BUYER_ID = "buyer-123";

    private PurchaseIntentParser parser;

    @BeforeEach
    void setUp() {
        parser = new PurchaseIntentParser();
    }

    @Test
    void acceptsReservationCodeByItself() {
        PurchaseIntent intent = parse("SWERTE");

        assertIntent(intent, "SWERTE", 1);
    }

    @Test
    void acceptsMineFollowedByReservationCode() {
        PurchaseIntent intent = parse("mine swerte");

        assertIntent(intent, "SWERTE", 1);
    }

    @Test
    void acceptsMineWithExclamationMark() {
        PurchaseIntent intent = parse("MINE! SWERTE");

        assertIntent(intent, "SWERTE", 1);
    }

    @Test
    void acceptsPlainNumericQuantity() {
        PurchaseIntent intent = parse("SWERTE 2");

        assertIntent(intent, "SWERTE", 2);
    }

    @Test
    void acceptsQuantityWithXPrefix() {
        PurchaseIntent intent = parse("MINE SWERTE X2");

        assertIntent(intent, "SWERTE", 2);
    }

    @Test
    void acceptsWhitespaceBetweenXAndQuantity() {
        PurchaseIntent intent = parse("MINE SWERTE X 2");

        assertIntent(intent, "SWERTE", 2);
    }

    @Test
    void normalizesExtraWhitespaceAndLetterCase() {
        PurchaseIntent intent =
                parse("   MiNe    sWeRtE    x2   ");

        assertIntent(intent, "SWERTE", 2);
    }

    @Test
    void rejectsMissingReservationCode() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(
                        PurchaseIntentRejectedException.class
                );
    }

    @Test
    void rejectsMineWithoutReservationCode() {
        assertThatThrownBy(() -> parse("MINE!"))
                .isInstanceOf(
                        PurchaseIntentRejectedException.class
                );
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> parse("SWERTE 0"))
                .isInstanceOf(
                        PurchaseIntentRejectedException.class
                );
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> parse("SWERTE -2"))
                .isInstanceOf(
                        PurchaseIntentRejectedException.class
                );
    }

    @Test
    void rejectsAbsurdQuantity() {
        assertThatThrownBy(() -> parse("SWERTE 1001"))
                .isInstanceOf(
                        PurchaseIntentRejectedException.class
                );
    }

    @Test
    void rejectsAdditionalUnrecognizedWords() {
        assertThatThrownBy(() ->
                parse("PLEASE MINE SWERTE")
        ).isInstanceOf(
                PurchaseIntentRejectedException.class
        );
    }

    @Test
    void rejectsReservationCodeContainingPunctuation() {
        assertThatThrownBy(() -> parse("MINE SWER-TE"))
                .isInstanceOf(
                        PurchaseIntentRejectedException.class
                );
    }

    private PurchaseIntent parse(String message) {
        return parser.parse(
                SELLER_ID,
                SESSION_ID,
                BUYER_ID,
                message
        );
    }

    private void assertIntent(
            PurchaseIntent intent,
            String expectedCode,
            int expectedQuantity
    ) {
        assertThat(intent.platformUserId())
                .isEqualTo(BUYER_ID);

        assertThat(intent.sellerId())
                .isEqualTo(SELLER_ID);

        assertThat(intent.sessionId())
                .isEqualTo(SESSION_ID);

        assertThat(intent.reservationCode())
                .isEqualTo(expectedCode);

        assertThat(intent.quantity())
                .isEqualTo(expectedQuantity);
    }
}