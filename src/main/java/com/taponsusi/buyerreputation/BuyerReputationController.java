package com.taponsusi.buyerreputation;

import com.taponsusi.buyerreputation.dto.BuyerReputationResponse;
import com.taponsusi.buyerreputation.dto.UpdateFlagRequest;
import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.common.exception.ResourceNotFoundException;
import com.taponsusi.security.AuthenticatedSeller;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/buyers")
public class BuyerReputationController {

    private final BuyerReputationService buyerReputationService;

    public BuyerReputationController(BuyerReputationService buyerReputationService) {
        this.buyerReputationService = buyerReputationService;
    }

    @GetMapping("/{buyerPlatformId}/reputation")
    public ResponseEntity<ApiResponse<BuyerReputationResponse>> get(@PathVariable String buyerPlatformId) {
        Long sellerId = AuthenticatedSeller.currentSellerId();

        BuyerReputation reputation = buyerReputationService.find(sellerId, buyerPlatformId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No reputation record for buyer: " + buyerPlatformId));

        return ResponseEntity.ok(ApiResponse.success(BuyerReputationResponse.from(reputation)));
    }

    @PatchMapping("/{buyerPlatformId}/reputation/flag")
    public ResponseEntity<ApiResponse<BuyerReputationResponse>> setFlag(
            @PathVariable String buyerPlatformId,
            @Valid @RequestBody UpdateFlagRequest request) {

        Long sellerId = AuthenticatedSeller.currentSellerId();
        BuyerReputation updated = buyerReputationService.setFlag(
                sellerId, buyerPlatformId, request.flaggedAs(), request.notes());

        return ResponseEntity.ok(ApiResponse.success(BuyerReputationResponse.from(updated)));
    }
}