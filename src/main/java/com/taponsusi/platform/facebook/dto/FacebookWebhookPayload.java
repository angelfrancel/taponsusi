package com.taponsusi.platform.facebook.dto;

import java.util.List;


public record FacebookWebhookPayload(String object, List<Entry> entry) {

    public record Entry(String id, List<Change> changes) {
    }

    public record Change(String field, Value value) {
    }

    public record Value(
            String item,
            String verb,
            String commentId,
            String postId,
            From from,
            String message,
            Long createdTime
    ) {
    }

    public record From(String id, String name) {
    }
}