package com.enviouse.futureshopsp.vaultproof;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.api.ShopModAPI;
import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.init.ModBlocks;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshopsp.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import com.enviouse.futureshopsp.server.transaction.ShopBuyService;
import com.enviouse.futureshopsp.server.transaction.ShopSellService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.slf4j.Logger;

/** Disposable exact hybrid surface proof for the separately installed vault registrant. */
@GameTestHolder(Futureshops.MODID)
public final class VaultSurfaceGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private VaultSurfaceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultServerShopSellUsesProvider(GameTestHelper helper) {
        if (!requireReady(helper)) {
            return;
        }
        ItemDef item = ShopCatalog.getItem("default", "minecraft:iron_ingot").orElse(null);
        if (item == null || item.sellPriceMinorUnits() <= 0L) {
            item = ShopCatalog.getItem("default", "minecraft:diamond").orElse(null);
        }
        helper.assertTrue(item != null && item.sellPriceMinorUnits() > 0L,
                "the exact hybrid catalog must expose one positive sell listing");
        Item resolved = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemId()));
        helper.assertTrue(resolved != null && resolved != Items.AIR,
                "the exact hybrid sell listing must resolve to a registered item");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(new ItemStack(resolved, 1));
        int itemsBefore = player.getInventory().countItem(resolved);
        long balanceBefore = BalanceManager.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        int stockBefore = ShopCatalog.getCurrentStock("default", item.resolutionKey());
        ShopSessionManager.open(player.getUUID(), "default");
        try {
            ShopSellService.handleSellRequest(player,
                    new C2SSellRequestPacket("default", item.resolutionKey(), 1));
        } finally {
            ShopSessionManager.close(player.getUUID());
        }

        long balanceAfter = BalanceManager.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(player.getInventory().countItem(resolved) == itemsBefore - 1,
                "vault server shop sell must remove the sold item after provider confirmation");
        helper.assertTrue(balanceAfter == balanceBefore + item.sellPriceMinorUnits(),
                "vault server shop sell must credit the exact provider amount");
        helper.assertTrue(ShopCatalog.getCurrentStock("default", item.resolutionKey()) == stockBefore + 1,
                "vault server shop sell must update the catalog stock after provider confirmation");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "vault server shop sell must release custody");
        LOGGER.info("FutureShops Vault surface route=server_shop_sell provider=vault status=CONFIRMED amount={} balance_delta={} item_delta={} stock_delta={} custody_incomplete={}",
                item.sellPriceMinorUnits(), balanceAfter - balanceBefore,
                player.getInventory().countItem(resolved) - itemsBefore,
                ShopCatalog.getCurrentStock("default", item.resolutionKey()) - stockBefore,
                BalanceManager.getCustodyStore().hasIncompleteRecords());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultPlayerShopBuyUsesEscrow(GameTestHelper helper) {
        if (!requireReady(helper)) {
            return;
        }
        BlockPos relativeShopPos = new BlockPos(1, 1, 1);
        BlockPos shopPos = helper.absolutePos(relativeShopPos);
        helper.setBlock(relativeShopPos, ModBlocks.SHOP_BLOCK.get().defaultBlockState());
        ShopBlockEntity shop = helper.getBlockEntity(relativeShopPos);
        helper.assertTrue(shop != null, "the exact hybrid player shop block must be available");
        shop.setPlacedByCreative(true);
        helper.assertTrue(shop.setAdminShopMode(true), "the disposable shop must enter admin mode");
        shop.setOwnerUuid(java.util.UUID.fromString("00000000-0000-0000-0000-000000000240"));
        int listingIndex = shop.addOrSelectListing("minecraft:diamond");
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        helper.assertTrue(listing != null, "the disposable admin shop must have a listing");
        listing.setTradeMode(ShopBlockEntity.TradeMode.MONEY);
        listing.setMoneyPriceMinor(1L);
        listing.setBaseQuantity(1);

        ServerPlayer buyer = helper.makeMockServerPlayerInLevel();
        long balanceBefore = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        int diamondsBefore = buyer.getInventory().countItem(Items.DIAMOND);
        PlayerShopBlockService.buy(buyer, shopPos, listingIndex, 1, "MONEY");

        long balanceAfter = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(buyer.getInventory().countItem(Items.DIAMOND) == diamondsBefore + 1,
                "vault player shop buy must deliver the item after provider confirmation");
        helper.assertTrue(balanceAfter == balanceBefore - 1L,
                "vault player shop buy must debit the exact provider amount");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "vault player shop buy must claim custody");
        LOGGER.info("FutureShops Vault surface route=player_shop_buy provider=vault status=CONFIRMED amount=1 balance_delta={} item_delta={} custody_incomplete={}",
                balanceAfter - balanceBefore,
                buyer.getInventory().countItem(Items.DIAMOND) - diamondsBefore,
                BalanceManager.getCustodyStore().hasIncompleteRecords());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultServerShopBuyUsesProvider(GameTestHelper helper) {
        if (!requireReady(helper)) {
            return;
        }
        ItemDef item = ShopCatalog.getItem("default", "minecraft:diamond").orElse(null);
        helper.assertTrue(item != null && item.buyPriceMinorUnits() > 0L,
                "the exact hybrid catalog must expose one positive buy listing");
        Item resolved = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemId()));
        helper.assertTrue(resolved != null && resolved != Items.AIR,
                "the exact hybrid buy listing must resolve to a registered item");

        ServerPlayer buyer = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(BalanceManager.deposit(buyer.getUUID(), item.buyPriceMinorUnits() + 100L).success(),
                "the exact hybrid buy fixture must seed sufficient provider funds");
        long balanceBefore = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        int itemsBefore = buyer.getInventory().countItem(resolved);
        ShopSessionManager.open(buyer.getUUID(), "default");
        try {
            ShopBuyService.handleBuyRequest(buyer,
                    C2SBuyRequestPacket.single("default", item.resolutionKey(), 1));
        } finally {
            ShopSessionManager.close(buyer.getUUID());
        }

        long balanceAfter = BalanceManager.queryBalance(buyer.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(buyer.getInventory().countItem(resolved) == itemsBefore + 1,
                "vault server shop buy must deliver the item after provider confirmation");
        helper.assertTrue(balanceAfter == balanceBefore - item.buyPriceMinorUnits(),
                "vault server shop buy must debit the exact provider amount");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "vault server shop buy must claim custody");
        LOGGER.info("FutureShops Vault surface route=server_shop_buy provider=vault status=CONFIRMED amount={} balance_delta={} item_delta={} custody_incomplete={}",
                item.buyPriceMinorUnits(), balanceAfter - balanceBefore,
                buyer.getInventory().countItem(resolved) - itemsBefore,
                BalanceManager.getCustodyStore().hasIncompleteRecords());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultCartAndPayUseProvider(GameTestHelper helper) {
        if (!requireReady(helper)) {
            return;
        }
        ServerPlayer cartPlayer = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(BalanceManager.deposit(cartPlayer.getUUID(), 1_000L).success(),
                "the cart fixture must seed the exact provider account through a durable deposit");
        long cartBefore = BalanceManager.queryBalance(cartPlayer.getUUID()).value().orElseThrow().balanceMinorUnits();
        int diamondsBefore = cartPlayer.getInventory().countItem(Items.DIAMOND);
        ShopSessionManager.open(cartPlayer.getUUID(), "default");
        try {
            ShopBuyService.handleBuyRequest(cartPlayer,
                    C2SBuyRequestPacket.cart("default",
                            java.util.List.of(new C2SBuyRequestPacket.LineItem("minecraft:diamond", 1))));
        } finally {
            ShopSessionManager.close(cartPlayer.getUUID());
        }
        long cartAfter = BalanceManager.queryBalance(cartPlayer.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(cartPlayer.getInventory().countItem(Items.DIAMOND) == diamondsBefore + 1,
                "vault cart buy must deliver the prepared reward");
        helper.assertTrue(cartAfter == cartBefore - 500L,
                "vault cart buy must debit the aggregate provider price");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "vault cart buy must claim delivery custody");

        ServerPlayer payer = helper.makeMockServerPlayerInLevel();
        ServerPlayer recipient = helper.makeMockServerPlayerInLevel();
        long payerBefore = BalanceManager.queryBalance(payer.getUUID()).value().orElseThrow().balanceMinorUnits();
        long recipientBefore = BalanceManager.queryBalance(recipient.getUUID()).value().orElseThrow().balanceMinorUnits();
        com.enviouse.futureshopsp.server.economy.TransactionResult transfer =
                BalanceManager.transfer(payer.getUUID(), recipient.getUUID(), 25L);
        helper.assertTrue(transfer.success(), "vault pay transfer must confirm through the provider");
        helper.assertTrue(BalanceManager.queryBalance(payer.getUUID()).value().orElseThrow().balanceMinorUnits()
                        == payerBefore - 25L,
                "vault pay transfer must debit the payer once");
        helper.assertTrue(BalanceManager.queryBalance(recipient.getUUID()).value().orElseThrow().balanceMinorUnits()
                        == recipientBefore + 25L,
                "vault pay transfer must credit the recipient once");
        LOGGER.info("FutureShops Vault surface route=cart_buy_and_pay provider=vault cart_status=CONFIRMED cart_amount=500 cart_balance_delta={} cart_item_delta={} pay_status={} pay_amount=25 pay_source_delta={} pay_target_delta={} custody_incomplete={}",
                cartAfter - cartBefore,
                cartPlayer.getInventory().countItem(Items.DIAMOND) - diamondsBefore,
                transfer.success() ? "CONFIRMED" : transfer.errorCode(),
                BalanceManager.queryBalance(payer.getUUID()).value().orElseThrow().balanceMinorUnits() - payerBefore,
                BalanceManager.queryBalance(recipient.getUUID()).value().orElseThrow().balanceMinorUnits() - recipientBefore,
                BalanceManager.getCustodyStore().hasIncompleteRecords());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultPublicApiUsesProvider(GameTestHelper helper) {
        if (!requireReady(helper)) {
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        long before = ShopModAPI.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        var withdrawal = ShopModAPI.withdraw(player.getUUID(), 2L);
        var deposit = ShopModAPI.deposit(player.getUUID(), 3L);
        long after = ShopModAPI.queryBalance(player.getUUID()).value().orElseThrow().balanceMinorUnits();
        helper.assertTrue(withdrawal.success() && deposit.success(),
                "vault public economy API must confirm withdraw and deposit");
        helper.assertTrue(after == before + 1L,
                "vault public economy API must preserve exact provider deltas");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "vault public economy API must leave no incomplete custody");
        LOGGER.info("FutureShops Vault surface route=public_api provider=vault status=CONFIRMED withdrawal=2 deposit=3 balance_delta={} custody_incomplete={}",
                after - before, BalanceManager.getCustodyStore().hasIncompleteRecords());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultPhysicalMoneyRoutesRefuse(GameTestHelper helper) {
        if (!requireReady(helper)) {
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack money = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, money);
        int moneyBefore = money.getCount();
        InteractionResultHolder<ItemStack> result = ModItems.MONEY_ITEM.get()
                .use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!result.getResult().consumesAction(),
                "vault money item use must refuse because physical money is internal only");
        helper.assertTrue(money.getCount() == moneyBefore,
                "vault money item refusal must preserve the item stack");

        int itemCountBefore = player.getInventory().countItem(ModItems.MONEY_ITEM.get());
        player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack(), "withdraw 1");
        player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack(), "deposit");
        helper.assertTrue(player.getInventory().countItem(ModItems.MONEY_ITEM.get()) == itemCountBefore,
                "vault physical commands must not mint or consume internal money");
        helper.assertTrue(!BalanceManager.getCustodyStore().hasIncompleteRecords(),
                "vault physical money refusal must not create custody");
        LOGGER.info("FutureShops Vault surface route=physical_money provider=vault status=REFUSED reason=INTERNAL_ONLY item_consumed={} command_item_delta={} custody_incomplete={}",
                money.getCount() != moneyBefore,
                player.getInventory().countItem(ModItems.MONEY_ITEM.get()) - itemCountBefore,
                BalanceManager.getCustodyStore().hasIncompleteRecords());
        helper.succeed();
    }

    private static boolean requireReady(GameTestHelper helper) {
        var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
        if (!"vault".equals(lifecycle.providerId())
                || lifecycle.lifecycle() != com.enviouse.futureshopsp.api.economy.ProviderLifecycle.READY) {
            helper.fail("the exact vault proof registrant must resolve READY before surface tests");
            return false;
        }
        return true;
    }
}
