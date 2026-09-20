package com.taponsusi.waitlist;

public record WaitlistOfferActionCommand(
        WaitlistOfferActionType action,
        Long waitlistEntryId
) {
}