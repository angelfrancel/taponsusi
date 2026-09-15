package com.taponsusi.platform.facebook;

import com.taponsusi.livesession.LiveSession;
import com.taponsusi.livesession.LiveSessionRepository;
import com.taponsusi.parser.CommentEvent;
import com.taponsusi.platform.facebook.dto.FacebookWebhookPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookCommentTimestampMappingTest {

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Test
    void mapsFacebookCreatedTimeToCommentEvent() {
        LiveSession session = new LiveSession(
                42L,
                "Facebook Live Sale",
                "FACEBOOK"
        );

        session.setFacebookPageId("page-123");

        ReflectionTestUtils.setField(
                session,
                "id",
                100L
        );

        when(liveSessionRepository.findByFacebookPageId(
                "page-123"
        )).thenReturn(Optional.of(session));

        long facebookCreatedTime = 1_789_344_000L;

        FacebookWebhookPayload.Value value =
                new FacebookWebhookPayload.Value(
                        "comment",
                        "add",
                        "comment-456",
                        "post-789",
                        new FacebookWebhookPayload.From(
                                "buyer-123",
                                "Sample Buyer"
                        ),
                        "MINE SWERTE",
                        facebookCreatedTime
                );

        FacebookWebhookPayload payload =
                new FacebookWebhookPayload(
                        "page",
                        List.of(
                                new FacebookWebhookPayload.Entry(
                                        "page-123",
                                        List.of(
                                                new FacebookWebhookPayload.Change(
                                                        "feed",
                                                        value
                                                )
                                        )
                                )
                        )
                );

        FacebookCommentMapper mapper =
                new FacebookCommentMapper(
                        liveSessionRepository
                );

        List<CommentEvent> events =
                mapper.mapToCommentEvents(payload);

        assertThat(events).hasSize(1);

        CommentEvent event = events.getFirst();

        assertThat(event.platform())
                .isEqualTo("FACEBOOK");

        assertThat(event.platformEventId())
                .isEqualTo("comment-456");

        assertThat(event.platformUserId())
                .isEqualTo("buyer-123");

        assertThat(event.sellerId())
                .isEqualTo(42L);

        assertThat(event.sessionId())
                .isEqualTo(100L);

        assertThat(event.rawMessage())
                .isEqualTo("MINE SWERTE");

        assertThat(event.sourceCreatedAt())
                .isEqualTo(
                        Instant.ofEpochSecond(
                                facebookCreatedTime
                        )
                );
    }
}