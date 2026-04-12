package com.enviouse.futureshops.client;

import com.enviouse.futureshops.client.screen.PlayerShopBlockScreen;
import com.enviouse.futureshops.client.screen.CartScreen;
import com.enviouse.futureshops.client.screen.BalTopOverviewScreen;
import com.enviouse.futureshops.client.screen.BalanceOverviewScreen;
import com.enviouse.futureshops.client.screen.ShopMainScreen;
import com.enviouse.futureshops.client.screen.ShopScreenMarker;
import com.enviouse.futureshops.client.screen.ShopUiUtil;
import com.enviouse.futureshops.client.screen.TransactionHistoryScreen;
import com.enviouse.futureshops.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshops.network.packets.S2CBalanceUiPacket;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CBarterResponsePacket;
import com.enviouse.futureshops.network.packets.S2CBuyResponsePacket;
import com.enviouse.futureshops.network.packets.S2CForceClosePacket;
import com.enviouse.futureshops.network.packets.S2CHistoryResponsePacket;
import com.enviouse.futureshops.network.packets.S2CInventorySyncPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.network.packets.S2CSellResponsePacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ShopClientPacketHandler {

    private ShopClientPacketHandler() {
    }

    /** Applies the full shop catalog + balance state received from the server, then opens the GUI. */
    public static void handleShopData(S2CShopDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.applyShopData(
                    packet.shopId(),
                    packet.balanceMinorUnits(),
                    packet.currencyName(),
                    packet.currencyDecimals(),
                    packet.categories(),
                    packet.items(),
                    packet.promos(),
                    packet.barterRecipes());
            ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(packet.shopId()));
            if (!(mc.screen instanceof ShopScreenMarker)) {
                mc.setScreen(new ShopMainScreen());
            }
        });
    }

    public static void handleInventorySync(S2CInventorySyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!packet.shopId().equals(ShopClientState.getActiveShopId())) {
                return;
            }
            ShopClientState.applyOwnedCounts(packet.itemCounts());
        });
    }

    public static void handleBalanceUi(S2CBalanceUiPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new BalanceOverviewScreen(
                packet.balanceMinorUnits(),
                packet.currencyName(),
                packet.currencyDecimals())));
    }

    public static void handleBalTopUi(S2CBalTopUiPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof BalTopOverviewScreen screen) {
                screen.updatePage(packet.page(), packet.totalPages(), packet.entries());
                return;
            }
            mc.setScreen(new BalTopOverviewScreen(
                    packet.page(),
                    packet.totalPages(),
                    packet.entries(),
                    packet.currencyName(),
                    packet.currencyDecimals()));
        });
    }

    public static void handleHistoryResponse(S2CHistoryResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!packet.shopId().equals(ShopClientState.getActiveShopId())) {
                return;
            }
            if (mc.screen instanceof TransactionHistoryScreen historyScreen) {
                if (!historyScreen.applyHistoryResponse(packet.page(), packet.totalPages(), packet.filter())) {
                    return;
                }
            }
            ShopClientState.applyHistoryPage(packet.entries());
        });
    }

    public static void handlePlayerShopData(S2CPlayerShopDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            PlayerShopClientState.apply(
                    packet.shopPos(),
                    packet.owner(),
                    packet.ownerName(),
                    packet.listedItemId(),
                    packet.tradeMode(),
                    packet.moneyPriceMinor(),
                    packet.barterItemId(),
                    packet.barterItemCount(),
                    packet.stock(),
                    packet.linked(),
                    packet.pendingSettlementMinor(),
                    packet.lifetimeRevenueMinor(),
                    packet.recentRevenueRows());
            if (!(mc.screen instanceof PlayerShopBlockScreen)) {
                mc.setScreen(new PlayerShopBlockScreen());
            }
        });
    }

    public static void handlePlayerShopResult(S2CPlayerShopResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> PlayerShopClientState.setResultCode(packet.code()));
    }

    public static void handleSettlementHistory(S2CSettlementHistoryPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!packet.shopPos().equals(PlayerShopClientState.shopPos())) {
                return;
            }
            PlayerShopClientState.applySettlementHistory(packet.page(), packet.totalPages(), packet.rows());
        });
    }

    public static void handleBuyResponse(S2CBuyResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setCurrentBalanceMinorUnits(packet.resultingBalanceMinorUnits());
            ShopClientState.setStatus(buildBuyMessage(packet), packet.success());

            if (packet.success() && packet.cartCheckout()) {
                ShopClientState.clearCart();
            }
        });
    }

    public static void handleSellResponse(S2CSellResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setCurrentBalanceMinorUnits(packet.resultingBalanceMinorUnits());
            ShopClientState.setStatus(buildSellMessage(packet), packet.success());
        });
    }

    public static void handleBarterResponse(S2CBarterResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ShopClientState.setStatus(buildBarterMessage(packet), packet.success()));
    }

    /**
     * Handles a server-initiated force-close.
     * Resets client state and closes any open shop screen.
     */
    public static void handleForceClose(S2CForceClosePacket ignoredPacket) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.reset();
            if (mc.screen instanceof ShopScreenMarker) {
                mc.setScreen(null);
            }
        });
    }

    private static Component buildBuyMessage(S2CBuyResponsePacket packet) {
        if (packet.success()) {
            return Component.translatable(
                    packet.cartCheckout()
                            ? "gui.futureshops.status.buy.cart.success"
                            : "gui.futureshops.status.buy.single.success",
                    packet.totalQuantity(),
                    ShopUiUtil.formatMinorUnits(packet.totalMinorUnits()));
        }
        return Component.translatable(errorKey(packet.errorCode()));
    }

    private static Component buildSellMessage(S2CSellResponsePacket packet) {
        if (packet.success()) {
            return Component.translatable(
                    "gui.futureshops.status.sell.success",
                    packet.quantity(),
                    ShopUiUtil.formatMinorUnits(packet.totalMinorUnits()));
        }
        return Component.translatable(errorKey(packet.errorCode()));
    }

    private static Component buildBarterMessage(S2CBarterResponsePacket packet) {
        if (packet.success()) {
            return Component.translatable(
                    "gui.futureshops.status.barter.success",
                    packet.outputQuantity(),
                    packet.multiplier());
        }
        return Component.translatable(errorKey(packet.errorCode()));
    }

    private static String errorKey(String errorCode) {
        return switch (errorCode) {
            case "OUT_OF_STOCK" -> "command.futureshops.buy.error.out_of_stock";
            case "INVENTORY_FULL" -> "command.futureshops.buy.error.inventory_full";
            case "SHOP_CLOSED" -> "command.futureshops.buy.error.shop_closed";
            case "COOLDOWN" -> "command.futureshops.buy.error.cooldown";
            case "INVALID_ITEM", "INVALID_RECIPE" -> "command.futureshops.buy.error.invalid_item";
            case "INSUFFICIENT_FUNDS" -> "command.futureshops.error.insufficient_funds";
            case "MISSING_ITEMS" -> "gui.futureshops.status.sell.error.missing_items";
            case "MISSING_INGREDIENTS" -> "gui.futureshops.status.barter.error.missing_ingredients";
            default -> "command.futureshops.error.server";
        };
    }
}
