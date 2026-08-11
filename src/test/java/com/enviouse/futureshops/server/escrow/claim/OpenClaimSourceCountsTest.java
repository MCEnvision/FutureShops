package com.enviouse.futureshops.server.escrow.claim;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenClaimSourceCountsTest {
    @Test
    void aggregateCountsEveryOwnedOpenClaimBeyondPageLimits() {
        ClaimRepository repository = new ClaimRepository();
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        for (int index = 0; index < 300; index++) {
            repository.create(claim(owner,
                    "auction.sale." + index));
        }
        for (int index = 0; index < 7; index++) {
            repository.create(claim(owner,
                    "bazaar.fill." + index));
        }
        repository.create(claim(owner, "shop.refund.one"));
        repository.create(claim(otherOwner, "auction.other.one"));

        OpenClaimSourceCounts counts = repository.openSourceCountsFor(
                owner, List.of("auction.", "bazaar."));

        assertEquals(308L, counts.totalOpenClaims());
        assertEquals(300L, counts.matching("auction."));
        assertEquals(7L, counts.matching("bazaar."));
        assertEquals(256, repository.pendingFor(owner, 1000).size());
    }

    @Test
    void quarantinedAndPartialClaimsRemainOpenWhileCompletedClaimsDoNot() {
        ClaimRepository repository = new ClaimRepository();
        UUID owner = UUID.randomUUID();
        EscrowClaim quarantined = repository.create(claim(owner,
                "auction.quarantined"));
        repository.quarantine(owner, quarantined.claimId(),
                Instant.ofEpochSecond(2L));
        EscrowClaim partial = repository.create(claim(owner,
                "bazaar.partial", 2L));
        repository.deliver(owner, partial.claimId(), "partial", 1L,
                Instant.ofEpochSecond(2L), (claim, requested) -> 1L);
        EscrowClaim completed = repository.create(claim(owner,
                "auction.completed"));
        repository.deliver(owner, completed.claimId(), "completed", 1L,
                Instant.ofEpochSecond(2L), (claim, requested) -> 1L);

        OpenClaimSourceCounts counts = repository.openSourceCountsFor(
                owner, List.of("auction.", "bazaar."));

        assertEquals(2L, counts.totalOpenClaims());
        assertEquals(1L, counts.matching("auction."));
        assertEquals(1L, counts.matching("bazaar."));
    }

    @Test
    void prefixInputIsStrictAndBounded() {
        ClaimRepository repository = new ClaimRepository();
        UUID owner = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> repository.openSourceCountsFor(owner,
                        List.of("auction.", "auction.")));
        assertThrows(IllegalArgumentException.class,
                () -> repository.openSourceCountsFor(owner,
                        List.of(" auction.")));
        assertThrows(IllegalArgumentException.class,
                () -> repository.openSourceCountsFor(owner,
                        java.util.Collections.nCopies(
                                OpenClaimSourceCounts.MAXIMUM_PREFIXES + 1,
                                "auction.")));
    }

    @Test
    void internalEscrowMoneyIsExcludedFromListingsAndCounts() {
        ClaimRepository repository = new ClaimRepository();
        UUID owner = UUID.randomUUID();
        EscrowClaim internal = new EscrowClaim(UUID.randomUUID(),
                UUID.randomUUID(), owner, "bazaar.internal.cash",
                ClaimKind.INTERNAL_ESCROW_MONEY, 10L, 10L,
                new byte[0], ClaimStatus.PENDING, "Internal cash",
                Instant.EPOCH, Instant.EPOCH);
        repository.create(internal);

        assertEquals(List.of(), repository.pendingFor(owner, 256));
        assertEquals(0L, repository.openSourceCountsFor(owner,
                List.of("bazaar.")).totalOpenClaims());
        assertEquals(List.of(internal),
                repository.forTransaction(internal.transactionId()));
    }

    private static EscrowClaim claim(UUID owner, String source) {
        return claim(owner, source, 1L);
    }

    private static EscrowClaim claim(
            UUID owner,
            String source,
            long units
    ) {
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(),
                owner, source, ClaimKind.MONEY, units, units,
                new byte[0], ClaimStatus.PENDING, "Claim",
                Instant.EPOCH, Instant.EPOCH);
    }
}
