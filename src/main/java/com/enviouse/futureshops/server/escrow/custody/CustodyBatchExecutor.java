package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CustodyBatchExecutor {
    public CustodyBatchExecutionResult execute(CustodyAdapter adapter,
                                               CustodyBatchPlan plan,
                                               Map<UUID, CustodyTransferEvidence> plannedEvidence,
                                               Instant now,
                                               CustodyBatchCommitter committer) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(plannedEvidence, "plannedEvidence");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(committer, "committer");
        if (!plan.adapterId().equals(adapter.adapterId())
                || plan.capability() != adapter.capability()) {
            throw new CustodyConflictException("Custody adapter does not match the batch plan");
        }
        if (!plannedEvidence.keySet().equals(plan.lotIds())) {
            throw new CustodyConflictException("Custody planned evidence must cover every batch lot");
        }
        CustodySimulationResult simulation = Objects.requireNonNull(adapter.simulate(plan), "simulation");
        if (simulation.requiredUnits() != plan.requiredUnits()) {
            throw new CustodyConflictException("Custody adapter simulated a different unit total");
        }
        if (!simulation.accepted()) {
            return new CustodyBatchExecutionResult(simulation, List.of(), Optional.empty());
        }
        CustodyPreparedBatch preparedBatch = CustodyPreparedBatch.prepare(plan,
                simulation.simulationToken(), plannedEvidence, now);
        committer.commit(CustodyBatchCommit.state(preparedBatch));
        CustodyPreparedBatch applyingBatch = preparedBatch.markApplying(0L, now);
        committer.commit(CustodyBatchCommit.state(applyingBatch));
        CustodyAdapterApplyResult application = Objects.requireNonNull(
                adapter.apply(plan, simulation.simulationToken()), "application");
        if (application.applied()) {
            if (!application.appliedLotIds().equals(plan.lotIds())
                    || !application.evidenceByLot().equals(plannedEvidence)) {
                throw new CustodyConflictException(
                        "Custody adapter reported a partial application");
            }
            commitApplied(plan, preparedBatch, applyingBatch,
                    application.evidenceByLot(), now, committer);
            return new CustodyBatchExecutionResult(simulation, preparedBatch.operations(),
                    Optional.of(application));
        }

        CustodyAdapterInspection inspection;
        try {
            inspection = Objects.requireNonNull(
                    adapter.inspect(simulation.simulationToken()), "inspection");
        } catch (RuntimeException exception) {
            throw new CustodyConflictException(
                    "Custody adapter inspection failed while the batch remains applying",
                    exception);
        }
        return switch (inspection.status()) {
            case APPLIED -> {
                if (!inspection.evidenceByLot().equals(plannedEvidence)) {
                    yield quarantine(simulation, preparedBatch, applyingBatch, application, now,
                            "Custody adapter evidence does not match the prepared batch",
                            committer);
                }
                commitApplied(plan, preparedBatch, applyingBatch,
                        inspection.evidenceByLot(), now, committer);
                CustodyAdapterApplyResult reconciled = new CustodyAdapterApplyResult(
                        true, plan.lotIds(), inspection.evidenceByLot(), inspection.detail());
                yield new CustodyBatchExecutionResult(simulation, preparedBatch.operations(),
                        Optional.of(reconciled));
            }
            case UNKNOWN -> quarantine(simulation, preparedBatch, applyingBatch, application, now,
                    inspection.detail(), committer);
            case NOT_APPLIED -> {
                if (!application.appliedLotIds().isEmpty()
                        || !application.evidenceByLot().isEmpty()) {
                    yield quarantine(simulation, preparedBatch, applyingBatch, application, now,
                            "Custody adapter apply and inspection reports conflict", committer);
                }
                CustodyPreparedBatch notApplied = applyingBatch.markNotApplied(1L, now,
                        inspection.detail());
                committer.commit(CustodyBatchCommit.state(notApplied));
                yield new CustodyBatchExecutionResult(simulation, preparedBatch.operations(),
                        Optional.of(application));
            }
        };
    }

    private static void commitApplied(CustodyBatchPlan plan,
                                      CustodyPreparedBatch preparedBatch,
                                      CustodyPreparedBatch applyingBatch,
                                      Map<UUID, CustodyTransferEvidence> evidenceByLot,
                                      Instant now,
                                      CustodyBatchCommitter committer) {
        List<CustodyMutation> mutations = new ArrayList<>(plan.lots().size());
        for (CustodyPreparedOperation operation : preparedBatch.operations()) {
            CustodyLot lot = operation.lotSnapshot();
            mutations.add(plan.operation() == CustodyOperation.RESERVE
                    ? CustodyMutation.reserve(lot)
                    : CustodyMutation.terminal(lot, plan.operation(), operation.requestKey(),
                    evidenceByLot.get(lot.lotId()), now));
        }
        CustodyPreparedBatch appliedBatch = applyingBatch.markApplied(1L,
                evidenceByLot, now);
        committer.commit(CustodyBatchCommit.applied(appliedBatch, mutations));
    }

    private static CustodyBatchExecutionResult quarantine(
            CustodySimulationResult simulation,
            CustodyPreparedBatch preparedBatch,
            CustodyPreparedBatch applyingBatch,
            CustodyAdapterApplyResult application,
            Instant now,
            String detail,
            CustodyBatchCommitter committer
    ) {
        CustodyPreparedBatch quarantined = applyingBatch.quarantine(1L, now, detail);
        committer.commit(CustodyBatchCommit.state(quarantined));
        return new CustodyBatchExecutionResult(simulation, preparedBatch.operations(),
                Optional.of(application));
    }
}
