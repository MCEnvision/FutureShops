package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

public record CustodyBatchExecutionResult(
        CustodySimulationResult simulation,
        List<CustodyPreparedOperation> preparedOperations,
        Optional<CustodyAdapterApplyResult> application
) {
    public CustodyBatchExecutionResult {
        Objects.requireNonNull(simulation, "simulation");
        Objects.requireNonNull(preparedOperations, "preparedOperations");
        Objects.requireNonNull(application, "application");
        preparedOperations = List.copyOf(preparedOperations);
        if (!simulation.accepted() && (!preparedOperations.isEmpty() || application.isPresent())) {
            throw new IllegalArgumentException("Rejected custody simulation cannot be applied");
        }
        if (simulation.accepted() && preparedOperations.isEmpty()) {
            throw new IllegalArgumentException("Accepted custody simulation requires prepared operations");
        }
    }

    public boolean applied() {
        return application.map(CustodyAdapterApplyResult::applied).orElse(false);
    }
}
