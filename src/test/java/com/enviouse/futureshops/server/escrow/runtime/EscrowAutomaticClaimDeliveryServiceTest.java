package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowAutomaticClaimDeliveryServiceTest {
    @Test
    void exactItemBudgetIsSharedAcrossOneServerTick() {
        ExactItemDeliveryTickBudget.State budget =
                new ExactItemDeliveryTickBudget.State();

        assertEquals(1, budget.remaining(40, 1));
        assertTrue(budget.tryAcquire(40, 1));
        assertEquals(0, budget.remaining(40, 1));
        assertFalse(budget.tryAcquire(40, 1));
        assertTrue(budget.tryAcquire(41, 1));
    }

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

    @Test
    void exactItemAttemptsAreBoundedWithoutStarvingMoneyClaims() {
        EscrowClaim firstExact = claim(ClaimKind.ITEM, 64L, 64L,
                ClaimStatus.PENDING, new byte[]{1});
        EscrowClaim secondExact = claim(ClaimKind.REFUND, 64L, 64L,
                ClaimStatus.PENDING, new byte[]{2});
        EscrowClaim thirdExact = claim(ClaimKind.BARTER_ITEM, 64L, 64L,
                ClaimStatus.PENDING, new byte[]{3});
        EscrowClaim firstMoney = claim(ClaimKind.MONEY, 10L, 10L,
                ClaimStatus.PENDING, new byte[0]);
        EscrowClaim secondMoney = claim(ClaimKind.MONEY, 20L, 20L,
                ClaimStatus.PENDING, new byte[0]);
        EscrowClaim thirdMoney = claim(ClaimKind.MONEY, 30L, 30L,
                ClaimStatus.PENDING, new byte[0]);

        List<EscrowClaim> selected = EscrowAutomaticClaimDeliveryService
                .selectDeliveries(List.of(firstExact, secondExact,
                        thirdExact, firstMoney, secondMoney, thirdMoney),
                        0, 4, 1);

        assertEquals(4, selected.size());
        assertEquals(1, selected.stream()
                .filter(EscrowAutomaticClaimDeliveryService
                        ::isExactItemClaim)
                .count());
        assertTrue(selected.contains(firstMoney));
        assertTrue(selected.contains(secondMoney));
        assertTrue(selected.contains(thirdMoney));
    }

    @Test
    void monetaryRefundDoesNotConsumeTheExactItemBudget() {
        EscrowClaim monetaryRefund = claim(ClaimKind.REFUND,
                100L, 100L, ClaimStatus.PENDING, new byte[0]);
        EscrowClaim itemRefund = claim(ClaimKind.REFUND,
                1L, 1L, ClaimStatus.PENDING, new byte[]{1});

        assertFalse(EscrowAutomaticClaimDeliveryService
                .isExactItemClaim(monetaryRefund));
        assertTrue(EscrowAutomaticClaimDeliveryService
                .isExactItemClaim(itemRefund));
        assertEquals(List.of(monetaryRefund),
                EscrowAutomaticClaimDeliveryService.selectDeliveries(
                        List.of(monetaryRefund, itemRefund), 0, 2, 0));
    }

    @Test
    void exactItemSelectionRotatesWithTheTickCursor() {
        EscrowClaim first = claim(ClaimKind.ITEM, 64L, 64L,
                ClaimStatus.PENDING, new byte[]{1});
        EscrowClaim second = claim(ClaimKind.ITEM, 64L, 64L,
                ClaimStatus.PENDING, new byte[]{2});
        EscrowClaim third = claim(ClaimKind.ITEM, 64L, 64L,
                ClaimStatus.PENDING, new byte[]{3});

        assertEquals(List.of(second),
                EscrowAutomaticClaimDeliveryService.selectDeliveries(
                        List.of(first, second, third), 1, 8, 1));
        assertEquals(List.of(third),
                EscrowAutomaticClaimDeliveryService.selectDeliveries(
                        List.of(first, second, third), 2, 8, 1));
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
