package com.taponsusi.waitlist;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class WaitlistStateMachine {

    private static final Map<WaitlistStatus, Set<WaitlistStatus>>
            ALLOWED_TRANSITIONS = createAllowedTransitions();

    public boolean isAllowed(
            WaitlistStatus currentStatus,
            WaitlistStatus requestedStatus
    ) {
        if (currentStatus == null || requestedStatus == null) {
            return false;
        }

        return ALLOWED_TRANSITIONS
                .getOrDefault(currentStatus, Set.of())
                .contains(requestedStatus);
    }

    public void validateTransition(
            WaitlistStatus currentStatus,
            WaitlistStatus requestedStatus
    ) {
        if (!isAllowed(currentStatus, requestedStatus)) {
            throw new InvalidWaitlistTransitionException(
                    currentStatus,
                    requestedStatus
            );
        }
    }

    private static Map<WaitlistStatus, Set<WaitlistStatus>>
    createAllowedTransitions() {
        Map<WaitlistStatus, Set<WaitlistStatus>> transitions =
                new EnumMap<>(WaitlistStatus.class);

        transitions.put(
                WaitlistStatus.WAITING,
                EnumSet.of(
                        WaitlistStatus.OFFERED,
                        WaitlistStatus.CANCELLED
                )
        );

        transitions.put(
                WaitlistStatus.OFFERED,
                EnumSet.of(
                        WaitlistStatus.PROMOTED,
                        WaitlistStatus.DECLINED,
                        WaitlistStatus.EXPIRED,
                        WaitlistStatus.CANCELLED
                )
        );

        return Map.copyOf(transitions);
    }
}