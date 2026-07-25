package com.enviouse.futureshops.gametest;

import com.enviouse.futureshops.catalog.CategoryDef;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferCartService;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopOfferService;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockSeedCapture;
import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockSeedSnapshot;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("futureshops")
@PrefixGameTestTemplate(false)
public final class ServerShopOfferGameTests {
    private static final String SHOP_ID = "gametest_offers";
    private static final String FREE_LISTING = "free_apple";
    private static final String FREE_OPTION = "claim";
    private static final String BARTER_LISTING = "barter_emerald";
    private static final String BARTER_OPTION = "iron_trade";
    private static final String FINITE_LISTING = "finite_diamond";
    private static final String CLAIM_LISTING = "pending_claim";
    private static final String BATCH = "futureshops.offer_service";
    private static List<ShopDefinition> originalDefinitions = List.of();

    private ServerShopOfferGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void installFixture(ServerLevel level) {
        MinecraftServer server = level.getServer();
        EscrowRuntimeService runtime = EscrowRuntimeManager.requireReady();
        originalDefinitions =
                new ArrayList<>(ShopCatalog.snapshot().values());
        install(server, runtime,
                withFixture(originalDefinitions, fixtureShop()));
    }

    @AfterBatch(batch = BATCH)
    public static void restoreCatalog(ServerLevel level) {
        install(level.getServer(), EscrowRuntimeManager.requireReady(),
                originalDefinitions);
        originalDefinitions = List.of();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/mobs/empty",
            batch = BATCH,
            timeoutTicks = 100
    )
    public static void explicitFreeAcquireReplaysAndConflicts(
            GameTestHelper helper
    ) {
        ConnectedPlayer connected = connectPlayer(helper, "free_offer");
        try {
            ServerPlayer player = connected.player();
            ShopSessionManager.open(player.getUUID(), SHOP_ID);

            ServerShopOfferListing listing = ShopCatalog.getOffer(
                    SHOP_ID, FREE_LISTING).orElseThrow();
            ServerShopOfferService.Request request =
                    new ServerShopOfferService.Request(
                            UUID.randomUUID(), player.getUUID(),
                            SHOP_ID, FREE_LISTING, FREE_OPTION,
                            OfferAction.ACQUIRE_FROM_SHOP, 1,
                            listing.revision(), Optional.empty(), 1);
            ServerShopOfferService.Result result =
                    ServerShopOfferService.execute(player, request);

            helper.assertTrue(
                    result.status() == ServerShopOfferService.Status.SUCCESS,
                    "Free offer did not return SUCCESS");
            helper.assertTrue(!result.replayed(),
                    "First free offer execution was marked as replay");
            helper.assertTrue(player.getInventory().countItem(Items.APPLE)
                            == 1,
                    "Free offer did not deliver exactly one apple");
            ServerShopOfferService.Result replay =
                    ServerShopOfferService.execute(player, request);
            helper.assertTrue(
                    replay.status() == ServerShopOfferService.Status.SUCCESS
                            && replay.replayed(),
                    "Free offer replay was not an exact success");
            helper.assertTrue(player.getInventory().countItem(Items.APPLE)
                            == 1,
                    "Free offer replay duplicated the output");

            ServerShopOfferService.Request changed =
                    new ServerShopOfferService.Request(
                            request.requestId(), player.getUUID(),
                            SHOP_ID, FREE_LISTING, FREE_OPTION,
                            OfferAction.ACQUIRE_FROM_SHOP, 2,
                            listing.revision(), Optional.empty(), 1);
            ServerShopOfferService.Result conflict =
                    ServerShopOfferService.execute(player, changed);
            helper.assertTrue(
                    conflict.status()
                            == ServerShopOfferService.Status.CONFLICT,
                    "Changed free offer replay did not conflict");
            helper.assertTrue(player.getInventory().countItem(Items.APPLE)
                            == 1,
                    "Changed replay mutated the inventory");
            helper.succeed();
        } finally {
            disconnect(helper, connected);
        }
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/mobs/empty",
            batch = BATCH,
            timeoutTicks = 100
    )
    public static void mixedCartReplaysAndConflicts(
            GameTestHelper helper
    ) {
        ConnectedPlayer connected = connectPlayer(helper, "cart_offer");
        try {
            ServerPlayer player = connected.player();
            ShopSessionManager.open(player.getUUID(), SHOP_ID);
            player.getInventory().items.set(
                    0, new ItemStack(Items.IRON_INGOT, 2));
            ServerShopOfferListing free = offer(FREE_LISTING);
            ServerShopOfferListing barter = offer(BARTER_LISTING);
            UUID requestId = UUID.randomUUID();
            ServerShopOfferCartService.Request request =
                    new ServerShopOfferCartService.Request(
                            requestId, player.getUUID(), SHOP_ID,
                            List.of(
                                    new ServerShopOfferCartService.LineRequest(
                                            FREE_LISTING, FREE_OPTION, 1,
                                            free.revision()),
                                    new ServerShopOfferCartService.LineRequest(
                                            BARTER_LISTING, BARTER_OPTION, 1,
                                            barter.revision())),
                            Optional.empty(), 2);

            ServerShopOfferCartService.Result result =
                    ServerShopOfferCartService.execute(player, request);
            helper.assertTrue(
                    result.status() == ServerShopOfferService.Status.SUCCESS,
                    "Mixed free and barter cart returned " + result.status()
                            + ", commit "
                            + (result.commit() != null)
                            + ", value commit "
                            + (result.valueCommit() != null));
            assertCartInventory(helper, player);

            ServerShopOfferCartService.Result replay =
                    ServerShopOfferCartService.execute(player, request);
            helper.assertTrue(
                    replay.status() == ServerShopOfferService.Status.SUCCESS
                            && replay.replayed(),
                    "Mixed cart replay was not an exact success");
            assertCartInventory(helper, player);

            ServerShopOfferCartService.Request changed =
                    new ServerShopOfferCartService.Request(
                            requestId, player.getUUID(), SHOP_ID,
                            List.of(
                                    new ServerShopOfferCartService.LineRequest(
                                            FREE_LISTING, FREE_OPTION, 1,
                                            free.revision()),
                                    new ServerShopOfferCartService.LineRequest(
                                            BARTER_LISTING, BARTER_OPTION, 2,
                                            barter.revision())),
                            Optional.empty(), 2);
            ServerShopOfferCartService.Result conflict =
                    ServerShopOfferCartService.execute(player, changed);
            helper.assertTrue(
                    conflict.status()
                            == ServerShopOfferService.Status.CONFLICT,
                    "Changed cart replay did not conflict");
            assertCartInventory(helper, player);
            helper.succeed();
        } finally {
            disconnect(helper, connected);
        }
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/mobs/empty",
            batch = BATCH,
            timeoutTicks = 100
    )
    public static void finiteStockExhaustionReturnsOutOfStock(
            GameTestHelper helper
    ) {
        ConnectedPlayer connected = connectPlayer(helper, "finite_offer");
        try {
            ServerPlayer player = connected.player();
            ShopSessionManager.open(player.getUUID(), SHOP_ID);
            ServerShopOfferListing listing = offer(FINITE_LISTING);
            ServerShopOfferService.Result acquired =
                    ServerShopOfferService.execute(player,
                            request(player, listing, FINITE_LISTING,
                                    FREE_OPTION, 2, 3));
            helper.assertTrue(
                    acquired.status()
                            == ServerShopOfferService.Status.SUCCESS,
                    "Finite offer did not consume its exact stock");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)
                            == 2,
                    "Finite offer did not deliver two diamonds");
            long remaining = EscrowRuntimeManager.requireReady()
                    .stockListing(new StockKey(
                            SHOP_ID, FINITE_LISTING))
                    .orElseThrow().availableQuantity();
            helper.assertTrue(remaining == 0L,
                    "Finite stock did not reach zero");

