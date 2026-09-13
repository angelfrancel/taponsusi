package com.taponsusi.livesession;

import com.taponsusi.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveSessionReadyTransitionTest {

    private static final Long SELLER_ID = 42L;
    private static final Long SESSION_ID = 100L;

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private EntityManager entityManager;

    @Mock
    private LiveSessionStateMachine stateMachine;

    @Mock
    private LiveSessionPreflightService preflightService;

    @InjectMocks
    private LiveSessionService liveSessionService;

    @BeforeEach
    void authenticateSeller() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        SELLER_ID,
                        null,
                        List.of()
                )
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void draftSessionCannotBecomeReadyWhenPreflightFails() {
        LiveSession session = new LiveSession(
                SELLER_ID,
                "Friday Live Sale",
                "SIMULATION"
        );

        SessionPreflightResponse failedPreflight =
                new SessionPreflightResponse(
                        SESSION_ID,
                        false,
                        List.of(
                                new PreflightCheckResponse(
                                        "HAS_INVENTORY",
                                        false,
                                        "Add at least one inventory item before marking the session ready."
                                )
                        )
                );

        when(liveSessionRepository.findForStatusTransition(
                SESSION_ID,
                SELLER_ID
        )).thenReturn(Optional.of(session));

        doThrow(new LiveSessionNotReadyException(failedPreflight))
                .when(preflightService)
                .requireReady(SESSION_ID);

        assertThatThrownBy(() ->
                liveSessionService.transitionStatus(
                        SESSION_ID,
                        LiveSessionStatus.READY
                )
        )
                .isInstanceOf(LiveSessionNotReadyException.class)
                .hasMessageContaining("Add at least one inventory item");

        verify(stateMachine).validate(
                LiveSessionStatus.DRAFT,
                LiveSessionStatus.READY
        );

        verify(preflightService).requireReady(SESSION_ID);
        verify(liveSessionRepository, never()).save(any());

        assertThat(session.getStatus())
                .isEqualTo(LiveSessionStatus.DRAFT);
    }
}