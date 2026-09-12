package com.taponsusi.order;

import com.taponsusi.seller.Seller;
import com.taponsusi.seller.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class ReservationExpirationScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final SellerRepository sellerRepository;
    private final SellerExpirationProcessor expirationProcessor;

    public ReservationExpirationScheduler(
            SellerRepository sellerRepository,
            SellerExpirationProcessor expirationProcessor
    ) {
        this.sellerRepository = sellerRepository;
        this.expirationProcessor = expirationProcessor;
    }

    @Scheduled(fixedDelayString = "300000")
    public void processExpirations() {
        List<Seller> allSellers = sellerRepository.findAll();

        for (Seller seller : allSellers) {
            try {
                expirationProcessor.processSeller(seller.getId());
            } catch (Exception exception) {
                log.error(
                        "Expiration processing failed for seller {}",
                        seller.getId(),
                        exception
                );
            }
        }
    }
}