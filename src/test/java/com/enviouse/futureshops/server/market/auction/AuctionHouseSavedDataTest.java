package com.enviouse.futureshops.server.market.auction;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleState;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowTestFixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseSavedDataTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void canonicalMutationRoundTripsAndReplays() {
        AuctionHouseSnapshot previous = AuctionHouseSnapshot.empty();
        CreateAuctionCommand command = createCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        AuctionHouseBook book = new AuctionHouseBook(previous);
        AuctionOperationResult result = book.create(command);
        assertTrue(result.durablyApplied());
        AuctionHouseMutation mutation = AuctionHouseMutation.between(
                previous, book.snapshot(), command.requestId());
        AuctionHouseSavedData data = new AuctionHouseSavedData();

        assertTrue(!data.applyCommitted(mutation).replayed());
        assertTrue(data.hasMaterializedState());
        assertTrue(data.applyCommitted(mutation).replayed());
        AuctionHouseSavedData loaded = AuctionHouseSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(data.snapshot(), loaded.snapshot());
        assertEquals(result.listing().orElseThrow(),
                loaded.listing(command.listingId()));
        assertEquals(result,
                loaded.receipt(command.requestId()).result());
    }

    @Test
    void malformedMissingNewerAndCorruptDataFailClosed() {
        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion",
                AuctionHouseSavedData.CURRENT_VERSION + 1);
        assertThrows(IllegalStateException.class,
                () -> AuctionHouseSavedData.load(newer));

        CompoundTag missing = new CompoundTag();
        missing.putInt("schemaVersion",
                AuctionHouseSavedData.CURRENT_VERSION);
        assertThrows(IllegalStateException.class,
                () -> AuctionHouseSavedData.load(missing));

        CompoundTag wrongVersion = new CompoundTag();
        wrongVersion.putString("schemaVersion", "one");
        assertThrows(IllegalStateException.class,
                () -> AuctionHouseSavedData.load(wrongVersion));

        CompoundTag wrongSnapshot = new CompoundTag();
        wrongSnapshot.putInt("schemaVersion",
                AuctionHouseSavedData.CURRENT_VERSION);
        wrongSnapshot.putString("snapshot", "bad");
        assertThrows(IllegalStateException.class,
                () -> AuctionHouseSavedData.load(wrongSnapshot));

        AuctionHouseSavedData data = new AuctionHouseSavedData();
        CompoundTag corrupt = data.save(new CompoundTag());
        byte[] encoded = corrupt.getByteArray("snapshot");
        encoded[encoded.length / 2] ^= 1;
        corrupt.putByteArray("snapshot", encoded);
        assertThrows(IllegalStateException.class,
                () -> AuctionHouseSavedData.load(corrupt));

        CompoundTag corruptLifecycle = data.save(new CompoundTag());
        byte[] lifecycle = corruptLifecycle.getByteArray(
                "escrowLifecycle");
        lifecycle[lifecycle.length / 2] ^= 1;
        corruptLifecycle.putByteArray("escrowLifecycle", lifecycle);
        assertThrows(IllegalStateException.class,
                () -> AuctionHouseSavedData.load(corruptLifecycle));
    }

    @Test
    void legacyEmptyDataMigratesToCanonicalEmptySnapshot() {
        AuctionHouseSavedData loaded = AuctionHouseSavedData.load(
                new CompoundTag());

        assertEquals(AuctionHouseSnapshot.empty(), loaded.snapshot());
        assertTrue(!loaded.hasMaterializedState());
        assertTrue(loaded.isDirty());
    }

    @Test
    void lifecycleStateRoundTripsInsideTheAuctionCheckpointStore() {
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner
                .commitCreate(AuctionHouseSnapshot.empty(), intent,
                        intent.plannedCustody().receipt());
        AuctionHouseSavedData data = new AuctionHouseSavedData();
        data.applyEscrowLifecycleCommitted(
                new AuctionEscrowLifecycleEvent.Prepare(intent));
        data.applyEscrowLifecycleCommitted(
                new AuctionEscrowLifecycleEvent.Commit(
                        Optional.of(intent.complete()), commit));

        AuctionHouseSavedData loaded = AuctionHouseSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(data.snapshot(), loaded.snapshot());
        assertEquals(data.escrowLifecycleSnapshot(),
                loaded.escrowLifecycleSnapshot());
        assertEquals(intent.complete(), loaded.createIntent(
                intent.requestId()));
        assertEquals(commit, loaded.escrowCommit(intent.requestId()));
    }

    @Test
    void versionOneSnapshotMigratesWithEmptyLifecycleState() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schemaVersion", 1);
        legacy.putByteArray("snapshot", AuctionHouseSnapshotCodec.encode(
                AuctionHouseSnapshot.empty()));

        AuctionHouseSavedData loaded = AuctionHouseSavedData.load(legacy);

        assertEquals(AuctionHouseSnapshot.empty(), loaded.snapshot());
        assertEquals(AuctionEscrowLifecycleState.empty(),
                loaded.escrowLifecycleSnapshot());
        assertTrue(loaded.isDirty());
    }

    @Test
    void conflictingAncestryCannotMutatePersistentState() {
        AuctionHouseSnapshot previous = AuctionHouseSnapshot.empty();
        CreateAuctionCommand first = createCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        AuctionHouseBook firstBook = new AuctionHouseBook(previous);
        firstBook.create(first);
        AuctionHouseMutation firstMutation = AuctionHouseMutation.between(
                previous, firstBook.snapshot(), first.requestId());

        CreateAuctionCommand stale = createCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        AuctionHouseBook staleBook = new AuctionHouseBook(previous);
        staleBook.create(stale);
        AuctionHouseMutation staleMutation = AuctionHouseMutation.between(
                previous, staleBook.snapshot(), stale.requestId());
        AuctionHouseSavedData data = new AuctionHouseSavedData();
        data.applyCommitted(firstMutation);
        AuctionHouseSnapshot committed = data.snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> data.applyCommitted(staleMutation));
        assertEquals(committed, data.snapshot());
    }

    private static CreateAuctionCommand createCommand(
            UUID requestId,
            UUID listingId,
            UUID sellerId
    ) {
        return new CreateAuctionCommand(requestId, listingId, sellerId,
                UUID.randomUUID(), new AuctionItemLot(UUID.randomUUID(),
                "minecraft:diamond", "a".repeat(64), 1, 32,
                "materials", "diamond minecraft"),
                AuctionListingType.AUCTION_WITH_BUYOUT, 100L, 1000L,
                new AuctionRuleSnapshot(10L, 250, 10L, 0, true,
                        60L, 60L, 120L, 2, true,
                        AuctionTimeBasis.REAL_TIME, true, 7L),
                1000L, 2000L);
    }
}
