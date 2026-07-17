package com.enviouse.futureshops.server.escrow.claim;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    @Test
    void claimCreationIsIdempotent() {
        ClaimRepository repository = repository();
        EscrowClaim claim = claim(10L);

        assertSame(claim, repository.create(claim));
        assertSame(claim, repository.create(claim));
        assertEquals(1, repository.snapshotClaims().size());
    }

    @Test
    void sameClaimIdWithDifferentAssetIsRejected() {
        ClaimRepository repository = repository();
        EscrowClaim first = claim(10L);
        repository.create(first);
        EscrowClaim conflicting = new EscrowClaim(first.claimId(), first.transactionId(), first.ownerId(),
                first.sourceKey(), first.kind(), 11L, 11L, first.payload(),
                ClaimStatus.PENDING, first.label(), NOW, NOW);

        assertThrows(ClaimConflictException.class, () -> repository.create(conflicting));
    }

    @Test
    void sameSourceCannotCreateTwoClaimIds() {
        ClaimRepository repository = repository();
        EscrowClaim first = claim(10L);
        EscrowClaim duplicateSource = new EscrowClaim(
                UUID.randomUUID(), first.transactionId(), first.ownerId(), first.sourceKey(),
                first.kind(), first.originalUnits(), first.originalUnits(), first.payload(),
                ClaimStatus.PENDING, first.label(), NOW, NOW);
        repository.create(first);

        assertThrows(ClaimConflictException.class,
                () -> repository.create(duplicateSource));
        assertEquals(1, repository.snapshotClaims().size());
    }

    @Test
    void deliveryUsesOneExactTimestampForPreflightCommitAndReplay() {
        ClaimRepository repository = repository();
        EscrowClaim claim = repository.create(claim(10L));
        Instant deliveredAt = NOW.plusNanos(19);

        ClaimAttemptResult preview = repository.preflightDeliver(
                claim.ownerId(), claim.claimId(), "timed delivery", 4L, deliveredAt);
        ClaimAttemptResult applied = repository.deliver(
                claim.ownerId(), claim.claimId(), "timed delivery", 4L, deliveredAt,
                (ignored, requested) -> requested);
        ClaimAttemptResult replay = repository.deliver(
                claim.ownerId(), claim.claimId(), "timed delivery", 4L, deliveredAt,
                (ignored, requested) -> {
                    throw new AssertionError("Replay must not deliver again");
                });

        assertEquals(deliveredAt, preview.deliveredAt());
        assertEquals(deliveredAt, applied.deliveredAt());
        assertEquals(deliveredAt, repository.get(claim.claimId()).updatedAt());
        assertTrue(replay.replayed());
        assertThrows(ClaimConflictException.class, () -> repository.preflightDeliver(
                claim.ownerId(), claim.claimId(), "timed delivery", 4L,
                deliveredAt.plusNanos(1)));
    }

    @Test
    void partialDeliveryLeavesRemainder() {
        ClaimRepository repository = repository();
        EscrowClaim claim = repository.create(claim(10L));

        ClaimAttemptResult result = repository.deliver(claim.ownerId(), claim.claimId(), "attempt one", 6L,
                NOW, (ignored, requested) -> requested);

        assertEquals(6L, result.deliveredUnits());
        assertEquals(4L, result.remainingUnits());
        assertEquals(ClaimStatus.PARTIALLY_DELIVERED, result.status());
        assertEquals(4L, repository.get(claim.claimId()).remainingUnits());
    }

    @Test
    void duplicateAttemptDoesNotDeliverAgain() {
        ClaimRepository repository = repository();
        EscrowClaim claim = repository.create(claim(10L));
        int[] calls = {0};
        ClaimDelivery delivery = (ignored, requested) -> {
            calls[0]++;
            return requested;
        };

        ClaimAttemptResult first = repository.deliver(
                claim.ownerId(), claim.claimId(), "attempt one", 10L, NOW, delivery);
        ClaimAttemptResult second = repository.deliver(
                claim.ownerId(), claim.claimId(), "attempt one", 10L, NOW, delivery);

        assertEquals(1, calls[0]);
        assertEquals(10L, first.deliveredUnits());
        assertTrue(second.replayed());
        assertEquals(ClaimStatus.COMPLETED, repository.get(claim.claimId()).status());
    }

    @Test
    void anotherOwnerCannotClaim() {
        ClaimRepository repository = repository();
        EscrowClaim claim = repository.create(claim(10L));

        assertThrows(ClaimConflictException.class, () -> repository.deliver(
                UUID.randomUUID(), claim.claimId(), "attempt one", 10L,
                NOW,
                (ignored, requested) -> requested));
    }

    @Test
    void invalidDeliveryResultCannotMutateClaim() {
        ClaimRepository repository = repository();
        EscrowClaim claim = repository.create(claim(10L));

        assertThrows(ClaimConflictException.class, () -> repository.deliver(
                claim.ownerId(), claim.claimId(), "attempt one", 5L,
                NOW,
                (ignored, requested) -> requested + 1L));
        assertEquals(10L, repository.get(claim.claimId()).remainingUnits());
    }

    @Test
    void preflightDoesNotCreateOrDeliverAClaim() {
        ClaimRepository repository = repository();
        EscrowClaim claim = claim(10L);

        repository.preflightCreate(claim);
        assertEquals(null, repository.get(claim.claimId()));
        repository.create(claim);

        ClaimAttemptResult preview = repository.preflightDeliver(
                claim.ownerId(), claim.claimId(), "preview", 6L, NOW);
        assertEquals(6L, preview.deliveredUnits());
        assertEquals(10L, repository.get(claim.claimId()).remainingUnits());
        assertTrue(repository.snapshotAttempts().isEmpty());
    }

    private static ClaimRepository repository() {
        return new ClaimRepository(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EscrowClaim claim(long units) {
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.MONEY,
                units, units, new byte[0], ClaimStatus.PENDING, "Test claim", NOW, NOW);
    }
}
