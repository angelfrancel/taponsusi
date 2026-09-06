package com.taponsusi.messaging;

public interface MessagingProvider {
    
    void sendMessage(String platform, String buyerPlatformId, String messageText);
}