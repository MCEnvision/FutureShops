package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CustodyBatchRecovery {
    private CustodyBatchRecovery() {
    }

    public static CustodyPreparedBatch recover(CustodyAdapter adapter,
                                               CustodyPreparedBatch batch,
                                               Instant now,
                                               CustodyBatchCommitter committer) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(committer, "committer");
        if (!batch.unresolved()) {
            throw new CustodyConflictException("Only unresolved custody batches can be recovered");
        }
        if (!batch.operations().get(0).adapterId().equals(adapter.adapterId())
                || batch.operations().get(0).adapterCapability() != adapter.capability()) {
            throw new CustodyConflictException("Custody recovery adapter does not match its batch");
        }
        if (batch.status() == CustodyBatchStatus.PREPARED) {
            CustodyPreparedBatch notApplied = batch.markNotApplied(batch.revision(), now,
                    "Prepared batch did not reach the custody adapter");
            committer.commit(CustodyBatchCommit.state(notApplied));
            return notApplied;
        }

        String token = batch.operations().get(0).simulationToken();
        CustodyAdapterInspection inspection = Objects.requireNonNull(
                adapter.inspect(token), "inspection");
        return switch (inspection.status()) {
            case NOT_APPLIED -> commitNotApplied(batch, now, inspection.detail(), committer);
            case UNKNOWN -> quarantine(batch, now, inspection.detail(), committer);
            case APPLIED -> recoverApplied(batch, inspection, now, committer);
        };
    }

    private static CustodyPreparedBatch recoverApplied(
            CustodyPreparedBatch batch,
            CustodyAdapterInspection inspection,
            Instant now,
            CustodyBatchCommitter committer
    ) {
        if (!inspection.evidenceByLot().equals(batch.plannedEvidenceByLot())) {
            return quarantine(batch, now,
                    "Custody adapter evidence does not match the prepared batch", committer);
        }
        List<CustodyMutation> mutations = new ArrayList<>(batch.operations().size());
        for (CustodyPreparedOperation operation : batch.operations()) {
            mutations.add(operation.operation() == CustodyOperation.RESERVE
                    ? CustodyMutation.reserve(operation.lotSnapshot())
                    : CustodyMutation.terminal(operation.lotSnapshot(), operation.operation(),
                    operation.requestKey(), operation.plannedEvidence(), now));
        }
        CustodyPreparedBatch applied = batch.markApplied(batch.revision(),
                inspection.evidenceByLot(), now);
        committer.commit(CustodyBatchCommit.applied(applied, mutations));
        return applied;
    }

    private static CustodyPreparedBatch commitNotApplied(
            CustodyPreparedBatch batch,
            Instant now,
            String detail,
            CustodyBatchCommitter committer
    ) {
        CustodyPreparedBatch notApplied = batch.markNotApplied(
                batch.revision(), now, detail);
        committer.commit(CustodyBatchCommit.state(notApplied));
        return notApplied;
    }

    private static CustodyPreparedBatch quarantine(
            CustodyPreparedBatch batch,
            Instant now,
            String detail,
            CustodyBatchCommitter committer
    ) {
        CustodyPreparedBatch quarantined = batch.quarantine(batch.revision(), now, detail);
        committer.commit(CustodyBatchCommit.state(quarantined));
        return quarantined;
    }
}
