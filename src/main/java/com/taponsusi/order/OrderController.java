package com.taponsusi.order;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.order.dto.OrderResponse;
import com.taponsusi.order.dto.UpdateOrderStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        Order updated = orderService.transitionStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(updated)));
    }
}