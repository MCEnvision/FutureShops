package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAssetEndpoint;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopConservationValidator;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemMutationReceipt;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyMutationReceipt;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopMoneyTransfer;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOfferSelection;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowOrchestrator;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPacketResponseIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopRequestIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopTradeMethod;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockReservationResolution;
import com.enviouse.futureshops.server.transaction.ServerShopOfferIntentFactory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferCommitCodecTest {
    private static final Instant NOW =
            Instant.parse("2026-07-24T20:00:00Z");
    private static final UUID PLAYER = id("player");
    private static final DimensionAwareShopReference REFERENCE =
            new DimensionAwareShopReference("default",
                    "minecraft:overworld", 1, 64, 2);

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void postCommitDeliveryRecoveryAndQuarantineRemainPending() {
        PlayerShopAtomicCommit commit =
                cartCommit("delivery outcome mapping").valueCommit();
        PlayerShopPacketResponseIdentity response =
                PlayerShopPacketResponseIdentity.from(
                        PlayerShopRequestIdentity.from(
                                commit.committedIntent(), 9));
        for (PlayerShopEscrowOrchestrator.Status status : List.of(
                PlayerShopEscrowOrchestrator.Status.RECOVERY_REQUIRED,
                PlayerShopEscrowOrchestrator.Status.QUARANTINED)) {
            PlayerShopEscrowOrchestrator.Result result =
                    new PlayerShopEscrowOrchestrator.Result(
                            response, status, commit,
                            status == PlayerShopEscrowOrchestrator.Status
                                    .RECOVERY_REQUIRED
                                    ? "DELIVERY_RECOVERY_REQUIRED"
                                    : "DELIVERY_QUARANTINED",
                            "Claims require a durable retry");

            assertFalse(ServerShopOfferService.preCommit(result));
            assertTrue(ServerShopOfferService.claimsPending(result));
            assertFalse(ServerShopOfferCartService.preCommit(result));
            assertTrue(ServerShopOfferCartService.claimsPending(result));
        }
    }

    @Test
    void everyAcquireCombinationConserves() {
        ServerShopOfferListing listing = listing();
        for (AcquireOfferOption option : listing.acquireOptions()) {
            ServerShopOfferIntentFactory.Prepared prepared =
                    ServerShopOfferIntentFactory.acquire(
                            id("request " + option.optionId()), PLAYER,
                            "default", listing, option, 2,
                            option.moneyCostPresent()
                                    ? PaymentSource.WALLET : null,
                            10_000L, REFERENCE, NOW);

            assertTrue(PlayerShopConservationValidator.validate(
                    prepared.intent()).conserved(), option.optionId());
            assertEquals(2, prepared.intent().requestedUnits());
            assertEquals(option.moneyCostPresent(),
                    !prepared.intent().moneyTransfers().isEmpty());
            assertEquals(option.hasItemCosts(),
                    prepared.intent().itemTransfers().stream().anyMatch(
                            value -> value.source().kind()
                                    == PlayerShopAssetEndpoint.Kind
                                    .ACTOR_INVENTORY));
        }
    }

    @Test
    void acquireRejectsMissingAndWrongDurableCostEvidence() {
        ServerShopOfferListing listing = listing();
        AcquireOfferOption option = listing.acquireOptions().get(3);
        PlayerShopEscrowIntent source =
                ServerShopOfferIntentFactory.acquire(
                        id("tampered cost evidence"), PLAYER,
                        "default", listing, option, 2,
                        PaymentSource.WALLET, 10_000L,
                        REFERENCE, NOW).intent();
        PlayerShopOfferSelection trusted =
                source.offerSelection().orElseThrow();
        PlayerShopEscrowIntent missing = withSelection(
                source, new PlayerShopOfferSelection(
                        trusted.listingId(), trusted.offerRevision(),
                        trusted.optionId(), trusted.action(),
                        trusted.listingLimits(), trusted.optionLimits(),
                        trusted.capacity(), trusted.outputComponents(),
                        List.of()));
        PlayerShopEscrowIntent wrong = withSelection(
                source, new PlayerShopOfferSelection(
                        trusted.listingId(), trusted.offerRevision(),
                        trusted.optionId(), trusted.action(),
                        trusted.listingLimits(), trusted.optionLimits(),
                        trusted.capacity(), trusted.outputComponents(),
                        List.of(new com.enviouse.futureshops.server
                                .escrow.playershop
                                .PlayerShopListingSnapshot.ItemTemplate(
                                "minecraft:stick", 1,
                                com.enviouse.futureshops.server.escrow
                                        .playershop.PlayerShopItemMatchMode
                                        .ITEM_ONLY,
                                new byte[]{1}))));

        assertFalse(PlayerShopConservationValidator.validate(missing)
                .conserved());
        assertFalse(PlayerShopConservationValidator.validate(wrong)
                .conserved());
    }

    @Test
    void multiInputSellConserves() {
        ServerShopOfferListing listing = listing();
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.sell(
                        id("sell request"), PLAYER, "default", listing,
                        listing.sellOptions().get(0), 3, REFERENCE, NOW);

        assertTrue(PlayerShopConservationValidator.validate(
                prepared.intent()).conserved());
        assertEquals(2, prepared.intent().itemTransfers().stream()
                .filter(value -> value.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY)
                .count());
        assertEquals(1, prepared.intent().moneyTransfers().size());
        assertEquals(450L, prepared.intent().moneyTransfers()
                .get(0).amountMinorUnits());
    }

    @Test
    void sellUsesAuthorizedPayoutTotal() {
        ServerShopOfferListing listing = listing();
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.sell(
                        id("authorized sell request"), PLAYER,
                        "default", listing,
                        listing.sellOptions().get(0), 3, 275L,
                        REFERENCE, NOW);

        assertTrue(PlayerShopConservationValidator.validate(
                prepared.intent()).conserved());
        assertEquals(275L, prepared.intent().moneyTransfers()
                .get(0).amountMinorUnits());
        assertEquals(275L, prepared.intent().claims()
                .get(0).moneyAmountMinorUnits());
    }

    @Test
    void commitRoundTripsFreeWithoutMoneyEvidence() {
        ServerShopOfferListing listing = listing();
        AcquireOfferOption free = listing.acquireOptions().get(0);
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.acquire(
                        id("free request"), PLAYER, "default", listing,
                        free, 2, null, 10_000L, REFERENCE, NOW);
        ServerShopOfferCommit commit = commit(
                prepared, Optional.empty(), 2);

        assertTrue(commit.moneyDebitMinorUnits().isEmpty());
        assertFalse(commit.paymentSource().isPresent());
        byte[] encoded = ServerShopOfferCommitCodec.encode(commit);
        ServerShopOfferCommit decoded =
                ServerShopOfferCommitCodec.decode(encoded);
        assertEquals(commit, decoded);
        assertArrayEquals(encoded,
                ServerShopOfferCommitCodec.encode(decoded));
    }

    @Test
    void commitRoundTripsCompoundMoneyAndItems() {
        ServerShopOfferListing listing = listing();
        AcquireOfferOption compound = listing.acquireOptions().get(3);
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.acquire(
                        id("compound request"), PLAYER, "default",
                        listing, compound, 2, PaymentSource.PHYSICAL,
                        10_000L, REFERENCE, NOW);
        ServerShopOfferCommit commit = commit(
                prepared, Optional.of(PaymentSource.PHYSICAL), 2);

        assertEquals(400L,
                commit.moneyDebitMinorUnits().orElseThrow());
        assertEquals(commit, ServerShopOfferCommitCodec.decode(
                ServerShopOfferCommitCodec.encode(commit)));
    }

    @Test
    void commitRoundTripsClaimsPendingState() {
        ServerShopOfferListing listing = listing();
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.acquire(
                        id("pending claim request"), PLAYER, "default",
                        listing, listing.acquireOptions().get(0), 1,
                        null, 10_000L, REFERENCE, NOW);
        ServerShopOfferCommit base =
                commit(prepared, Optional.empty(), 1);
        ServerShopOfferCommit pending = ServerShopOfferCommit.create(
                base.requestId(), base.playerId(), base.shopId(),
                base.listingId(), base.optionId(), base.action(),
                base.quantity(), base.offerRevision(),
                base.paymentSource(), base.quotedAt(), true,
                base.valueCommit(), base.stockReservation(),
                base.stockCommit(), base.bundleSavings());

        ServerShopOfferCommit decoded =
                ServerShopOfferCommitCodec.decode(
                        ServerShopOfferCommitCodec.encode(pending));

        assertTrue(decoded.claimsPending());
        assertEquals(pending, decoded);
    }

    @Test
    void codecRejectsTruncationAndTrailingBytes() {
        ServerShopOfferListing listing = listing();
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.acquire(
                        id("codec request"), PLAYER, "default", listing,
                        listing.acquireOptions().get(0), 1, null,
                        10_000L, REFERENCE, NOW);
        byte[] encoded = ServerShopOfferCommitCodec.encode(
                commit(prepared, Optional.empty(), 1));
        byte[] trailing = java.util.Arrays.copyOf(
                encoded, encoded.length + 1);

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferCommitCodec.decode(
                        java.util.Arrays.copyOf(
                                encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferCommitCodec.decode(trailing));
    }

    @Test
    void mixedCartNormalizesMoneyInputsOutputsAndConserves() {
        ServerShopOfferIntentFactory.CartPrepared prepared =
                mixedCart();
        PlayerShopEscrowIntent intent = prepared.intent();

        assertTrue(PlayerShopConservationValidator.validate(
                intent).conserved());
        assertEquals(PlayerShopTradeMethod.MONEY_AND_BARTER,
                intent.tradeMethod());
        assertEquals(1, intent.moneyTransfers().size());
        assertEquals(1_100L,
                intent.moneyTransfers().get(0).amountMinorUnits());
        assertEquals(6, itemTotal(intent,
                PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY,
                "minecraft:emerald"));
        assertEquals(3, itemTotal(intent,
                PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY,
                "minecraft:stick"));
        assertEquals(8, itemTotal(intent,
                PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                "minecraft:diamond"));
        assertEquals(16, itemTotal(intent,
                PlayerShopAssetEndpoint.Kind.ADMIN_MINT,
                "minecraft:apple"));
        assertEquals(2, intent.itemTransfers().stream()
                .filter(transfer -> transfer.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY)
                .count());
        assertEquals(2, intent.claims().size());
    }

    @Test
    void cartCommitRoundTripsCanonicallyWithSavings() {
        ServerShopOfferCartCommit commit = cartCommit();

        byte[] encoded =
                ServerShopOfferCartCommitCodec.encode(commit);
        ServerShopOfferCartCommit decoded =
                ServerShopOfferCartCommitCodec.decode(encoded);

        assertEquals(commit, decoded);
        assertArrayEquals(encoded,
                ServerShopOfferCartCommitCodec.encode(decoded));
        assertEquals(8, ServerShopOfferCartCommit.stockQuantities(
                decoded.lines()).get("bundle"));
        assertTrue(decoded.lines().stream().anyMatch(line ->
                line.savings().isPresent()));
    }

    @Test
    void cartStockEvidenceMatchesByIdentityInsteadOfListPosition() {
        ServerShopOfferCartCommit base = cartCommit();
        String listingFingerprint =
                base.lines().get(0).listingFingerprint();
        List<ServerShopOfferCartCommit.Line> lines = List.of(
                new ServerShopOfferCartCommit.Line(
                        "barter_emerald", "iron_trade", 1, 7L,
                        listingFingerprint, Optional.empty()),
                new ServerShopOfferCartCommit.Line(
                        "free_apple", "claim", 1, 7L,
                        listingFingerprint, Optional.empty()));
        StockKey barter = new StockKey(
                "default", "barter_emerald");
        StockKey free = new StockKey(
                "default", "free_apple");
        StockMutationCommand.ReserveBatch reserve =
                new StockMutationCommand.ReserveBatch(
                        ServerShopOfferCartCommit.stockReserveRequestId(
                                base.requestId()),
                        base.requestId(), List.of(
                        new StockReservationRequest(
                                barter,
                                StockReservationDirection.OUTBOUND,
                                1L, 3L),
                        new StockReservationRequest(
                                free,
                                StockReservationDirection.OUTBOUND,
                                1L, 3L)), NOW);
        StockMutationCommand.ResolveBatch resolution =
                new StockMutationCommand.ResolveBatch(
                        ServerShopOfferCartCommit.stockCommitRequestId(
                                base.requestId()),
                        StockMutationType.COMMIT_BATCH,
                        base.requestId(), List.of(
                        new StockReservationResolution(
                                StockReservationId.forTransaction(
                                        base.requestId(), barter,
                                        StockReservationDirection.OUTBOUND),
                                0L),
                        new StockReservationResolution(
                                StockReservationId.forTransaction(
                                        base.requestId(), free,
                                        StockReservationDirection.OUTBOUND),
                                0L)), NOW.plusSeconds(1));

        assertEquals(barter,
                reserve.reservations().get(0).stockKey());
        assertEquals(StockReservationId.forTransaction(
                        base.requestId(), free,
                        StockReservationDirection.OUTBOUND),
                resolution.reservations().get(0).reservationId());

        ServerShopOfferCartCommit commit =
                ServerShopOfferCartCommit.create(
                        base.requestId(), base.playerId(), base.shopId(),
                        lines, base.paymentSource(), base.quotedAt(),
                        base.valueCommit(), reserve, resolution);
        assertEquals(commit, ServerShopOfferCartCommitCodec.decode(
                ServerShopOfferCartCommitCodec.encode(commit)));
    }

    @Test
    void cartCommitRoundTripsClaimsPendingState() {
        ServerShopOfferCartCommit base = cartCommit();
        ServerShopOfferCartCommit pending =
                ServerShopOfferCartCommit.create(
                        base.requestId(), base.playerId(), base.shopId(),
                        base.lines(), base.paymentSource(), base.quotedAt(),
                        true, base.valueCommit(), base.stockReservation(),
                        base.stockCommit());

        ServerShopOfferCartCommit decoded =
                ServerShopOfferCartCommitCodec.decode(
                        ServerShopOfferCartCommitCodec.encode(pending));

        assertTrue(decoded.claimsPending());
        assertEquals(pending, decoded);
    }

    @Test
    void cartCodecRejectsBadMagicTruncationAndTrailingBytes() {
        byte[] encoded = ServerShopOfferCartCommitCodec.encode(
                cartCommit());
        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 1;
        byte[] truncated = java.util.Arrays.copyOf(
                encoded, encoded.length - 1);
        byte[] trailing = java.util.Arrays.copyOf(
                encoded, encoded.length + 1);

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferCartCommitCodec.decode(badMagic));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferCartCommitCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferCartCommitCodec.decode(trailing));
    }

    @Test
    void savedCommitCachesRemainCanonicalAndReplaySafe() {
        ServerShopOfferCommit commit = commit(
                ServerShopOfferIntentFactory.acquire(
                        id("saved commit cache"), PLAYER, "default",
                        listing(), listing().acquireOptions().get(0),
                        1, null, 10_000L, REFERENCE, NOW),
                Optional.empty(), 1);
        ServerShopOfferCommitSavedData data =
                new ServerShopOfferCommitSavedData();

        assertTrue(data.commit(commit));
        assertFalse(data.commit(commit));
        assertEquals(1L, data.mutationRevision());
        assertEquals(List.of(commit), data.entries());
        assertEquals(commit,
                data.find(commit.requestId()).orElseThrow());

        ServerShopOfferCommitSavedData loaded =
                ServerShopOfferCommitSavedData.load(
                        data.save(new CompoundTag()));
        assertEquals(1L, loaded.mutationRevision());
        assertEquals(List.of(commit), loaded.entries());
    }

    @Test
    void savedCartCommitCachesRemainCanonicalAndReplaySafe() {
        ServerShopOfferCartCommit commit = cartCommit();
        ServerShopOfferCartCommitSavedData data =
                new ServerShopOfferCartCommitSavedData();

        assertTrue(data.commit(commit));
        assertFalse(data.commit(commit));
        assertEquals(1L, data.mutationRevision());
        assertEquals(List.of(commit), data.entries());
        assertEquals(commit,
                data.find(commit.requestId()).orElseThrow());

        ServerShopOfferCartCommitSavedData loaded =
                ServerShopOfferCartCommitSavedData.load(
                        data.save(new CompoundTag()));
        assertEquals(1L, loaded.mutationRevision());
        assertEquals(List.of(commit), loaded.entries());
    }

    @Test
    void singleReplayArchiveCompactsAndSurvivesRestart() {
        ServerShopOfferPreparedSavedData.Entry prepared =
                singlePreparedEntry("single archive first");
        ServerShopOfferCommit first = commit(
                new ServerShopOfferIntentFactory.Prepared(
                        prepared.action(), prepared.listingId(),
                        prepared.optionId(), prepared.offerRevision(),
                        prepared.intent()),
                prepared.paymentSource(), prepared.quantity());
        ServerShopOfferReplayReceipt receipt =
                ServerShopOfferReplayReceipt.single(
                        prepared, first);
        ServerShopOfferCommitSavedData commits =
                new ServerShopOfferCommitSavedData(1, 2);
        ServerShopOfferPreparedSavedData preparedData =
                new ServerShopOfferPreparedSavedData(1, 2);

        assertTrue(commits.commit(first));
        preparedData.prepare(prepared);
        assertTrue(commits.recordReplayReceipt(receipt));
        assertTrue(preparedData.recordReplayReceipt(receipt));
        assertEquals(first.requestId(),
                commits.compactOldestReplay().orElseThrow());
        assertEquals(first.requestId(),
                preparedData.compactOldestReplay().orElseThrow());
        assertEquals(0, commits.size());
        assertEquals(0, preparedData.size());

        ServerShopOfferCommitSavedData loadedCommits =
                ServerShopOfferCommitSavedData.load(
                        commits.save(new CompoundTag()));
        ServerShopOfferPreparedSavedData loadedPrepared =
                ServerShopOfferPreparedSavedData.load(
                        preparedData.save(new CompoundTag()));
        assertEquals(receipt, loadedCommits.findArchived(
                first.requestId()).orElseThrow());
        assertEquals(receipt, loadedPrepared.findArchived(
                first.requestId()).orElseThrow());
        assertFalse(loadedCommits.commit(first));
    }

    @Test
    void cartReplayArchiveCompactsAndSurvivesRestart() {
        ServerShopOfferCartPreparedSavedData.Entry prepared =
                cartPreparedEntry("cart archive first");
        ServerShopOfferCartCommit first =
                cartCommit("cart archive first");
        ServerShopOfferReplayReceipt receipt =
                ServerShopOfferReplayReceipt.cart(
                        prepared, first);
        ServerShopOfferCartCommitSavedData commits =
                new ServerShopOfferCartCommitSavedData(1, 2);
        ServerShopOfferCartPreparedSavedData preparedData =
                new ServerShopOfferCartPreparedSavedData(1, 2);

        assertTrue(commits.commit(first));
        assertTrue(preparedData.prepare(prepared));
        assertTrue(commits.recordReplayReceipt(receipt));
        assertTrue(preparedData.recordReplayReceipt(receipt));
        assertEquals(first.requestId(),
                commits.compactOldestReplay().orElseThrow());
        assertEquals(first.requestId(),
                preparedData.compactOldestReplay().orElseThrow());
        assertEquals(0, commits.size());
        assertEquals(0, preparedData.size());

        ServerShopOfferCartCommitSavedData loadedCommits =
                ServerShopOfferCartCommitSavedData.load(
                        commits.save(new CompoundTag()));
        ServerShopOfferCartPreparedSavedData loadedPrepared =
                ServerShopOfferCartPreparedSavedData.load(
                        preparedData.save(new CompoundTag()));
        assertEquals(receipt, loadedCommits.findArchived(
                first.requestId()).orElseThrow());
        assertEquals(receipt, loadedPrepared.findArchived(
                first.requestId()).orElseThrow());
        assertFalse(loadedCommits.commit(first));
    }

    @Test
    void archiveCapacityFailsClosedWithoutDroppingOldIdentity() {
        ServerShopOfferPreparedSavedData.Entry firstPrepared =
                singlePreparedEntry("archive capacity first");
        ServerShopOfferCommit first = commit(
                new ServerShopOfferIntentFactory.Prepared(
                        firstPrepared.action(),
                        firstPrepared.listingId(),
                        firstPrepared.optionId(),
                        firstPrepared.offerRevision(),
                        firstPrepared.intent()),
                firstPrepared.paymentSource(),
                firstPrepared.quantity());
        ServerShopOfferReplayReceipt firstReceipt =
                ServerShopOfferReplayReceipt.single(
                        firstPrepared, first);
        ServerShopOfferCommitSavedData commits =
                new ServerShopOfferCommitSavedData(1, 1);
        commits.commit(first);
        commits.recordReplayReceipt(firstReceipt);
        commits.compactOldestReplay();

        ServerShopOfferPreparedSavedData.Entry secondPrepared =
                singlePreparedEntry("archive capacity second");
        ServerShopOfferCommit second = commit(
                new ServerShopOfferIntentFactory.Prepared(
                        secondPrepared.action(),
                        secondPrepared.listingId(),
                        secondPrepared.optionId(),
                        secondPrepared.offerRevision(),
                        secondPrepared.intent()),
                secondPrepared.paymentSource(),
                secondPrepared.quantity());
        commits.commit(second);

        assertFalse(commits.canRecordReplayReceipt(
                second.requestId()));
        assertThrows(IllegalStateException.class,
                () -> commits.recordReplayReceipt(
                        ServerShopOfferReplayReceipt.single(
                                secondPrepared, second)));
        assertEquals(firstReceipt, commits.findArchived(
                first.requestId()).orElseThrow());
        assertEquals(second, commits.find(
                second.requestId()).orElseThrow());
    }

    private static ServerShopOfferCommit commit(
            ServerShopOfferIntentFactory.Prepared prepared,
            Optional<PaymentSource> source,
            int quantity
    ) {
        PlayerShopEscrowIntent intent = prepared.intent();
        List<PlayerShopMoneyMutationReceipt> money = intent.moneyTransfers()
                .stream().map(transfer -> moneyReceipt(
                        intent.requestId(), transfer))
                .toList();
        List<PlayerShopItemMutationReceipt> items = intent.itemTransfers()
                .stream().map(transfer -> itemReceipt(
                        intent.requestId(), transfer))
                .toList();
        PlayerShopAtomicCommit value = PlayerShopAtomicCommit.create(
                intent, NOW.plusSeconds(1), money, items, List.of());
        StockReservationDirection direction =
                prepared.action() == OfferAction.ACQUIRE_FROM_SHOP
                        ? StockReservationDirection.OUTBOUND
                        : StockReservationDirection.INBOUND;
        StockKey key = new StockKey("default", prepared.listingId());
        StockMutationCommand.ReserveBatch reserve =
                new StockMutationCommand.ReserveBatch(
                        ServerShopOfferCommit.stockReserveRequestId(
                                intent.requestId()),
                        intent.requestId(), List.of(
                        new StockReservationRequest(key, direction,
                                quantity, 3L)), NOW);
        StockMutationCommand.ResolveBatch resolution =
                new StockMutationCommand.ResolveBatch(
                        ServerShopOfferCommit.stockCommitRequestId(
                                intent.requestId()),
                        StockMutationType.COMMIT_BATCH,
                        intent.requestId(), List.of(
                        new StockReservationResolution(
                                StockReservationId.forTransaction(
                                        intent.requestId(), key,
                                        direction), 0L)),
                        NOW.plusSeconds(1));
        return ServerShopOfferCommit.create(
                intent.requestId(), intent.actorId(), "default",
                prepared.listingId(), prepared.optionId(),
                prepared.action(), quantity, prepared.offerRevision(),
                source, NOW, value, reserve, resolution);
    }

    private static ServerShopOfferIntentFactory.CartPrepared mixedCart() {
        return mixedCart("mixed cart request");
    }

    private static ServerShopOfferIntentFactory.CartPrepared mixedCart(
            String requestKey
    ) {
        ServerShopOfferListing listing = listing();
        List<AcquireOfferOption> options = listing.acquireOptions();
        return ServerShopOfferIntentFactory.acquireCart(
                id(requestKey), PLAYER, "default",
                List.of(
                        new ServerShopOfferIntentFactory.AcquireLine(
                                listing, options.get(0), 2),
                        new ServerShopOfferIntentFactory.AcquireLine(
                                listing, options.get(1), 3),
                        new ServerShopOfferIntentFactory.AcquireLine(
                                listing, options.get(2), 2),
                        new ServerShopOfferIntentFactory.AcquireLine(
                                listing, options.get(3), 1)),
                PaymentSource.WALLET, 10_000L, REFERENCE, NOW);
    }

    private static ServerShopOfferCartCommit cartCommit() {
        return cartCommit("mixed cart request");
    }

    private static ServerShopOfferCartCommit cartCommit(
            String requestKey
    ) {
        ServerShopOfferIntentFactory.CartPrepared prepared =
                mixedCart(requestKey);
        PlayerShopEscrowIntent intent = prepared.intent();
        List<PlayerShopMoneyMutationReceipt> money =
                intent.moneyTransfers().stream()
                        .map(transfer -> moneyReceipt(
                                intent.requestId(), transfer))
                        .toList();
        List<PlayerShopItemMutationReceipt> items =
                intent.itemTransfers().stream()
                        .map(transfer -> itemReceipt(
                                intent.requestId(), transfer))
                        .toList();
        PlayerShopAtomicCommit value = PlayerShopAtomicCommit.create(
                intent, NOW.plusSeconds(1), money, items, List.of());
        ServerShopBundleSavings.Snapshot savings =
                new ServerShopBundleSavings.Snapshot(
                        1_200L, 1_000L, 200L, 1_666L,
                        List.of(new ServerShopBundleSavings
                                .ComparisonRevision(
                                "diamond", "diamond_single",
                                "money", 4L)));
        List<ServerShopOfferCartCommit.Line> lines =
                prepared.lines().stream().map(line ->
                        ServerShopOfferCartCommit.captureLine(
                                line.listing(),
                                line.option().optionId(),
                                line.quantity(),
                                line.option().optionId().equals("money")
                                        ? Optional.of(savings)
                                        : Optional.empty()))
                        .toList();
        StockKey key = new StockKey("default", "bundle");
        StockMutationCommand.ReserveBatch reserve =
                new StockMutationCommand.ReserveBatch(
                        ServerShopOfferCartCommit.stockReserveRequestId(
                                intent.requestId()),
                        intent.requestId(),
                        List.of(new StockReservationRequest(
                                key, StockReservationDirection.OUTBOUND,
                                8, 3L)), NOW);
        StockMutationCommand.ResolveBatch resolution =
                new StockMutationCommand.ResolveBatch(
                        ServerShopOfferCartCommit.stockCommitRequestId(
                                intent.requestId()),
                        StockMutationType.COMMIT_BATCH,
                        intent.requestId(),
                        List.of(new StockReservationResolution(
                                StockReservationId.forTransaction(
                                        intent.requestId(), key,
                                        StockReservationDirection.OUTBOUND),
                                0L)),
                        NOW.plusSeconds(1));
        return ServerShopOfferCartCommit.create(
                intent.requestId(), intent.actorId(), "default",
                lines, Optional.of(PaymentSource.WALLET), NOW,
                value, reserve, resolution);
    }

    private static ServerShopOfferPreparedSavedData.Entry
    singlePreparedEntry(String requestKey) {
        ServerShopOfferListing listing = listing();
        AcquireOfferOption option = listing.acquireOptions().get(0);
        ServerShopOfferIntentFactory.Prepared prepared =
                ServerShopOfferIntentFactory.acquire(
                        id(requestKey), PLAYER, "default",
                        listing, option, 1, null,
                        10_000L, REFERENCE, NOW);
        ServerShopOfferCommit value = commit(
                prepared, Optional.empty(), 1);
        return new ServerShopOfferPreparedSavedData.Entry(
                prepared.intent().requestId(), PLAYER, "default",
                listing.listingId(), option.optionId(),
                OfferAction.ACQUIRE_FROM_SHOP, 1,
                listing.revision(), Optional.empty(), NOW,
                listing, prepared.intent(),
                value.stockReservation());
    }

    private static ServerShopOfferCartPreparedSavedData.Entry
    cartPreparedEntry(String requestKey) {
        ServerShopOfferIntentFactory.CartPrepared prepared =
                mixedCart(requestKey);
        ServerShopOfferCartCommit commit =
                cartCommit(requestKey);
        List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines =
                new ArrayList<>();
        for (int index = 0;
             index < prepared.lines().size(); index++) {
            ServerShopOfferIntentFactory.AcquireLine line =
                    prepared.lines().get(index);
            lines.add(new ServerShopOfferCartPreparedSavedData.QuotedLine(
                    line.listing(), line.option().optionId(),
                    line.quantity(), 3L,
                    commit.lines().get(index).savings()));
        }
        List<ServerShopOfferCartService.LineRequest> requests =
                lines.stream().map(line ->
                        new ServerShopOfferCartService.LineRequest(
                                line.listing().listingId(),
                                line.optionId(), line.quantity(),
                                line.listing().revision())).toList();
        ServerShopOfferCartService.Request request =
                new ServerShopOfferCartService.Request(
                        prepared.intent().requestId(), PLAYER,
                        "default", requests,
                        Optional.of(PaymentSource.WALLET), 0);
        return new ServerShopOfferCartPreparedSavedData.Entry(
                request.requestId(), PLAYER, "default",
                request.fingerprint(),
                Optional.of(PaymentSource.WALLET), NOW,
                lines, prepared.intent(),
                commit.stockReservation());
    }

    private static int itemTotal(
            PlayerShopEscrowIntent intent,
            PlayerShopAssetEndpoint.Kind source,
            String itemId
    ) {
        return intent.itemTransfers().stream()
                .filter(transfer -> transfer.source().kind() == source)
                .filter(transfer -> transfer.lot().itemId()
                        .equals(itemId))
                .mapToInt(transfer -> transfer.lot().quantity())
                .sum();
    }

    private static PlayerShopMoneyMutationReceipt moneyReceipt(
            UUID requestId,
            PlayerShopMoneyTransfer transfer
    ) {
        return PlayerShopMoneyMutationReceipt.applied(
                requestId,
                transfer,
                transfer.sourceBalanceAfterMinorUnits(),
                transfer.destinationBalanceAfterMinorUnits(),
                bytes("money evidence"));
    }

    private static PlayerShopItemMutationReceipt itemReceipt(
            UUID requestId,
            PlayerShopItemTransfer transfer
    ) {
        PlayerShopItemMutationReceipt.FundingKind kind =
                transfer.source().kind()
                        == PlayerShopAssetEndpoint.Kind.ADMIN_MINT
                        ? PlayerShopItemMutationReceipt.FundingKind
                        .ADMIN_MINT
                        : PlayerShopItemMutationReceipt.FundingKind
                        .INVENTORY_REMOVAL;
        return PlayerShopItemMutationReceipt.funded(
                requestId, transfer, kind,
                bytes("item evidence " + transfer.transferId()));
    }

    private static PlayerShopEscrowIntent withSelection(
            PlayerShopEscrowIntent source,
            PlayerShopOfferSelection selection
    ) {
        return PlayerShopEscrowIntent.prepared(
                source.requestId(), source.actorId(),
                source.ownerId(), source.shopIdentity(),
                source.operation(), source.tradeMethod(),
                source.paymentSource(), source.requestedUnits(),
                source.quoteCreatedAt(), source.listing(),
                source.moneyTransfers(), source.itemTransfers(),
                source.claims(), source.storageMutations(),
                Optional.of(selection));
    }

    private static ServerShopOfferListing listing() {
        OfferItemComponent diamond = new OfferItemComponent(
                "diamond", "minecraft:diamond", 1, "");
        OfferItemComponent apple = new OfferItemComponent(
                "apple", "minecraft:apple", 2, "");
        OfferItemComponent emerald = new OfferItemComponent(
                "emerald", "minecraft:emerald", 2, "");
        OfferItemComponent stick = new OfferItemComponent(
                "stick", "minecraft:stick", 1, "");
        List<AcquireOfferOption> acquire = new ArrayList<>();
        acquire.add(AcquireOfferOption.free("free"));
        acquire.add(AcquireOfferOption.money("money", 300L));
        acquire.add(new AcquireOfferOption(
                "barter", "Items", false, false, 0L,
                List.of(emerald, stick), 1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), ""));
        acquire.add(new AcquireOfferOption(
                "compound", "Money and Items", false, true, 200L,
                List.of(emerald, stick), 1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), ""));
        SellOfferOption sell = new SellOfferOption(
                "sell_bundle", "Sell bundle",
                List.of(diamond, apple), 150L, 100L,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
        return new ServerShopOfferListing(
                "bundle", 7L, "Bundle", "Two outputs", "all",
                "minecraft:diamond", "", true, 0L, "",
                List.of(diamond, apple), acquire, List.of(sell),
                OfferStockPolicy.limited(100L, 0L),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