            ServerShopOfferService.Result exhausted =
                    ServerShopOfferService.execute(player,
                            request(player, listing, FINITE_LISTING,
                                    FREE_OPTION, 1, 4));
            helper.assertTrue(
                    exhausted.status()
                            == ServerShopOfferService.Status.OUT_OF_STOCK,
                    "Exhausted finite offer did not return OUT_OF_STOCK");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)
                            == 2,
                    "Out of stock request delivered an item");
            helper.succeed();
        } finally {
            disconnect(helper, connected);
        }
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/mobs/empty",
            batch = BATCH,
            timeoutTicks = 100
    )
    public static void fullInventoryCreatesPendingClaim(
            GameTestHelper helper
    ) {
        ConnectedPlayer connected = connectPlayer(helper, "claim_offer");
        try {
            ServerPlayer player = connected.player();
            ShopSessionManager.open(player.getUUID(), SHOP_ID);
            for (int index = 0;
                 index < player.getInventory().items.size(); index++) {
                player.getInventory().items.set(index,
                        new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().offhand.set(
                    0, new ItemStack(Items.COBBLESTONE, 64));
            ServerShopOfferListing listing = offer(CLAIM_LISTING);
            ServerShopOfferService.Result result =
                    ServerShopOfferService.execute(player,
                            request(player, listing, CLAIM_LISTING,
                                    FREE_OPTION, 1, 5));

            helper.assertTrue(
                    result.status()
                            == ServerShopOfferService.Status.CLAIMS_PENDING,
                    "Full inventory did not return CLAIMS_PENDING");
            helper.assertTrue(result.commit() != null
                            && result.commit().claimsPending(),
                    "Full inventory commit did not retain a pending claim");
            helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT)
                            == 0,
                    "Pending claim output was inserted into a full inventory");
            helper.succeed();
        } finally {
            disconnect(helper, connected);
        }
    }

    private static ShopDefinition fixtureShop() {
        List<ServerShopOfferListing> offers = List.of(
                offer(FREE_LISTING, "Free Apple", "minecraft:apple",
                        AcquireOfferOption.free(FREE_OPTION),
                        OfferStockPolicy.unlimited()),
                offer(BARTER_LISTING, "Barter Emerald",
                        "minecraft:emerald",
                        new AcquireOfferOption(
                                BARTER_OPTION, "Two Iron", false,
                                false, 0L,
                                List.of(new OfferItemComponent(
                                        "iron", "minecraft:iron_ingot",
                                        2, "")),
                                1, OfferLimitPolicy.defaults(),
                                OfferSchedule.always(), ""),
                        OfferStockPolicy.unlimited()),
                offer(FINITE_LISTING, "Finite Diamond",
                        "minecraft:diamond",
                        AcquireOfferOption.free(FREE_OPTION),
                        OfferStockPolicy.limited(2L, 0L)),
                offer(CLAIM_LISTING, "Pending Claim",
                        "minecraft:gold_ingot",
                        AcquireOfferOption.free(FREE_OPTION),
                        OfferStockPolicy.unlimited()));
        List<ItemDef> items = List.of(
                item(FREE_LISTING, "minecraft:apple", -1),
                item(BARTER_LISTING, "minecraft:emerald", -1),
                item(FINITE_LISTING, "minecraft:diamond", 2),
                item(CLAIM_LISTING, "minecraft:gold_ingot", -1));
        return new ShopDefinition(
                2, SHOP_ID, "GameTest Offers",
                List.of(new CategoryDef("all", "All", 0)),
                items, List.of(), List.of(), offers);
    }

    private static ServerShopOfferListing offer(
            String listingId,
            String displayName,
            String itemId,
            AcquireOfferOption option,
            OfferStockPolicy stockPolicy
    ) {
        ServerShopOfferListing unversioned =
                new ServerShopOfferListing(
                        listingId, 0L, displayName, "",
                        "all", itemId, "", true, 0L, "",
                        List.of(new OfferItemComponent(
                                "output", itemId, 1, "")),
                        List.of(option), List.of(), stockPolicy,
                        OfferLimitPolicy.defaults(), OfferSchedule.always(),
                        List.of());
        return unversioned.withRevision(
                ServerShopOfferRevision.compute(unversioned));
    }

    private static ServerShopOfferListing offer(String listingId) {
        return ShopCatalog.getOffer(SHOP_ID, listingId).orElseThrow();
    }

    private static ItemDef item(
            String listingId,
            String itemId,
            int stock
    ) {
        return new ItemDef(
                listingId, itemId, listingId,
                0L, 0L, stock, false, "all", 0, "", 0L);
    }

    private static ServerShopOfferService.Request request(
            ServerPlayer player,
            ServerShopOfferListing listing,
            String listingId,
            String optionId,
            int quantity,
            int responseToken
    ) {
        return new ServerShopOfferService.Request(
                UUID.randomUUID(), player.getUUID(), SHOP_ID,
                listingId, optionId, OfferAction.ACQUIRE_FROM_SHOP,
                quantity, listing.revision(), Optional.empty(),
                responseToken);
    }

    private static void assertCartInventory(
            GameTestHelper helper,
            ServerPlayer player
    ) {
        helper.assertTrue(player.getInventory().countItem(Items.APPLE)
                        == 1,
                "Mixed cart did not preserve one free output");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD)
                        == 1,
                "Mixed cart did not preserve one barter output");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT)
                        == 0,
                "Mixed cart did not consume exactly two iron");
    }

    private static List<ShopDefinition> withFixture(
            List<ShopDefinition> original,
            ShopDefinition fixture
    ) {
        List<ShopDefinition> definitions = new ArrayList<>(original);
        definitions.removeIf(value -> value.shopId().equals(
                fixture.shopId()));
        definitions.add(fixture);
        return List.copyOf(definitions);
    }

    private static void install(
            MinecraftServer server,
            EscrowRuntimeService runtime,
            List<ShopDefinition> definitions
    ) {
        CatalogStockSeedSnapshot target =
                CatalogStockSeedCapture.captureConfiguration(definitions);
        runtime.commitStockMutation(new StockMutationCommand.Reconcile(
                UUID.randomUUID(), target.definitions(),
                target.fingerprint(), nextMutationTime(runtime)));
        ShopCatalog.publishDurableDefinitions(server, definitions);
    }

    private static Instant nextMutationTime(EscrowRuntimeService runtime) {
        Instant latest = runtime.stockSnapshot().listings().values().stream()
                .map(value -> value.updatedAt())
                .max(Instant::compareTo).orElse(Instant.EPOCH);
        Instant now = Instant.now();
        return now.isAfter(latest) ? now : latest.plusNanos(1L);
    }

    private static ConnectedPlayer connectPlayer(
            GameTestHelper helper,
            String name
    ) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(
                server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        return new ConnectedPlayer(player, channel);
    }

    private static void disconnect(
            GameTestHelper helper,
            ConnectedPlayer connected
    ) {
        ServerPlayer player = connected.player();
        ShopSessionManager.close(player.getUUID());
        helper.getLevel().getServer().getPlayerList().remove(player);
        connected.channel().finishAndReleaseAll();
    }

    private record ConnectedPlayer(
            ServerPlayer player,
            EmbeddedChannel channel
    ) {
    }
}
