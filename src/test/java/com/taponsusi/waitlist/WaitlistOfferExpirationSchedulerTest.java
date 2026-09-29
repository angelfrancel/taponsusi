package com.taponsusi.waitlist;

import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistOfferExpirationSchedulerTest {

    @Test
    void oneSellerFailureDoesNotStopAnotherSeller() {
        Seller firstSeller = mock(Seller.class);
        Seller secondSeller = mock(Seller.class);
        SellerRepository sellerRepository = mock(SellerRepository.class);
        WaitlistOfferExpirationCandidateReader candidateReader =
                mock(WaitlistOfferExpirationCandidateReader.class);
        WaitlistResolutionService resolutionService =
                mock(WaitlistResolutionService.class);

        when(firstSeller.getId()).thenReturn(101L);
        when(secondSeller.getId()).thenReturn(102L);
        when(sellerRepository.findAll())
                .thenReturn(List.of(firstSeller, secondSeller));
        when(candidateReader.findDueOffers(101L))
                .thenThrow(new IllegalStateException("Seller A database failure"));
        when(candidateReader.findDueOffers(102L))
                .thenReturn(List.of(
                        new WaitlistOfferExpirationCandidateReader.Candidate(401L, 301L)
                ));

        new WaitlistOfferExpirationScheduler(
                sellerRepository, candidateReader, resolutionService
        ).expireDueOffers();

        verify(resolutionService).expireOffer(102L, 301L, 401L);
    }
}
