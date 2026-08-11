package com.taponsusi.platform.facebook;

import com.taponsusi.parser.CommentEvent;
import com.taponsusi.platform.facebook.dto.FacebookWebhookPayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
public class FacebookCommentMapper {

    public List<CommentEvent> mapToCommentEvents(FacebookWebhookPayload payload) {
        List<CommentEvent> events = new ArrayList<>();

        if (payload.entry() == null) {
            return events;
        }

        for (FacebookWebhookPayload.Entry entry : payload.entry()) {
            if (entry.changes() == null) continue;

            for (FacebookWebhookPayload.Change change : entry.changes()) {
                if (!isRelevantCommentAdd(change)) continue;

                FacebookWebhookPayload.Value value = change.value();

                // TODO: resolve sellerId/sessionId from entry.id (the
                // Facebook Page ID) once Page-to-seller linkage exists.
                // Hardcoded null is intentionally invalid — this MUST
                // be wired before this adapter can process real events,
                // not silently defaulted to something plausible-looking.
                Long sellerId = null;
                Long sessionId = null;

                events.add(new CommentEvent(
                        "FACEBOOK",
                        value.commentId(),
                        value.from().id(),
                        sellerId,
                        sessionId,
                        value.message()
                ));
            }
        }

        return events;
    }

    private boolean isRelevantCommentAdd(FacebookWebhookPayload.Change change) {
        return "feed".equals(change.field())
                && change.value() != null
                && "comment".equals(change.value().item())
                && "add".equals(change.value().verb());
    }
}