package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedCashRequestKeyTest {
    @Test
    void mintKeysAreCompactDeterministicAndIdentityBound() {
        UUID transactionId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111");
        UUID batchId = UUID.fromString(
                "22222222-2222-2222-2222-222222222222");
        LedgerAccountId destination = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET,
                "33333333-3333-3333-3333-333333333333");
        String reserve = ProtectedCashRedemptionSupport
                .reserveMintRequestKey(transactionId, destination, batchId);
        String commit = ProtectedCashRedemptionSupport
                .commitMintRequestKey(transactionId, destination, batchId);
        String release = ProtectedCashRedemptionSupport
                .releaseMintRequestKey(transactionId, destination, batchId);

        assertEquals(92, reserve.length());
        assertEquals(91, commit.length());
        assertEquals(92, release.length());
        assertEquals(reserve, ProtectedCashRedemptionSupport
                .reserveMintRequestKey(transactionId, destination, batchId));
        assertEquals(3, Set.of(reserve, commit, release).size());
        assertTrue(reserve.startsWith("protected.cash.mint.reserve."));
    }

    @Test
    void everyMintIdentityComponentChangesTheDigest() {
        UUID transactionId = UUID.fromString(
                "41111111-1111-1111-1111-111111111111");
        UUID batchId = UUID.fromString(
                "42222222-2222-2222-2222-222222222222");
        LedgerAccountId destination = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET,
                "43333333-3333-3333-3333-333333333333");
        String baseline = ProtectedCashRedemptionSupport
                .reserveMintRequestKey(transactionId, destination, batchId);
        Set<String> variants = Set.of(
                baseline,
                ProtectedCashRedemptionSupport.reserveMintRequestKey(
                        UUID.fromString(
                                "51111111-1111-1111-1111-111111111111"),
                        destination, batchId),
                ProtectedCashRedemptionSupport.reserveMintRequestKey(
                        transactionId, new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                "53333333-3333-3333-3333-333333333333"),
                        batchId),
                ProtectedCashRedemptionSupport.reserveMintRequestKey(
                        transactionId, new LedgerAccountId(
                                LedgerAccountType.TRANSACTION_ESCROW,
                                destination.ownerKey()), batchId),
                ProtectedCashRedemptionSupport.reserveMintRequestKey(
                        transactionId, destination, UUID.fromString(
                                "52222222-2222-2222-2222-222222222222")));

        assertEquals(5, variants.size());
    }
}
