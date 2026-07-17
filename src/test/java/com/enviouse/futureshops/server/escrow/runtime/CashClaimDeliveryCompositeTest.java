package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CashClaimCustodySupport;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryDeliveryToken;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashClaimDeliveryCompositeTest {
    private static final Instant CREATED =
            Instant.parse("2026-07-18T12:00:00Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void compositeCodecRoundTripsAndRejectsCorruption() {
        Fixture fixture = fixture();

        byte[] encoded = CashClaimDeliveryCommitCodec.encode(
                fixture.commit());

        assertEquals(fixture.commit(),
                CashClaimDeliveryCommitCodec.decode(encoded));
        byte[] corrupted = encoded.clone();
        corrupted[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> CashClaimDeliveryCommitCodec.decode(corrupted));
        assertThrows(IllegalArgumentException.class,
                () -> CashClaimDeliveryCommitCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
    }

    @Test
    void savedInventoryRecoveryCompletesClaimAndReplaysOnce() {
        Fixture fixture = fixture();
        ClaimSavedData claims = new ClaimSavedData();
        claims.createCommitted(fixture.claim());
        CustodySavedData custody = new CustodySavedData();
        CustodyPreparedBatch prepared = fixture.commit().custody().batch();
        CustodyPreparedBatch initial = new CustodyPreparedBatch(
                prepared.batchId(), prepared.transactionId(),
                prepared.requestKey(), prepared.operations(),
                CustodyBatchStatus.PREPARED, prepared.preparedAt(),
                prepared.preparedAt(), 0L, "Prepared");
        custody.applyBatchCommit(CustodyBatchCommit.state(initial));
        custody.applyBatchCommit(CustodyBatchCommit.state(
                initial.markApplying(0L, initial.updatedAt())));
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), claims,
                        new EscrowAdministrativeAuditSavedData(), custody,
                        new ProtectedMintSavedData());
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CASH_CLAIM_DELIVERY_COMMIT,
                CashClaimDeliveryCommitCodec.encode(fixture.commit()));
        JournalRecord record = new JournalRecord(1L,
                fixture.claim().transactionId(),
                EscrowStepIds.forEvent(
                        fixture.claim().transactionId(), event),
                EscrowJournalEventCodec.encode(event));

        assertEquals(EscrowPreflightResult.APPLY,
                applier.preflight(fixture.claim().transactionId(), event));
        applier.apply(record, event);

        assertEquals(ClaimStatus.COMPLETED,
                claims.getClaim(fixture.claim().claimId()).status());
        assertEquals(0L,
                claims.getClaim(fixture.claim().claimId())
                        .remainingUnits());
        assertEquals(CustodyLotState.RELEASED,
                custody.getLot(fixture.lot().lotId()).state());
        assertEquals(CustodyBatchStatus.APPLIED,
                custody.getPreparedBatch(
                        fixture.commit().custody().batch().batchId())
                        .status());
        assertEquals(EscrowPreflightResult.REPLAY,
                applier.preflight(fixture.claim().transactionId(), event));

        applier.apply(record, event);

        assertEquals(ClaimStatus.COMPLETED,
                claims.getClaim(fixture.claim().claimId()).status());
        assertEquals(CustodyLotState.RELEASED,
                custody.getLot(fixture.lot().lotId()).state());
    }

    @Test
    void reserveOnlyCrashCutConvergesOnCompositeReplay() {
        Fixture fixture = fixture();
        ClaimSavedData claims = new ClaimSavedData();
        claims.createCommitted(fixture.claim());
        CustodySavedData custody = new CustodySavedData();
        CustodyPreparedBatch applied = fixture.commit().custody().batch();
        CustodyPreparedBatch initial = new CustodyPreparedBatch(
                applied.batchId(), applied.transactionId(),
                applied.requestKey(), applied.operations(),
                CustodyBatchStatus.PREPARED, applied.preparedAt(),
                applied.preparedAt(), 0L, "Prepared");
        custody.applyBatchCommit(CustodyBatchCommit.state(initial));
        custody.applyBatchCommit(CustodyBatchCommit.state(
                initial.markApplying(0L, initial.updatedAt())));
        custody.reserveCommitted(fixture.commit().reserveMutation()
                .resultingLot());
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), claims,
                        new EscrowAdministrativeAuditSavedData(), custody,
                        new ProtectedMintSavedData());
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CASH_CLAIM_DELIVERY_COMMIT,
                CashClaimDeliveryCommitCodec.encode(fixture.commit()));
        JournalRecord record = new JournalRecord(1L,
                fixture.claim().transactionId(),
                EscrowStepIds.forEvent(
                        fixture.claim().transactionId(), event),
                EscrowJournalEventCodec.encode(event));

        applier.apply(record, event);

        assertEquals(ClaimStatus.COMPLETED,
                claims.getClaim(fixture.claim().claimId()).status());
        assertEquals(CustodyLotState.RELEASED,
                custody.getLot(fixture.lot().lotId()).state());
        assertEquals(EscrowPreflightResult.REPLAY,
                applier.preflight(fixture.claim().transactionId(), event));
    }

    private static Fixture fixture() {
        UUID claimId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        long denomination = 25L;
        int billCount = 7;
        ItemStack stack = new ItemStack(Items.EMERALD, billCount);
        stack.getOrCreateTag().putString("foreign", "preserved");
        byte[] stackNbt = ItemStackSnapshotCodec.encode(stack);
        ForeignCashClaimPayload payload = ForeignCashClaimPayload.capture(
                "coinmod", "a".repeat(64), "minecraft:emerald",
                denomination, billCount, 0, 0, 1, stackNbt);
        long units = Math.multiplyExact(denomination, (long) billCount);
        EscrowClaim claim = new EscrowClaim(claimId, transactionId,
                ownerId, "foreign cash claim " + claimId,
                ClaimKind.FOREIGN_CASH, units, units,
                ForeignCashClaimPayloadCodec.encode(payload),
                ClaimStatus.PENDING, "Foreign cash", CREATED, CREATED);
        ProtectedMintSavedData mints = new ProtectedMintSavedData();
        CustodyLot lot = CashClaimDeliveryPlanner.expectedLot(claim, mints);
        String requestKey = CashClaimDeliveryPlanner.deliveryRequestKey(
                claimId, attemptId);
        CustodyBatchPlan plan = new CustodyBatchPlan(
                CustodyOperation.RELEASE, requestKey,
                CashClaimCustodySupport.PLAYER_INVENTORY_ADAPTER_ID,
                CustodyAdapterCapability.RECONCILABLE,
                lot.protectionTier(), List.of(lot), lot.units());
        UUID batchId = CustodyPreparedBatch.deterministicId(
                transactionId, requestKey);
        byte[] before = hash("inventory before");
        byte[] after = hash("inventory after");
        PlayerInventoryDeliveryToken token =
                PlayerInventoryDeliveryToken.create(ownerId, claimId,
                        transactionId, batchId, lot.lotId(), requestKey,
                        lot.assetFingerprint(), before, after);
        CustodyEndpointEvidence destination =
                new CustodyEndpointEvidence(
                        CashClaimCustodySupport
                                .PLAYER_INVENTORY_ADAPTER_ID,
                        CustodyAdapterCapability.RECONCILABLE,
                        ownerId.toString(), "inventory.main", before, after,
                        token.encode());
        CustodyTransferEvidence releaseEvidence =
                new CustodyTransferEvidence(
                        lot.holdEvidence().destination(), destination);
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                token.encode(), Map.of(lot.lotId(), releaseEvidence),
                CREATED.plusSeconds(1));
        CustodyPreparedBatch applying = prepared.markApplying(
                0L, CREATED.plusSeconds(1));
        CustodyPreparedBatch applied = applying.markApplied(
                1L, Map.of(lot.lotId(), releaseEvidence),
                CREATED.plusSeconds(1));
        CustodyMutation mutation = CustodyMutation.terminal(lot,
                CustodyOperation.RELEASE, requestKey, releaseEvidence,
                CREATED.plusSeconds(1));
        CustodyBatchCommit custody = CustodyBatchCommit.applied(
                applied, List.of(mutation));
        ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(ownerId,
                claimId, requestKey, units, CREATED.plusSeconds(1));
        return new Fixture(claim, lot,
                new CashClaimDeliveryCommit(delivery, custody));
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            EscrowClaim claim,
            CustodyLot lot,
            CashClaimDeliveryCommit commit
    ) {
    }
}
