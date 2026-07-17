package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignAtmWithdrawalCommitCodecTest {
    @Test
    void multiDenominationCommitRoundTripsCanonicalizesAndFingerprints() {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalTestFixtures.commit();

        byte[] encoded = ForeignAtmWithdrawalCommitCodec.encode(commit);
        ForeignAtmWithdrawalCommit decoded =
                ForeignAtmWithdrawalCommitCodec.decode(encoded);

        assertEquals(commit, decoded);
        assertEquals(7100L, decoded.amountMinorUnits());
        assertEquals(ForeignAtmWithdrawalTestFixtures.PROVIDER,
                decoded.providerId());
        assertEquals(ForeignAtmWithdrawalTestFixtures.SIGNATURE,
                decoded.configSignature());
        assertEquals(commit.fingerprint(), decoded.fingerprint());
        assertArrayEquals(encoded,
                ForeignAtmWithdrawalCommitCodec.encode(decoded));

        List<EscrowClaim> reversed = new ArrayList<>(commit.cashClaims());
        Collections.reverse(reversed);
        ForeignAtmWithdrawalCommit reordered =
                new ForeignAtmWithdrawalCommit(
                        commit.requestId(), commit.playerId(),
                        commit.committedTransaction(),
                        commit.ledgerTransaction(), reversed);
        assertEquals(commit, reordered);
        assertArrayEquals(encoded,
                ForeignAtmWithdrawalCommitCodec.encode(reordered));
    }

    @Test
    void codecRejectsTamperingSchemasTruncationTrailingDataAndBounds() {
        byte[] encoded = ForeignAtmWithdrawalCommitCodec.encode(
                ForeignAtmWithdrawalTestFixtures.commit());

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(badMagic));

        byte[] newer = encoded.clone();
        newer[7] = 2;
        assertThrows(IllegalStateException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(newer));

        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(new byte[
                        ForeignAtmWithdrawalCommitCodec.MAX_ENCODED_BYTES + 1]));

        int claimCountOffset = claimCountOffset(encoded);
        byte[] zeroClaims = encoded.clone();
        ByteBuffer.wrap(zeroClaims).putInt(claimCountOffset, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(zeroClaims));

        byte[] tooManyClaims = encoded.clone();
        ByteBuffer.wrap(tooManyClaims).putInt(claimCountOffset,
                ForeignAtmWithdrawalCommit.MAX_CASH_CLAIMS + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(tooManyClaims));
    }

    @Test
    void codecRejectsNoncanonicalClaimWireOrderAndInvalidLengths() {
        byte[] encoded = ForeignAtmWithdrawalCommitCodec.encode(
                ForeignAtmWithdrawalTestFixtures.commit());
        int claimCountOffset = claimCountOffset(encoded);
        int claimCount = ByteBuffer.wrap(encoded).getInt(claimCountOffset);
        byte[] reordered = reverseFrames(encoded,
                claimCountOffset + Integer.BYTES, claimCount);
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(reordered));

        byte[] zeroTransaction = encoded.clone();
        ByteBuffer.wrap(zeroTransaction).putInt(40, 0);
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(zeroTransaction));

        byte[] excessiveTransaction = encoded.clone();
        ByteBuffer.wrap(excessiveTransaction).putInt(40, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalCommitCodec.decode(
                        excessiveTransaction));
    }

    @Test
    void requestLedgerClaimsAndAssetsMustCloseOverOneIdentity() {
        ForeignAtmWithdrawalCommit valid =
                ForeignAtmWithdrawalTestFixtures.commit();

        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        UUID.randomUUID(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        valid.cashClaims()));

        long total = valid.amountMinorUnits();
        LedgerTransaction forgedLedger = new LedgerTransaction(
                valid.requestId(),
                ForeignAtmWithdrawalCommit.ledgerIdempotencyKey(
                        valid.requestId()),
                ForeignAtmWithdrawalCommit.LEDGER_REASON,
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                valid.playerId().toString()), -total),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.TRANSACTION_ESCROW), total)));
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), forgedLedger,
                        valid.cashClaims()));

        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        valid.cashClaims().subList(
                                0, valid.cashClaims().size() - 1)));

        List<EscrowClaim> duplicated = new ArrayList<>(valid.cashClaims());
        duplicated.add(valid.cashClaims().get(0));
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        duplicated));
    }

    @Test
    void claimsRejectProtectedKindCrossedConfigAndIncompletePortions() {
        ForeignAtmWithdrawalCommit valid =
                ForeignAtmWithdrawalTestFixtures.commit();
        EscrowClaim first = valid.cashClaims().get(0);
        EscrowClaim protectedKind = copyClaim(first, ClaimKind.PROTECTED_CASH,
                first.payload());
        List<EscrowClaim> changedKind = new ArrayList<>(valid.cashClaims());
        changedKind.set(0, protectedKind);
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        changedKind));

        ForeignCashClaimPayload original =
                ForeignAtmWithdrawalTestFixtures.payload(first);
        ForeignCashClaimPayload crossed = ForeignCashClaimPayload.capture(
                original.providerId(),
                "fedcba9876543210".repeat(4),
                original.registryItemId(),
                original.denominationMinorUnits(), original.stackCount(),
                original.denominationIndex(), original.portionIndex(),
                original.portionCount(), original.serializedItemStackNbt());
        EscrowClaim crossedClaim = copyClaim(first, ClaimKind.FOREIGN_CASH,
                ForeignCashClaimPayloadCodec.encode(crossed));
        List<EscrowClaim> crossedClaims = new ArrayList<>(valid.cashClaims());
        crossedClaims.set(0, crossedClaim);
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        crossedClaims));

        List<EscrowClaim> incomplete = valid.cashClaims().stream()
                .filter(claim -> ForeignAtmWithdrawalTestFixtures.payload(claim)
                        .portionIndex() != 1)
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        incomplete));
    }

    @Test
    void fullStackNbtMustMatchItsExternalAssetLot() {
        ForeignAtmWithdrawalCommit valid =
                ForeignAtmWithdrawalTestFixtures.commit();
        EscrowTransaction transaction = valid.committedTransaction();
        List<EscrowAssetLot> lots = new ArrayList<>(transaction.assetLots());
        EscrowAssetLot original = lots.get(1);
        byte[] changedNbt = original.serializedPayload();
        changedNbt[0] ^= 1;
        lots.set(1, new EscrowAssetLot(
                original.lotId(), original.type(), original.protectionLevel(),
                original.source(), original.destination(), original.quantity(),
                original.money(), changedNbt, original.attributes()));
        EscrowTransaction changed = copyTransaction(transaction, lots);

        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(), changed,
                        valid.ledgerTransaction(), valid.cashClaims()));
    }

    @Test
    void componentAndAggregateBoundsFailBeforeEncoding() {
        ForeignAtmWithdrawalCommit valid =
                ForeignAtmWithdrawalTestFixtures.commit();
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalCommit(
                        valid.requestId(), valid.playerId(),
                        valid.committedTransaction(), valid.ledgerTransaction(),
                        Collections.nCopies(
                                ForeignAtmWithdrawalCommit.MAX_CASH_CLAIMS + 1,
                                valid.cashClaims().get(0))));

        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalTestFixtures.commit(List.of(
                        new ForeignAtmWithdrawalTestFixtures.PayloadSpec(
                                0, 0, 1,
                                ForeignAtmWithdrawalCommit.MAX_TOTAL_STACK_COUNT,
                                "examplecurrency:banknote", 1L,
                                new byte[]{1}),
                        new ForeignAtmWithdrawalTestFixtures.PayloadSpec(
                                1, 0, 1, 1,
                                "examplecurrency:coin", 1L,
                                new byte[]{2}))));

        assertThrows(IllegalArgumentException.class,
                () -> ForeignAtmWithdrawalTestFixtures.commit(List.of(
                        new ForeignAtmWithdrawalTestFixtures.PayloadSpec(
                                0, 0, 3, 1,
                                "examplecurrency:banknote", 1L,
                                new byte[700_000]),
                        new ForeignAtmWithdrawalTestFixtures.PayloadSpec(
                                0, 1, 3, 1,
                                "examplecurrency:banknote", 1L,
                                new byte[700_000]),
                        new ForeignAtmWithdrawalTestFixtures.PayloadSpec(
                                0, 2, 3, 1,
                                "examplecurrency:banknote", 1L,
                                new byte[700_000]))));
    }

    @Test
    void compositeSurfaceContainsNoProtectedMintComponent() {
        List<String> components = Arrays.stream(
                        ForeignAtmWithdrawalCommit.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertEquals(List.of("requestId", "playerId",
                "committedTransaction", "ledgerTransaction", "cashClaims"),
                components);
        assertFalse(Arrays.stream(
                        ForeignAtmWithdrawalCommit.class.getRecordComponents())
                .anyMatch(component -> component.getType().getName()
                        .contains("ProtectedMint")));
        assertTrue(validClaimKindsOnly());
    }

    private static boolean validClaimKindsOnly() {
        return ForeignAtmWithdrawalTestFixtures.commit().cashClaims().stream()
                .allMatch(claim -> claim.kind() == ClaimKind.FOREIGN_CASH);
    }

    private static EscrowClaim copyClaim(EscrowClaim claim,
                                         ClaimKind kind,
                                         byte[] payload) {
        return new EscrowClaim(
                claim.claimId(), claim.transactionId(), claim.ownerId(),
                claim.sourceKey(), kind, claim.originalUnits(),
                claim.remainingUnits(), payload, claim.status(), claim.label(),
                claim.createdAt(), claim.updatedAt());
    }

    private static EscrowTransaction copyTransaction(
            EscrowTransaction transaction,
            List<EscrowAssetLot> lots
    ) {
        return new EscrowTransaction(
                transaction.transactionId(), transaction.parentTransactionId(),
                transaction.requestKey(), transaction.operation(),
                transaction.state(), transaction.participants(), lots,
                transaction.timestamps(), transaction.revision(),
                transaction.configRevision(), transaction.lastError(),
                transaction.retryMetadata(), transaction.shopReference());
    }

    private static int claimCountOffset(byte[] encoded) {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        int offset = 40;
        offset += Integer.BYTES + input.getInt(offset);
        offset += Integer.BYTES + input.getInt(offset);
        return offset;
    }

    private static byte[] reverseFrames(byte[] encoded,
                                        int start,
                                        int count) {
        if (count < 2) {
            throw new IllegalArgumentException(
                    "Frame reversal requires multiple frames");
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
}
