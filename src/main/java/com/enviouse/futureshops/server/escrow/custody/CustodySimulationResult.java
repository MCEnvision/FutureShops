package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;

public record CustodySimulationResult(
        boolean accepted,
        long requiredUnits,
        long availableUnits,
        String simulationToken,
        String reason
) {
    public CustodySimulationResult {
        Objects.requireNonNull(simulationToken, "simulationToken");
        Objects.requireNonNull(reason, "reason");
        simulationToken = simulationToken.strip();
        reason = reason.strip();
        if (requiredUnits <= 0L || availableUnits < 0L) {
            throw new IllegalArgumentException("Custody simulation units are invalid");
        }
        if (accepted) {
            if (availableUnits < requiredUnits || simulationToken.isEmpty()) {
                throw new IllegalArgumentException("Accepted custody simulation lacks full capacity or a token");
            }
        } else if (reason.isEmpty()) {
            throw new IllegalArgumentException("Rejected custody simulation requires a reason");
        }
    }

    public static CustodySimulationResult accepted(long requiredUnits,
                                                    long availableUnits,
                                                    String token) {
        return new CustodySimulationResult(true, requiredUnits, availableUnits, token, "");
    }

    public static CustodySimulationResult rejected(long requiredUnits,
                                                    long availableUnits,
                                                    String reason) {
        return new CustodySimulationResult(false, requiredUnits, availableUnits, "", reason);
    }
}
