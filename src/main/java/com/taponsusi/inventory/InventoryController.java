package com.taponsusi.inventory;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.inventory.dto.CreateInventoryItemRequest;
import com.taponsusi.inventory.dto.InventoryItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/api/v1/sessions/{sessionId}/inventory")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> create(
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateInventoryItemRequest request) {

        InventoryItemResponse response = inventoryService.create(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/sessions/{sessionId}/inventory")
    public ResponseEntity<ApiResponse<List<InventoryItemResponse>>> list(@PathVariable Long sessionId) {
        List<InventoryItemResponse> items = inventoryService.listForSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/api/v1/inventory/{id}")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getById(@PathVariable Long id) {
        InventoryItemResponse response = inventoryService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}