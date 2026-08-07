package com.taponsusi.livesession;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.livesession.dto.CreateLiveSessionRequest;
import com.taponsusi.livesession.dto.LiveSessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    public LiveSessionController(LiveSessionService liveSessionService) {
        this.liveSessionService = liveSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LiveSessionResponse>> create(
            @Valid @RequestBody CreateLiveSessionRequest request) {

        LiveSessionResponse response = liveSessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LiveSessionResponse>>> list() {
        List<LiveSessionResponse> sessions = liveSessionService.listForCurrentSeller();
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> getById(@PathVariable Long id) {
        LiveSessionResponse response = liveSessionService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}