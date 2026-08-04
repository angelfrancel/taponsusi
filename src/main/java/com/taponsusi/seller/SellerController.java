package com.taponsusi.seller;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.seller.dto.RegisterSellerRequest;
import com.taponsusi.seller.dto.SellerResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sellers")
public class SellerController {

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<SellerResponse>> register(
            @Valid @RequestBody RegisterSellerRequest request) {

        SellerResponse response = sellerService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}