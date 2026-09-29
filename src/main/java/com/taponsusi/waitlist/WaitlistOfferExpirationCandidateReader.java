package com.taponsusi.waitlist;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class WaitlistOfferExpirationCandidateReader {

    private final WaitlistEntryRepository repository;
    private final TenantContext tenantContext;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistOfferExpirationCandidateReader(
            WaitlistEntryRepository repository,
            TenantContext tenantContext,
            Clock clock
    ) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Candidate> findDueOffers(Long sellerId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);

        return repository
                .findTop50BySellerIdAndStatusAndOfferExpiresAtLessThanEqualOrderByOfferExpiresAtAscIdAsc(
                        sellerId,
                        WaitlistStatus.OFFERED,
                        clock.instant()
                )
                .stream()
                .map(entry -> new Candidate(
                        entry.getId(), entry.getInventoryItemId()
                ))
                .toList();
    }

    public record Candidate(Long entryId, Long inventoryItemId) {
    }
}
