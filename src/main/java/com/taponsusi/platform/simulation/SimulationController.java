package com.taponsusi.platform.simulation;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.platform.simulation.dto.SimulationRequest;
import com.taponsusi.platform.simulation.dto.SimulationResult;
import com.taponsusi.platform.simulation.dto.SimulationWaitlistActionRequest;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.waitlist.WaitlistOfferActionHandler;
import com.taponsusi.waitlist.WaitlistOfferActionResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private final SimulationService simulationService;
    private final WaitlistOfferActionHandler waitlistOfferActionHandler;

    public SimulationController(
            SimulationService simulationService,
            WaitlistOfferActionHandler waitlistOfferActionHandler
    ) {
        this.simulationService = simulationService;
        this.waitlistOfferActionHandler = waitlistOfferActionHandler;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<SimulationResult>> run(@Valid @RequestBody SimulationRequest request) {
        SimulationResult result = simulationService.run(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/waitlist-actions")
    public ResponseEntity<ApiResponse<WaitlistOfferActionResult>> submitWaitlistAction(
            @Valid @RequestBody SimulationWaitlistActionRequest request
    ) {
        return waitlistOfferActionHandler.handle(
                        AuthenticatedSeller.currentSellerId(),
                        request.buyerPlatformId(),
                        request.payload()
                )
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
                .orElseGet(() -> ResponseEntity.badRequest().body(
                        ApiResponse.error(
                                "INVALID_WAITLIST_ACTION_PAYLOAD",
                                "Malformed waitlist action payload"
                        )
                ));
    }
}
