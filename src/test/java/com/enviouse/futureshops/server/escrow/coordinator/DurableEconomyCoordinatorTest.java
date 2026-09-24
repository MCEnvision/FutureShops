package com.enviouse.futureshops.server.escrow.coordinator;

import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.LegId;
import com.enviouse.futureshops.api.economy.RootId;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableEconomyCoordinatorTest {
    private static final String FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final BindingV1 BINDING = new BindingV1(
            "test", 1, "fixture", "fixture-v1", FINGERPRINT, FINGERPRINT,
            "lineage", UUID.randomUUID(), "coins", 2, "fixture-store", 1L, 1);

    @TempDir
    Path temp;

    @Test
    void flushesIntentBeforeEffectAndReplaysAfterCleanRestart() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        Path journal = temp.resolve("escrow.wal");
        RootId rootId = new RootId(UUID.randomUUID());
        BoundLeg leg = leg(rootId, UUID.randomUUID(), BINDING, 10L, true);
        RouteContext route = route(BINDING);
        PreparedRoot prepared;
        try (DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, effect)) {
            prepared = coordinator.admit(route, rootId, List.of(leg),
                    new CustodyPlan("custody-1", 10L));
            assertEquals(0, effect.dispatches);
            DurableEconomyCoordinator.SettlementOutcome result = coordinator.execute(prepared);
            assertEquals(DurableEconomyCoordinator.SettlementOutcome.Status.CONFIRMED, result.status());
            assertEquals(1, effect.dispatches);
            assertEquals(OperationState.RESOLVED, coordinator.root(rootId).orElseThrow().state());
        }
        try (DurableEconomyCoordinator reopened = DurableEconomyCoordinator.open(journal, effect)) {
            DurableEconomyCoordinator.SettlementOutcome replay = reopened.execute(
                    reopened.admit(route, rootId, List.of(leg),
                            new CustodyPlan("custody-1", 10L)));
            assertEquals(DurableEconomyCoordinator.SettlementOutcome.Status.REPLAYED, replay.status());
            assertEquals(1, effect.dispatches);
        }
    }

    @Test
    void unknownOutcomeFreezesAndRecoveryUsesLookupWithoutRetry() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        effect.next = DispatchResult.unknown();
        Path journal = temp.resolve("unknown.wal");
        RootId rootId = new RootId(UUID.randomUUID());
        BoundLeg leg = leg(rootId, UUID.randomUUID(), BINDING, 12L, true);
        try (DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, effect)) {
            PreparedRoot prepared = coordinator.admit(route(BINDING), rootId, List.of(leg),
                    new CustodyPlan("custody-unknown", 12L));
            assertEquals(DurableEconomyCoordinator.SettlementOutcome.Status.PENDING,
                    coordinator.execute(prepared).status());
            assertEquals(CoordinatorLifecycle.FROZEN, coordinator.lifecycle());
            assertEquals(1, effect.dispatches);
            effect.receipts.put(leg.legId().value(),
                    new DispatchReceipt("external-12", 12L, leg.payloadFingerprint()));
            DurableEconomyCoordinator.RecoveryOutcome recovery = coordinator.recover(rootId);
            assertEquals(DurableEconomyCoordinator.RecoveryOutcome.Status.RESOLVED, recovery.status());
            assertEquals(1, effect.dispatches);
            assertEquals(OperationState.RESOLVED, coordinator.root(rootId).orElseThrow().state());
        }
    }

    @Test
    void changedPayloadAndBindingAreRefusedAndClaimsCollectAtMostOnce() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        Path journal = temp.resolve("claims.wal");
        RootId rootId = new RootId(UUID.randomUUID());
        BindingV1 otherBinding = new BindingV1(
                "test", 1, "fixture", "fixture-v1", FINGERPRINT, FINGERPRINT,
                "lineage", UUID.randomUUID(), "coins", 2, "fixture-store", 1L, 1);
        BoundLeg first = leg(rootId, UUID.randomUUID(), BINDING, 5L, true);
        BoundLeg second = leg(rootId, UUID.randomUUID(), BINDING, 7L, true);
        try (DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, effect)) {
            PreparedRoot prepared = coordinator.admit(route(BINDING), rootId, List.of(first, second),
                    new CustodyPlan("custody-claims", 12L));
            effect.next = DispatchResult.confirmed(
                    new DispatchReceipt("external-5", 5L, first.payloadFingerprint()));
            effect.nextByLeg.put(second.legId().value(), DispatchResult.unknown());
            assertEquals(DurableEconomyCoordinator.SettlementOutcome.Status.PARTIAL,
                    coordinator.execute(prepared).status());
            DurableEconomyCoordinator.ClaimSnapshot claim = coordinator.claims().get(0);
            assertThrows(CoordinatorException.class,
                    () -> coordinator.collect(new ClaimId(claim.claimId().value()), otherBinding));
            assertEquals(DurableEconomyCoordinator.ClaimOutcome.Status.COLLECTED,
                    coordinator.collect(new ClaimId(claim.claimId().value()), BINDING).status());
            assertEquals(DurableEconomyCoordinator.ClaimOutcome.Status.ALREADY_COLLECTED,
                    coordinator.collect(new ClaimId(claim.claimId().value()), BINDING).status());
        }
    }

    @Test
    void concurrentClaimCollectionDeliversOnlyOnce() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        Path journal = temp.resolve("concurrent.wal");
        RootId rootId = new RootId(UUID.randomUUID());
        BoundLeg first = leg(rootId, UUID.randomUUID(), BINDING, 5L, true);
        BoundLeg second = leg(rootId, UUID.randomUUID(), BINDING, 7L, true);
        try (DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, effect)) {
            PreparedRoot prepared = coordinator.admit(route(BINDING), rootId, List.of(first, second),
                    new CustodyPlan("custody-concurrent", 12L));
            effect.next = DispatchResult.confirmed(
                    new DispatchReceipt("external-5", 5L, first.payloadFingerprint()));
            effect.nextByLeg.put(second.legId().value(), DispatchResult.unknown());
            coordinator.execute(prepared);
            ClaimId claimId = coordinator.claims().get(0).claimId();
            var pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            var firstResult = pool.submit(() -> {
                start.await();
                return coordinator.collect(claimId, BINDING).status();
            });
            var secondResult = pool.submit(() -> {
                start.await();
                return coordinator.collect(claimId, BINDING).status();
            });
            start.countDown();
            var statuses = List.of(firstResult.get(), secondResult.get());
            pool.shutdownNow();
            assertEquals(1, statuses.stream().filter(
                    value -> value == DurableEconomyCoordinator.ClaimOutcome.Status.COLLECTED).count());
            assertEquals(1, statuses.stream().filter(
                    value -> value == DurableEconomyCoordinator.ClaimOutcome.Status.ALREADY_COLLECTED).count());
        }
    }

    @Test
    void malformedCoordinatorPayloadIsRejected() throws Exception {
        Path journalPath = temp.resolve("corrupt.wal");
        try (WriteAheadJournal journal = WriteAheadJournal.open(journalPath)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), new byte[] {1, 2, 3});
        }
        CoordinatorException failure = assertThrows(CoordinatorException.class,
                () -> DurableEconomyCoordinator.open(journalPath, new FixtureEffect()));
        assertEquals(CoordinatorError.STORAGE_CORRUPT, failure.error());
    }

    @Test
    void uncleanRestartRecoversPreparedIntentWithoutDispatching() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        Path journal = temp.resolve("prepared.wal");
        RootId rootId = new RootId(UUID.randomUUID());
        BoundLeg leg = leg(rootId, UUID.randomUUID(), BINDING, 4L, true);
        DurableEconomyCoordinator first = DurableEconomyCoordinator.open(journal, effect);
        first.admit(route(BINDING), rootId, List.of(leg), new CustodyPlan("custody-prepared", 4L));
        first.closeUncleanForTest();
        try (DurableEconomyCoordinator reopened = DurableEconomyCoordinator.open(journal, effect)) {
            assertEquals(CoordinatorLifecycle.RECOVERING, reopened.lifecycle());
            assertEquals(DurableEconomyCoordinator.RecoveryOutcome.Status.RESOLVED,
                    reopened.recover(rootId).status());
            assertEquals(0, effect.dispatches);
            assertEquals(OperationState.RESOLVED, reopened.root(rootId).orElseThrow().state());
        }
    }

    @Test
    void receiptSurvivesFreshEffectProcessAndIsResolvedByLookup() throws Exception {
        Path journal = temp.resolve("fresh-process.wal");
        Path receipts = temp.resolve("provider-receipts.bin");
        RootId rootId = new RootId(UUID.randomUUID());
        BoundLeg leg = leg(rootId, UUID.randomUUID(), BINDING, 9L, true);
        try (PersistentFixtureEffect firstEffect = new PersistentFixtureEffect(receipts, true);
             DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, firstEffect)) {
            PreparedRoot prepared = coordinator.admit(route(BINDING), rootId, List.of(leg),
                    new CustodyPlan("custody-fresh", 9L));
            assertEquals(DurableEconomyCoordinator.SettlementOutcome.Status.PENDING,
                    coordinator.execute(prepared).status());
        }
        try (PersistentFixtureEffect secondEffect = new PersistentFixtureEffect(receipts, false);
             DurableEconomyCoordinator reopened = DurableEconomyCoordinator.open(journal, secondEffect)) {
            assertEquals(DurableEconomyCoordinator.RecoveryOutcome.Status.RESOLVED,
                    reopened.recover(rootId).status());
            assertEquals(0, secondEffect.dispatches);
        }
    }

    @Test
    void operationTransitionMatrixRejectsTerminalReplay() {
        assertTrue(OperationState.PREPARED.canTransitionTo(OperationState.SUBMITTED));
        assertTrue(OperationState.SUBMITTED.canTransitionTo(OperationState.UNKNOWN));
        assertTrue(OperationState.UNKNOWN.canTransitionTo(OperationState.CONFIRMED));
        assertTrue(OperationState.CONFIRMED.canTransitionTo(OperationState.RESOLVED));
        assertTrue(!OperationState.RESOLVED.canTransitionTo(OperationState.SUBMITTED));
        assertTrue(!OperationState.FROZEN.canTransitionTo(OperationState.SUBMITTED));
    }

    @Test
    void drainingStopsAdmissionsUntilExplicitResume() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        Path journal = temp.resolve("draining.wal");
        try (DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, effect)) {
            coordinator.beginDraining();
            assertEquals(CoordinatorLifecycle.DRAINING, coordinator.lifecycle());
            RootId rootId = new RootId(UUID.randomUUID());
            BoundLeg leg = leg(rootId, UUID.randomUUID(), BINDING, 2L, true);
            assertThrows(CoordinatorException.class,
                    () -> coordinator.admit(route(BINDING), rootId, List.of(leg),
                            new CustodyPlan("custody-drain", 2L)));
            coordinator.resumeReady();
            assertEquals(CoordinatorLifecycle.READY, coordinator.lifecycle());
            assertTrue(coordinator.admit(route(BINDING), rootId, List.of(leg),
                    new CustodyPlan("custody-drain", 2L)).rootId().equals(rootId));
        }
    }

    @Test
    void custodyMustMatchTheAdmittedLegTotal() throws Exception {
        FixtureEffect effect = new FixtureEffect();
        Path journal = temp.resolve("custody-mismatch.wal");
        RootId rootId = new RootId(UUID.randomUUID());
        BoundLeg leg = leg(rootId, UUID.randomUUID(), BINDING, 2L, true);
        try (DurableEconomyCoordinator coordinator = DurableEconomyCoordinator.open(journal, effect)) {
            CoordinatorException failure = assertThrows(CoordinatorException.class,
                    () -> coordinator.admit(route(BINDING), rootId, List.of(leg),
                            new CustodyPlan("custody-mismatch", 3L)));
            assertEquals(CoordinatorError.INVALID_AMOUNT, failure.error());
            assertEquals(0, effect.dispatches);
            assertEquals(0, coordinator.rootCount());
        }
    }

    private static RouteContext route(BindingV1 binding) {
        return new RouteContext("fixture.route", UUID.randomUUID(), binding, true, true);
    }

    private static BoundLeg leg(RootId rootId, UUID legId, BindingV1 binding, long amount,
                                boolean debit) {
        return new BoundLeg(rootId, new LegId(legId), binding, "fixture.operation", amount,
                FINGERPRINT, debit);
    }

    private static final class FixtureEffect implements DurableEconomyEffect {
        private final Map<UUID, DispatchReceipt> receipts = new ConcurrentHashMap<>();
        private final Map<UUID, DispatchResult> nextByLeg = new ConcurrentHashMap<>();
        private DispatchResult next = null;
        private int dispatches;

        @Override
        public DispatchResult dispatch(BoundLeg leg) {
            dispatches++;
            DispatchResult result = nextByLeg.remove(leg.legId().value());
            if (result == null) {
                result = next;
            }
            if (result == null) {
                result = DispatchResult.confirmed(
                        new DispatchReceipt("external-" + leg.minorUnits(), leg.minorUnits(),
                                leg.payloadFingerprint()));
            }
            if (result.status() == DispatchResult.Status.CONFIRMED) {
                receipts.put(leg.legId().value(), result.receipt().orElseThrow());
            }
            next = null;
            return result;
        }

        @Override
        public Optional<DispatchReceipt> lookup(BoundLeg leg) {
            return Optional.ofNullable(receipts.get(leg.legId().value()));
        }
    }

    private static final class PersistentFixtureEffect implements DurableEconomyEffect, AutoCloseable {
        private final Path path;
        private final boolean writeAndReportUnknown;
        private final Map<UUID, DispatchReceipt> receipts = new ConcurrentHashMap<>();
        private int dispatches;

        private PersistentFixtureEffect(Path path, boolean writeAndReportUnknown) throws IOException {
            this.path = path;
            this.writeAndReportUnknown = writeAndReportUnknown;
            if (java.nio.file.Files.exists(path)) {
                try (DataInputStream input = new DataInputStream(java.nio.file.Files.newInputStream(path))) {
                    while (true) {
                        try {
                            receipts.put(new UUID(input.readLong(), input.readLong()),
                                    new DispatchReceipt(input.readUTF(), input.readLong(), input.readUTF()));
                        } catch (EOFException end) {
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public DispatchResult dispatch(BoundLeg leg) {
            dispatches++;
            DispatchReceipt receipt = receipts.computeIfAbsent(leg.legId().value(), ignored ->
                    new DispatchReceipt("persistent-" + leg.minorUnits(), leg.minorUnits(),
                            leg.payloadFingerprint()));
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                DataOutputStream output = new DataOutputStream(java.nio.channels.Channels.newOutputStream(channel));
                output.writeLong(leg.legId().value().getMostSignificantBits());
                output.writeLong(leg.legId().value().getLeastSignificantBits());
                output.writeUTF(receipt.externalOperationId());
                output.writeLong(receipt.minorUnits());
                output.writeUTF(receipt.payloadFingerprint());
                output.flush();
                channel.force(true);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return writeAndReportUnknown ? DispatchResult.unknown() : DispatchResult.confirmed(receipt);
        }

        @Override
        public Optional<DispatchReceipt> lookup(BoundLeg leg) {
            return Optional.ofNullable(receipts.get(leg.legId().value()));
        }

        @Override
        public void close() {
        }
    }
}
