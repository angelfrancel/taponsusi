package com.taponsusi.platform.facebook;

import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.parser.CommentEvent;
import com.taponsusi.platform.facebook.dto.FacebookWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;


@Component
public class FacebookCommentMapper {

    private static final Logger log = LoggerFactory.getLogger(FacebookCommentMapper.class);

    private final LiveSessionRepository liveSessionRepository;

    public FacebookCommentMapper(LiveSessionRepository liveSessionRepository) {
        this.liveSessionRepository = liveSessionRepository;
    }

    public List<CommentEvent> mapToCommentEvents(FacebookWebhookPayload payload) {
        List<CommentEvent> events = new ArrayList<>();

        if (payload.entry() == null) {
            return events;
        }

        for (FacebookWebhookPayload.Entry entry : payload.entry()) {
            if (entry.changes() == null) continue;

            Optional<LiveSession> session = liveSessionRepository.findByFacebookPageId(entry.id());
            if (session.isEmpty()) {
                log.warn("Received webhook for unlinked Facebook Page ID: {}", entry.id());
                continue;
            }

            for (FacebookWebhookPayload.Change change : entry.changes()) {
                if (!isRelevantCommentAdd(change)) continue;

                FacebookWebhookPayload.Value value = change.value();

                Instant sourceCreatedAt =
                        value.createdTime() != null
                                ? Instant.ofEpochSecond(value.createdTime())
                                : Instant.now();

                events.add(new CommentEvent(
                        "FACEBOOK",
                        value.commentId(),
                        value.from().id(),
                        session.get().getSellerId(),
                        session.get().getId(),
                        value.message(),
                        sourceCreatedAt
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