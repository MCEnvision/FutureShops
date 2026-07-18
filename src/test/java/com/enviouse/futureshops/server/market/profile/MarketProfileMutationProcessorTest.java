package com.enviouse.futureshops.server.market.profile;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.session.MarketServerSessionRegistry;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketProfileMutationProcessorTest {
    private static final long NOW = 1000L;
    private static final MarketProfileSavedData.ProductKey PRODUCT =
            new MarketProfileSavedData.ProductKey(
                    "minecraft:emerald", 3L);
    private static final MarketProfileMutationProcessor.TargetCatalog
            ALL_TARGETS = new MarketProfileMutationProcessor
            .TargetCatalog() {
        @Override
        public boolean auctionListingExists(UUID listingId) {
            return true;
        }

        @Override
        public boolean bazaarProductExists(
                MarketProfileSavedData.ProductKey product
        ) {
            return true;
        }
    };

    @Test
    void mutationsAdvanceRevisionOnceAndReturnUpdatedCounts() {
        Harness harness = harness(MarketModule.BAZAAR, "products");
        MarketProfileMutationResult favorite = harness.process(
                command(harness, 0L,
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, true)));
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                favorite.resultCode());
        assertEquals(1L, favorite.profileRevision());
        assertEquals(1, favorite.favoriteProductCount());

        UUID alertId = UUID.randomUUID();
        MarketProfileMutationResult alert = harness.process(
                command(harness, 1L,
                        new MarketProfileMutation.PriceAlertAdd(alertId,
                                PRODUCT,
                                MarketProfileSavedData.AlertDirection
                                        .AT_OR_ABOVE, 500L)));
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                alert.resultCode());
        assertEquals(2L, alert.profileRevision());
        assertEquals(1, alert.priceAlertCount());

        MarketProfileMutationResult removed = harness.process(
                command(harness, 2L,
                        new MarketProfileMutation.PriceAlertRemove(
                                alertId)));
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                removed.resultCode());
        assertEquals(3L, removed.profileRevision());
        assertEquals(0, removed.priceAlertCount());
        MarketProfileMutationResult unfavorite = harness.process(
                command(harness, 3L,
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, false)));
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                unfavorite.resultCode());
        assertEquals(4L, unfavorite.profileRevision());
        assertEquals(0, unfavorite.favoriteProductCount());
        assertEquals(4L, harness.profiles.snapshot(harness.player)
                .revision());
    }

    @Test
    void exactReplayReturnsOriginalResultAfterSaveAndRestart() {
        Harness harness = harness(MarketModule.AUCTION_HOUSE, "browse");
        MarketProfileMutationCommand command = command(harness, 0L,
                new MarketProfileMutation.AuctionWatch(
                        UUID.randomUUID(), true));
        MarketProfileMutationResult original = harness.process(command);
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                original.resultCode());

        MarketProfileMutationResult replay = harness.process(command);
        assertEquals(original.asReplay(), replay);
        assertEquals(1L, harness.profiles.snapshot(harness.player)
                .revision());

        CompoundTag encoded = harness.profiles.save(new CompoundTag());
        MarketProfileSavedData restored = MarketProfileSavedData.load(
                encoded.copy());
        MarketProfileMutationProcessor restarted =
                new MarketProfileMutationProcessor(
                        MarketServerSessionRegistry.defaults());
        MarketProfileMutationResult restartReplay = restarted.process(
                harness.player, command, NOW + 1L, restored,
                ALL_TARGETS, access(MarketModule.AUCTION_HOUSE,
                        true, true));

        assertEquals(original.asReplay(), restartReplay);
        assertEquals(1L, restored.snapshot(harness.player).revision());
        MarketProfileMutationCommand conflict =
                new MarketProfileMutationCommand(command.requestId(),
                        command.routeNonce(), command.module(),
                        command.view(),
                        command.expectedProfileRevision(),
                        new MarketProfileMutation.AuctionWatch(
                                ((MarketProfileMutation.AuctionWatch)
                                        command.mutation()).listingId(),
                                false));
        assertEquals(MarketProfileMutationResultCode.REQUEST_CONFLICT,
                restarted.process(harness.player, conflict, NOW + 2L,
                        restored, ALL_TARGETS,
                        access(MarketModule.AUCTION_HOUSE,
                                true, true)).resultCode());
    }

    @Test
    void conflictingReuseAndStaleRevisionFailClosed() {
        Harness harness = harness(MarketModule.BAZAAR, "products");
        UUID requestId = UUID.randomUUID();
        MarketProfileMutationCommand first = command(harness,
                requestId, 0L,
                new MarketProfileMutation.BazaarFavorite(
                        PRODUCT, true));
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                harness.process(first).resultCode());
        MarketProfileMutationCommand conflict = command(harness,
                requestId, 0L,
                new MarketProfileMutation.BazaarFavorite(
                        PRODUCT, false));
        assertEquals(MarketProfileMutationResultCode.REQUEST_CONFLICT,
                harness.process(conflict).resultCode());
        assertTrue(harness.profiles.snapshot(harness.player)
                .favoriteProducts().contains(PRODUCT));

        MarketProfileMutationCommand stale = command(harness, 0L,
                new MarketProfileMutation.PriceAlertRemove(
                        UUID.randomUUID()));
        assertEquals(MarketProfileMutationResultCode.STALE_PROFILE,
                harness.process(stale).resultCode());
        assertEquals(1L, harness.profiles.snapshot(harness.player)
                .revision());
    }

    @Test
    void browseMutationsRequireAccessButFrozenPresentationIsAllowed() {
        Harness unavailable = harness(MarketModule.BAZAAR, "products");
        MarketProfileMutationCommand denied = command(unavailable, 0L,
                new MarketProfileMutation.BazaarFavorite(
                        PRODUCT, true));
        MarketProfileMutationResult deniedResult =
                unavailable.processor.process(unavailable.player,
                        denied, NOW, unavailable.profiles, ALL_TARGETS,
                        new MarketProfileMutationProcessor.AccessState(
                                true, false,
                                Optional.of(control(
                                        MarketModule.BAZAAR))));
        assertEquals(MarketProfileMutationResultCode.ESCROW_NOT_READY,
                deniedResult.resultCode());
        assertEquals(0L, deniedResult.profileRevision());

        Harness missingControl = harness(MarketModule.BAZAAR,
                "products");
        MarketProfileMutationResult disabled =
                missingControl.processor.process(missingControl.player,
                        command(missingControl, 0L,
                                new MarketProfileMutation
                                        .BazaarFavorite(PRODUCT, true)),
                        NOW, missingControl.profiles, ALL_TARGETS,
                        new MarketProfileMutationProcessor.AccessState(
                                true, true, Optional.empty()));
        assertEquals(MarketProfileMutationResultCode
                        .MODULE_CONTROL_UNAVAILABLE,
                disabled.resultCode());

        Harness frozen = harness(MarketModule.BAZAAR, "products");
        MarketProfileMutationResult allowed =
                frozen.processor.process(frozen.player,
                        command(frozen, 0L,
                                new MarketProfileMutation
                                        .BazaarFavorite(PRODUCT, true)),
                        NOW, frozen.profiles, ALL_TARGETS,
                        access(MarketModule.BAZAAR, false, true));
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                allowed.resultCode());
        assertEquals(1L, allowed.profileRevision());
    }

    @Test
    void notificationCleanupRemainsAvailableInClaimsOnlyMode() {
        Harness harness = harness(MarketModule.AUCTION_HOUSE, "claims");
        UUID target = UUID.randomUUID();
        UUID otherModule = UUID.randomUUID();
        harness.profiles.notify(harness.player, notification(target,
                MarketProfileSavedData.Module.AUCTION_HOUSE));
        harness.profiles.notify(harness.player, notification(otherModule,
                MarketProfileSavedData.Module.BAZAAR));
        assertEquals(2L, harness.profiles.snapshot(harness.player)
                .revision());
        MarketProfileMutationCommand command = command(harness, 2L,
                new MarketProfileMutation.NotificationsRead(
                        List.of(target, otherModule)));

        MarketProfileMutationResult result = harness.processor.process(
                harness.player, command, NOW, harness.profiles,
                ALL_TARGETS,
                new MarketProfileMutationProcessor.AccessState(
                        true, false, Optional.empty()));

        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                result.resultCode());
        assertEquals(1, result.affectedCount());
        assertEquals(3L, result.profileRevision());
        assertEquals(1, result.unreadNotificationCount());
        assertTrue(harness.profiles.snapshot(harness.player)
                .notifications().stream().filter(value ->
                        value.notificationId().equals(target))
                .findFirst().orElseThrow().read());
        assertFalse(harness.profiles.snapshot(harness.player)
                .notifications().stream().filter(value ->
                        value.notificationId().equals(otherModule))
                .findFirst().orElseThrow().read());
    }

    @Test
    void missingTargetsAndNoChangeDoNotAdvanceRevision() {
        Harness harness = harness(MarketModule.AUCTION_HOUSE, "browse");
        MarketProfileMutationProcessor.TargetCatalog missing =
                new MarketProfileMutationProcessor.TargetCatalog() {
                    @Override
                    public boolean auctionListingExists(UUID listingId) {
                        return false;
                    }

                    @Override
                    public boolean bazaarProductExists(
                            MarketProfileSavedData.ProductKey product
                    ) {
                        return false;
                    }
                };
        MarketProfileMutationCommand add = command(harness, 0L,
                new MarketProfileMutation.AuctionWatch(
                        UUID.randomUUID(), true));
        assertEquals(MarketProfileMutationResultCode.TARGET_NOT_FOUND,
                harness.processor.process(harness.player, add, NOW,
                        harness.profiles, missing,
                        access(MarketModule.AUCTION_HOUSE,
                                true, true)).resultCode());
        assertEquals(0L, harness.profiles.snapshot(harness.player)
                .revision());

        Harness noChange = harness(MarketModule.AUCTION_HOUSE,
                "browse");
        MarketProfileMutationCommand remove = command(noChange, 0L,
                new MarketProfileMutation.AuctionWatch(
                        UUID.randomUUID(), false));
        assertEquals(MarketProfileMutationResultCode.NO_CHANGE,
                noChange.process(remove).resultCode());
        assertEquals(0L, noChange.profiles.snapshot(noChange.player)
                .revision());
    }

    @Test
    void watchAndUnwatchBothApplyAsRevisionedMutations() {
        Harness harness = harness(MarketModule.AUCTION_HOUSE, "browse");
        UUID listing = UUID.randomUUID();
        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                harness.process(command(harness, 0L,
                        new MarketProfileMutation.AuctionWatch(
                                listing, true))).resultCode());
        MarketProfileMutationResult removed = harness.process(
                command(harness, 1L,
                        new MarketProfileMutation.AuctionWatch(
                                listing, false)));

        assertEquals(MarketProfileMutationResultCode.SUCCESS,
                removed.resultCode());
        assertEquals(2L, removed.profileRevision());
        assertEquals(0, removed.watchedAuctionCount());
    }

    @Test
    void evictedRequestCannotMutateAfterRestart() {
        UUID player = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        MarketProfileSavedData profiles = new MarketProfileSavedData();
        MarketProfileMutationCommand retired =
                new MarketProfileMutationCommand(
                        new UUID(7L, 1L), route, MarketModule.BAZAAR,
                        "products", 0L,
                        new MarketProfileMutation.BazaarFavorite(
                                PRODUCT, true));
        int total = MarketProfileSavedData.MAX_MUTATION_RECEIPTS
                + MarketProfileSavedData.MAX_MUTATION_TOMBSTONES + 1;
        for (int index = 0; index < total; index++) {
            MarketProfileMutationCommand command = index == 0
                    ? retired : new MarketProfileMutationCommand(
                    new UUID(7L, index + 1L), route,
                    MarketModule.BAZAAR, "products", 0L,
                    new MarketProfileMutation.BazaarFavorite(
                            PRODUCT, true));
            profiles.recordMutationReceipt(player,
                    new MarketProfileMutationReceipt(player,
                            command.fingerprint(),
                            MarketProfileMutationResult.from(command,
                                    MarketProfileMutationResultCode
                                            .NO_CHANGE,
                                    MarketProfileSavedData.Snapshot
                                            .empty(), 0, false)));
        }
        MarketProfileSavedData restored = MarketProfileSavedData.load(
                profiles.save(new CompoundTag()).copy());
        MarketServerSessionRegistry sessions =
                MarketServerSessionRegistry.defaults();
        sessions.open(player, MarketModule.BAZAAR, "products", route,
                NOW);
        MarketProfileMutationProcessor processor =
                new MarketProfileMutationProcessor(sessions);

        MarketProfileMutationResult result = processor.process(player,
                retired, NOW, restored, ALL_TARGETS,
                access(MarketModule.BAZAAR, true, true));

        assertEquals(MarketProfileMutationResultCode.STALE_REPLAY_EPOCH,
                result.resultCode());
        assertFalse(restored.snapshot(player).favoriteProducts()
                .contains(PRODUCT));
        assertEquals(0L, restored.snapshot(player).revision());
    }

    private static Harness harness(
            MarketModule module,
            String view
    ) {
        UUID player = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        MarketServerSessionRegistry sessions =
                MarketServerSessionRegistry.defaults();
        sessions.open(player, module, view, route, NOW);
        return new Harness(player, route, module, view, sessions,
                new MarketProfileSavedData());
    }

    private static MarketProfileMutationCommand command(
            Harness harness,
            long revision,
            MarketProfileMutation mutation
    ) {
        return command(harness, UUID.randomUUID(), revision, mutation);
    }

    private static MarketProfileMutationCommand command(
            Harness harness,
            UUID requestId,
            long revision,
            MarketProfileMutation mutation
    ) {
        return new MarketProfileMutationCommand(requestId,
                harness.route, harness.module, harness.view, revision,
                mutation);
    }

    private static MarketProfileMutationProcessor.AccessState access(
            MarketModule module,
            boolean configured,
            boolean escrowReady
    ) {
        return new MarketProfileMutationProcessor.AccessState(configured,
                escrowReady, Optional.of(control(module)));
    }

    private static MarketModuleControl control(MarketModule module) {
        MarketControlModule controlModule = switch (module) {
            case SHOP -> MarketControlModule.SHOP;
            case BAZAAR -> MarketControlModule.BAZAAR;
            case AUCTION_HOUSE -> MarketControlModule.AUCTION_HOUSE;
        };
        return MarketControlState.initial(0L).module(controlModule);
    }

    private static MarketProfileSavedData.Notification notification(
            UUID id,
            MarketProfileSavedData.Module module
    ) {
        return new MarketProfileSavedData.Notification(id, module,
                MarketProfileSavedData.NotificationKind.CLAIM_READY,
                "claim", "notification.claim", "ready", NOW,
                false);
    }

    private record Harness(
            UUID player,
            UUID route,
            MarketModule module,
            String view,
            MarketServerSessionRegistry sessions,
            MarketProfileSavedData profiles,
            MarketProfileMutationProcessor processor
    ) {
        private Harness(
                UUID player,
                UUID route,
                MarketModule module,
                String view,
                MarketServerSessionRegistry sessions,
                MarketProfileSavedData profiles
        ) {
            this(player, route, module, view, sessions, profiles,
                    new MarketProfileMutationProcessor(sessions));
        }

        private MarketProfileMutationResult process(
                MarketProfileMutationCommand command
        ) {
            return processor.process(player, command, NOW, profiles,
                    ALL_TARGETS, access(module, true, true));
        }
    }
}
