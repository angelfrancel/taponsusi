package com.taponsusi.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulationMessengerProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulationMessengerProvider.class);

    @Override
    public void sendMessage(String platform, String buyerPlatformId, String messageText) {
        log.info("[MESSAGE:{}] To buyer {}: {}", platform, buyerPlatformId, messageText);
    }
}