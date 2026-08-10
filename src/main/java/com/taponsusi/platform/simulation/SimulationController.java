package com.taponsusi.platform.simulation;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.platform.simulation.dto.SimulationRequest;
import com.taponsusi.platform.simulation.dto.SimulationResult;
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

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<SimulationResult>> run(@Valid @RequestBody SimulationRequest request) {
        SimulationResult result = simulationService.run(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}