package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowAutomaticClaimDeliveryServiceTest {
    @Test
    void moneyRequestIdentityIsStableForOneClaimState() {
        EscrowClaim claim = claim(
                ClaimKind.MONEY, 100L, 100L,
                ClaimStatus.PENDING, new byte[0]);

        assertEquals(
                EscrowAutomaticClaimDeliveryService.moneyRequestId(claim),
                EscrowAutomaticClaimDeliveryService.moneyRequestId(claim));
    }

    @Test
    void partialMoneyClaimUsesNewRequestIdentity() {
        UUID claimId = UUID.randomUUID();
        EscrowClaim full = claim(claimId,
                ClaimKind.MONEY, 100L, 100L,
                ClaimStatus.PENDING, new byte[0]);
        EscrowClaim partial = claim(claimId,
                ClaimKind.MONEY, 100L, 25L,
                ClaimStatus.PARTIALLY_DELIVERED, new byte[0]);

        assertNotEquals(
                EscrowAutomaticClaimDeliveryService.moneyRequestId(full),
                EscrowAutomaticClaimDeliveryService
                        .moneyRequestId(partial));
    }

    @Test
    void onlyPublicMoneyAndExactItemsAreAutomatic() {
        assertTrue(EscrowAutomaticClaimDeliveryService
                .automaticallyDeliverable(claim(
                        ClaimKind.MONEY, 10L, 10L,
                        ClaimStatus.PENDING, new byte[0])));
        assertTrue(EscrowAutomaticClaimDeliveryService
                .automaticallyDeliverable(claim(
                        ClaimKind.ITEM, 1L, 1L,
                        ClaimStatus.PENDING, new byte[]{1})));
        assertTrue(EscrowAutomaticClaimDeliveryService
                .automaticallyDeliverable(claim(
                        ClaimKind.MONEY, 10L, 4L,
                        ClaimStatus.PARTIALLY_DELIVERED,
                        new byte[0])));
        assertFalse(EscrowAutomaticClaimDeliveryService
                .automaticallyDeliverable(claim(
                        ClaimKind.PROTECTED_CASH, 10L, 10L,
                        ClaimStatus.PENDING, new byte[]{1})));
        assertFalse(EscrowAutomaticClaimDeliveryService
                .automaticallyDeliverable(claim(
                        ClaimKind.ITEM, 1L, 0L,
                        ClaimStatus.COMPLETED, new byte[]{1})));
    }

    private static EscrowClaim claim(
            ClaimKind kind,
            long original,
            long remaining,
            ClaimStatus status,
            byte[] payload
    ) {
        return claim(UUID.randomUUID(), kind, original,
                remaining, status, payload);
    }

    private static EscrowClaim claim(
            UUID claimId,
            ClaimKind kind,
            long original,
            long remaining,
            ClaimStatus status,
            byte[] payload
    ) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new EscrowClaim(claimId, UUID.randomUUID(),
                UUID.randomUUID(), "auction.test", kind,
                original, remaining, payload, status,
                "test", now, now);
    }
}
