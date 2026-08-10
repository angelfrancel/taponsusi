package com.taponsusi.security;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;


@Component
public class TenantContext {

    public void applyCurrentSeller(EntityManager em) {
        applyCurrentSeller(em, AuthenticatedSeller.currentSellerId());
    }

    public void applyCurrentSeller(EntityManager em, Long sellerId) {
        em.createNativeQuery("SELECT set_config('app.current_seller_id', :sellerId, true)")
                .setParameter("sellerId", String.valueOf(sellerId))
                .getSingleResult();
    }
}