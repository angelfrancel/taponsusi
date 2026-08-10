package com.taponsusi.platform.simulation.dto;

public record SimulationResult(
        int ordersCreated,
        int reservationsRejected,
        int duplicateEventsIgnored,
        int inventoryConflictsPrevented,
        long averageLatencyMs
) {
}