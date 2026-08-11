package com.enviouse.futureshops.server.market.profile;

import com.enviouse.futureshops.client.market.MarketModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketProfileSavedDataTest {
    private static final UUID PLAYER = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final MarketProfileSavedData.ProductKey PRODUCT =
            new MarketProfileSavedData.ProductKey("minecraft:emerald", 3L);

    @Test
    void profileRoundTripsFavoritesAlertsAndNotifications() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        UUID listing = UUID.fromString(
                "20000000-0000-0000-0000-000000000001");
        UUID alertId = UUID.fromString(
                "30000000-0000-0000-0000-000000000001");
        data.setAuctionWatched(PLAYER, listing, true);
        data.setProductFavorite(PLAYER, PRODUCT, true);
        data.recordRecentProduct(PLAYER, PRODUCT);
        assertTrue(data.putPriceAlert(PLAYER,
                new MarketProfileSavedData.PriceAlert(alertId, PRODUCT,
                        MarketProfileSavedData.AlertDirection.AT_OR_ABOVE,
                        500L, true, 1000L, -1L)));

        assertEquals(1, data.evaluatePrice(
                PLAYER, PRODUCT, 500L, 1200L).size());
        MarketProfileSavedData.Snapshot expected = data.snapshot(PLAYER);
        assertEquals(1, expected.unreadNotifications());
        assertFalse(expected.priceAlerts().get(0).enabled());

        CompoundTag encoded = data.save(new CompoundTag());
        MarketProfileSavedData restored =
                MarketProfileSavedData.load(encoded.copy());

        assertEquals(expected, restored.snapshot(PLAYER));
        UUID notificationId = expected.notifications().get(0)
                .notificationId();
        assertTrue(restored.markNotificationRead(
                PLAYER, notificationId));
        assertEquals(0, restored.snapshot(PLAYER)
                .unreadNotifications());
        assertEquals(1, restored.clearReadNotifications(PLAYER));
        assertTrue(restored.snapshot(PLAYER).notifications().isEmpty());
    }

    @Test
    void boundedCollectionsEvictOldestPresentationState() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        UUID first = null;
        UUID last = null;
        for (int index = 0;
             index <= MarketProfileSavedData.MAX_WATCHED_AUCTIONS;
             index++) {
            UUID listing = new UUID(4L, index + 1L);
            if (index == 0) {
                first = listing;
            }
            last = listing;
            data.setAuctionWatched(PLAYER, listing, true);
        }
        MarketProfileSavedData.Snapshot snapshot = data.snapshot(PLAYER);

        assertEquals(MarketProfileSavedData.MAX_WATCHED_AUCTIONS,
                snapshot.watchedAuctions().size());
        assertFalse(snapshot.watchedAuctions().contains(first));
        assertTrue(snapshot.watchedAuctions().contains(last));
    }

    @Test
    void alertCapacityRejectsNewIdentityWithoutReplacingState() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        for (int index = 0;
             index < MarketProfileSavedData.MAX_PRICE_ALERTS;
             index++) {
            assertTrue(data.putPriceAlert(PLAYER, alert(
                    new UUID(5L, index + 1L), index + 1L)));
        }

        assertFalse(data.putPriceAlert(PLAYER,
                alert(new UUID(6L, 1L), 999L)));
        assertEquals(MarketProfileSavedData.MAX_PRICE_ALERTS,
                data.snapshot(PLAYER).priceAlerts().size());
    }

    @Test
    void malformedOrOversizedStateFailsClosed() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        data.setProductFavorite(PLAYER, PRODUCT, true);
        CompoundTag encoded = data.save(new CompoundTag());
        encoded.putString("schemaVersion", "wrong");

        assertThrows(IllegalStateException.class,
                () -> MarketProfileSavedData.load(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketProfileSavedData.ProductKey("", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketProfileSavedData.PriceAlert(
                        UUID.randomUUID(), PRODUCT,
                        MarketProfileSavedData.AlertDirection.AT_OR_BELOW,
                        0L, true, 1L, -1L));
    }

    @Test
    void revisionChangesOnlyWhenPresentationStateChanges() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        UUID listing = UUID.randomUUID();
        assertTrue(data.setAuctionWatched(PLAYER, listing, true));
        assertEquals(1L, data.snapshot(PLAYER).revision());
        assertFalse(data.setAuctionWatched(PLAYER, listing, true));
        assertEquals(1L, data.snapshot(PLAYER).revision());
        assertTrue(data.setProductFavorite(PLAYER, PRODUCT, true));
        assertEquals(2L, data.snapshot(PLAYER).revision());

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.notify(PLAYER, notification(first));
        data.notify(PLAYER, notification(second));
        assertEquals(4L, data.snapshot(PLAYER).revision());
        assertEquals(2, data.markNotificationsRead(PLAYER,
                MarketProfileSavedData.Module.BAZAAR,
                List.of(first, second)));
        assertEquals(5L, data.snapshot(PLAYER).revision());
    }

    @Test
    void schemaOneMigratesWithoutRevisionOrReplayState() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        data.setProductFavorite(PLAYER, PRODUCT, true);
        CompoundTag encoded = data.save(new CompoundTag());
        encoded.putInt("schemaVersion", 1);
        ListTag players = encoded.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag player = players.getCompound(index);
            player.remove("revision");
            player.remove("mutationReceipts");
            player.remove("mutationTombstones");
            player.remove("mutationReplayFilterVersion");
            player.remove("mutationReplayFilterBits");
            player.remove("mutationReplayFilterHashes");
            player.remove("mutationReplayFilter");
        }

        MarketProfileSavedData restored = MarketProfileSavedData.load(
                encoded);

        assertEquals(0L, restored.snapshot(PLAYER).revision());
        assertEquals(List.of(PRODUCT), restored.snapshot(PLAYER)
                .favoriteProducts());
    }

    @Test
    void receiptsAndReplayEpochRoundTripWithDeterministicRotation() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        UUID route = UUID.randomUUID();
        MarketProfileMutationCommand first = null;
        MarketProfileMutationCommand second = null;
        MarketProfileMutationCommand last = null;
        int total = MarketProfileSavedData.MAX_MUTATION_RECEIPTS
                + MarketProfileSavedData.MAX_MUTATION_TOMBSTONES + 1;
        for (int index = 0; index < total; index++) {
            MarketProfileMutationCommand command =
                    new MarketProfileMutationCommand(
                            new UUID(9L, index + 1L), route,
                            MarketModule.BAZAAR, "products", 0L,
                            new MarketProfileMutation.BazaarFavorite(
                                    PRODUCT, true));
            if (index == 0) {
                first = command;
            } else if (index == 1) {
                second = command;
            }
            last = command;
            data.recordMutationReceipt(PLAYER, receipt(command));
        }

        assertEquals(0L, data.snapshot(PLAYER).revision());
        assertTrue(data.mutationReceipt(PLAYER,
                first.requestId()).isEmpty());
        assertEquals(1L, data.snapshot(PLAYER).replayEpoch());
        assertEquals(MarketProfileSavedData.RetiredMutationRequest.NONE,
                data.retiredMutationRequest(PLAYER, first.requestId(),
                        first.fingerprint()));
        assertEquals(MarketProfileSavedData.RetiredMutationRequest.NONE,
                data.retiredMutationRequest(PLAYER, second.requestId(),
                        second.fingerprint()));
        assertTrue(data.mutationReceipt(PLAYER,
                last.requestId()).isPresent());

        MarketProfileSavedData restored = MarketProfileSavedData.load(
                data.save(new CompoundTag()).copy());
        assertEquals(1L, restored.snapshot(PLAYER).replayEpoch());
        assertEquals(MarketProfileSavedData.RetiredMutationRequest.NONE,
                restored.retiredMutationRequest(PLAYER,
                        first.requestId(), first.fingerprint()));
        assertEquals(MarketProfileSavedData.RetiredMutationRequest.NONE,
                restored.retiredMutationRequest(PLAYER,
                        second.requestId(), second.fingerprint()));
        assertEquals(data.mutationReceipt(PLAYER, last.requestId()),
                restored.mutationReceipt(PLAYER, last.requestId()));
    }

    @Test
    void replayEpochIsRequiredAndValidated() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        data.setProductFavorite(PLAYER, PRODUCT, true);
        CompoundTag encoded = data.save(new CompoundTag());

        CompoundTag missingEpoch = encoded.copy();
        missingEpoch.getList("players", Tag.TAG_COMPOUND)
                .getCompound(0).remove("mutationReplayEpoch");
        assertThrows(IllegalStateException.class, () ->
                MarketProfileSavedData.load(missingEpoch));

        CompoundTag negativeEpoch = encoded.copy();
        negativeEpoch.getList("players", Tag.TAG_COMPOUND)
                .getCompound(0).putLong("mutationReplayEpoch", -1L);
        assertThrows(IllegalStateException.class, () ->
                MarketProfileSavedData.load(negativeEpoch));
    }

    @Test
    void schemaTwoBloomStateMigratesToANewExactEpoch() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        data.setProductFavorite(PLAYER, PRODUCT, true);
        CompoundTag encoded = data.save(new CompoundTag());
        encoded.putInt("schemaVersion", 2);
        CompoundTag player = encoded.getList("players", Tag.TAG_COMPOUND)
                .getCompound(0);
        player.remove("mutationReplayEpoch");
        player.putInt("mutationReplayFilterVersion", 1);
        player.putInt("mutationReplayFilterBits", 32768);
        player.putInt("mutationReplayFilterHashes", 5);
        player.putLongArray("mutationReplayFilter", new long[512]);

        MarketProfileSavedData restored = MarketProfileSavedData.load(
                encoded);

        assertEquals(1L, restored.snapshot(PLAYER).replayEpoch());
        assertEquals(List.of(PRODUCT), restored.snapshot(PLAYER)
                .favoriteProducts());
    }

    @Test
    void tenThousandRetirementsNeverCreateFalseReplayMatches() {
        MarketProfileSavedData data = new MarketProfileSavedData();
        UUID route = UUID.randomUUID();
        for (int index = 1; index <= 10_000; index++) {
            MarketProfileMutationCommand command =
                    new MarketProfileMutationCommand(
                            new UUID(19L, index), route,
                            MarketModule.BAZAAR, "products", 0L,
                            data.snapshot(PLAYER).replayEpoch(),
                            new MarketProfileMutation.BazaarFavorite(
                                    PRODUCT, true));
            data.recordMutationReceipt(PLAYER, receipt(command));
        }
        MarketProfileMutationCommand unseen =
                new MarketProfileMutationCommand(new UUID(20L, 1L),
                        route, MarketModule.BAZAAR, "products", 0L,
                        data.snapshot(PLAYER).replayEpoch(),
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, true));

        assertTrue(data.snapshot(PLAYER).replayEpoch() > 0L);
        assertEquals(MarketProfileSavedData.RetiredMutationRequest.NONE,
                data.retiredMutationRequest(PLAYER, unseen.requestId(),
                        unseen.fingerprint()));
    }

    private static MarketProfileSavedData.PriceAlert alert(
            UUID id,
            long threshold
    ) {
        return new MarketProfileSavedData.PriceAlert(id, PRODUCT,
                MarketProfileSavedData.AlertDirection.AT_OR_ABOVE,
                threshold, true, 100L, -1L);
    }

    private static MarketProfileSavedData.Notification notification(
            UUID id
    ) {
        return new MarketProfileSavedData.Notification(id,
                MarketProfileSavedData.Module.BAZAAR,
                MarketProfileSavedData.NotificationKind.PRICE_ALERT,
                "product", "notification.price", "detail", 100L,
                false);
    }

    private static MarketProfileMutationReceipt receipt(
            MarketProfileMutationCommand command
    ) {
        return new MarketProfileMutationReceipt(PLAYER,
                command.fingerprint(), MarketProfileMutationResult.from(
                command, MarketProfileMutationResultCode.NO_CHANGE,
                MarketProfileSavedData.Snapshot.empty(), 0, false));
    }
}
