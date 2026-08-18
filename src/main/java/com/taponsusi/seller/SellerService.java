package com.taponsusi.seller;

import com.taponsusi.common.email.EmailService;
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
    private final EmailService emailService;

    public SellerService(SellerRepository sellerRepository, PasswordEncoder passwordEncoder, JwtService jwtService, EmailService emailService) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @Transactional
    public SellerResponse register(RegisterSellerRequest request) {
        if (sellerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "An account with email " + request.email() + " already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        Seller seller = new Seller(request.email(), hashedPassword, request.businessName());
        seller.setVerificationToken(java.util.UUID.randomUUID().toString());
        seller.setVerificationTokenExpiresAt(java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS));

        Seller saved = sellerRepository.save(seller);

        emailService.send(saved.getEmail(), "Verify your TaponSusi account",
                "Click to verify your email: http://localhost:5173/verify?token="
                        + saved.getVerificationToken()
                        + "\n\nThis link expires in 24 hours.");

        return SellerResponse.from(saved);
    }

    @Transactional
    public void verifyEmail(String token) {
        Seller seller = sellerRepository.findByVerificationToken(token)
                .orElseThrow(() -> new com.taponsusi.common.exception.ResourceNotFoundException(
                        "Invalid or already-used verification token"));

        if (seller.getVerificationTokenExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("Verification link has expired");
        }

        seller.setEmailVerified(true);
        seller.setVerificationToken(null);
        seller.setVerificationTokenExpiresAt(null);
        sellerRepository.save(seller);
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