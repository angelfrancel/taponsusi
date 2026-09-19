package com.taponsusi.waitlist;

import java.util.Optional;

public record WaitlistResolutionResult(
        WaitlistEntry resolvedEntry,
        Optional<WaitlistEntry> nextOffer
) {
}