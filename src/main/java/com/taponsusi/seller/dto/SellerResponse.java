package com.taponsusi.seller.dto;

import com.taponsusi.seller.Seller;

import java.time.Instant;

public record SellerResponse(
        Long id,
        String email,
        String businessName,
        Instant createdAt
) {
    public static SellerResponse from(Seller seller) {
        return new SellerResponse(
                seller.getId(),
                seller.getEmail(),
                seller.getBusinessName(),
                seller.getCreatedAt()
        );
    }
}