package com.taponsusi.waitlist;

public record WaitlistOfferActionResult(
        WaitlistOfferActionType action,
        Long waitlistEntryId,
        Long createdOrderId,
        Long nextOfferedEntryId
) {

    public static WaitlistOfferActionResult accepted(
            Long waitlistEntryId,
            Long createdOrderId
    ) {
        return new WaitlistOfferActionResult(
                WaitlistOfferActionType.ACCEPT,
                waitlistEntryId,
                createdOrderId,
                null
        );
    }

    public static WaitlistOfferActionResult declined(
            Long waitlistEntryId,
            Long nextOfferedEntryId
    ) {
        return new WaitlistOfferActionResult(
                WaitlistOfferActionType.DECLINE,
                waitlistEntryId,
                null,
                nextOfferedEntryId
        );
    }
}