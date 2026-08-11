package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimRepository;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalEscrowCashCrashWindowTest {
    private static final UUID OWNER_ID = UUID.fromString(
            "a3000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION_ID = UUID.fromString(
            "a3000000-0000-0000-0000-000000000002");

    @Test
    void depositedCashCannotBeListedOrCollectedBeforeShopReservation() {
        EscrowClaim claim = new EscrowClaim(
                UUID.fromString(
                        "a3000000-0000-0000-0000-000000000003"),
                TRANSACTION_ID, OWNER_ID,
                "protected.cash.internal.overflow",
                ClaimKind.INTERNAL_ESCROW_MONEY, 500L, 500L,
                new byte[0], ClaimStatus.PENDING,
                "Internal escrow cash", Instant.EPOCH, Instant.EPOCH);
        ClaimRepository repository = new ClaimRepository();
        repository.create(claim);

        assertEquals(claim, ClaimJournalCodec.decodeClaim(
                ClaimJournalCodec.encodeClaim(claim)));
        assertEquals(List.of(), repository.pendingFor(OWNER_ID, 256));
        assertEquals(0L, repository.openSourceCountsFor(OWNER_ID,
                List.of("protected.cash.")).totalOpenClaims());
        assertFalse(EscrowMoneyClaimService.publiclyCollectible(
                claim, OWNER_ID));
        assertTrue(PlayerShopLiveEscrowService
                .internalPhysicalFundingClaim(claim, OWNER_ID,
                        TRANSACTION_ID));
        assertEquals(ClaimStatus.PENDING,
                repository.get(claim.claimId()).status());
        assertEquals(500L,
                repository.get(claim.claimId()).remainingUnits());
        assertTrue(repository.snapshotAttempts().isEmpty());
    }

    @Test
    void ordinaryMoneyNeverPassesThePlayerShopInternalValidator() {
        EscrowClaim publicMoney = new EscrowClaim(
                UUID.fromString(
                        "a3000000-0000-0000-0000-000000000004"),
                TRANSACTION_ID, OWNER_ID,
                "protected.cash.public.overflow", ClaimKind.MONEY,
                500L, 500L, new byte[0], ClaimStatus.PENDING,
                "Public money", Instant.EPOCH, Instant.EPOCH);

        assertTrue(EscrowMoneyClaimService.publiclyCollectible(
                publicMoney, OWNER_ID));
        assertFalse(PlayerShopLiveEscrowService
                .internalPhysicalFundingClaim(publicMoney, OWNER_ID,
                        TRANSACTION_ID));
    }
}
