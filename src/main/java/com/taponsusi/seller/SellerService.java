package com.taponsusi.seller;

import com.taponsusi.common.exception.DuplicateResourceException;
import com.taponsusi.seller.dto.RegisterSellerRequest;
import com.taponsusi.seller.dto.SellerResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;

    public SellerService(SellerRepository sellerRepository, PasswordEncoder passwordEncoder) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SellerResponse register(RegisterSellerRequest request) {
        if (sellerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "An account with email " + request.email() + " already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        Seller seller = new Seller(request.email(), hashedPassword, request.businessName());
        Seller saved = sellerRepository.save(seller);

        return SellerResponse.from(saved);
    }
}