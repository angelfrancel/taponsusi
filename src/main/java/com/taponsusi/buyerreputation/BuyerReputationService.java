package com.taponsusi.buyerreputation;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BuyerReputationService {

    private final BuyerReputationRepository buyerReputationRepository;
    private final TenantContext tenantContext;

    @PersistenceContext
    private EntityManager entityManager;

    public BuyerReputationService(BuyerReputationRepository buyerReputationRepository,
                                  TenantContext tenantContext) {
        this.buyerReputationRepository = buyerReputationRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(Long sellerId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        return buyerReputationRepository.findBySellerIdAndBuyerPlatformId(sellerId, buyerPlatformId)
                .map(r -> r.getFlaggedAs() == ReputationFlag.BLOCKED)
                .orElse(false);
    }

    @Transactional
    public void recordOrderCreated(Long sellerId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        BuyerReputation reputation = findOrCreate(sellerId, buyerPlatformId);
        reputation.incrementTotalOrders();
        buyerReputationRepository.save(reputation);
    }

    @Transactional
    public void recordPaid(Long sellerId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        BuyerReputation reputation = findOrCreate(sellerId, buyerPlatformId);
        reputation.incrementPaidOnTime();
        buyerReputationRepository.save(reputation);
    }

    @Transactional
    public void recordOverdue(Long sellerId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        BuyerReputation reputation = findOrCreate(sellerId, buyerPlatformId);
        reputation.incrementOverdue();
        buyerReputationRepository.save(reputation);
    }

    @Transactional
    public void recordCancelledBySeller(Long sellerId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        BuyerReputation reputation = findOrCreate(sellerId, buyerPlatformId);
        reputation.incrementCancelledBySeller();
        buyerReputationRepository.save(reputation);
    }

    @Transactional(readOnly = true)
    public Optional<BuyerReputation> find(Long sellerId, String buyerPlatformId) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        return buyerReputationRepository.findBySellerIdAndBuyerPlatformId(sellerId, buyerPlatformId);
    }

    @Transactional
    public BuyerReputation setFlag(Long sellerId, String buyerPlatformId, ReputationFlag flag, String notes) {
        tenantContext.applyCurrentSeller(entityManager, sellerId);
        BuyerReputation reputation = findOrCreate(sellerId, buyerPlatformId);
        reputation.setFlaggedAs(flag);
        if (notes != null) {
            reputation.setNotes(notes);
        }
        return buyerReputationRepository.save(reputation);
    }

    private BuyerReputation findOrCreate(Long sellerId, String buyerPlatformId) {
        return buyerReputationRepository.findBySellerIdAndBuyerPlatformId(sellerId, buyerPlatformId)
                .orElseGet(() -> new BuyerReputation(sellerId, buyerPlatformId));
    }
}