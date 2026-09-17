package com.taponsusi.waitlist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitlistStateMachineTest {

    private final WaitlistStateMachine stateMachine =
            new WaitlistStateMachine();

    @Test
    void waitingEntryCanBeOfferedOrCancelled() {
        assertThat(stateMachine.isAllowed(
                WaitlistStatus.WAITING,
                WaitlistStatus.OFFERED
        )).isTrue();

        assertThat(stateMachine.isAllowed(
                WaitlistStatus.WAITING,
                WaitlistStatus.CANCELLED
        )).isTrue();
    }

    @Test
    void offeredEntryCanReachAnySupportedFinalOutcome() {
        assertThat(stateMachine.isAllowed(
                WaitlistStatus.OFFERED,
                WaitlistStatus.PROMOTED
        )).isTrue();

        assertThat(stateMachine.isAllowed(
                WaitlistStatus.OFFERED,
                WaitlistStatus.DECLINED
        )).isTrue();

        assertThat(stateMachine.isAllowed(
                WaitlistStatus.OFFERED,
                WaitlistStatus.EXPIRED
        )).isTrue();

        assertThat(stateMachine.isAllowed(
                WaitlistStatus.OFFERED,
                WaitlistStatus.CANCELLED
        )).isTrue();
    }

    @Test
    void waitingEntryCannotSkipDirectlyToPromoted() {
        assertThatThrownBy(() ->
                stateMachine.validateTransition(
                        WaitlistStatus.WAITING,
                        WaitlistStatus.PROMOTED
                )
        )
                .isInstanceOf(InvalidWaitlistTransitionException.class)
                .hasMessageContaining("WAITING")
                .hasMessageContaining("PROMOTED");
    }

    @Test
    void finalStatusesCannotTransitionAgain() {
        WaitlistStatus[] finalStatuses = {
                WaitlistStatus.PROMOTED,
                WaitlistStatus.DECLINED,
                WaitlistStatus.EXPIRED,
                WaitlistStatus.CANCELLED
        };

        for (WaitlistStatus finalStatus : finalStatuses) {
            for (WaitlistStatus requestedStatus : WaitlistStatus.values()) {
                assertThat(stateMachine.isAllowed(
                        finalStatus,
                        requestedStatus
                ))
                        .as("%s must be terminal", finalStatus)
                        .isFalse();
            }
        }
    }

    @Test
    void sameStatusTransitionIsNotAllowed() {
        for (WaitlistStatus status : WaitlistStatus.values()) {
            assertThat(stateMachine.isAllowed(status, status))
                    .as("%s must not transition to itself", status)
                    .isFalse();
        }
    }

    @Test
    void validTransitionDoesNotThrow() {
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(
                        WaitlistStatus.WAITING,
                        WaitlistStatus.OFFERED
                )
        );
    }

    @Test
    void nullStatusesAreRejected() {
        assertThat(stateMachine.isAllowed(
                null,
                WaitlistStatus.OFFERED
        )).isFalse();

        assertThat(stateMachine.isAllowed(
                WaitlistStatus.WAITING,
                null
        )).isFalse();
    }
}