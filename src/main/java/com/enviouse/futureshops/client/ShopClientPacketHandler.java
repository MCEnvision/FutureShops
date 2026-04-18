package com.enviouse.futureshops.client;

import com.enviouse.futureshops.client.screen.BarterScreen;
import com.enviouse.futureshops.client.screen.CartScreen;
import com.enviouse.futureshops.client.screen.FranchiseManagementScreen;
import com.enviouse.futureshops.client.screen.ItemDetailScreen;
import com.enviouse.futureshops.client.screen.PlayerShopBarterScreen;
import com.enviouse.futureshops.client.screen.PlayerShopBlockScreen;
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
import com.enviouse.futureshops.network.packets.S2CFranchiseDataPacket;
import com.enviouse.futureshops.network.packets.S2CHistoryResponsePacket;
import com.enviouse.futureshops.network.packets.S2CInventorySyncPacket;
import com.enviouse.futureshops.network.packets.S2CLocalShopsPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.network.packets.S2CSellResponsePacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
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
                    packet.barterRecipes(),
                    packet.adminShopEnabled(),
                    packet.nearbyShops());
            ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(packet.shopId()));
            // If already on ShopMainScreen, update in-place (preserves nearbyMode, scroll, etc.)
            if (mc.screen instanceof ShopMainScreen existing) {
                existing.refreshAfterDataUpdate();
            } else {
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
                packet.playerUuid(),
                packet.playerName(),
                packet.balanceMinorUnits(),
                packet.currencyName(),
                packet.currencyDecimals(),
                packet.totalRevenueMinor(),
                packet.pendingSettlementMinor(),
                packet.shopCount(),
                packet.listingCount(),
                packet.totalStock(),
                packet.lowSupplyCount(),
                packet.shopSummaries(),
                packet.alerts())));
    }

    public static void handleBalTopUi(S2CBalTopUiPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof BalTopOverviewScreen screen) {
                screen.updatePage(
                        packet.page(),
                        packet.totalPages(),
                        packet.entries(),
                        packet.activityLeaderUuid(),
                        packet.activityLeaderName(),
                        packet.activityLeaderCount(),
                        packet.topSellerUuid(),
                        packet.topSellerName(),
                        packet.topSellerCount(),
                        packet.popularItemId(),
                        packet.popularItemTrades(),
                        packet.popularItemQuantity(),
                        packet.franchises());
                return;
            }
            mc.setScreen(new BalTopOverviewScreen(
                    packet.page(),
                    packet.totalPages(),
                    packet.entries(),
                    packet.currencyName(),
                    packet.currencyDecimals(),
                    packet.activityLeaderUuid(),
                    packet.activityLeaderName(),
                    packet.activityLeaderCount(),
                    packet.topSellerUuid(),
                    packet.topSellerName(),
                    packet.topSellerCount(),
                    packet.popularItemId(),
                    packet.popularItemTrades(),
                    packet.popularItemQuantity(),
                    packet.franchises()));
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
                    packet.ownerUuid(),
                    packet.ownerName(),
                    packet.listings(),
                    packet.linked(),
                    packet.pendingSettlementMinor(),
                    packet.lifetimeRevenueMinor(),
                    packet.recentRevenueRows(),
                    packet.shopName(),
                    packet.singleItemMode(),
                    packet.barterStorageSame(),
                    packet.description(),
                    packet.franchiseName());
            if (!(mc.screen instanceof PlayerShopBlockScreen)) {
                // Pass current screen as parent for back-button navigation (Items 4, 9)
                mc.setScreen(new PlayerShopBlockScreen(mc.screen));
            }
        });
    }

    public static void handlePlayerShopResult(S2CPlayerShopResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            PlayerShopClientState.setResultCode(packet.code());

            // Update ConfirmationModal on open screens
            boolean isSuccess = packet.success();
            String msg = packet.chatMessage() != null && !packet.chatMessage().isBlank() ? packet.chatMessage() : packet.code();
            if (mc.screen instanceof PlayerShopBlockScreen psScreen) {
                psScreen.onTransactionResult(isSuccess, msg);
            } else if (mc.screen instanceof PlayerShopBarterScreen barterScreen) {
                barterScreen.onTransactionResult(isSuccess, msg);
            }

            // Item 18: On barter/storage failure, close the UI and show a chat message
            String code = packet.code();
            if (!packet.success() && ("STORAGE_FULL".equals(code) || "ROLLBACK".equals(code) || "MISSING_BARTER_ITEMS".equals(code))) {
                if (mc.screen instanceof ShopScreenMarker) {
                    mc.setScreen(null);
                }
                if (mc.player != null && packet.chatMessage() != null && !packet.chatMessage().isBlank()) {
                    mc.player.sendSystemMessage(Component.literal(packet.chatMessage()));
                }
            }
        });
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

            // Update ConfirmationModal if ItemDetailScreen is open
            if (mc.screen instanceof ItemDetailScreen detailScreen) {
                detailScreen.onTransactionResult(packet.success(), buildBuyMessage(packet).getString());
            } else if (mc.screen instanceof CartScreen cartScreen) {
                cartScreen.onTransactionResult(packet.success(), buildBuyMessage(packet).getString());
            }
        });
    }

    public static void handleSellResponse(S2CSellResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setCurrentBalanceMinorUnits(packet.resultingBalanceMinorUnits());
            ShopClientState.setStatus(buildSellMessage(packet), packet.success());

            // Update ConfirmationModal if ItemDetailScreen is open
            if (mc.screen instanceof ItemDetailScreen detailScreen) {
                detailScreen.onTransactionResult(packet.success(), buildSellMessage(packet).getString());
            }
        });
    }

    public static void handleBarterResponse(S2CBarterResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setStatus(buildBarterMessage(packet), packet.success());

            // Update ConfirmationModal if BarterScreen is open
            if (mc.screen instanceof BarterScreen barterScreen) {
                barterScreen.onTransactionResult(packet.success(), buildBarterMessage(packet).getString());
            }
        });
    }

    /**
     * Handles a server-initiated force-close.
     * Resets client state and closes any open shop screen.
     */
    public static void handleForceClose(S2CForceClosePacket ignoredPacket) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.reset();
            PlayerShopCartState.clear(); // Item 34: Clear player shop cart on disconnect
            if (mc.screen instanceof ShopScreenMarker) {
                mc.setScreen(null);
            }
        });
    }

    /**
     * Handles aggregated local shop data for the franchise/owner browsing UI.
     */
    public static void handleLocalShops(S2CLocalShopsPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.applyLocalShops(packet.owners());
            if (mc.screen instanceof ShopMainScreen existing) {
                existing.refreshAfterDataUpdate();
            }
        });
    }

    /**
     * Handles franchise data — opens or updates the FranchiseManagementScreen.
     */
    public static void handleFranchiseData(S2CFranchiseDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof FranchiseManagementScreen screen) {
                screen.updateData(packet.inFranchise(), packet.franchiseId(), packet.franchiseName(),
                        packet.isLeader(), packet.members(), packet.hasPendingInvite(), packet.pendingFranchiseName());
            } else {
                mc.setScreen(new FranchiseManagementScreen(
                        packet.inFranchise(), packet.franchiseId(), packet.franchiseName(),
                        packet.isLeader(), packet.members(), packet.hasPendingInvite(), packet.pendingFranchiseName()));
            }
        });
    }

    /**
     * Handles cart verification response — stores warnings in client state
     * for the cart screen to display.
     */
    public static void handleCartVerification(S2CVerifyCartResponsePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ShopClientState.applyCartVerification(packet.allOk(), packet.warnings()));
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
