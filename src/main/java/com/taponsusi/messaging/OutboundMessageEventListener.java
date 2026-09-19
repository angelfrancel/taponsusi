package com.taponsusi.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OutboundMessageEventListener {

    private final OutboundMessageDispatcher dispatcher;

    public OutboundMessageEventListener(
            OutboundMessageDispatcher dispatcher
    ) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            OutboundMessageQueuedEvent event
    ) {
        dispatcher.dispatch(
                event.sellerId(),
                event.messageId()
        );
    }
}