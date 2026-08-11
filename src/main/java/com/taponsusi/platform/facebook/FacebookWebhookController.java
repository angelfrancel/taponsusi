package com.taponsusi.platform.facebook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/webhooks/facebook")
public class FacebookWebhookController {

    private final String verifyToken;

    public FacebookWebhookController(@Value("${taponsusi.facebook.webhook-verify-token}") String verifyToken) {
        this.verifyToken = verifyToken;
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
}