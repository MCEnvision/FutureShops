package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscrowJournalCodecTest {
    @Test
    void compositeWireIdsRemainAppendOnly() {
        assertEquals(14,
                EscrowJournalEventType.ATM_WITHDRAWAL_COMMIT.wireId());
        assertEquals(15,
                EscrowJournalEventType.FOREIGN_ATM_WITHDRAWAL_COMMIT
                        .wireId());
        assertEquals(16,
                EscrowJournalEventType.CASH_CLAIM_DELIVERY_COMMIT
                        .wireId());
        assertEquals(17, EscrowJournalEventType
                .PROTECTED_CASH_REDEMPTION_RESERVATION.wireId());
        assertEquals(18, EscrowJournalEventType
                .PROTECTED_CASH_REDEMPTION_SETTLEMENT.wireId());
        assertEquals(19, EscrowJournalEventType
                .PROTECTED_CASH_REDEMPTION_CANCELLATION.wireId());
        assertEquals(23,
                EscrowJournalEventType.PLAYER_PAYMENT_COMMIT.wireId());
        assertEquals(24,
                EscrowJournalEventType.STOCK_MUTATION.wireId());
        assertEquals(EscrowJournalEventType.ATM_WITHDRAWAL_COMMIT,
                EscrowJournalEventType.fromWireId(14));
        assertEquals(
                EscrowJournalEventType.FOREIGN_ATM_WITHDRAWAL_COMMIT,
                EscrowJournalEventType.fromWireId(15));
        assertEquals(
                EscrowJournalEventType.CASH_CLAIM_DELIVERY_COMMIT,
                EscrowJournalEventType.fromWireId(16));
        assertEquals(EscrowJournalEventType
                        .PROTECTED_CASH_REDEMPTION_RESERVATION,
                EscrowJournalEventType.fromWireId(17));
        assertEquals(EscrowJournalEventType
                        .PROTECTED_CASH_REDEMPTION_SETTLEMENT,
                EscrowJournalEventType.fromWireId(18));
        assertEquals(EscrowJournalEventType
                        .PROTECTED_CASH_REDEMPTION_CANCELLATION,
                EscrowJournalEventType.fromWireId(19));
        assertEquals(EscrowJournalEventType.PLAYER_PAYMENT_COMMIT,
                EscrowJournalEventType.fromWireId(23));
        assertEquals(EscrowJournalEventType.STOCK_MUTATION,
                EscrowJournalEventType.fromWireId(24));
    }

    @Test
    void envelopeRoundTrips() {
        EscrowJournalEvent event = new EscrowJournalEvent(EscrowJournalEventType.CLAIM_CREATE,
                new byte[]{1, 2, 3});
        EscrowJournalEvent decoded = EscrowJournalEventCodec.decode(EscrowJournalEventCodec.encode(event));

        assertEquals(event.type(), decoded.type());
        assertArrayEquals(event.body(), decoded.body());
    }

    @Test
    void atomicCustodyBatchRoundTripsInsideTheJournalEnvelope() {
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        UUID transactionId = UUID.randomUUID();
        CustodyTransferEvidence evidence = custodyEvidence(transactionId);
        CustodyLot lot = CustodyLot.held(UUID.randomUUID(), transactionId,
                "atomic custody", CustodyAssetType.WALLET_RESERVE,
                CustodyProtectionTier.PROTECTED, 25L,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(), evidence, now);
        CustodyBatchPlan plan = CustodyBatchPlan.create(
                CustodyOperation.RESERVE, "atomic custody", List.of(lot));
        CustodyPreparedBatch batch = CustodyPreparedBatch.prepare(plan,
                "atomic token", Map.of(lot.lotId(), evidence), now);
        CustodyBatchCommit commit = CustodyBatchCommit.state(batch);
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CUSTODY_BATCH,
                CustodyBatchCommitCodec.encode(commit));

        EscrowJournalEvent decoded = EscrowJournalEventCodec.decode(
                EscrowJournalEventCodec.encode(event));

        assertEquals(EscrowJournalEventType.CUSTODY_BATCH, decoded.type());
        assertEquals(commit, CustodyBatchCommitCodec.decode(decoded.body()));
    }

    @Test
    void ledgerRoundTrips() {
        LedgerTransaction transaction = new LedgerTransaction(UUID.randomUUID(), "request one", "hold", List.of(
                new LedgerLeg(new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, "player"), -50L),
                new LedgerLeg(new LedgerAccountId(LedgerAccountType.TRANSACTION_ESCROW, "transaction"), 50L)));

        assertEquals(transaction, LedgerJournalCodec.decode(LedgerJournalCodec.encode(transaction)));
    }

    @Test
    void claimRoundTrips() {
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        EscrowClaim claim = new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.ITEM, 3L, 3L,
                new byte[]{5, 6}, ClaimStatus.PENDING, "Item", now, now);

        EscrowClaim decoded = ClaimJournalCodec.decodeClaim(ClaimJournalCodec.encodeClaim(claim));

        assertEquals(claim.claimId(), decoded.claimId());
        assertEquals(claim.transactionId(), decoded.transactionId());
        assertEquals(claim.sourceKey(), decoded.sourceKey());
        assertArrayEquals(claim.payload(), decoded.payload());
        assertEquals(claim.createdAt(), decoded.createdAt());
        assertEquals(claim.updatedAt(), decoded.updatedAt());
    }

    @Test
    void deliveryRoundTrips() {
        ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(
                UUID.randomUUID(), UUID.randomUUID(), "request one", 10L,
                Instant.parse("2026-07-16T12:00:00.123456789Z"));

        assertEquals(delivery, ClaimJournalCodec.decodeDelivery(ClaimJournalCodec.encodeDelivery(delivery)));
        assertEquals(delivery.deliveredAt(), ClaimJournalCodec.decodeDelivery(
                ClaimJournalCodec.encodeDelivery(delivery)).deliveredAt());
    }

    @Test
    void claimQuarantineRoundTripsWithDeterministicRequestKey() {
        UUID owner = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        ClaimQuarantineCommit quarantine = ClaimQuarantineCommit.create(
                owner, claim, transaction, now, "MISSING_ITEM_REGISTRY_ENTRY");

        assertEquals(quarantine, ClaimJournalCodec.decodeQuarantine(
                ClaimJournalCodec.encodeQuarantine(quarantine)));
        assertEquals(quarantine.requestKey(), ClaimQuarantineCommit.create(
                owner, claim, transaction, now, "MISSING_ITEM_REGISTRY_ENTRY").requestKey());
        assertThrows(IllegalArgumentException.class, () -> new ClaimQuarantineCommit(
                owner, claim, transaction, "arbitrary", now, "MISSING_ITEM_REGISTRY_ENTRY"));
    }

    @Test
    void moneyClaimSettlementRoundTrips() {
        UUID owner = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        MoneyClaimSettlement settlement = MoneyClaimSettlement.create(
                request, owner, claim, 0L, 0L, 0L,
                25L, 100L, 4L,
                Instant.parse("2026-07-16T12:00:00.123456789Z"));

        assertEquals(settlement, MoneyClaimSettlementCodec.decode(MoneyClaimSettlementCodec.encode(settlement)));
    }

    @Test
    void corruptEnvelopeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> EscrowJournalEventCodec.decode(new byte[]{1, 2, 3}));
    }

    @Test
    void moneyClaimSettlementRequiresOneSharedRequestKey() {
        UUID owner = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(
                owner, claim, "claim one", 25L,
                Instant.parse("2026-07-16T12:00:00.123456789Z"));
        LedgerTransaction ledger = new LedgerTransaction(request, "ledger one",
                MoneyClaimSettlement.LEDGER_REASON, List.of(
                new LedgerLeg(new LedgerAccountId(LedgerAccountType.PLAYER_CLAIM, claim.toString()), -25L),
                new LedgerLeg(new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, owner.toString()), 25L)));

        assertThrows(IllegalArgumentException.class,
                () -> new MoneyClaimSettlement(
                        request, 0L, 0L, 0L, 25L,
                        100L, 4L, delivery, ledger));
    }

    @Test
    void administrativeAuditRoundTrips() {
        EscrowAdministrativeRecord record = new EscrowAdministrativeRecord(
                UUID.randomUUID(), "console", EscrowAdministrativeAction.FORCE_REFUND,
                Optional.of(new com.enviouse.futureshops.server.escrow.model.EscrowTransactionId(
                        UUID.randomUUID())),
                "Storage was repaired", Instant.parse("2026-07-16T12:00:00.123456789Z"),
                true, "Refund scheduled");

        assertEquals(record, AdministrativeAuditJournalCodec.decode(
                AdministrativeAuditJournalCodec.encode(record)));
    }

    @Test
    void lineagePreservesNanoseconds() {
        JournalLineage lineage = new JournalLineage(
                UUID.randomUUID(), Instant.parse("2026-07-16T12:00:00.123456789Z"));

        assertEquals(lineage, JournalLineageCodec.decode(JournalLineageCodec.encode(lineage)));
    }

    @Test
    void lineageRejectsOutOfRangeNanoseconds() {
        JournalLineage lineage = new JournalLineage(
                UUID.randomUUID(), Instant.parse("2026-07-16T12:00:00Z"));
        byte[] encoded = JournalLineageCodec.encode(lineage);
        ByteBuffer.wrap(encoded).putInt(
                Integer.BYTES + Long.BYTES * 3, -1);

        assertThrows(IllegalArgumentException.class, () -> JournalLineageCodec.decode(encoded));
    }

    @Test
    void administrativeAuditRejectsMalformedUtf8AndBooleanFlags() {
        EscrowAdministrativeRecord record = new EscrowAdministrativeRecord(
                UUID.randomUUID(), "console", EscrowAdministrativeAction.ENTER_MAINTENANCE,
                Optional.empty(), "Inspect storage", Instant.parse("2026-07-16T12:00:00Z"),
                true, "Maintenance requested");
        byte[] malformedText = AdministrativeAuditJournalCodec.encode(record);
        malformedText[Integer.BYTES + Long.BYTES * 2 + Integer.BYTES] = (byte) 0xC3;
        assertThrows(IllegalArgumentException.class,
                () -> AdministrativeAuditJournalCodec.decode(malformedText));

        byte[] malformedFlag = AdministrativeAuditJournalCodec.encode(record);
        ByteBuffer cursor = ByteBuffer.wrap(malformedFlag);
        cursor.position(Integer.BYTES + Long.BYTES * 2);
        cursor.position(cursor.position() + Integer.BYTES + cursor.getInt(cursor.position()));
        cursor.position(cursor.position() + Integer.BYTES + cursor.getInt(cursor.position()));
        malformedFlag[cursor.position()] = 2;
        assertThrows(IllegalArgumentException.class,
                () -> AdministrativeAuditJournalCodec.decode(malformedFlag));
    }

    @Test
    void legacyClaimJournalTimestampStillDecodes() throws Exception {
        Instant now = Instant.parse("2026-07-16T12:00:00.123Z");
        EscrowClaim claim = new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "legacy source", ClaimKind.ITEM, 1L, 1L,
                new byte[]{4}, ClaimStatus.PENDING, "Item", now, now);

        EscrowClaim decoded = ClaimJournalCodec.decodeClaim(encodeLegacyClaim(claim));

        assertEquals(now, decoded.createdAt());
        assertEquals(now, decoded.updatedAt());
    }

    @Test
    void claimJournalRejectsOutOfRangeNanoseconds() {
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        EscrowClaim claim = new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.ITEM, 1L, 1L,
                new byte[]{4}, ClaimStatus.PENDING, "Item", now, now);
        byte[] encoded = ClaimJournalCodec.encodeClaim(claim);
        ByteBuffer.wrap(encoded).putInt(encoded.length - 16, -1);

        assertThrows(IllegalArgumentException.class, () -> ClaimJournalCodec.decodeClaim(encoded));
    }

    @Test
    void binaryStringsRejectUnpairedSurrogates() {
        assertThrows(IllegalArgumentException.class, () -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BinaryCodecSupport.writeString(new DataOutputStream(bytes), "\uD800", 16);
        });
    }

    private static byte[] encodeLegacyClaim(EscrowClaim claim) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(1);
        BinaryCodecSupport.writeUuid(output, claim.claimId());
        BinaryCodecSupport.writeUuid(output, claim.transactionId());
        BinaryCodecSupport.writeUuid(output, claim.ownerId());
        BinaryCodecSupport.writeString(output, claim.kind().name(), 128);
        output.writeLong(claim.originalUnits());
        output.writeLong(claim.remainingUnits());
        output.writeInt(claim.payload().length);
        output.write(claim.payload());
        BinaryCodecSupport.writeString(output, claim.status().name(), 128);
        BinaryCodecSupport.writeString(output, claim.label(), 640);
        output.writeLong(claim.createdAt().toEpochMilli());
        output.writeLong(claim.updatedAt().toEpochMilli());
        output.flush();
        return bytes.toByteArray();
    }

    private static CustodyTransferEvidence custodyEvidence(UUID transactionId) {
        return new CustodyTransferEvidence(
                CustodyEndpointEvidence.captured(
                        "wallet", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                        "player", "wallet", new byte[]{1}, new byte[]{2}, "wallet mutation"),
                CustodyEndpointEvidence.captured(
                        "escrow", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                        transactionId.toString(), "held", new byte[]{3}, new byte[]{4},
                        "escrow mutation"));
    }
}
