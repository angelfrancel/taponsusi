package com.taponsusi.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;


class SimulationMessengerProviderTest {

    @Test
    void sendMessageDoesNotThrowForNormalInput() {
        SimulationMessengerProvider provider = new SimulationMessengerProvider();

        assertThatCode(() -> provider.sendMessage("SIMULATION", "BUYER_1", "Your total is ₱349."))
                .doesNotThrowAnyException();
    }

    @Test
    void sendMessageDoesNotThrowForNullOrEmptyMessage() {
        SimulationMessengerProvider provider = new SimulationMessengerProvider();

        assertThatCode(() -> provider.sendMessage("SIMULATION", "BUYER_1", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> provider.sendMessage("SIMULATION", "BUYER_1", ""))
                .doesNotThrowAnyException();
    }
}