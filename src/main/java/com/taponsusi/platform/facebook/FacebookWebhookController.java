package com.taponsusi.platform.facebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taponsusi.parser.CommentEvent;
import com.taponsusi.parser.PurchaseIntent;
import com.taponsusi.parser.PurchaseIntentParser;
import com.taponsusi.parser.PurchaseIntentRejectedException;
import com.taponsusi.platform.facebook.dto.FacebookWebhookPayload;
import com.taponsusi.reservation.OutOfStockException;
import com.taponsusi.reservation.ReservationContendedException;
import com.taponsusi.reservation.ReservationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/webhooks/facebook")
public class FacebookWebhookController {

    private static final Logger log = LoggerFactory.getLogger(FacebookWebhookController.class);

    private final String verifyToken;
    private final FacebookSignatureVerifier signatureVerifier;
    private final FacebookCommentMapper commentMapper;
    private final PurchaseIntentParser purchaseIntentParser;
    private final ReservationEngine reservationEngine;
    private final ObjectMapper objectMapper;

    public FacebookWebhookController(
            @Value("${taponsusi.facebook.webhook-verify-token}") String verifyToken,
            FacebookSignatureVerifier signatureVerifier,
            FacebookCommentMapper commentMapper,
            PurchaseIntentParser purchaseIntentParser,
            ReservationEngine reservationEngine,
            ObjectMapper objectMapper) {
        this.verifyToken = verifyToken;
        this.signatureVerifier = signatureVerifier;
        this.commentMapper = commentMapper;
        this.purchaseIntentParser = purchaseIntentParser;
        this.reservationEngine = reservationEngine;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).body("Verification token mismatch");
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody String rawBody) {

        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            log.warn("Rejected Facebook webhook: invalid or missing signature");
            return ResponseEntity.ok().build();
        }

        FacebookWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, FacebookWebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse Facebook webhook payload", e);
            return ResponseEntity.ok().build();
        }

        List<CommentEvent> events = commentMapper.mapToCommentEvents(payload);

        for (CommentEvent event : events) {
            try {
                PurchaseIntent intent = purchaseIntentParser.parse(
                        event.sellerId(), event.sessionId(),
                        event.platformUserId(), event.rawMessage());

                log.info("Parsed valid purchase intent from Facebook comment: {} qty={}",
                        intent.reservationCode(), intent.quantity());

            } catch (PurchaseIntentRejectedException e) {
                log.info("Rejected Facebook comment ({}): {}", e.getReasonCode(), e.getMessage());
            } catch (OutOfStockException | ReservationContendedException e) {
                log.info("Reservation not fulfilled: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error processing Facebook comment event", e);
            }
        }

        return ResponseEntity.ok().build();
    }
}