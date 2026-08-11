package com.enviouse.futureshops.server.escrow.claim;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimSavedDataTest {
    @Test
    void saveAndLoadPreservePartialClaimAndAttempt() {
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        EscrowClaim claim = new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.ITEM, 10L, 10L,
                new byte[]{4, 5, 6}, ClaimStatus.PENDING,
                "Won item", now, now);
        ClaimSavedData data = new ClaimSavedData();
        data.createCommitted(claim);
        data.deliverCommitted(
                claim.ownerId(), claim.claimId(), "claim attempt one", 4L, now);

        ClaimSavedData loaded = ClaimSavedData.load(data.save(new CompoundTag()));
        EscrowClaim restored = loaded.getClaim(claim.claimId());

        assertEquals(6L, restored.remainingUnits());
        assertEquals(ClaimStatus.PARTIALLY_DELIVERED, restored.status());
        assertEquals(now, restored.createdAt());
        ClaimAttemptResult attempt = loaded.deliverCommitted(
                claim.ownerId(), claim.claimId(), "claim attempt one", 4L, now);
        assertEquals(now, attempt.deliveredAt());
        assertTrue(loaded.deliverCommitted(claim.ownerId(), claim.claimId(),
                "claim attempt one", 4L, now).replayed());
    }

    @Test
    void newerSchemaFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class, () -> ClaimSavedData.load(tag));
    }

    @Test
    void currentSchemaMissingStoresFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", 3);

        assertThrows(IllegalStateException.class, () -> ClaimSavedData.load(tag));
    }

    @Test
    void schemaOneEpochMillisecondsAreMigrated() {
        Instant now = Instant.parse("2026-07-16T12:00:00.123Z");
        EscrowClaim claim = new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.MONEY, 10L, 10L,
                new byte[0], ClaimStatus.PENDING,
                "Money", now, now);
        ClaimSavedData current = new ClaimSavedData();
        current.createCommitted(claim);
        CompoundTag legacy = current.save(new CompoundTag());
        legacy.putInt("schemaVersion", 1);
        net.minecraft.nbt.ListTag claims = legacy.getList("claims", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag entry = claims.getCompound(0);
        entry.putLong("created", now.toEpochMilli());
        entry.putLong("updated", now.toEpochMilli());
        entry.remove("createdEpochSecond");
        entry.remove("createdNano");
        entry.remove("updatedEpochSecond");
        entry.remove("updatedNano");

        ClaimSavedData restored = ClaimSavedData.load(legacy);

        assertEquals(now, restored.getClaim(claim.claimId()).createdAt());
    }

    @Test
    void schemaTwoClaimsAndAttemptsReceiveConservativeIdentityAndTime() {
        Instant createdAt = Instant.parse("2026-07-16T12:00:00.123456789Z");
        Instant deliveredAt = createdAt.plusSeconds(1).plusNanos(7);
        EscrowClaim claim = new EscrowClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.MONEY,
                10L, 10L, new byte[0], ClaimStatus.PENDING,
                "Money", createdAt, createdAt);
        ClaimSavedData current = new ClaimSavedData();
        current.createCommitted(claim);
        current.deliverCommitted(
                claim.ownerId(), claim.claimId(), "legacy attempt", 4L, deliveredAt);
        CompoundTag legacy = current.save(new CompoundTag());
        legacy.putInt("schemaVersion", 2);
        CompoundTag claimEntry = legacy.getList(
                "claims", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        claimEntry.remove("source");
        CompoundTag attemptEntry = legacy.getList(
                "attempts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        attemptEntry.remove("deliveredEpochSecond");
        attemptEntry.remove("deliveredNano");

        ClaimSavedData restored = ClaimSavedData.load(legacy);
        EscrowClaim migrated = restored.getClaim(claim.claimId());
        ClaimAttemptResult attempt = restored.deliverCommitted(
                claim.ownerId(), claim.claimId(), "legacy attempt", 4L, deliveredAt);

        assertEquals("legacy.claim." + claim.claimId(), migrated.sourceKey());
        assertEquals(deliveredAt, attempt.deliveredAt());
        assertTrue(attempt.replayed());
    }

    @Test
    void openClaimPageSurvivesSavedDataRoundTrip() {
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        UUID ownerId = new UUID(12L, 1L);
        EscrowClaim pending = new EscrowClaim(new UUID(0L, 1L),
                UUID.randomUUID(), ownerId, "bazaar.claim.pending",
                ClaimKind.MONEY, 10L, 10L, new byte[0],
                ClaimStatus.PENDING, "Pending money", now, now);
        EscrowClaim quarantined = new EscrowClaim(new UUID(0L, 2L),
                UUID.randomUUID(), ownerId,
                "bazaar.claim.quarantined", ClaimKind.REFUND,
                20L, 20L, new byte[0], ClaimStatus.PENDING,
                "Quarantined refund", now, now);
        EscrowClaim internal = new EscrowClaim(new UUID(0L, 3L),
                UUID.randomUUID(), ownerId, "bazaar.claim.internal",
                ClaimKind.INTERNAL_ESCROW_MONEY, 30L, 30L,
                new byte[0], ClaimStatus.PENDING, "Internal money",
                now, now);
        EscrowClaim otherSource = new EscrowClaim(new UUID(0L, 4L),
                UUID.randomUUID(), ownerId, "auction.claim.pending",
                ClaimKind.MONEY, 40L, 40L, new byte[0],
                ClaimStatus.PENDING, "Auction money", now, now);
        ClaimSavedData data = new ClaimSavedData();
        data.createCommitted(pending);
        data.createCommitted(quarantined);
        data.createCommitted(internal);
        data.createCommitted(otherSource);
        data.quarantineCommitted(ownerId, quarantined.claimId(),
                now.plusSeconds(1));

        ClaimSavedData loaded = ClaimSavedData.load(
                data.save(new CompoundTag()));
        OpenClaimPage page = loaded.openPageFor(
                ownerId, "bazaar.", 0, 10);

        assertEquals(2, page.totalResults());
        assertEquals(1, page.pageCount());
        assertEquals(List.of(pending.claimId(), quarantined.claimId()),
                page.claims().stream().map(EscrowClaim::claimId).toList());
        assertEquals(ClaimStatus.QUARANTINED,
                page.claims().get(1).status());
    }
}
