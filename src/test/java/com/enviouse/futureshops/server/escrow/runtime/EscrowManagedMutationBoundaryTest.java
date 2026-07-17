package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpoint;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointManifest;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointReference;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowSavedDataCheckpointBundle;
import com.enviouse.futureshops.server.escrow.checkpoint.TrustedEscrowCheckpoint;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowManagedMutationBoundaryTest {
    private static final String REJECTION =
            "Managed escrow saved data requires a journal mutation permit";

    @Test
    void everyManagedSavedDataRejectsDirectMutation() {
        ManagedStores stores = managedStores();

        assertRejected(() -> stores.transactions().applyCommitted(null));
        assertRejected(() -> stores.transactions().replaceFromValidated(null));
        assertRejected(() -> stores.ledger().applyCommitted(null));
        assertRejected(() -> stores.ledger().replaceFromValidated(null));
        assertRejected(() -> stores.claims().createCommitted(null));
        assertRejected(() -> stores.claims().deliverCommitted(
                null, null, null, 0L, null));
        assertRejected(() -> stores.claims().quarantineCommitted(null, null, null));
        assertRejected(() -> stores.claims().applyMaintenanceReplace(null));
        assertRejected(() -> stores.claims().replaceFromValidated(null));
        assertRejected(() -> stores.administrativeAudit().append(null));
        assertRejected(() -> stores.administrativeAudit().replaceFromValidated(null));
        assertRejected(() -> stores.custody().reserveCommitted(null));
        assertRejected(() -> stores.custody().releaseCommitted(
                null, null, null, null));
        assertRejected(() -> stores.custody().consumeCommitted(
                null, null, null, null));
        assertRejected(() -> stores.custody().quarantineCommitted(
                null, null, null, null));
        assertRejected(() -> stores.custody().applyCommitted(null));
        assertRejected(() -> stores.custody().prepareCommitted(null));
        assertRejected(() -> stores.custody().applyBatchCommitted(null));
        assertRejected(() -> stores.custody().applyBatchCommit(null));
        assertRejected(() -> stores.custody().replaceFromValidated(null));
        assertRejected(() -> stores.protectedMints().applyCommitted(null));
        assertRejected(() -> stores.protectedMints().replaceFromValidated(null));
        assertRejected(() -> stores.runtimeMetadata().establishLineage(null, 1L));
        assertRejected(() -> stores.runtimeMetadata().advance(null, 2L));
        assertRejected(() -> stores.runtimeMetadata().adoptTrustedCheckpoint(null));
        assertRejected(() -> stores.runtimeMetadata().applyMaintenance(null, null));
        assertRejected(() -> stores.runtimeMetadata().replaceFromValidated(null));

        assertEquals(0L, stores.ledger().balance(
                LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE)));
        assertFalse(stores.runtimeMetadata().journalLineage().isPresent());
    }

    @Test
    void permitBindingIsOneWayAndIdentityChecked() {
        EscrowRuntimeSavedData runtime = new EscrowRuntimeSavedData();
        EscrowMutationPermit first = runtime.acquireManagedMutationPermit();

        runtime.bindManagedMutationPermit(first);
        assertTrue(runtime.isManagedRuntimeData());
        assertThrows(IllegalStateException.class,
                () -> runtime.bindManagedMutationPermit(new EscrowMutationPermit()));
        assertTrue(Arrays.stream(EscrowManagedSavedData.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("unbind")));
    }

    @Test
    void permitScopeIsReentrantExceptionSafeAndThreadBound() throws InterruptedException {
        EscrowMutationPermit permit = new EscrowMutationPermit();
        EscrowMutationPermit other = new EscrowMutationPermit();

        assertThrows(IllegalStateException.class, () -> {
            try (EscrowMutationPermit.Scope ignored = permit.activate()) {
                try (EscrowMutationPermit.Scope nested = permit.activate()) {
                    assertTrue(permit.isActive());
                    assertThrows(IllegalStateException.class, other::activate);
                    throw new IllegalStateException("planned failure");
                }
            }
        });
        assertFalse(permit.isActive());

        EscrowMutationPermit.Scope ownerScope = permit.activate();
        AtomicReference<Throwable> crossThreadFailure = new AtomicReference<>();
        Thread otherThread = new Thread(() -> {
            try {
                ownerScope.close();
            } catch (Throwable failure) {
                crossThreadFailure.set(failure);
            }
        });
        otherThread.start();
        otherThread.join();
        assertTrue(crossThreadFailure.get() instanceof IllegalStateException);
        assertTrue(permit.isActive());
        ownerScope.close();
        assertFalse(permit.isActive());
    }

    @Test
    void failedRuntimeOpenCanReuseThePermitRetainedByRuntimeData() {
        EscrowRuntimeSavedData runtime = new EscrowRuntimeSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        EscrowMutationPermit firstAttempt = runtime.acquireManagedMutationPermit();
        ledger.bindManagedMutationPermit(firstAttempt);

        assertRejected(() -> ledger.applyCommitted(transaction("failed.open", 7L)));
        EscrowMutationPermit retry = runtime.acquireManagedMutationPermit();
        assertSame(firstAttempt, retry);
        ledger.bindManagedMutationPermit(retry);

        try (EscrowMutationPermit.Scope ignored = retry.activate()) {
            runtime.establishLineage(id("failed.open.lineage"), 1L);
            ledger.applyCommitted(transaction("failed.open", 7L));
        }
        assertEquals(7L, ledger.balance(playerAccount("failed.open")));
        assertRejected(() -> ledger.applyCommitted(transaction("failed.open.second", 1L)));
    }

    @Test
    void journalCommitAndReplayMutateManagedStores(@TempDir Path directory) {
        ManagedStores stores = managedStores();
        EscrowSavedDataMutationApplier applier = stores.applier();
        Path journal = directory.resolve("managed.wal");
        LedgerTransaction transaction = transaction("managed.replay", 25L);
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.LEDGER_APPLY,
                LedgerJournalCodec.encode(transaction));
        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(
                journal, stores.runtimeMetadata(), applier, () -> false, stores.permit());

        assertEquals(EscrowRuntimeState.READY, first.start());
        assertTrue(first.commit(transaction.transactionId(), event).record().isPresent());
        assertEquals(25L, stores.ledger().balance(playerAccount("managed.replay")));
        first.stop();

        EscrowRuntimeCoordinator replay = new EscrowRuntimeCoordinator(
                journal, stores.runtimeMetadata(), applier,
                stores.transactions()::hasMaterializedState, stores.permit());
        assertEquals(EscrowRuntimeState.READY, replay.start());
        assertEquals(25L, stores.ledger().balance(playerAccount("managed.replay")));
        assertEquals(2L, stores.runtimeMetadata().lastAppliedSequence());
        replay.stop();
        assertRejected(() -> stores.ledger().applyCommitted(
                transaction("managed.replay.direct", 1L)));
    }

    @Test
    void trustedCheckpointRestoreMutatesManagedStores(@TempDir Path directory) {
        StoreSet source = new StoreSet();
        UUID sourceLineage = id("checkpoint.source.lineage");
        source.ledger.applyCommitted(transaction("checkpoint.source", 42L));
        source.runtimeMetadata.establishLineage(sourceLineage, 1L);
        source.runtimeMetadata.advance(sourceLineage, 2L);
        EscrowSavedDataCheckpointBundle sourceBundle = source.bundle();
        UUID checkpointId = id("checkpoint.id");
        UUID replacementLineage = id("checkpoint.replacement.lineage");
        Instant createdAt = Instant.parse("2026-07-17T12:00:00Z");
        EscrowCheckpoint checkpoint = new EscrowCheckpoint(
                checkpointId, sourceLineage, replacementLineage, 2L, createdAt,
                sourceBundle.captureSnapshots());
        EscrowCheckpointManifest manifest = new EscrowCheckpointManifest(
                checkpointId, sourceLineage, replacementLineage, 2L, createdAt,
                1L, new byte[EscrowCheckpointManifest.SHA256_BYTES]);
        TrustedEscrowCheckpoint trusted = new TrustedEscrowCheckpoint(
                checkpoint, new EscrowCheckpointReference(manifest),
                directory.resolve("generation"));

        ManagedStores target = managedStores();
        EscrowSavedDataCheckpointBundle targetBundle = target.bundle();
        assertRejected(() -> target.ledger().applyCommitted(
                transaction("checkpoint.direct", 1L)));
        targetBundle.prepareTrustedRestore(trusted).apply();

        assertEquals(42L, target.ledger().balance(playerAccount("checkpoint.source")));
        assertEquals(sourceLineage,
                target.runtimeMetadata().journalLineage().orElseThrow());
        assertEquals(2L, target.runtimeMetadata().lastAppliedSequence());
        assertRejected(() -> target.runtimeMetadata().advance(sourceLineage, 3L));
    }

    @Test
    void permitHasNoPublicFactoryOrRuntimeAccessor() throws NoSuchMethodException {
        assertEquals(0, EscrowMutationPermit.class.getConstructors().length);
        assertTrue(Arrays.stream(EscrowMutationPermit.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .noneMatch(method -> method.getReturnType() == EscrowMutationPermit.class));
        assertTrue(Arrays.stream(EscrowMutationPermit.class.getFields())
                .noneMatch(field -> field.getType() == EscrowMutationPermit.class));
        for (Class<?> type : List.of(
                EscrowRuntimeSavedData.class,
                EscrowRuntimeService.class,
                EscrowRuntimeCoordinator.class,
                EscrowSavedDataMutationApplier.class,
                EscrowSavedDataCheckpointBundle.class)) {
            assertTrue(Arrays.stream(type.getMethods())
                    .noneMatch(method -> method.getReturnType()
                            == EscrowMutationPermit.class), type.getName());
        }
        assertTrue(Arrays.stream(EscrowSavedDataCheckpointBundle.class.getMethods())
                .noneMatch(method -> method.getName().equals("fromServer")));
        assertFalse(Modifier.isPublic(EscrowRuntimeSavedData.class
                .getDeclaredMethod("acquireManagedMutationPermit").getModifiers()));
        for (Class<?> type : List.of(
                EscrowTransactionSavedData.class,
                LedgerSavedData.class,
                ClaimSavedData.class,
                EscrowAdministrativeAuditSavedData.class,
                CustodySavedData.class,
                ProtectedMintSavedData.class,
                EscrowRuntimeSavedData.class)) {
            assertTrue(EscrowManagedSavedData.class.isAssignableFrom(type), type.getName());
        }
    }

    private static ManagedStores managedStores() {
        StoreSet stores = new StoreSet();
        EscrowMutationPermit permit = stores.runtimeMetadata.acquireManagedMutationPermit();
        stores.transactions.bindManagedMutationPermit(permit);
        stores.ledger.bindManagedMutationPermit(permit);
        stores.claims.bindManagedMutationPermit(permit);
        stores.administrativeAudit.bindManagedMutationPermit(permit);
        stores.custody.bindManagedMutationPermit(permit);
        stores.protectedMints.bindManagedMutationPermit(permit);
        return new ManagedStores(stores, permit);
    }

    private static LedgerTransaction transaction(String key, long amount) {
        return new LedgerTransaction(id(key + ".transaction"), key + ".request",
                "managed mutation fixture", List.of(
                new LedgerLeg(LedgerAccountId.system(
                        LedgerAccountType.ADMIN_SOURCE), -amount),
                new LedgerLeg(playerAccount(key), amount)));
    }

    private static LedgerAccountId playerAccount(String key) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                id(key + ".player").toString());
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable mutation) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, mutation);
        assertEquals(REJECTION, failure.getMessage());
    }

    private static final class StoreSet {
        private final EscrowTransactionSavedData transactions =
                new EscrowTransactionSavedData();
        private final LedgerSavedData ledger = new LedgerSavedData();
        private final ClaimSavedData claims = new ClaimSavedData();
        private final EscrowAdministrativeAuditSavedData administrativeAudit =
                new EscrowAdministrativeAuditSavedData();
        private final CustodySavedData custody = new CustodySavedData();
        private final ProtectedMintSavedData protectedMints = new ProtectedMintSavedData();
        private final EscrowRuntimeSavedData runtimeMetadata = new EscrowRuntimeSavedData();

        private EscrowSavedDataCheckpointBundle bundle() {
            return new EscrowSavedDataCheckpointBundle(
                    transactions, ledger, claims, administrativeAudit, custody,
                    protectedMints, runtimeMetadata, () -> true);
        }
    }

    private record ManagedStores(StoreSet stores, EscrowMutationPermit permit) {
        private EscrowTransactionSavedData transactions() {
            return stores.transactions;
        }

        private LedgerSavedData ledger() {
            return stores.ledger;
        }

        private ClaimSavedData claims() {
            return stores.claims;
        }

        private EscrowAdministrativeAuditSavedData administrativeAudit() {
            return stores.administrativeAudit;
        }

        private CustodySavedData custody() {
            return stores.custody;
        }

        private ProtectedMintSavedData protectedMints() {
            return stores.protectedMints;
        }

        private EscrowRuntimeSavedData runtimeMetadata() {
            return stores.runtimeMetadata;
        }

        private EscrowSavedDataMutationApplier applier() {
            return new EscrowSavedDataMutationApplier(
                    transactions(), ledger(), claims(), administrativeAudit(), custody(),
                    protectedMints(), MaintenanceRuntimeMutationHandler.unavailable(),
                    AtmWithdrawalApplyFaultInjector.NONE, permit);
        }

        private EscrowSavedDataCheckpointBundle bundle() {
            return new EscrowSavedDataCheckpointBundle(
                    transactions(), ledger(), claims(), administrativeAudit(), custody(),
                    protectedMints(), runtimeMetadata(), () -> true, permit);
        }
    }
}
