package com.enviouse.futureshopsp.gametest;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.catalog.ShopDefinitionLoader;
import com.enviouse.futureshopsp.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshopsp.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshopsp.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshopsp.server.pricing.DynamicPricingSavedData;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.AdminCartVerificationService;
import com.enviouse.futureshopsp.server.shop.ShopDataService;
import com.enviouse.futureshopsp.server.transaction.ShopBuyService;
import com.enviouse.futureshopsp.server.transaction.ShopSellService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(Futureshops.MODID)
@PrefixGameTestTemplate(false)
public final class DynamicPricingGameTests {
    private static final String IRON = "minecraft:iron_ingot";

    private DynamicPricingGameTests() {
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void purchasesAndCartUseAdjustedPrices(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_buy", player -> {
            var server = player.getServer();
            DynamicPricingSavedData.get(server).getState("pricing_buy", IRON).currentPriceMinor = 150L;
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.single("pricing_buy", IRON, 2));
            helper.assertTrue(balance(player) == 9_700L, "Two iron must debit the adjusted price of 300");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 2, "The buy must deliver two iron");
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.cart("pricing_buy", List.of(
                    new C2SBuyRequestPacket.LineItem(IRON, 2),
                    new C2SBuyRequestPacket.LineItem("minecraft:cobblestone", 3)), revision(player)));
            helper.assertTrue(balance(player) == 9_280L, "Cart checkout must debit 300 plus 120");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 4, "The cart must deliver its iron");
            helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 3, "The cart must deliver its cobblestone");
        });
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void salesUseAdjustedPrices(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_sell", player -> {
            DynamicPricingSavedData.get(player.getServer()).getState("pricing_sell", IRON).currentPriceMinor = 150L;
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
            ShopSellService.handleSellRequest(player, new C2SSellRequestPacket("pricing_sell", IRON, 4));
            helper.assertTrue(balance(player) == 10_300L, "Four iron must credit 300 while retaining the configured price ratio");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 0, "The sale must consume four iron");
        });
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void catalogUsesAdjustedPrices(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_catalog", player -> {
            var server = player.getServer();
            DynamicPricingSavedData.get(server).getState("pricing_catalog", IRON).currentPriceMinor = 150L;
            var iron = ShopCatalog.buildItems("pricing_catalog", server).stream()
                    .filter(item -> item.listingId().equals(IRON)).findFirst().orElseThrow();
            helper.assertTrue(iron.buyPrice() == 150L && iron.sellPrice() == 75L,
                    "The client catalog must contain the same adjusted buy and sell prices as transactions");
            Config.dynamicPricingEnabled = false;
            var fixed = ShopCatalog.buildItems("pricing_catalog", server).stream()
                    .filter(item -> item.listingId().equals(IRON)).findFirst().orElseThrow();
            helper.assertTrue(fixed.buyPrice() == 100L && fixed.sellPrice() == 50L,
                    "Disabling dynamic pricing must restore configured prices without removing saved state");
        });
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void activityChangesTheNextTransaction(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_activity", player -> {
            var server = player.getServer();
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.single("pricing_activity", IRON, 64));
            helper.assertTrue(balance(player) == 3_600L, "The initial purchase must use the base price");
            var state = DynamicPricingSavedData.get(server).getState("pricing_activity", IRON);
            helper.assertTrue(state.buysSinceLastCalc == 64, "A successful buy must record its quantity");
            DynamicPricingEngine.reset();
            long previousRevision = revision(player);
            for (int tick = 0; tick < 399; tick++) DynamicPricingEngine.onServerTick(server);
            helper.assertTrue(state.currentPriceMinor == 0L, "Prices must wait for the configured 400 tick interval");
            DynamicPricingEngine.onServerTick(server);
            helper.assertTrue(revision(player) > previousRevision, "A recalculation must refresh the open shop snapshot");
            helper.assertTrue(state.currentPriceMinor == 136L && state.buysSinceLastCalc == 0,
                    "The reported configuration must recalculate demand and clear activity counters");
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.single("pricing_activity", IRON, 1, revision(player)));
            helper.assertTrue(balance(player) == 3_464L, "The next purchase must debit the recalculated 136");
            ShopSellService.handleSellRequest(player, new C2SSellRequestPacket("pricing_activity", IRON, 64, revision(player)));
            helper.assertTrue(balance(player) == 7_816L, "The sale must credit 64 times the adjusted sell price of 68");
            helper.assertTrue(state.sellsSinceLastCalc == 64, "A successful sale must record supply");
            DynamicPricingEngine.recalculate(server);
            helper.assertTrue(state.currentPriceMinor == 109L, "Supply must reduce the next price");
            ShopSellService.handleSellRequest(player, new C2SSellRequestPacket("pricing_activity", IRON, 1, revision(player)));
            helper.assertTrue(balance(player) == 7_871L, "The next sale must use the newly adjusted sell price of 55");
        });
    }

    private static void withFixture(GameTestHelper helper, String shopId, Consumer<ServerPlayer> assertions) throws Exception {
        helper.assertTrue("internal".equals(BalanceManager.getLifecycleSnapshotOrUnresolved().providerId()),
                "Dynamic pricing regression tests require the internal provider");
        var server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        boolean enabled = Config.dynamicPricingEnabled;
        int interval = Config.dynamicPricingRecalcIntervalSec;
        double demand = Config.dynamicPricingDemandWeight;
        double supply = Config.dynamicPricingSupplyWeight;
        double decay = Config.dynamicPricingDecayRate;
        double increase = Config.dynamicPricingMaxIncreasePct;
        double decrease = Config.dynamicPricingMaxDecreasePct;
        Path fixture = Files.createTempFile(ShopDefinitionLoader.adminShopPath().getParent(), "pricing_regression_", ".json");
        try {
            Files.writeString(fixture, """
                    {"shopId":"%s","items":[
                      {"itemId":"minecraft:iron_ingot","buyPrice":100,"sellPrice":50,"stock":-1},
                      {"itemId":"minecraft:cobblestone","buyPrice":40,"sellPrice":10,"stock":-1},
                      {"id":"iron_variant","itemId":"minecraft:iron_ingot","buyPrice":100,"sellPrice":50,"stock":-1},
                      {"itemId":"minecraft:gold_ingot","buyPrice":100,"sellPrice":50,"stock":-1},
                      {"itemId":"minecraft:dirt","buyPrice":0,"sellPrice":20,"stock":-1}
                    ],"promos":[
                      {"promoId":"gold_sale","promoType":"PERCENTAGE","targetItemId":"minecraft:gold_ingot","discountValue":10}
                    ]}
                    """.formatted(shopId));
            ShopCatalog.reload(server);
            for (var item : ShopCatalog.get(shopId).orElseThrow().items()) {
                var state = DynamicPricingSavedData.get(server).getState(shopId, item.resolutionKey());
                state.currentPriceMinor = 0L;
                state.resetCounters();
            }
            Config.dynamicPricingEnabled = true;
            Config.dynamicPricingRecalcIntervalSec = 20;
            Config.dynamicPricingDemandWeight = 0.6D;
            Config.dynamicPricingSupplyWeight = 0.4D;
            Config.dynamicPricingDecayRate = 0.98D;
            Config.dynamicPricingMaxIncreasePct = 200D;
            Config.dynamicPricingMaxDecreasePct = 40D;
            player.getInventory().clearContent();
            helper.assertTrue(BalanceManager.setInternalBalance(player.getUUID(), 10_000L).confirmed(),
                    "The fixture must initialize the balance");
            ShopSessionManager.open(player.getUUID(), shopId);
            assertions.accept(player);
        } finally {
            ShopSessionManager.close(player.getUUID());
            player.getInventory().clearContent();
            Config.dynamicPricingEnabled = enabled;
            Config.dynamicPricingRecalcIntervalSec = interval;
            Config.dynamicPricingDemandWeight = demand;
            Config.dynamicPricingSupplyWeight = supply;
            Config.dynamicPricingDecayRate = decay;
            Config.dynamicPricingMaxIncreasePct = increase;
            Config.dynamicPricingMaxDecreasePct = decrease;
            DynamicPricingEngine.reset();
            Files.delete(fixture);
            ShopCatalog.reload(server);
        }
        helper.succeed();
    }

    private static long balance(ServerPlayer player) {
        return BalanceManager.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
    }

    private static long revision(ServerPlayer player) {
        return ShopSessionManager.get(player.getUUID()).orElseThrow().snapshotRevision();
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void promotionsAndCartVerificationUseTheSameQuote(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_promos", player -> {
            var server = player.getServer();
            var data = DynamicPricingSavedData.get(server);
            data.getState("pricing_promos", IRON).currentPriceMinor = 150L;
            data.getState("pricing_promos", "minecraft:gold_ingot").currentPriceMinor = 150L;
            helper.assertTrue(ShopCatalog.setRuntimePromo("pricing_promos", IRON, "PERCENTAGE",
                    20D, 0, 0, 0, 0, false), "The promotion fixture must be accepted");
            var items = ShopCatalog.buildItems("pricing_promos", server);
            var iron = items.stream().filter(item -> item.listingId().equals(IRON)).findFirst().orElseThrow();
            var gold = items.stream().filter(item -> item.listingId().equals("minecraft:gold_ingot")).findFirst().orElseThrow();
            helper.assertTrue(iron.buyPrice() == 150L && iron.promoPrice() == 120L && iron.sellPrice() == 75L,
                    "Runtime promotions must apply after dynamic adjustment without discounting sell payouts");
            helper.assertTrue(gold.buyPrice() == 150L && gold.promoPrice() == 135L,
                    "Static promotions must also apply after dynamic adjustment");
            var accepted = AdminCartVerificationService.evaluate(server, "pricing_promos",
                    List.of(new C2SVerifyAdminCartPacket.AdminCartLine(IRON, 2, 120L)));
            var outdated = AdminCartVerificationService.evaluate(server, "pricing_promos",
                    List.of(new C2SVerifyAdminCartPacket.AdminCartLine(IRON, 2, 80L)));
            helper.assertTrue(accepted.allOk(), "The displayed dynamic promo quote must pass cart verification");
            helper.assertTrue(!outdated.allOk() && outdated.warnings().getFirst().warningCode().equals("PRICE_CHANGED"),
                    "The static promo quote must be rejected as outdated");
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.cart("pricing_promos", List.of(
                    new C2SBuyRequestPacket.LineItem(IRON, 2),
                    new C2SBuyRequestPacket.LineItem("minecraft:gold_ingot", 2))));
            helper.assertTrue(balance(player) == 9_490L, "Checkout must debit the same dynamic promotional prices");
            ShopCatalog.setRuntimePromo("pricing_promos", IRON, "BUY_X_GET_Y", 0D, 2, 1, 0, 0, false);
            helper.assertTrue(ShopCatalog.calculateLineCost("pricing_promos", IRON, 3, server) == 300L,
                    "Quantity promotions must retain adjusted pricing and free item grouping");
        });
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void variantsAndSellOnlyListingsStayIndependent(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_variants", player -> {
            var server = player.getServer();
            var data = DynamicPricingSavedData.get(server);
            data.getState("pricing_variants", IRON).currentPriceMinor = 150L;
            data.getState("pricing_variants", "minecraft:dirt").currentPriceMinor = 12L;
            helper.assertTrue(ShopCatalog.getEffectiveBuyPrice("pricing_variants", "iron_variant", server) == 100L,
                    "Two listings for the same registry item must have independent activity");
            helper.assertTrue(ShopCatalog.getEffectiveSellPrice("pricing_variants", "iron_variant", server) == 50L,
                    "Variant sell prices must use the listing key");
            player.getInventory().add(new ItemStack(Items.DIRT, 2));
            ShopSellService.handleSellRequest(player, new C2SSellRequestPacket("pricing_variants", "minecraft:dirt", 2));
            helper.assertTrue(balance(player) == 10_024L, "Sell only listings must use their own reference price");
            helper.assertTrue(ShopCatalog.getEffectiveBuyPrice("pricing_variants", "minecraft:dirt", server) == 0L,
                    "A dynamic sell only listing must not become buyable");
            Config.dynamicPricingEnabled = false;
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.single("pricing_variants", IRON, 1, revision(player)));
            helper.assertTrue(balance(player) == 9_924L, "Disabled dynamic pricing must debit the original price");
            ShopSellService.handleSellRequest(player, new C2SSellRequestPacket("pricing_variants", IRON, 1, revision(player)));
            helper.assertTrue(balance(player) == 9_974L, "Disabled dynamic pricing must credit the original price");
        });
    }

    @GameTest(template = "empty", batch = "dynamic_pricing", timeoutTicks = 100)
    public static void outdatedConfirmationCannotMoveMoneyOrItems(GameTestHelper helper) throws Exception {
        withFixture(helper, "pricing_snapshot", player -> {
            DynamicPricingSavedData.get(player.getServer()).getState("pricing_snapshot", IRON).currentPriceMinor = 150L;
            ShopDataService.sendShopData(player, "pricing_snapshot", false, false);
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.single("pricing_snapshot", IRON, 1, 0L));
            helper.assertTrue(balance(player) == 10_000L && player.getInventory().countItem(Items.IRON_INGOT) == 0,
                    "A confirmation from before the price refresh must not debit or deliver items");
            long revision = ShopSessionManager.get(player.getUUID()).orElseThrow().snapshotRevision();
            ShopBuyService.handleBuyRequest(player, C2SBuyRequestPacket.single("pricing_snapshot", IRON, 1, revision));
            helper.assertTrue(balance(player) == 9_850L && player.getInventory().countItem(Items.IRON_INGOT) == 1,
                    "A fresh confirmation must buy at the adjusted price");
        });
    }
}
