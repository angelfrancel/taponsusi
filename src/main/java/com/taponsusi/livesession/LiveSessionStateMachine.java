package com.taponsusi.livesession;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


@Component
public class LiveSessionStateMachine {

    private static final Map<
            LiveSessionStatus,
            Set<LiveSessionStatus>
            > ALLOWED_TRANSITIONS =
            new EnumMap<>(LiveSessionStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(
                LiveSessionStatus.DRAFT,
                EnumSet.of(LiveSessionStatus.READY)
        );

        ALLOWED_TRANSITIONS.put(
                LiveSessionStatus.READY,
                EnumSet.of(
                        LiveSessionStatus.DRAFT,
                        LiveSessionStatus.LIVE
                )
        );

        ALLOWED_TRANSITIONS.put(
                LiveSessionStatus.LIVE,
                EnumSet.of(
                        LiveSessionStatus.PAUSED,
                        LiveSessionStatus.ENDED
                )
        );

        ALLOWED_TRANSITIONS.put(
                LiveSessionStatus.PAUSED,
                EnumSet.of(
                        LiveSessionStatus.LIVE,
                        LiveSessionStatus.ENDED
                )
        );

        ALLOWED_TRANSITIONS.put(
                LiveSessionStatus.ENDED,
                EnumSet.noneOf(LiveSessionStatus.class)
        );
    }

    public void validate(
            LiveSessionStatus currentStatus,
            LiveSessionStatus requestedStatus
    ) {
        Set<LiveSessionStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(
                        currentStatus,
                        Set.of()
                );

        if (!allowed.contains(requestedStatus)) {
            throw new InvalidLiveSessionTransitionException(
                    currentStatus,
                    requestedStatus
            );
        }
    }
}