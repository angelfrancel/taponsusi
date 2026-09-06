package com.taponsusi.reservationproof;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.reservationproof.dto.ProofVerificationResponse;
import com.taponsusi.security.AuthenticatedSeller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservation-proofs")
public class ReservationProofController {

    private final ReservationProofService reservationProofService;

    public ReservationProofController(ReservationProofService reservationProofService) {
        this.reservationProofService = reservationProofService;
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<ProofVerificationResponse>> verify(
            @RequestParam String buyerPlatformId,
            @RequestParam String token) {

        Long sellerId = AuthenticatedSeller.currentSellerId();
        ProofVerificationResponse response = reservationProofService.verifyToken(sellerId, buyerPlatformId, token);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}