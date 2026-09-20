package com.taponsusi.waitlist;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistOfferResponseParserTest {

    private final WaitlistOfferResponseParser parser =
            new WaitlistOfferResponseParser();

    @Test
    void parsesStandaloneYesAsAcceptance() {
        assertThat(parser.parse("YES"))
                .contains(
                        WaitlistOfferResponse.ACCEPT
                );

        assertThat(parser.parse("yes"))
                .contains(
                        WaitlistOfferResponse.ACCEPT
                );

        assertThat(parser.parse("  YeS  "))
                .contains(
                        WaitlistOfferResponse.ACCEPT
                );
    }

    @Test
    void parsesStandaloneNoAsDecline() {
        assertThat(parser.parse("NO"))
                .contains(
                        WaitlistOfferResponse.DECLINE
                );

        assertThat(parser.parse("no"))
                .contains(
                        WaitlistOfferResponse.DECLINE
                );

        assertThat(parser.parse("  No  "))
                .contains(
                        WaitlistOfferResponse.DECLINE
                );
    }

    @Test
    void rejectsAdditionalWords() {
        assertThat(parser.parse("YES PLEASE"))
                .isEmpty();

        assertThat(parser.parse("NO THANKS"))
                .isEmpty();

        assertThat(parser.parse("I SAY YES"))
                .isEmpty();

        assertThat(parser.parse("YES I WANT IT"))
                .isEmpty();
    }

    @Test
    void rejectsSimilarButAmbiguousText() {
        assertThat(parser.parse("Y"))
                .isEmpty();

        assertThat(parser.parse("N"))
                .isEmpty();

        assertThat(parser.parse("YEP"))
                .isEmpty();

        assertThat(parser.parse("NOPE"))
                .isEmpty();

        assertThat(parser.parse("YES!"))
                .isEmpty();

        assertThat(parser.parse("NO!"))
                .isEmpty();
    }

    @Test
    void rejectsNullAndBlankMessages() {
        assertThat(parser.parse(null))
                .isEqualTo(Optional.empty());

        assertThat(parser.parse(""))
                .isEmpty();

        assertThat(parser.parse("   "))
                .isEmpty();
    }

    @Test
    void rejectsUnrelatedComments() {
        assertThat(parser.parse("MINE SWERTE"))
                .isEmpty();

        assertThat(parser.parse("INVOICE"))
                .isEmpty();

        assertThat(parser.parse("How much?"))
                .isEmpty();
    }
}