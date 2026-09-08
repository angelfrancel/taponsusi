package com.taponsusi.order;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.order.dto.OrderResponse;
import com.taponsusi.order.dto.UpdateOrderStatusRequest;
import com.taponsusi.reservationproof.ReservationProofService;
import com.taponsusi.reservationproof.dto.ReservationProofResponse;
import com.taponsusi.security.AuthenticatedSeller;
import com.taponsusi.websocket.ReservationEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final ReservationProofService reservationProofService;
    private final ReservationEventPublisher reservationEventPublisher;

    public OrderController(OrderService orderService,
                           ReservationProofService reservationProofService,
                           ReservationEventPublisher reservationEventPublisher) {
        this.orderService = orderService;
        this.reservationProofService = reservationProofService;
        this.reservationEventPublisher = reservationEventPublisher;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        Order updated = orderService.transitionStatus(id, request.status());
        reservationEventPublisher.publishOrderUpdated(updated);
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(updated)));
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<ApiResponse<ReservationProofResponse>> getProof(@PathVariable Long id) {
        Long sellerId = AuthenticatedSeller.currentSellerId();
        ReservationProofResponse response = reservationProofService.getProofForOrder(sellerId, id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}