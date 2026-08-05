package com.taponsusi.seller.dto;

public record LoginResponse(
        String token,
        SellerResponse seller
) {
}