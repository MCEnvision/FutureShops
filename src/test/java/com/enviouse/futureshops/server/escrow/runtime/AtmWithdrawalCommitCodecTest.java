package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalCommitCodecTest {
    @Test
    void multiDenominationStackPortionsRoundTripWithStableFingerprint() {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();

        byte[] encoded = AtmWithdrawalCommitCodec.encode(commit);
        AtmWithdrawalCommit decoded = AtmWithdrawalCommitCodec.decode(encoded);

        assertEquals(commit, decoded);
        assertEquals(7100L, decoded.amountMinorUnits());
        assertEquals(2, decoded.mintIssues().size());
        assertEquals(3, decoded.cashClaims().size());
        assertEquals(commit.fingerprint(), decoded.fingerprint());
        assertArrayEquals(encoded, AtmWithdrawalCommitCodec.encode(decoded));

        List<ProtectedMintJournalEvent> reversedIssues =
                new ArrayList<>(commit.mintIssues());
        List<EscrowClaim> reversedClaims = new ArrayList<>(commit.cashClaims());
        Collections.reverse(reversedIssues);
        Collections.reverse(reversedClaims);
        AtmWithdrawalCommit reordered = new AtmWithdrawalCommit(
                commit.playerId(), commit.committedTransaction(),
                commit.ledgerTransaction(), reversedIssues, reversedClaims);
        assertEquals(commit, reordered);
        assertArrayEquals(encoded, AtmWithdrawalCommitCodec.encode(reordered));
    }

    @Test
    void protectedCashPayloadRoundTripsAndRejectsCorruptionAndBounds() {
        EscrowClaim claim = AtmWithdrawalTestFixtures.commit().cashClaims().get(0);
        byte[] encoded = claim.payload();
        ProtectedCashClaimPayload payload =
                ProtectedCashClaimPayloadCodec.decode(encoded);

        assertArrayEquals(encoded, ProtectedCashClaimPayloadCodec.encode(payload));
        assertTrue(payload.billCount() <= ProtectedCashClaimPayload.MAX_STACK_BILLS);

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashClaimPayloadCodec.decode(badMagic));
        byte[] newer = encoded.clone();
        newer[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> ProtectedCashClaimPayloadCodec.decode(newer));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashClaimPayloadCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedCashClaimPayloadCodec.decode(
                        new byte[ProtectedCashClaimPayloadCodec.MAX_ENCODED_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> new ProtectedCashClaimPayload(
                        payload.batchId(), payload.denominationMinorUnits(),
                        payload.authorizedCount(), payload.portionIndex(),
                        payload.portionCount(),
                        ProtectedCashClaimPayload.MAX_STACK_BILLS + 1,
                        payload.serverIdentityEvidence(), payload.checksumEvidence()));
    }

    @Test
    void compositeCodecRejectsCorruptionNewerSchemaTrailingDataAndBounds() {
        byte[] encoded = AtmWithdrawalCommitCodec.encode(
                AtmWithdrawalTestFixtures.commit());

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(badMagic));

        byte[] newer = encoded.clone();
        newer[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> AtmWithdrawalCommitCodec.decode(newer));

        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(
                        new byte[AtmWithdrawalCommitCodec.MAX_ENCODED_BYTES + 1]));
    }

    @Test
    void compositeCodecRejectsInvalidWireCountsLengthsAndOrdering() {
        byte[] encoded = AtmWithdrawalCommitCodec.encode(
                AtmWithdrawalTestFixtures.commit());

        byte[] zeroTransactionLength = encoded.clone();
        ByteBuffer.wrap(zeroTransactionLength).putInt(24, 0);
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(zeroTransactionLength));

        byte[] excessiveTransactionLength = encoded.clone();
        ByteBuffer.wrap(excessiveTransactionLength).putInt(24, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(excessiveTransactionLength));

        int issueCountOffset = issueCountOffset(encoded);
        byte[] zeroIssues = encoded.clone();
        ByteBuffer.wrap(zeroIssues).putInt(issueCountOffset, 0);
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(zeroIssues));
        byte[] excessiveIssues = encoded.clone();
        ByteBuffer.wrap(excessiveIssues).putInt(
                issueCountOffset, AtmWithdrawalCommit.MAX_MINT_ISSUES + 1);
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(excessiveIssues));

        int claimCountOffset = claimCountOffset(encoded, issueCountOffset);
        byte[] zeroClaims = encoded.clone();
        ByteBuffer.wrap(zeroClaims).putInt(claimCountOffset, 0);
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(zeroClaims));
        byte[] excessiveClaims = encoded.clone();
        ByteBuffer.wrap(excessiveClaims).putInt(
                claimCountOffset, AtmWithdrawalCommit.MAX_CASH_CLAIMS + 1);
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(excessiveClaims));

        byte[] reorderedIssues = reverseFrames(
                encoded, issueCountOffset + Integer.BYTES,
                ByteBuffer.wrap(encoded).getInt(issueCountOffset));
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(reorderedIssues));
        byte[] reorderedClaims = reverseFrames(
                encoded, claimCountOffset + Integer.BYTES,
                ByteBuffer.wrap(encoded).getInt(claimCountOffset));
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalCommitCodec.decode(reorderedClaims));
    }

    @Test
    void unmatchedClaimsForgedLedgerAndBatchMismatchFailClosed() {
        AtmWithdrawalCommit valid = AtmWithdrawalTestFixtures.commit();
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), valid.committedTransaction(), valid.ledgerTransaction(),
                valid.mintIssues(), valid.cashClaims().subList(
                0, valid.cashClaims().size() - 1)));

        long total = valid.amountMinorUnits();
        LedgerTransaction forgedLedger = new LedgerTransaction(
                valid.transactionId(),
                AtmWithdrawalCommit.ledgerIdempotencyKey(valid.transactionId()),
                AtmWithdrawalCommit.LEDGER_REASON,
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                valid.playerId().toString()), -total),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.TRANSACTION_ESCROW), total)));
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), valid.committedTransaction(), forgedLedger,
                valid.mintIssues(), valid.cashClaims()));

        List<LedgerLeg> reversedLegs = new ArrayList<>(
                valid.ledgerTransaction().legs());
        Collections.reverse(reversedLegs);
        LedgerTransaction reorderedLedger = new LedgerTransaction(
                valid.transactionId(), valid.ledgerTransaction().idempotencyKey(),
                valid.ledgerTransaction().reason(), reversedLegs);
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), valid.committedTransaction(), reorderedLedger,
                valid.mintIssues(), valid.cashClaims()));

        ProtectedMintJournalEvent original = valid.mintIssues().get(0);
        ProtectedMintBatch oldBatch = original.batch().orElseThrow();
        ProtectedMintBatch changedBatch = ProtectedMintBatch.issue(
                oldBatch.transactionId(), oldBatch.authorizeRequestKey(),
                oldBatch.denominationMinorUnits(), oldBatch.authorizedCount(),
                "different-server", oldBatch.authorizedAt(),
                AtmWithdrawalTestFixtures.EVIDENCE);
        List<ProtectedMintJournalEvent> changedIssues = new ArrayList<>(
                valid.mintIssues());
        changedIssues.set(0, ProtectedMintJournalEvent.issue(changedBatch));
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), valid.committedTransaction(), valid.ledgerTransaction(),
                changedIssues, valid.cashClaims()));
    }

    @Test
    void componentCountsAndArithmeticOverflowFailBeforeEncoding() {
        AtmWithdrawalCommit valid = AtmWithdrawalTestFixtures.commit();
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), valid.committedTransaction(), valid.ledgerTransaction(),
                Collections.nCopies(AtmWithdrawalCommit.MAX_MINT_ISSUES + 1,
                        valid.mintIssues().get(0)), valid.cashClaims()));
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), valid.committedTransaction(), valid.ledgerTransaction(),
                valid.mintIssues(),
                Collections.nCopies(AtmWithdrawalCommit.MAX_CASH_CLAIMS + 1,
                        valid.cashClaims().get(0))));

        List<ProtectedMintJournalEvent> overflowing = List.of(
                AtmWithdrawalTestFixtures.issue("overflow-one", Long.MAX_VALUE, 1),
                AtmWithdrawalTestFixtures.issue("overflow-two", Long.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class,
                () -> AtmWithdrawalTestFixtures.commit(overflowing));
        assertNotEquals(overflowing.get(0).batch().orElseThrow().batchId(),
                overflowing.get(1).batch().orElseThrow().batchId());
    }

    @Test
    void participantRolesMustMatchTheSealedWithdrawalShapeExactly() {
        AtmWithdrawalCommit valid = AtmWithdrawalTestFixtures.commit();
        EscrowTransaction transaction = valid.committedTransaction();
        EscrowParty player = EscrowParty.player(valid.playerId());
        EscrowParticipant system = transaction.participants().stream()
                .filter(value -> !value.party().equals(player))
                .findFirst().orElseThrow();
        EscrowTransaction withExtraRole = new EscrowTransaction(
                transaction.transactionId(), transaction.parentTransactionId(),
                transaction.requestKey(), transaction.operation(), transaction.state(),
                Set.of(new EscrowParticipant(player, EnumSet.of(
                                EscrowParticipantRole.INITIATOR,
                                EscrowParticipantRole.PAYER,
                                EscrowParticipantRole.RECIPIENT,
                                EscrowParticipantRole.BUYER)),
                        system),
                transaction.assetLots(), transaction.timestamps(), transaction.revision(),
                transaction.configRevision(), transaction.lastError(),
                transaction.retryMetadata(), transaction.shopReference());

        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), withExtraRole, valid.ledgerTransaction(),
                valid.mintIssues(), valid.cashClaims()));

        EscrowParticipant playerParticipant = transaction.participants().stream()
                .filter(value -> value.party().equals(player))
                .findFirst().orElseThrow();
        EscrowTransaction withExtraSystemRole = new EscrowTransaction(
                transaction.transactionId(), transaction.parentTransactionId(),
                transaction.requestKey(), transaction.operation(), transaction.state(),
                Set.of(playerParticipant, new EscrowParticipant(
                        system.party(), EnumSet.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.CUSTODIAN,
                        EscrowParticipantRole.SELLER))),
                transaction.assetLots(), transaction.timestamps(), transaction.revision(),
                transaction.configRevision(), transaction.lastError(),
                transaction.retryMetadata(), transaction.shopReference());
        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), withExtraSystemRole, valid.ledgerTransaction(),
                valid.mintIssues(), valid.cashClaims()));
    }

    @Test
    void protectedCurrencySystemIdentityMustMatchExactly() {
        AtmWithdrawalCommit valid = AtmWithdrawalTestFixtures.commit();
        EscrowTransaction transaction = valid.committedTransaction();
        EscrowParty expectedSystem = EscrowParty.system(
                AtmWithdrawalCommit.SYSTEM_PARTY_ID);
        EscrowParty forgedSystem = EscrowParty.system("another_protected_currency");
        EscrowParticipant player = transaction.participants().stream()
                .filter(value -> value.party().equals(
                        EscrowParty.player(valid.playerId())))
                .findFirst().orElseThrow();
        EscrowParticipant originalSystem = transaction.participants().stream()
                .filter(value -> value.party().equals(expectedSystem))
                .findFirst().orElseThrow();
        List<EscrowAssetLot> forgedLots = transaction.assetLots().stream()
                .map(lot -> replaceParty(lot, expectedSystem, forgedSystem))
                .toList();
        EscrowTransaction forged = new EscrowTransaction(
                transaction.transactionId(), transaction.parentTransactionId(),
                transaction.requestKey(), transaction.operation(), transaction.state(),
                Set.of(player, new EscrowParticipant(
                        forgedSystem, originalSystem.roles())),
                forgedLots, transaction.timestamps(), transaction.revision(),
                transaction.configRevision(), transaction.lastError(),
                transaction.retryMetadata(), transaction.shopReference());

        assertThrows(IllegalArgumentException.class, () -> new AtmWithdrawalCommit(
                valid.playerId(), forged, valid.ledgerTransaction(),
                valid.mintIssues(), valid.cashClaims()));
    }

    private static int issueCountOffset(byte[] encoded) {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        int offset = 24;
        offset += Integer.BYTES + input.getInt(offset);
        offset += Integer.BYTES + input.getInt(offset);
        return offset;
    }

    private static int claimCountOffset(byte[] encoded, int issueCountOffset) {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        int issueCount = input.getInt(issueCountOffset);
        int offset = issueCountOffset + Integer.BYTES;
        for (int index = 0; index < issueCount; index++) {
            offset += Integer.BYTES + input.getInt(offset);
        }
        return offset;
    }

    private static byte[] reverseFrames(byte[] encoded, int start, int count) {
        if (count < 2) {
            throw new IllegalArgumentException("Frame reversal requires multiple frames");
        }
        ByteBuffer input = ByteBuffer.wrap(encoded);
        List<byte[]> frames = new ArrayList<>(count);
        int end = start;
        for (int index = 0; index < count; index++) {
            int size = input.getInt(end);
            int frameBytes = Math.addExact(Integer.BYTES, size);
            frames.add(Arrays.copyOfRange(encoded, end,
                    Math.addExact(end, frameBytes)));
            end = Math.addExact(end, frameBytes);
        }
        byte[] changed = encoded.clone();
        int offset = start;
        for (int index = frames.size() - 1; index >= 0; index--) {
            byte[] frame = frames.get(index);
            System.arraycopy(frame, 0, changed, offset, frame.length);
            offset = Math.addExact(offset, frame.length);
        }
        assertEquals(end, offset);
        return changed;
    }

    private static EscrowAssetLot replaceParty(EscrowAssetLot lot,
                                               EscrowParty expected,
                                               EscrowParty replacement) {
        return new EscrowAssetLot(
                lot.lotId(), lot.type(), lot.protectionLevel(),
                lot.source().equals(expected) ? replacement : lot.source(),
                lot.destination().equals(expected) ? replacement : lot.destination(),
                lot.quantity(), lot.money(), lot.serializedPayload(), lot.attributes());
    }
}
