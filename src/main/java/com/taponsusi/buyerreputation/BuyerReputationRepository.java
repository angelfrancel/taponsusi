package com.taponsusi.buyerreputation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuyerReputationRepository extends JpaRepository<BuyerReputation, Long> {

    Optional<BuyerReputation> findBySellerIdAndBuyerPlatformId(Long sellerId, String buyerPlatformId);
}