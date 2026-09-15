package com.taponsusi.platform.simulation;

import com.taponsusi.parser.CommentEvent;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;


public class SimulationCommentGenerator {

    public static List<CommentEvent> generate(Long sellerId, Long sessionId,
                                              String reservationCode, int buyerCount) {
        List<CommentEvent> events = new ArrayList<>(buyerCount);

        Instant baseSourceTime = Instant.now();

        for (int i = 0; i < buyerCount; i++) {
            String buyerId = "SIM_BUYER_" + i;
            String eventId = "SIM_EVENT_" + sessionId + "_" + i;

            events.add(new CommentEvent(
                    "SIMULATION",
                    eventId,
                    buyerId,
                    sellerId,
                    sessionId,
                    reservationCode,
                    baseSourceTime.plusNanos(i)
            ));
        }

        return events;
    }
}