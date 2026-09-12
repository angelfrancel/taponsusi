package com.taponsusi.livesession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class LiveSessionStateMachineTest {

    private LiveSessionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new LiveSessionStateMachine();
    }

    @Test
    void allowsValidSetupAndLiveTransitions() {
        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.DRAFT,
                        LiveSessionStatus.READY
                )
        );

        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.READY,
                        LiveSessionStatus.LIVE
                )
        );

        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.LIVE,
                        LiveSessionStatus.PAUSED
                )
        );

        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.PAUSED,
                        LiveSessionStatus.LIVE
                )
        );

        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.LIVE,
                        LiveSessionStatus.ENDED
                )
        );

        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.PAUSED,
                        LiveSessionStatus.ENDED
                )
        );
    }

    @Test
    void allowsReadySessionToReturnToDraft() {
        assertThatNoException().isThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.READY,
                        LiveSessionStatus.DRAFT
                )
        );
    }

    @Test
    void preventsGoingLiveDirectlyFromDraft() {
        assertThatThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.DRAFT,
                        LiveSessionStatus.LIVE
                )
        ).isInstanceOf(
                InvalidLiveSessionTransitionException.class
        );
    }

    @Test
    void preventsChangingAnEndedSession() {
        assertThatThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.ENDED,
                        LiveSessionStatus.LIVE
                )
        ).isInstanceOf(
                InvalidLiveSessionTransitionException.class
        );
    }

    @Test
    void preventsSkippingFromReadyDirectlyToEnded() {
        assertThatThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.READY,
                        LiveSessionStatus.ENDED
                )
        ).isInstanceOf(
                InvalidLiveSessionTransitionException.class
        );
    }

    @Test
    void preventsTransitioningToTheSameStatus() {
        assertThatThrownBy(() ->
                stateMachine.validate(
                        LiveSessionStatus.LIVE,
                        LiveSessionStatus.LIVE
                )
        ).isInstanceOf(
                InvalidLiveSessionTransitionException.class
        );
    }
}