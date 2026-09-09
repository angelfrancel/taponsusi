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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private static final Logger log = LoggerFactory.getLogger(SellerService.class);

    @Value("${taponsusi.email.log-links-for-dev}")
    private boolean logLinksForDev;

    public SellerService(SellerRepository sellerRepository, PasswordEncoder passwordEncoder, JwtService jwtService, EmailService emailService, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
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

        String verificationLink = "http://localhost:5173/verify?token=" + saved.getVerificationToken();

        emailService.send(saved.getEmail(), "Verify your TaponSusi account",
                "Hi,\n\n"
                        + "Thanks for creating a TaponSusi account. Please confirm this is your "
                        + "email address by clicking the link below:\n\n"
                        + verificationLink + "\n\n"
                        + "This link expires in 24 hours. If you didn't create this account, "
                        + "you can safely ignore this email.\n\n"
                        + "— TaponSusi");

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

    @Transactional
    public void forgotPassword(String email) {
        // Always succeeds from the caller's perspective, whether or not
        // the email exists — never reveal which emails are registered.
        sellerRepository.findByEmail(email).ifPresent(seller -> {
            String token = java.util.UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken(
                    seller.getId(), token, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS));
            passwordResetTokenRepository.save(resetToken);

            String resetLink = "http://localhost:5173/reset-password?token=" + token;

            emailService.send(seller.getEmail(), "Reset your TaponSusi password",
                    "Hi,\n\n"
                            + "We received a request to reset your TaponSusi account password. "
                            + "Click the link below to choose a new one:\n\n"
                            + resetLink + "\n\n"
                            + "This link expires in 1 hour. If you didn't request this, you can "
                            + "safely ignore this email — your password will stay unchanged.\n\n"
                            + "— TaponSusi");
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new com.taponsusi.common.exception.ResourceNotFoundException(
                        "Invalid or already-used reset token"));

        if (resetToken.isUsed()) {
            throw new IllegalStateException("This reset link has already been used");
        }
        if (resetToken.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("Reset link has expired");
        }

        Seller seller = sellerRepository.findById(resetToken.getSellerId())
                .orElseThrow(() -> new com.taponsusi.common.exception.ResourceNotFoundException("Seller not found"));

        seller.setPasswordHash(passwordEncoder.encode(newPassword));
        sellerRepository.save(seller);

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);
    }

    public LoginResponse login(LoginRequest request) {
        Seller seller = sellerRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), seller.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!seller.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        String token = jwtService.generateToken(seller.getId(), seller.getEmail());
        return new LoginResponse(token, SellerResponse.from(seller));
    }
}