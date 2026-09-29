package com.taponsusi.common.exception;

import com.taponsusi.common.dto.ApiResponse;
import com.taponsusi.livesession.LiveSessionNotReadyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.taponsusi.inventory.InvalidReservationCodeException;
import com.taponsusi.reservation.SessionNotLiveException;
import com.taponsusi.waitlist.WaitlistOfferNotAcceptableException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WaitlistOfferNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleWaitlistOfferNotAcceptable(
            WaitlistOfferNotAcceptableException ex
    ) {
        boolean wrongBuyer = ex.getReason()
                == WaitlistOfferNotAcceptableException.Reason.WRONG_BUYER;

        return ResponseEntity.status(
                        wrongBuyer ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT
                )
                .body(ApiResponse.error(
                        wrongBuyer
                                ? "WAITLIST_BUYER_MISMATCH"
                                : "WAITLIST_OFFER_NOT_ACCEPTABLE",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DUPLICATE_RESOURCE", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedActionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("UNAUTHORIZED_ACTION", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException ex
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "INVALID_REQUEST_BODY",
                        "Request body must be valid JSON"
                ));
    }

    @ExceptionHandler(InvalidReservationCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidReservationCode(
            InvalidReservationCodeException ex
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "INVALID_RESERVATION_CODE",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(SessionNotLiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionNotLive(
            SessionNotLiveException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "SESSION_NOT_LIVE",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Something went wrong. Please try again."));
    }

    @ExceptionHandler(com.taponsusi.order.InvalidOrderTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTransition(
            com.taponsusi.order.InvalidOrderTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INVALID_ORDER_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(com.taponsusi.seller.EmailNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailNotVerified(com.taponsusi.seller.EmailNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("EMAIL_NOT_VERIFIED", ex.getMessage()));
    }

    @ExceptionHandler(
            com.taponsusi.livesession.InvalidLiveSessionTransitionException.class
    )
    public ResponseEntity<ApiResponse<Void>> handleInvalidLiveSessionTransition(
            com.taponsusi.livesession.InvalidLiveSessionTransitionException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.error(
                                "INVALID_LIVE_SESSION_TRANSITION",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(LiveSessionNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handleLiveSessionNotReady(
            LiveSessionNotReadyException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "LIVE_SESSION_NOT_READY",
                        exception.getMessage()
                ));
    }
}
