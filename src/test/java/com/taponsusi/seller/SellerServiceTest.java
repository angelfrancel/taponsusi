package com.taponsusi.seller;

import com.taponsusi.common.email.EmailService;
import com.taponsusi.security.JwtService;
import com.taponsusi.seller.dto.LoginRequest;
import com.taponsusi.seller.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock private SellerRepository sellerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;

    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        sellerService = new SellerService(
                sellerRepository, passwordEncoder, jwtService, emailService, passwordResetTokenRepository);
    }

    @Test
    void loginRejectsUnverifiedAccountEvenWithCorrectPassword() {
        Seller seller = new Seller("unverified@test.com", "hashed", "Unverified Shop");
        seller.setEmailVerified(false);

        when(sellerRepository.findByEmail("unverified@test.com")).thenReturn(Optional.of(seller));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);

        LoginRequest request = new LoginRequest("unverified@test.com", "correct-password");

        assertThatThrownBy(() -> sellerService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void loginSucceedsForVerifiedAccountWithCorrectPassword() {
        Seller seller = new Seller("verified@test.com", "hashed", "Verified Shop");
        seller.setEmailVerified(true);

        when(sellerRepository.findByEmail("verified@test.com")).thenReturn(Optional.of(seller));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.generateToken(seller.getId(), seller.getEmail())).thenReturn("fake-jwt");

        LoginRequest request = new LoginRequest("verified@test.com", "correct-password");
        LoginResponse response = sellerService.login(request);

        assertThat(response.token()).isEqualTo("fake-jwt");
        assertThat(response.seller().email()).isEqualTo("verified@test.com");
    }

    @Test
    void loginRejectsWrongPasswordBeforeCheckingVerificationStatus() {
        // Verified is deliberately left false here — if the code checked
        // verification before password, this would throw the wrong
        // exception and this test would catch that regression.
        Seller seller = new Seller("verified@test.com", "hashed", "Verified Shop");
        seller.setEmailVerified(false);

        when(sellerRepository.findByEmail("verified@test.com")).thenReturn(Optional.of(seller));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        LoginRequest request = new LoginRequest("verified@test.com", "wrong-password");

        assertThatThrownBy(() -> sellerService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(sellerRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("nobody@test.com", "whatever");

        assertThatThrownBy(() -> sellerService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(passwordEncoder);
    }
}