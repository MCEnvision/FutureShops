package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.server.escrow.runtime
        .ServerShopOfferReplayReceipt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferUsageSavedDataTest {
    private static final UUID PLAYER = UUID.fromString(
            "81000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST = UUID.fromString(
            "82000000-0000-0000-0000-000000000002");
    private static final OfferLimitPolicy LISTING_LIMITS =
            new OfferLimitPolicy(5, 5L, 5L, 60L, 0L);
    private static final OfferLimitPolicy OPTION_LIMITS =
            new OfferLimitPolicy(5, 4L, 4L, 60L, 0L);

    @Test
    void listingAggregateAndOptionScopesRemainIndependent() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();

        assertEquals(ServerShopOfferUsageSavedData.Decision.ALLOWED,
                data.checkListing(PLAYER, "default", "bundle",
                        OfferAction.ACQUIRE_FROM_SHOP, 5,
                        LISTING_LIMITS, 100L));
        data.recordListing(REQUEST, PLAYER, "default", "bundle",
                OfferAction.ACQUIRE_FROM_SHOP, 5,
                LISTING_LIMITS, 100L);
        data.recordOption(REQUEST, PLAYER, "default", "bundle",
                "money", OfferAction.ACQUIRE_FROM_SHOP, 2,
                OPTION_LIMITS, 100L);
        data.recordOption(REQUEST, PLAYER, "default", "bundle",
                "barter", OfferAction.ACQUIRE_FROM_SHOP, 3,
                OPTION_LIMITS, 100L);

        assertEquals(ServerShopOfferUsageSavedData.Decision.LIFETIME_LIMIT,
                data.checkListing(PLAYER, "default", "bundle",
                        OfferAction.ACQUIRE_FROM_SHOP, 1,
                        LISTING_LIMITS, 101L));
        assertEquals(ServerShopOfferUsageSavedData.Decision.ALLOWED,
                data.checkOption(PLAYER, "default", "bundle",
                        "money", OfferAction.ACQUIRE_FROM_SHOP, 2,
                        OPTION_LIMITS, 101L));
        assertEquals(ServerShopOfferUsageSavedData.Decision.LIFETIME_LIMIT,
                data.checkOption(PLAYER, "default", "bundle",
                        "money", OfferAction.ACQUIRE_FROM_SHOP, 3,
                        OPTION_LIMITS, 101L));
        assertEquals(ServerShopOfferUsageSavedData.Decision.ALLOWED,
                data.checkOption(PLAYER, "default", "bundle",
                        "barter", OfferAction.ACQUIRE_FROM_SHOP, 1,
                        OPTION_LIMITS, 101L));
    }

    @Test
    void replayDoesNotDoubleCountAndSurvivesSaveLoad() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        data.recordListing(REQUEST, PLAYER, "default", "bundle",
                OfferAction.ACQUIRE_FROM_SHOP, 5,
                LISTING_LIMITS, 100L);
        data.recordListing(REQUEST, PLAYER, "default", "bundle",
                OfferAction.ACQUIRE_FROM_SHOP, 5,
                LISTING_LIMITS, 100L);
        data.recordOption(REQUEST, PLAYER, "default", "bundle",
                "money", OfferAction.ACQUIRE_FROM_SHOP, 2,
                OPTION_LIMITS, 100L);
        data.recordOption(REQUEST, PLAYER, "default", "bundle",
                "money", OfferAction.ACQUIRE_FROM_SHOP, 2,
                OPTION_LIMITS, 100L);

        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(5L, lifetime(saved, ""));
        assertEquals(2L, lifetime(saved, "money"));

        ServerShopOfferUsageSavedData loaded =
                ServerShopOfferUsageSavedData.load(saved);
        loaded.recordListing(REQUEST, PLAYER, "default", "bundle",
                OfferAction.ACQUIRE_FROM_SHOP, 5,
                LISTING_LIMITS, 100L);
        loaded.recordOption(REQUEST, PLAYER, "default", "bundle",
                "money", OfferAction.ACQUIRE_FROM_SHOP, 2,
                OPTION_LIMITS, 100L);
        CompoundTag replayed = loaded.save(new CompoundTag());

        assertEquals(5L, lifetime(replayed, ""));
        assertEquals(2L, lifetime(replayed, "money"));
    }

    @Test
    void maximumPerRequestAppliesToListingAggregate() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();

        assertEquals(ServerShopOfferUsageSavedData.Decision.LIFETIME_LIMIT,
                data.checkListing(PLAYER, "default", "bundle",
                        OfferAction.ACQUIRE_FROM_SHOP, 6,
                        LISTING_LIMITS, 100L));
    }

    @Test
    void sellCapacityReservationIsDurableReplaySafeAndReleasable() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();

        assertTrue(data.reserveCapacity(
                REQUEST, "default", "ore", "sell", 2, 3L, 100L));
        assertTrue(data.reserveCapacity(
                REQUEST, "default", "ore", "sell", 2, 3L, 100L));
        assertEquals(ServerShopOfferUsageSavedData.Decision.LIFETIME_LIMIT,
                data.checkCapacity(
                        "default", "ore", "sell", 2, 3L, 101L));

        ServerShopOfferUsageSavedData loaded =
                ServerShopOfferUsageSavedData.load(
                        data.save(new CompoundTag()));
        assertEquals(ServerShopOfferUsageSavedData.Decision.LIFETIME_LIMIT,
                loaded.checkCapacity(
                        "default", "ore", "sell", 2, 3L, 101L));

        loaded.releaseCapacity(
                REQUEST, "default", "ore", "sell", 2, 3L);
        assertEquals(ServerShopOfferUsageSavedData.Decision.ALLOWED,
                loaded.checkCapacity(
                        "default", "ore", "sell", 3, 3L, 102L));
    }

    @Test
    void corruptAndDuplicateUsageRowsFailClosed() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        data.recordListing(REQUEST, PLAYER, "default", "bundle",
                OfferAction.ACQUIRE_FROM_SHOP, 1,
                LISTING_LIMITS, 100L);
        CompoundTag negative = data.save(new CompoundTag());
        negative.getList("Usages", Tag.TAG_COMPOUND)
                .getCompound(0).putLong("Lifetime", -1L);
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferUsageSavedData.load(negative));

        CompoundTag duplicate = data.save(new CompoundTag());
        ListTag rows = duplicate.getList(
                "Usages", Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferUsageSavedData.load(duplicate));
    }

    @Test
    void requestEvidenceBeyondLegacyWindowSurvivesRestart() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        OfferLimitPolicy lifetimeTracked =
                new OfferLimitPolicy(1, 20_000L, 0L, 0L, 0L);
        UUID first = new UUID(91L, 1L);
        for (int index = 1; index <= 10_001; index++) {
            data.recordOption(
                    new UUID(91L, index), PLAYER,
                    "default", "bundle", "money",
                    OfferAction.ACQUIRE_FROM_SHOP, 1,
                    lifetimeTracked, 100L + index);
        }
        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(10_001L, lifetime(saved, "money"));

        ServerShopOfferUsageSavedData loaded =
                ServerShopOfferUsageSavedData.load(saved);
        loaded.recordOption(
                first, PLAYER, "default", "bundle", "money",
                OfferAction.ACQUIRE_FROM_SHOP, 1,
                lifetimeTracked, 20_000L);

        assertEquals(10_001L,
                lifetime(loaded.save(new CompoundTag()), "money"));
    }

    @Test
    void compactReplayEvidenceReconstructsCartUsageOnce() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        UUID request = new UUID(92L, 1L);
        ServerShopOfferReplayReceipt receipt =
                new ServerShopOfferReplayReceipt(
                        request,
                        ServerShopOfferReplayReceipt.Kind.CART,
                        "c".repeat(64),
                        com.enviouse.futureshops.server.escrow.runtime
                                .ServerShopOfferService.Status.SUCCESS,
                        List.of(
                                new ServerShopOfferReplayReceipt
                                        .UsageEvidence(
                                        request, PLAYER, "default",
                                        "bundle", "money",
                                        OfferAction.ACQUIRE_FROM_SHOP,
                                        2, LISTING_LIMITS,
                                        OPTION_LIMITS, 0L, 100L),
                                new ServerShopOfferReplayReceipt
                                        .UsageEvidence(
                                        request, PLAYER, "default",
                                        "bundle", "barter",
                                        OfferAction.ACQUIRE_FROM_SHOP,
                                        3, LISTING_LIMITS,
                                        OPTION_LIMITS, 0L, 100L)));

        data.reconcileArchived(receipt, true);
        data.reconcileArchived(receipt, true);
        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(5L, lifetime(saved, ""));
        assertEquals(2L, lifetime(saved, "money"));
        assertEquals(3L, lifetime(saved, "barter"));

        ServerShopOfferUsageSavedData loaded =
                ServerShopOfferUsageSavedData.load(saved);
        loaded.reconcileArchived(receipt, true);
        CompoundTag replayed = loaded.save(new CompoundTag());
        assertEquals(5L, lifetime(replayed, ""));
        assertEquals(2L, lifetime(replayed, "money"));
        assertEquals(3L, lifetime(replayed, "barter"));
    }

    @Test
    void unlimitedScopesDoNotAccumulateReplayIdentities() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        OfferLimitPolicy unlimited =
                new OfferLimitPolicy(64, 0L, 0L, 0L, 0L);

        for (int index = 0; index < 1_000; index++) {
            data.recordOption(
                    new UUID(100L, index + 1L),
                    PLAYER, "default", "bundle", "money",
                    OfferAction.ACQUIRE_FROM_SHOP, 1,
                    unlimited, 100L + index);
        }

        assertTrue(data.save(new CompoundTag())
                .getList("Usages", Tag.TAG_COMPOUND).isEmpty());
    }

    @Test
    void nonLifetimeReplayIdentitiesPruneAfterWindowsExpire() {
        ServerShopOfferUsageSavedData data =
                new ServerShopOfferUsageSavedData();
        OfferLimitPolicy windowed =
                new OfferLimitPolicy(64, 0L, 100L, 60L, 5L);
        data.recordOption(
                REQUEST, PLAYER, "default", "bundle", "money",
                OfferAction.ACQUIRE_FROM_SHOP, 1,
                windowed, 100L);
        data.recordOption(
                new UUID(REQUEST.getMostSignificantBits(), 99L),
                PLAYER, "default", "bundle", "money",
                OfferAction.ACQUIRE_FROM_SHOP, 1,
                windowed, 161L);

        CompoundTag saved = data.save(new CompoundTag());
        ListTag rows = saved.getList("Usages", Tag.TAG_COMPOUND);
        assertEquals(1, rows.size());
        assertEquals(1, rows.getCompound(0)
                .getList("Requests", Tag.TAG_COMPOUND).size());
    }

    private static long lifetime(CompoundTag tag, String optionId) {
        ListTag rows = tag.getList("Usages", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            if (row.getString("Option").equals(optionId)) {
                return row.getLong("Lifetime");
            }
        }
        throw new AssertionError("Missing usage scope " + optionId);
    }
}
