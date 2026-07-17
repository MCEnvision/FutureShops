package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import com.enviouse.futureshops.server.escrow.runtime.EscrowPreparedCheckpointRestore;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowSavedDataCheckpointBundleTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00.123456789Z");

    @Test
    void roundTripRestoresNonemptyStateInAllSevenStores() {
        StoreSet source = seededStores("roundtrip.source", lineage("roundtrip.source"), 3L);
        StoreSet live = seededStores("roundtrip.live", lineage("roundtrip.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> expected = source.bundle().captureSnapshots();

        live.bundle().prepareSnapshots(expected, source.lineage(), source.sequence()).apply();

        assertSnapshotsEqual(expected, live.bundle().captureSnapshots());
        assertEquals(source.transactions().getTransaction(
                        new EscrowTransactionId(source.transactionId())),
                live.transactions().getTransaction(
                        new EscrowTransactionId(source.transactionId())));
        assertEquals(source.ledger().snapshotBalances(), live.ledger().snapshotBalances());
        assertEquals(source.claims().getClaim(source.claimId()),
                live.claims().getClaim(source.claimId()));
        assertEquals(source.administrativeAudit().getRecord(source.auditId()),
                live.administrativeAudit().getRecord(source.auditId()));
        assertEquals(source.custody().getLot(source.custodyLotId()),
                live.custody().getLot(source.custodyLotId()));
        assertEquals(source.protectedMints().getBatch(source.mintBatchId()),
                live.protectedMints().getBatch(source.mintBatchId()));
        assertEquals(Optional.of(source.lineage()), live.runtimeMetadata().journalLineage());
        assertEquals(source.sequence(), live.runtimeMetadata().lastAppliedSequence());
        assertAllStoresMaterialized(live);
    }

    @ParameterizedTest
    @EnumSource(EscrowCheckpointStore.class)
    void corruptedComponentCausesZeroLiveMutation(EscrowCheckpointStore corruptedStore) {
        StoreSet source = seededStores("corrupt.source", lineage("corrupt.source"), 3L);
        StoreSet live = seededStores("corrupt.live", lineage("corrupt.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> baseline = live.bundle().captureSnapshots();
        EnumMap<EscrowCheckpointStore, byte[]> corrupted = copy(
                source.bundle().captureSnapshots());
        byte[] component = corrupted.get(corruptedStore);
        component[component.length - 1] ^= 1;

        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(corrupted,
                        source.lineage(), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());
    }

    @Test
    void missingAndExtraComponentsCauseZeroLiveMutation() {
        StoreSet source = seededStores("shape.source", lineage("shape.source"), 3L);
        StoreSet live = seededStores("shape.live", lineage("shape.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> baseline = live.bundle().captureSnapshots();
        EnumMap<EscrowCheckpointStore, byte[]> missing = copy(
                source.bundle().captureSnapshots());
        missing.remove(EscrowCheckpointStore.CLAIMS);

        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(missing,
                        source.lineage(), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());

        Map<EscrowCheckpointStore, byte[]> extra = new HashMap<>(
                source.bundle().captureSnapshots());
        extra.put(null, new byte[]{1});
        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(extra,
                        source.lineage(), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());
    }

    @Test
    void componentAndAggregateLimitsBoundCaptureAndRestore() {
        StoreSet source = seededStores("limits.source", lineage("limits.source"), 3L);
        StoreSet live = seededStores("limits.live", lineage("limits.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> snapshots = source.bundle().captureSnapshots();
        Map<EscrowCheckpointStore, byte[]> baseline = live.bundle().captureSnapshots();
        int largestComponent = snapshots.values().stream()
                .mapToInt(value -> value.length).max().orElseThrow();
        long aggregate = snapshots.values().stream().mapToLong(value -> value.length).sum();
        assertTrue(largestComponent - 1 > EscrowCheckpointComponentCodec.FIXED_BYTES);
        assertTrue(aggregate > 1L);

        EscrowSavedDataCheckpointBundle captureComponentLimited = limitedBundle(
                source, largestComponent - 1,
                EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES);
        assertThrows(EscrowCheckpointSnapshotException.class,
                captureComponentLimited::captureSnapshots);
        EscrowSavedDataCheckpointBundle restoreComponentLimited = limitedBundle(
                live, largestComponent - 1,
                EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES);
        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> restoreComponentLimited.prepareSnapshots(snapshots,
                        source.lineage(), source.sequence()));

        EscrowSavedDataCheckpointBundle captureAggregateLimited = limitedBundle(
                source, EscrowCheckpoint.MAX_STORE_BYTES, aggregate - 1L);
        assertThrows(EscrowCheckpointSnapshotException.class,
                captureAggregateLimited::captureSnapshots);
        EscrowSavedDataCheckpointBundle restoreAggregateLimited = limitedBundle(
                live, EscrowCheckpoint.MAX_STORE_BYTES, aggregate - 1L);
        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> restoreAggregateLimited.prepareSnapshots(snapshots,
                        source.lineage(), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());
    }

    @Test
    void newerComponentSchemaAndWrongStoreIdentityCauseZeroLiveMutation() {
        StoreSet source = seededStores("header.source", lineage("header.source"), 3L);
        StoreSet live = seededStores("header.live", lineage("header.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> baseline = live.bundle().captureSnapshots();
        EnumMap<EscrowCheckpointStore, byte[]> newer = copy(
                source.bundle().captureSnapshots());
        ByteBuffer.wrap(newer.get(EscrowCheckpointStore.TRANSACTIONS))
                .putInt(Integer.BYTES, EscrowCheckpointComponentCodec.FORMAT_VERSION + 1);

        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(newer,
                        source.lineage(), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());

        EnumMap<EscrowCheckpointStore, byte[]> wrongStore = copy(
                source.bundle().captureSnapshots());
        ByteBuffer.wrap(wrongStore.get(EscrowCheckpointStore.TRANSACTIONS))
                .putInt(Integer.BYTES * 2, EscrowCheckpointStore.LEDGER.wireId());
        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(wrongStore,
                        source.lineage(), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());
    }

    @Test
    void wrongThreadApplyCanRetryOnceAndSuccessfulApplyIsOneShot() {
        StoreSet source = seededStores("thread.source", lineage("thread.source"), 3L);
        StoreSet live = seededStores("thread.live", lineage("thread.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> expected = source.bundle().captureSnapshots();
        Map<EscrowCheckpointStore, byte[]> baseline = live.bundle().captureSnapshots();
        EscrowPreparedCheckpointRestore prepared = live.bundle().prepareSnapshots(
                expected, source.lineage(), source.sequence());

        live.serverThread().set(false);
        assertThrows(IllegalStateException.class, prepared::apply);
        live.serverThread().set(true);
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());

        prepared.apply();
        assertSnapshotsEqual(expected, live.bundle().captureSnapshots());
        assertThrows(IllegalStateException.class, prepared::apply);
    }

    @Test
    void lineageAndBaseSequenceMismatchCauseZeroLiveMutation() {
        StoreSet source = seededStores("cursor.source", lineage("cursor.source"), 3L);
        StoreSet live = seededStores("cursor.live", lineage("cursor.live"), 2L);
        Map<EscrowCheckpointStore, byte[]> snapshots = source.bundle().captureSnapshots();
        Map<EscrowCheckpointStore, byte[]> baseline = live.bundle().captureSnapshots();

        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(snapshots,
                        lineage("cursor.wrong"), source.sequence()));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());
        assertThrows(EscrowCheckpointSnapshotException.class,
                () -> live.bundle().prepareSnapshots(snapshots,
                        source.lineage(), source.sequence() + 1L));
        assertSnapshotsEqual(baseline, live.bundle().captureSnapshots());
    }

    private static StoreSet seededStores(String key, UUID lineage, long sequence) {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        EscrowAdministrativeAuditSavedData administrativeAudit =
                new EscrowAdministrativeAuditSavedData();
        CustodySavedData custody = new CustodySavedData();
        ProtectedMintSavedData protectedMints = new ProtectedMintSavedData();
        EscrowRuntimeSavedData runtimeMetadata = new EscrowRuntimeSavedData();
        AtomicBoolean serverThread = new AtomicBoolean(true);
        UUID transactionId = id(key + ".transaction");
        UUID playerId = id(key + ".player");
        UUID claimId = id(key + ".claim");
        UUID auditId = id(key + ".audit");
        UUID custodyLotId = id(key + ".custody");
        UUID mintBatchId = id(key + ".mint.batch");

        EscrowParty payer = EscrowParty.player(playerId);
        EscrowParty recipient = EscrowParty.system(key + ".recipient");
        EscrowTransaction transaction = EscrowTransaction.create(
                new EscrowTransactionId(transactionId), Optional.empty(),
                new EscrowRequestKey(key + ".payment"), EscrowOperation.PLAYER_PAYMENT,
                Set.of(
                        new EscrowParticipant(payer, Set.of(
                                EscrowParticipantRole.INITIATOR,
                                EscrowParticipantRole.PAYER)),
                        new EscrowParticipant(recipient, Set.of(
                                EscrowParticipantRole.BENEFICIARY,
                                EscrowParticipantRole.RECIPIENT))),
                List.of(new EscrowAssetLot(id(key + ".asset"),
                        EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED, payer, recipient, 1L,
                        Optional.of(new MoneyAmount("futureshops:credits", 125L)),
                        new byte[0], Map.of("source", key))),
                NOW, 4L, Optional.empty());
        transactions.applyCommitted(transaction);

        LedgerAccountId wallet = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, playerId.toString());
        ledger.applyCommitted(new LedgerTransaction(id(key + ".ledger"),
                key + ".ledger", "checkpoint fixture", List.of(
                new LedgerLeg(LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE), -125L),
                new LedgerLeg(wallet, 125L))));

        claims.createCommitted(new EscrowClaim(claimId, transactionId, playerId,
                key + ".claim.source", ClaimKind.MONEY, 50L, 50L, new byte[0],
                ClaimStatus.PENDING, key + " claim", NOW, NOW));

        administrativeAudit.append(new EscrowAdministrativeRecord(auditId, key + ".admin",
                EscrowAdministrativeAction.ENTER_MAINTENANCE, Optional.empty(),
                "checkpoint fixture", NOW, true, "entered"));

        CustodyEndpointEvidence custodySource = CustodyEndpointEvidence.captured(
                "wallet", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                playerId.toString(), "wallet", new byte[]{1}, new byte[]{2},
                key + ".custody.source");
        CustodyEndpointEvidence custodyDestination = CustodyEndpointEvidence.captured(
                "escrow_vault", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                "escrow", "vault", new byte[]{3}, new byte[]{4},
                key + ".custody.destination");
        custody.reserveCommitted(CustodyLot.held(custodyLotId, transactionId,
                key + ".custody.reserve", CustodyAssetType.WALLET_RESERVE,
                CustodyProtectionTier.PROTECTED, 75L,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(),
                new CustodyTransferEvidence(custodySource, custodyDestination), NOW));

        UUID mintTransactionId = id(key + ".mint.transaction");
        ProtectedMintBatch mintBatch = ProtectedMintBatch.plan(mintBatchId,
                mintTransactionId, key + ".mint.authorize", 25L, 4,
                key + ".server", NOW,
                (batch, mintTransaction, denomination, count, server, authorizedAt) ->
                        "evidence." + batch + "." + mintTransaction + "."
                                + denomination + "." + count);
        protectedMints.authorizeCommitted(mintBatch);

        runtimeMetadata.establishLineage(lineage, 1L);
        for (long next = 2L; next <= sequence; next++) {
            runtimeMetadata.advance(lineage, next);
        }

        EscrowSavedDataCheckpointBundle bundle = new EscrowSavedDataCheckpointBundle(
                transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, runtimeMetadata, serverThread::get);
        return new StoreSet(lineage, sequence, transactions, ledger, claims,
                administrativeAudit, custody, protectedMints, runtimeMetadata,
                serverThread, bundle, transactionId, claimId, auditId,
                custodyLotId, mintBatchId);
    }

    private static EscrowSavedDataCheckpointBundle limitedBundle(
            StoreSet stores, int maximumStoreBytes, long maximumAggregateBytes) {
        return new EscrowSavedDataCheckpointBundle(stores.transactions(), stores.ledger(),
                stores.claims(), stores.administrativeAudit(), stores.custody(),
                stores.protectedMints(), stores.runtimeMetadata(),
                stores.serverThread()::get, maximumStoreBytes, maximumAggregateBytes);
    }

    private static void assertAllStoresMaterialized(StoreSet stores) {
        assertTrue(stores.transactions().hasMaterializedState());
        assertTrue(stores.ledger().hasMaterializedState());
        assertTrue(stores.claims().hasMaterializedState());
        assertTrue(stores.administrativeAudit().hasMaterializedState());
        assertTrue(stores.custody().hasMaterializedState());
        assertTrue(stores.protectedMints().hasMaterializedState());
        assertNotNull(stores.runtimeMetadata().journalLineage().orElse(null));
    }

    private static UUID lineage(String key) {
        return id(key + ".lineage");
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static EnumMap<EscrowCheckpointStore, byte[]> copy(
            Map<EscrowCheckpointStore, byte[]> snapshots) {
        EnumMap<EscrowCheckpointStore, byte[]> copy =
                new EnumMap<>(EscrowCheckpointStore.class);
        snapshots.forEach((store, value) -> copy.put(store, value.clone()));
        return copy;
    }

    private static void assertSnapshotsEqual(
            Map<EscrowCheckpointStore, byte[]> expected,
            Map<EscrowCheckpointStore, byte[]> actual) {
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            assertArrayEquals(expected.get(store), actual.get(store), store.name());
        }
    }

    private record StoreSet(
            UUID lineage,
            long sequence,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            EscrowRuntimeSavedData runtimeMetadata,
            AtomicBoolean serverThread,
            EscrowSavedDataCheckpointBundle bundle,
            UUID transactionId,
            UUID claimId,
            UUID auditId,
            UUID custodyLotId,
            UUID mintBatchId) {
    }
}
