package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceCustodyDisposition;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedState;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
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
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaintenanceRepairJournalCodecTest {
    private static final Instant NOW = Instant.parse("2026-07-17T18:00:00.123456789Z");

    @Test
    void everyExactEffectRoundTripsWithStableBytes() {
        EscrowTransaction transaction = transaction(UUID.randomUUID());
        EscrowClaim claim = claim(UUID.randomUUID(), transaction.transactionId().value());
        CustodyPreparedBatch batch = quarantinedBatch();
        List<MaintenanceRepairJournalEntry> entries = List.of(
                new MaintenanceRepairJournalEntry(
                        command(MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                                new MaintenanceRepairPayload.RetryReset(), false, false),
                        new MaintenanceRepairJournalEntry.AuditOnly()),
                new MaintenanceRepairJournalEntry(
                        command(MaintenanceRepairTarget.runtime(),
                                new MaintenanceRepairPayload.EnterMaintenance("incident.9"),
                                true, true),
                        new MaintenanceRepairJournalEntry.RuntimeState(
                                new MaintenanceRuntimeSnapshot(1L, fingerprint(10)))),
                new MaintenanceRepairJournalEntry(
                        command(MaintenanceRepairTarget.transaction(
                                        transaction.transactionId().value()),
                                new MaintenanceRepairPayload.RetryReset(), true, true),
                        new MaintenanceRepairJournalEntry.TransactionState(transaction)),
                new MaintenanceRepairJournalEntry(
                        command(MaintenanceRepairTarget.claim(claim.claimId()),
                                new MaintenanceRepairPayload.ClaimQuarantine(), true, true),
                        new MaintenanceRepairJournalEntry.ClaimState(claim)),
                new MaintenanceRepairJournalEntry(
                        command(MaintenanceRepairTarget.custodyLot(batch.operations().get(0)
                                        .lotSnapshot().lotId()),
                                new MaintenanceRepairPayload.CustodyReconcile(
                                        fingerprint(11), MaintenanceCustodyDisposition.CONFIRM_HELD),
                                true, true),
                        new MaintenanceRepairJournalEntry.CustodyLotVerification(
                                batch.operations().get(0).lotSnapshot().lotId(), 0L,
                                fingerprint(12))),
                new MaintenanceRepairJournalEntry(
                        command(MaintenanceRepairTarget.custodyBatch(batch.batchId()),
                                new MaintenanceRepairPayload.CustodyQuarantine(), true, true),
                        new MaintenanceRepairJournalEntry.CustodyBatchState(
                                CustodyBatchCommit.state(batch))));

        for (MaintenanceRepairJournalEntry entry : entries) {
            byte[] encoded = MaintenanceRepairJournalCodec.encode(entry);
            MaintenanceRepairJournalEntry decoded =
                    MaintenanceRepairJournalCodec.decode(encoded);
            assertEquals(entry, decoded);
            assertArrayEquals(encoded, MaintenanceRepairJournalCodec.encode(decoded));
        }
    }

    @Test
    void journalEnvelopeKeepsTheCompositeEventTogether() {
        MaintenanceRepairJournalEntry entry = new MaintenanceRepairJournalEntry(
                command(MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                        new MaintenanceRepairPayload.RetryReset(), false, false),
                new MaintenanceRepairJournalEntry.AuditOnly());
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MAINTENANCE_REPAIR,
                MaintenanceRepairJournalCodec.encode(entry));

        EscrowJournalEvent decoded = EscrowJournalEventCodec.decode(
                EscrowJournalEventCodec.encode(event));

        assertEquals(EscrowJournalEventType.MAINTENANCE_REPAIR, decoded.type());
        assertEquals(entry, MaintenanceRepairJournalCodec.decode(decoded.body()));
    }

    @Test
    void framingKindsLengthsAndTrailingDataFailClosed() {
        MaintenanceRepairJournalEntry entry = new MaintenanceRepairJournalEntry(
                command(MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                        new MaintenanceRepairPayload.RetryReset(), false, false),
                new MaintenanceRepairJournalEntry.AuditOnly());
        byte[] encoded = MaintenanceRepairJournalCodec.encode(entry);

        byte[] badMagic = encoded.clone();
        badMagic[0] = 0;
        assertMalformed(badMagic);

        byte[] newer = encoded.clone();
        ByteBuffer.wrap(newer, 4, 4).putInt(
                MaintenanceRepairJournalCodec.CURRENT_SCHEMA + 1);
        assertThrows(IllegalStateException.class,
                () -> MaintenanceRepairJournalCodec.decode(newer));

        int commandBytes = ByteBuffer.wrap(encoded, 8, 4).getInt();
        int effectOffset = 12 + commandBytes;
        byte[] badKind = encoded.clone();
        badKind[effectOffset] = 99;
        assertMalformed(badKind);

        byte[] badLength = encoded.clone();
        ByteBuffer.wrap(badLength, effectOffset + 1, 4).putInt(Integer.MAX_VALUE);
        assertMalformed(badLength);

        assertMalformed(Arrays.copyOf(encoded, encoded.length - 1));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        trailing[trailing.length - 1] = 1;
        assertMalformed(trailing);
        assertMalformed(new byte[MaintenanceRepairJournalCodec.MAX_ENCODED_BYTES + 1]);
    }

    @Test
    void commandAndEffectTargetMismatchesFailClosed() {
        EscrowClaim claim = claim(UUID.randomUUID(), UUID.randomUUID());
        MaintenanceRepairCommand command = command(
                MaintenanceRepairTarget.claim(UUID.randomUUID()),
                new MaintenanceRepairPayload.ClaimQuarantine(), true, true);

        assertThrows(IllegalArgumentException.class,
                () -> new MaintenanceRepairJournalEntry(command,
                        new MaintenanceRepairJournalEntry.ClaimState(claim)));
    }

    private static MaintenanceRepairCommand command(MaintenanceRepairTarget target,
                                                    MaintenanceRepairPayload payload,
                                                    boolean confirmed,
                                                    boolean successful) {
        return MaintenanceRepairCommand.create(UUID.randomUUID(), "console",
                "Verified maintenance repair", confirmed, NOW, target,
                MaintenanceExpectedState.revision(0L), payload, successful,
                successful ? "Applied" : "Rejected");
    }

    private static EscrowTransaction transaction(UUID transactionId) {
        EscrowParty player = EscrowParty.player(UUID.randomUUID());
        EscrowParty system = EscrowParty.system("atm");
        return EscrowTransaction.create(new EscrowTransactionId(transactionId),
                Optional.empty(), new EscrowRequestKey("request " + transactionId),
                EscrowOperation.ATM_WITHDRAWAL,
                Set.of(new EscrowParticipant(player, Set.of(
                                EscrowParticipantRole.INITIATOR,
                                EscrowParticipantRole.PAYER)),
                        new EscrowParticipant(system, Set.of(
                                EscrowParticipantRole.BENEFICIARY))),
                List.of(new EscrowAssetLot(UUID.randomUUID(),
                        EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED, player, system, 1L,
                        Optional.of(new MoneyAmount("futureshops:credits", 25L)),
                        new byte[0], Map.of())), NOW.minusSeconds(10), 1L,
                Optional.empty());
    }

    private static EscrowClaim claim(UUID claimId, UUID transactionId) {
        return new EscrowClaim(claimId, transactionId, UUID.randomUUID(),
                "source " + claimId, ClaimKind.ITEM, 2L, 2L,
                new byte[]{1, 2, 3}, ClaimStatus.PENDING, "Diamonds",
                NOW.minusSeconds(5), NOW.minusSeconds(5));
    }

    private static CustodyPreparedBatch quarantinedBatch() {
        UUID transactionId = UUID.randomUUID();
        CustodyTransferEvidence evidence = evidence("codec batch");
        CustodyLot lot = CustodyLot.held(UUID.randomUUID(), transactionId,
                "codec reserve", CustodyAssetType.WALLET_RESERVE,
                CustodyProtectionTier.PROTECTED, 25L,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(),
                evidence, NOW.minusSeconds(5));
        CustodyBatchPlan plan = CustodyBatchPlan.create(
                CustodyOperation.RESERVE, "codec reserve", List.of(lot));
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(plan,
                "codec token", Map.of(lot.lotId(), evidence), NOW.minusSeconds(5));
        return prepared.quarantine(0L, NOW, "Verified quarantine");
    }

    private static CustodyTransferEvidence evidence(String token) {
        CustodyEndpointEvidence source = CustodyEndpointEvidence.captured("wallet",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, "player", "wallet",
                new byte[]{1}, new byte[]{2}, token + " source");
        CustodyEndpointEvidence destination = CustodyEndpointEvidence.captured("vault",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, "escrow", "vault",
                new byte[]{3}, new byte[]{4}, token + " destination");
        return new CustodyTransferEvidence(source, destination);
    }

    private static MaintenanceStateFingerprint fingerprint(int seed) {
        byte[] bytes = new byte[MaintenanceStateFingerprint.BYTE_LENGTH];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (seed + index);
        }
        return MaintenanceStateFingerprint.of(bytes);
    }

    private static void assertMalformed(byte[] encoded) {
        assertThrows(IllegalArgumentException.class,
                () -> MaintenanceRepairJournalCodec.decode(encoded));
    }
}
