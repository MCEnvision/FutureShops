package com.enviouse.futureshops.server.escrow.custody;

public interface CustodyAdapter {
    String adapterId();

    CustodyAdapterCapability capability();

    CustodySimulationResult simulate(CustodyBatchPlan plan);

    CustodyAdapterApplyResult apply(CustodyBatchPlan plan, String simulationToken);

    CustodyAdapterInspection inspect(String simulationToken);
}
