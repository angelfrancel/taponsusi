package com.taponsusi.platform.simulation;

import com.taponsusi.parser.CommentEvent;

import java.util.ArrayList;
import java.util.List;


public class SimulationCommentGenerator {

    public static List<CommentEvent> generate(Long sellerId, Long sessionId,
                                              String reservationCode, int buyerCount) {
        List<CommentEvent> events = new ArrayList<>(buyerCount);

        for (int i = 0; i < buyerCount; i++) {
            String buyerId = "SIM_BUYER_" + i;
            String eventId = "SIM_EVENT_" + sessionId + "_" + i;

            events.add(new CommentEvent(
                    "SIMULATION",
                    eventId,
                    buyerId,
                    sellerId,
                    sessionId,
                    reservationCode
            ));
        }

        return events;
    }
}