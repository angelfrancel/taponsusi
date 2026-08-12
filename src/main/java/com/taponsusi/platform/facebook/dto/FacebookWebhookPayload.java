package com.taponsusi.platform.facebook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


public record FacebookWebhookPayload(String object, List<Entry> entry) {

    public record Entry(String id, List<Change> changes) {
    }

    public record Change(String field, Value value) {
    }

    public record Value(
            String item,
            String verb,
            @JsonProperty("comment_id") String commentId,
            @JsonProperty("post_id") String postId,
            From from,
            String message,
            @JsonProperty("created_time") Long createdTime
    ) {
    }

    public record From(String id, String name) {
    }
}