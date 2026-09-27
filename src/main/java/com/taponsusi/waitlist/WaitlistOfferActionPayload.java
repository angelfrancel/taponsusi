package com.taponsusi.waitlist;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WaitlistOfferActionPayload {

    private static final Pattern PAYLOAD_PATTERN =
            Pattern.compile(
                    "^WAITLIST_(ACCEPT|DECLINE):([1-9][0-9]*)$"
            );

    public String createAcceptPayload(
            Long waitlistEntryId
    ) {
        return createPayload(
                WaitlistOfferActionType.ACCEPT,
                waitlistEntryId
        );
    }

    public String createDeclinePayload(
            Long waitlistEntryId
    ) {
        return createPayload(
                WaitlistOfferActionType.DECLINE,
                waitlistEntryId
        );
    }

    public Optional<WaitlistOfferActionCommand> parse(
            String payload
    ) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = PAYLOAD_PATTERN.matcher(
                payload.trim()
        );

        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            WaitlistOfferActionType action =
                    WaitlistOfferActionType.valueOf(
                            matcher.group(1)
                    );

            Long waitlistEntryId =
                    Long.valueOf(
                            matcher.group(2)
                    );

            return Optional.of(
                    new WaitlistOfferActionCommand(
                            action,
                            waitlistEntryId
                    )
            );
        } catch (
                IllegalArgumentException
                | ArithmeticException exception
        ) {
            return Optional.empty();
        }
    }

    private String createPayload(
            WaitlistOfferActionType action,
            Long waitlistEntryId
    ) {
        if (action == null) {
            throw new IllegalArgumentException(
                    "Waitlist action is required"
            );
        }

        if (waitlistEntryId == null
                || waitlistEntryId <= 0) {
            throw new IllegalArgumentException(
                    "Persisted waitlist entry ID is required"
            );
        }

        return "WAITLIST_"
                + action.name()
                + ":"
                + waitlistEntryId;
    }
}