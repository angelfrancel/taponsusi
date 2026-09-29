package com.taponsusi.waitlist;

import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WaitlistOfferExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            WaitlistOfferExpirationScheduler.class
    );

    private final SellerRepository sellerRepository;
    private final WaitlistOfferExpirationCandidateReader candidateReader;
    private final WaitlistResolutionService resolutionService;

    public WaitlistOfferExpirationScheduler(
            SellerRepository sellerRepository,
            WaitlistOfferExpirationCandidateReader candidateReader,
            WaitlistResolutionService resolutionService
    ) {
        this.sellerRepository = sellerRepository;
        this.candidateReader = candidateReader;
        this.resolutionService = resolutionService;
    }

    @Scheduled(fixedDelayString = "${taponsusi.waitlist.expiration-check-delay-ms:300000}")
    public void expireDueOffers() {
        for (Seller seller : sellerRepository.findAll()) {
            expireSellerOffers(seller.getId());
        }
    }

    private void expireSellerOffers(Long sellerId) {
        try {
            for (WaitlistOfferExpirationCandidateReader.Candidate candidate
                    : candidateReader.findDueOffers(sellerId)) {
                try {
                    resolutionService.expireOffer(
                            sellerId,
                            candidate.inventoryItemId(),
                            candidate.entryId()
                    );
                } catch (WaitlistOfferNotAcceptableException
                         | ResourceNotFoundException staleOffer) {
                    // Another worker may have accepted or expired this candidate.
                    log.debug("Waitlist offer {} changed before expiration",
                            candidate.entryId());
                } catch (RuntimeException failure) {
                    log.error("Could not expire waitlist offer {} for seller {}",
                            candidate.entryId(), sellerId, failure);
                }
            }
        } catch (RuntimeException failure) {
            log.error("Could not load due waitlist offers for seller {}",
                    sellerId, failure);
        }
    }
}
