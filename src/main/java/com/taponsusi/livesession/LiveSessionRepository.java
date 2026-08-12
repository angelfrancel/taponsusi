package com.taponsusi.livesession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findAllBySellerId(Long sellerId);

    Optional<LiveSession> findByIdAndSellerId(Long id, Long sellerId);

    java.util.Optional<LiveSession> findByFacebookPageId(String facebookPageId);
}