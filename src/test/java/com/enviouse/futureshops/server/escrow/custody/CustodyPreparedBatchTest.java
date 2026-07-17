package com.enviouse.futureshops.server.escrow.custody;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustodyPreparedBatchTest {
    @Test
    void batchRoundTripAndStateTransitionsPreserveExactMembership() {
        CustodyLot first = CustodyTestFixtures.itemLot("batch first", 2);
        CustodyLot secondSource = CustodyTestFixtures.itemLot("batch second", 3);
        CustodyLot second = new CustodyLot(secondSource.lotId(), first.transactionId(),
                secondSource.reserveRequestKey(), secondSource.assetType(),
                secondSource.protectionTier(), secondSource.sourceCapability(),
                secondSource.state(), secondSource.units(), secondSource.currencyProvider(),
                secondSource.itemSnapshots(), secondSource.protectedProvenance(),
                secondSource.assetFingerprint(), secondSource.holdEvidence(),
                secondSource.createdAt(), secondSource.updatedAt(), secondSource.revision());
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "atomic batch", List.of(first, second));
        Map<java.util.UUID, CustodyTransferEvidence> evidence = Map.of(
                first.lotId(), first.holdEvidence(), second.lotId(), second.holdEvidence());

        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(
                plan, "simulation one", evidence, CustodyTestFixtures.NOW);
        CustodyPreparedBatch decoded = CustodyPreparedBatchCodec.decode(
                CustodyPreparedBatchCodec.encode(prepared));

        assertEquals(prepared, decoded);
        assertEquals(plan.lotIds(), decoded.lotIds());
        assertTrue(decoded.unresolved());

        Instant applyingAt = CustodyTestFixtures.NOW.plusSeconds(1);
        CustodyPreparedBatch applying = decoded.markApplying(0L, applyingAt);
        CustodyPreparedBatch applied = applying.markApplied(1L, evidence,
                applyingAt.plusSeconds(1));

        assertEquals(CustodyBatchStatus.APPLIED, applied.status());
        assertEquals(2L, applied.revision());
        assertEquals(evidence, applied.plannedEvidenceByLot());
    }

    @Test
    void batchRejectsCrossTransactionMembershipAndStaleTransitions() {
        CustodyLot first = CustodyTestFixtures.itemLot("cross first", 2);
        CustodyLot second = CustodyTestFixtures.itemLot("cross second", 3);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "cross batch", List.of(first, second));
        Map<java.util.UUID, CustodyTransferEvidence> evidence = Map.of(
                first.lotId(), first.holdEvidence(), second.lotId(), second.holdEvidence());

        assertThrows(CustodyConflictException.class, () -> CustodyPreparedBatch.prepare(
                plan, "simulation two", evidence, CustodyTestFixtures.NOW));

        CustodyBatchPlan single = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "single batch", List.of(first));
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(single,
                "simulation three", Map.of(first.lotId(), first.holdEvidence()),
                CustodyTestFixtures.NOW);
        assertThrows(CustodyConflictException.class,
                () -> prepared.markApplying(1L, CustodyTestFixtures.NOW.plusSeconds(1)));
        assertThrows(CustodyConflictException.class,
                () -> prepared.markApplying(0L, CustodyTestFixtures.NOW.minusSeconds(1)));
    }

    @Test
    void appliedEvidenceMustMatchEveryPlannedLotExactly() {
        CustodyLot lot = CustodyTestFixtures.itemLot("evidence batch", 1);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "evidence batch", List.of(lot));
        CustodyPreparedBatch applying = CustodyPreparedBatch.prepare(plan,
                "simulation four", Map.of(lot.lotId(), lot.holdEvidence()),
                CustodyTestFixtures.NOW).markApplying(0L,
                CustodyTestFixtures.NOW.plusSeconds(1));

        assertThrows(CustodyConflictException.class, () -> applying.markApplied(
                1L, Map.of(), CustodyTestFixtures.NOW.plusSeconds(2)));
    }

    @Test
    void repositoryIsIdempotentAndRejectsSkippedOrConflictingTransitions() {
        CustodyLot lot = CustodyTestFixtures.itemLot("repository batch", 1);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "repository batch", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                "simulation five", evidence, CustodyTestFixtures.NOW);
        CustodyPreparedBatchRepository repository = new CustodyPreparedBatchRepository();

        assertFalse(repository.apply(prepared).replayed());
        assertTrue(repository.apply(prepared).replayed());
        assertTrue(repository.hasUnresolved());

        CustodyPreparedBatch applying = prepared.markApplying(0L,
                CustodyTestFixtures.NOW.plusSeconds(1));
        assertFalse(repository.apply(applying).replayed());
        CustodyPreparedBatch skipped = new CustodyPreparedBatch(applying.batchId(),
                applying.transactionId(), applying.requestKey(), applying.operations(),
                CustodyBatchStatus.QUARANTINED, applying.preparedAt(),
                CustodyTestFixtures.NOW.plusSeconds(3), 3L, "Invalid");
        assertThrows(CustodyConflictException.class, () -> repository.apply(skipped));

        CustodyPreparedBatch applied = applying.markApplied(1L, evidence,
                CustodyTestFixtures.NOW.plusSeconds(2));
        assertFalse(repository.apply(applied).replayed());
        assertFalse(repository.hasUnresolved());
        assertTrue(repository.apply(applied).replayed());

        CustodyPreparedBatchRepository restored = new CustodyPreparedBatchRepository();
        restored.restore(repository.snapshot());
        assertEquals(applied, restored.get(applied.batchId()));
        assertFalse(restored.hasUnresolved());
    }

    @Test
    void savedDataAppliesOneJournaledOutcomeForEveryBatchMutation() {
        CustodyLot first = CustodyTestFixtures.itemLot("commit first", 2);
        CustodyLot other = CustodyTestFixtures.itemLot("commit second", 3);
        CustodyLot second = new CustodyLot(other.lotId(), first.transactionId(),
                other.reserveRequestKey(), other.assetType(), other.protectionTier(),
                other.sourceCapability(), other.state(), other.units(), other.currencyProvider(),
                other.itemSnapshots(), other.protectedProvenance(), other.assetFingerprint(),
                other.holdEvidence(), other.createdAt(), other.updatedAt(), other.revision());
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "commit batch", List.of(first, second));
        Map<java.util.UUID, CustodyTransferEvidence> evidence = Map.of(
                first.lotId(), first.holdEvidence(), second.lotId(), second.holdEvidence());
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                "simulation six", evidence, CustodyTestFixtures.NOW);
        CustodyPreparedBatch applying = prepared.markApplying(0L,
                CustodyTestFixtures.NOW.plusSeconds(1));
        CustodyPreparedBatch applied = applying.markApplied(1L, evidence,
                CustodyTestFixtures.NOW.plusSeconds(2));
        CustodyBatchCommit outcome = CustodyBatchCommit.applied(applied,
                List.of(CustodyMutation.reserve(first), CustodyMutation.reserve(second)));
        CustodySavedData data = new CustodySavedData();

        assertFalse(data.applyBatchCommit(CustodyBatchCommit.state(prepared)).replayed());
        assertFalse(data.applyBatchCommit(CustodyBatchCommit.state(applying)).replayed());
        assertFalse(data.applyBatchCommit(outcome).replayed());
        assertTrue(data.applyBatchCommit(outcome).replayed());
        assertFalse(data.hasUnresolvedPreparedOperations());
        assertEquals(first, data.getLot(first.lotId()));
        assertEquals(second, data.getLot(second.lotId()));

        CustodySavedData restored = CustodySavedData.load(data.save(new CompoundTag()));
        assertFalse(restored.hasUnresolvedPreparedOperations());
        assertEquals(first, restored.getLot(first.lotId()));
    }

    @Test
    void executorJournalsApplyingBeforeOneAtomicExternalApply() {
        CustodyLot first = CustodyTestFixtures.itemLot("executor first", 2);
        CustodyLot second = sameTransaction(first,
                CustodyTestFixtures.itemLot("executor second", 3));
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "executor batch", List.of(first, second));
        Map<java.util.UUID, CustodyTransferEvidence> evidence = Map.of(
                first.lotId(), first.holdEvidence(), second.lotId(), second.holdEvidence());
        List<CustodyBatchCommit> commits = new ArrayList<>();
        TestAdapter adapter = new TestAdapter(evidence,
                CustodyAdapterInspection.applied(evidence, "Applied"), commits);

        new CustodyBatchExecutor().execute(adapter, plan, evidence,
                CustodyTestFixtures.NOW, commits::add);

        assertEquals(3, commits.size());
        assertEquals(CustodyBatchStatus.PREPARED, commits.get(0).batch().status());
        assertEquals(CustodyBatchStatus.APPLYING, commits.get(1).batch().status());
        assertEquals(CustodyBatchStatus.APPLIED, commits.get(2).batch().status());
        assertEquals(2, commits.get(2).mutations().size());
        assertTrue(adapter.applyObservedApplying);
    }

    @Test
    void negativeApplyReconcilesACompletedSideEffectBeforeReturning() {
        CustodyLot lot = CustodyTestFixtures.itemLot("late failure applied", 2);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "late failure applied", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());
        List<CustodyBatchCommit> commits = new ArrayList<>();
        NegativeApplyAdapter adapter = new NegativeApplyAdapter(
                CustodyAdapterApplyResult.rejected("Late verification failed"),
                CustodyAdapterInspection.applied(evidence, "Durable side effect found"), null);

        CustodyBatchExecutionResult result = new CustodyBatchExecutor().execute(
                adapter, plan, evidence, CustodyTestFixtures.NOW, commits::add);

        assertTrue(adapter.sideEffectPerformed);
        assertTrue(adapter.inspectCalled);
        assertEquals(adapter.applyToken, adapter.inspectToken);
        assertTrue(result.applied());
        assertEquals(3, commits.size());
        assertEquals(CustodyBatchStatus.APPLIED, commits.get(2).batch().status());
        assertEquals(evidence, commits.get(2).batch().plannedEvidenceByLot());
    }

    @Test
    void negativeApplyRequiresDurableNotAppliedInspection() {
        CustodyLot lot = CustodyTestFixtures.itemLot("durable rejection", 2);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "durable rejection", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());
        List<CustodyBatchCommit> commits = new ArrayList<>();
        NegativeApplyAdapter adapter = new NegativeApplyAdapter(
                CustodyAdapterApplyResult.rejected("Apply rejected"),
                CustodyAdapterInspection.notApplied("Durable token has no side effect"), null);

        CustodyBatchExecutionResult result = new CustodyBatchExecutor().execute(
                adapter, plan, evidence, CustodyTestFixtures.NOW, commits::add);

        assertTrue(adapter.inspectCalled);
        assertFalse(result.applied());
        assertEquals(CustodyBatchStatus.NOT_APPLIED, commits.get(2).batch().status());
        assertEquals("Durable token has no side effect", commits.get(2).batch().detail());
    }

    @Test
    void negativeApplyQuarantinesUnknownPartialAndMismatchedInspections() {
        CustodyLot lot = CustodyTestFixtures.itemLot("negative ambiguity", 2);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "negative ambiguity", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());

        List<CustodyBatchCommit> unknownCommits = new ArrayList<>();
        new CustodyBatchExecutor().execute(new NegativeApplyAdapter(
                        CustodyAdapterApplyResult.rejected("Late failure"),
                        CustodyAdapterInspection.unknown("Token outcome is unknown"), null),
                plan, evidence, CustodyTestFixtures.NOW, unknownCommits::add);
        assertEquals(CustodyBatchStatus.QUARANTINED,
                unknownCommits.get(2).batch().status());

        List<CustodyBatchCommit> partialCommits = new ArrayList<>();
        CustodyAdapterApplyResult partial = new CustodyAdapterApplyResult(false,
                Set.of(lot.lotId()), evidence, "Partial result reported");
        new CustodyBatchExecutor().execute(new NegativeApplyAdapter(partial,
                        CustodyAdapterInspection.notApplied("Token reports no full application"),
                        null),
                plan, evidence, CustodyTestFixtures.NOW, partialCommits::add);
        assertEquals(CustodyBatchStatus.QUARANTINED,
                partialCommits.get(2).batch().status());

        CustodyTransferEvidence wrong = CustodyTestFixtures.evidence(
                "player_inventory", CustodyAdapterCapability.RECONCILABLE,
                "late failure wrong evidence");
        List<CustodyBatchCommit> mismatchCommits = new ArrayList<>();
        new CustodyBatchExecutor().execute(new NegativeApplyAdapter(
                        CustodyAdapterApplyResult.rejected("Late failure"),
                        CustodyAdapterInspection.applied(
                                Map.of(lot.lotId(), wrong), "Different side effect found"), null),
                plan, evidence, CustodyTestFixtures.NOW, mismatchCommits::add);
        assertEquals(CustodyBatchStatus.QUARANTINED,
                mismatchCommits.get(2).batch().status());
        assertTrue(mismatchCommits.get(2).mutations().isEmpty());
    }

    @Test
    void failedInspectionLeavesApplyingForDurableRecoveryAndReplay() {
        CustodyLot lot = CustodyTestFixtures.itemLot("inspection recovery", 2);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "inspection recovery", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());
        List<CustodyBatchCommit> executionCommits = new ArrayList<>();
        NegativeApplyAdapter adapter = new NegativeApplyAdapter(
                CustodyAdapterApplyResult.rejected("Late verification failed"), null,
                new IllegalStateException("Inspection storage unavailable"));

        assertThrows(CustodyConflictException.class,
                () -> new CustodyBatchExecutor().execute(adapter, plan, evidence,
                        CustodyTestFixtures.NOW, executionCommits::add));

        assertTrue(adapter.sideEffectPerformed);
        assertTrue(adapter.inspectCalled);
        assertEquals(2, executionCommits.size());
        CustodyPreparedBatch applying = executionCommits.get(1).batch();
        assertEquals(CustodyBatchStatus.APPLYING, applying.status());

        List<CustodyBatchCommit> recoveryCommits = new ArrayList<>();
        CustodyPreparedBatch recovered = CustodyBatchRecovery.recover(
                new TestAdapter(evidence,
                        CustodyAdapterInspection.applied(evidence, "Recovered side effect"),
                        List.of()),
                applying, CustodyTestFixtures.NOW.plusSeconds(1), recoveryCommits::add);
        assertEquals(CustodyBatchStatus.APPLIED, recovered.status());
        assertEquals(1, recoveryCommits.size());

        CustodySavedData data = new CustodySavedData();
        data.applyBatchCommit(executionCommits.get(0));
        data.applyBatchCommit(executionCommits.get(1));
        assertFalse(data.applyBatchCommit(recoveryCommits.get(0)).replayed());
        assertTrue(data.applyBatchCommit(recoveryCommits.get(0)).replayed());
        assertFalse(data.hasUnresolvedPreparedOperations());
        assertEquals(lot, data.getLot(lot.lotId()));
    }

    @Test
    void applyingRecoveryUsesInspectionAndQuarantinesUnknownOrMismatchedEvidence() {
        CustodyLot lot = CustodyTestFixtures.itemLot("recover batch", 2);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "recover batch", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());
        CustodyPreparedBatch applying = CustodyPreparedBatch.prepare(plan,
                "recover token", evidence, CustodyTestFixtures.NOW).markApplying(0L,
                CustodyTestFixtures.NOW.plusSeconds(1));

        List<CustodyBatchCommit> appliedCommits = new ArrayList<>();
        CustodyPreparedBatch applied = CustodyBatchRecovery.recover(
                new TestAdapter(evidence,
                        CustodyAdapterInspection.applied(evidence, "Applied"), List.of()),
                applying, CustodyTestFixtures.NOW.plusSeconds(2), appliedCommits::add);
        assertEquals(CustodyBatchStatus.APPLIED, applied.status());
        assertEquals(1, appliedCommits.get(0).mutations().size());

        List<CustodyBatchCommit> unknownCommits = new ArrayList<>();
        CustodyPreparedBatch unknown = CustodyBatchRecovery.recover(
                new TestAdapter(evidence,
                        CustodyAdapterInspection.unknown("Adapter outcome is unknown"), List.of()),
                applying, CustodyTestFixtures.NOW.plusSeconds(2), unknownCommits::add);
        assertEquals(CustodyBatchStatus.QUARANTINED, unknown.status());
        assertTrue(unknownCommits.get(0).mutations().isEmpty());

        CustodyTransferEvidence wrong = CustodyTestFixtures.evidence(
                "player_inventory", CustodyAdapterCapability.RECONCILABLE, "wrong evidence");
        List<CustodyBatchCommit> mismatchCommits = new ArrayList<>();
        CustodyPreparedBatch mismatch = CustodyBatchRecovery.recover(
                new TestAdapter(evidence, CustodyAdapterInspection.applied(
                        Map.of(lot.lotId(), wrong), "Applied with different evidence"), List.of()),
                applying, CustodyTestFixtures.NOW.plusSeconds(2), mismatchCommits::add);
        assertEquals(CustodyBatchStatus.QUARANTINED, mismatch.status());
    }

    @Test
    void preparedCrashBoundaryIsSafelyClosedWithoutInspectingTheAdapter() {
        CustodyLot lot = CustodyTestFixtures.itemLot("prepared recovery", 1);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "prepared recovery", List.of(lot));
        Map<java.util.UUID, CustodyTransferEvidence> evidence =
                Map.of(lot.lotId(), lot.holdEvidence());
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                "prepared token", evidence, CustodyTestFixtures.NOW);
        TestAdapter adapter = new TestAdapter(evidence,
                CustodyAdapterInspection.unknown("Must not inspect"), List.of());
        List<CustodyBatchCommit> commits = new ArrayList<>();

        CustodyPreparedBatch recovered = CustodyBatchRecovery.recover(adapter, prepared,
                CustodyTestFixtures.NOW.plusSeconds(1), commits::add);

        assertEquals(CustodyBatchStatus.NOT_APPLIED, recovered.status());
        assertFalse(adapter.inspectCalled);
        assertEquals(1, recovered.revision());
    }

    @Test
    void compositeApplyRejectsPartiallyMaterializedLotsWithoutCompletingTheRest() {
        CustodyLot first = CustodyTestFixtures.itemLot("partial material first", 2);
        CustodyLot second = sameTransaction(first,
                CustodyTestFixtures.itemLot("partial material second", 3));
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "partial material", List.of(first, second));
        Map<java.util.UUID, CustodyTransferEvidence> evidence = Map.of(
                first.lotId(), first.holdEvidence(), second.lotId(), second.holdEvidence());
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                "partial material token", evidence, CustodyTestFixtures.NOW);
        CustodyPreparedBatch applying = prepared.markApplying(0L,
                CustodyTestFixtures.NOW.plusSeconds(1));
        CustodyPreparedBatch applied = applying.markApplied(1L, evidence,
                CustodyTestFixtures.NOW.plusSeconds(2));
        CustodyBatchCommit commit = CustodyBatchCommit.applied(applied,
                List.of(CustodyMutation.reserve(first), CustodyMutation.reserve(second)));
        CustodySavedData data = new CustodySavedData();
        data.applyBatchCommit(CustodyBatchCommit.state(prepared));
        data.applyBatchCommit(CustodyBatchCommit.state(applying));
        data.applyCommitted(CustodyMutation.reserve(first));

        assertThrows(CustodyConflictException.class, () -> data.applyBatchCommit(commit));
        assertEquals(first, data.getLot(first.lotId()));
        assertEquals(null, data.getLot(second.lotId()));
    }

    @Test
    void terminalBatchUsesTheDestinationAdapterAndExactTerminalEvidence() {
        CustodyLot lot = CustodyTestFixtures.itemLot("terminal reserve", 2);
        CustodyTransferEvidence evidence = CustodyTestFixtures.terminalEvidence("terminal release");
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RELEASE,
                "terminal release", List.of(lot));

        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                "terminal token", Map.of(lot.lotId(), evidence), CustodyTestFixtures.NOW);

        assertEquals(evidence.destination().adapterId(),
                prepared.operations().get(0).adapterId());
        assertEquals(plan, prepared.plan());
    }

    private static CustodyLot sameTransaction(CustodyLot first, CustodyLot other) {
        return new CustodyLot(other.lotId(), first.transactionId(),
                other.reserveRequestKey(), other.assetType(), other.protectionTier(),
                other.sourceCapability(), other.state(), other.units(), other.currencyProvider(),
                other.itemSnapshots(), other.protectedProvenance(), other.assetFingerprint(),
                other.holdEvidence(), other.createdAt(), other.updatedAt(), other.revision());
    }

    private static final class TestAdapter implements CustodyAdapter {
        private final Map<java.util.UUID, CustodyTransferEvidence> evidence;
        private final CustodyAdapterInspection inspection;
        private final List<CustodyBatchCommit> observedCommits;
        private boolean applyObservedApplying;
        private boolean inspectCalled;

        private TestAdapter(Map<java.util.UUID, CustodyTransferEvidence> evidence,
                            CustodyAdapterInspection inspection,
                            List<CustodyBatchCommit> observedCommits) {
            this.evidence = evidence;
            this.inspection = inspection;
            this.observedCommits = observedCommits;
        }

        @Override
        public String adapterId() {
            return "player_inventory";
        }

        @Override
        public CustodyAdapterCapability capability() {
            return CustodyAdapterCapability.RECONCILABLE;
        }

        @Override
        public CustodySimulationResult simulate(CustodyBatchPlan plan) {
            return CustodySimulationResult.accepted(
                    plan.requiredUnits(), plan.requiredUnits(), "test simulation");
        }

        @Override
        public CustodyAdapterApplyResult apply(CustodyBatchPlan plan, String simulationToken) {
            applyObservedApplying = observedCommits.size() == 2
                    && observedCommits.get(1).batch().status() == CustodyBatchStatus.APPLYING;
            return new CustodyAdapterApplyResult(true, Set.copyOf(evidence.keySet()), evidence, "");
        }

        @Override
        public CustodyAdapterInspection inspect(String simulationToken) {
            inspectCalled = true;
            return inspection;
        }
    }

    private static final class NegativeApplyAdapter implements CustodyAdapter {
        private final CustodyAdapterApplyResult application;
        private final CustodyAdapterInspection inspection;
        private final RuntimeException inspectionFailure;
        private boolean sideEffectPerformed;
        private boolean inspectCalled;
        private String applyToken;
        private String inspectToken;

        private NegativeApplyAdapter(CustodyAdapterApplyResult application,
                                     CustodyAdapterInspection inspection,
                                     RuntimeException inspectionFailure) {
            this.application = application;
            this.inspection = inspection;
            this.inspectionFailure = inspectionFailure;
        }

        @Override
        public String adapterId() {
            return "player_inventory";
        }

        @Override
        public CustodyAdapterCapability capability() {
            return CustodyAdapterCapability.RECONCILABLE;
        }

        @Override
        public CustodySimulationResult simulate(CustodyBatchPlan plan) {
            return CustodySimulationResult.accepted(
                    plan.requiredUnits(), plan.requiredUnits(), "late failure token");
        }

        @Override
        public CustodyAdapterApplyResult apply(CustodyBatchPlan plan, String simulationToken) {
            sideEffectPerformed = true;
            applyToken = simulationToken;
            return application;
        }

        @Override
        public CustodyAdapterInspection inspect(String simulationToken) {
            inspectCalled = true;
            inspectToken = simulationToken;
            if (inspectionFailure != null) {
                throw inspectionFailure;
            }
            return inspection;
        }
    }
}
