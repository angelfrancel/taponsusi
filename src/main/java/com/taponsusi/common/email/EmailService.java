package com.taponsusi.common.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;


@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";

    private final String apiKey;
    private final String fromAddress;
    private final RestClient restClient;

    public EmailService(@Value("${taponsusi.email.sendgrid-api-key}") String apiKey,
                        @Value("${taponsusi.email.from-address}") String fromAddress) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.restClient = RestClient.create();
    }

    public void send(String toAddress, String subject, String bodyText) {
        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of(
                        "to", List.of(Map.of("email", toAddress))
                )),
                "from", Map.of("email", fromAddress),
                "subject", subject,
                "content", List.of(Map.of(
                        "type", "text/plain",
                        "value", bodyText
                ))
        );

        try {
            restClient.post()
                    .uri(SENDGRID_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Email failure should never break the calling flow
            log.error("Failed to send email to {}: {}", toAddress, e.getMessage(), e);
        }
    }
}