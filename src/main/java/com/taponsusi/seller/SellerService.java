package com.taponsusi.seller;

import com.taponsusi.common.exception.DuplicateResourceException;
import com.taponsusi.security.JwtService;
import com.taponsusi.seller.dto.LoginRequest;
import com.taponsusi.seller.dto.LoginResponse;
import com.taponsusi.seller.dto.RegisterSellerRequest;
import com.taponsusi.seller.dto.SellerResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SellerService(SellerRepository sellerRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    public LoginResponse login(LoginRequest request) {
        Seller seller = sellerRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), seller.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(seller.getId(), seller.getEmail());
        return new LoginResponse(token, SellerResponse.from(seller));
    }
}