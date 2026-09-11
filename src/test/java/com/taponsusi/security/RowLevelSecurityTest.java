package com.taponsusi.security;

import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class RowLevelSecurityTest {

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void sellerCannotSeeAnotherSellersLiveSessions() {
        Seller sellerA = sellerRepository.save(
                new Seller("rls-a@test.com", "hash", "Shop A")
        );
        Seller sellerB = sellerRepository.save(
                new Seller("rls-b@test.com", "hash", "Shop B")
        );

        setCurrentSeller(sellerA.getId());
        LiveSession sessionA = liveSessionRepository.save(
                new LiveSession(
                        sellerA.getId(),
                        "Seller A Live",
                        "SIMULATION"
                )
        );

        setCurrentSeller(sellerB.getId());
        LiveSession sessionB = liveSessionRepository.save(
                new LiveSession(
                        sellerB.getId(),
                        "Seller B Live",
                        "SIMULATION"
                )
        );

        entityManager.flush();
        entityManager.clear();

        setCurrentSeller(sellerA.getId());

        List<LiveSession> visibleToA = liveSessionRepository.findAll();

        assertThat(visibleToA)
                .extracting(LiveSession::getId)
                .contains(sessionA.getId())
                .doesNotContain(sessionB.getId());

        assertThat(visibleToA)
                .allMatch(session ->
                        session.getSellerId().equals(sellerA.getId())
                );

        entityManager.clear();
        setCurrentSeller(sellerB.getId());

        List<LiveSession> visibleToB = liveSessionRepository.findAll();

        assertThat(visibleToB)
                .extracting(LiveSession::getId)
                .contains(sessionB.getId())
                .doesNotContain(sessionA.getId());

        assertThat(visibleToB)
                .allMatch(session ->
                        session.getSellerId().equals(sellerB.getId())
                );
    }

    private void setCurrentSeller(Long sellerId) {
        entityManager.createNativeQuery("SELECT set_config('app.current_seller_id', :sellerId, true)")
                .setParameter("sellerId", String.valueOf(sellerId))
                .getSingleResult();
    }
}