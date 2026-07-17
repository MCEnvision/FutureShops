package com.enviouse.futureshops.client;

import com.enviouse.futureshops.client.screen.BarterScreen;
import com.enviouse.futureshops.client.screen.CartScreen;
import com.enviouse.futureshops.client.screen.FranchiseManagementScreen;
import com.enviouse.futureshops.client.screen.ItemDetailScreen;
import com.enviouse.futureshops.client.screen.PlayerShopBarterScreen;
import com.enviouse.futureshops.client.screen.PlayerShopBlockScreen;
import com.enviouse.futureshops.client.screen.PlayerStorefrontScreen;
import com.enviouse.futureshops.client.screen.BalTopOverviewScreen;
import com.enviouse.futureshops.client.screen.AtmScreen;
import com.enviouse.futureshops.client.screen.BalanceOverviewScreen;
import com.enviouse.futureshops.client.screen.ShopMainScreen;
import com.enviouse.futureshops.client.screen.ShopScreenMarker;
import com.enviouse.futureshops.client.screen.ShopUiUtil;
import com.enviouse.futureshops.client.screen.TransactionHistoryScreen;
import com.enviouse.futureshops.network.packets.S2CAdminEditAckPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
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
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ShopClientPacketHandler {

    private ShopClientPacketHandler() {
    }

    public static void handleAtmData(S2CAtmDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof AtmScreen atm) {
                atm.applyData(packet);
            } else {
                mc.setScreen(new AtmScreen(mc.screen, packet));
            }
        });
    }

    public static void handleAtmResult(S2CAtmResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ShopClientState.setCurrentBalanceMinorUnits(packet.balanceMinor());
            if (mc.screen instanceof AtmScreen atm) {
                atm.applyResult(packet);
            }
        });
    }

    /**
     * Applies the full shop catalog + balance state received from the server.
     *
     * <p>When {@code packet.forceOpen()} is true (the explicit open path), the GUI is opened if
     * it is not already showing. When false (stock refresh, post-transaction sync, admin reload),
     * the packet only updates an already-open shop-flow screen and is otherwise discarded — so a
     * player who closed the shop but still has a live server-side session does not get the GUI
     * forced back open by background server activity.
     */
    public static void handleShopData(S2CShopDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            boolean shopMainOpen = mc.screen instanceof ShopMainScreen;
            boolean shopFlowOpen = mc.screen instanceof ShopScreenMarker;
            if (!packet.forceOpen() && !shopFlowOpen) {
                // Silent refresh and the player isn't viewing the shop — nothing to do.
                return;
            }

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
                    packet.nearbyShops(),
                    packet.canEdit());
            ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(packet.shopId()));
            if (shopMainOpen) {
                // Update in-place — preserves nearbyMode, scroll, tabs.
                ((ShopMainScreen) mc.screen).refreshAfterDataUpdate();
            } else if (packet.forceOpen()) {
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
        mc.execute(() -> {
            // Resolve the parent to the SHOP ROOT, never a sibling overview — otherwise a sibling
            // (Leaders/Franchise) re-opening this dashboard via a packet would set its parent to
            // that sibling, and Back would ping-pong Profile↔Leaders forever (only Close escaped).
            net.minecraft.client.gui.screens.Screen current = mc.screen;
            net.minecraft.client.gui.screens.Screen parent =
                    current instanceof BalanceOverviewScreen b ? b.getParent()
                    : (current instanceof BalTopOverviewScreen || current instanceof FranchiseManagementScreen)
                            ? new ShopMainScreen()
                    : current;
            mc.setScreen(new BalanceOverviewScreen(
                parent,
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
                packet.alerts()));
        });
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
                        packet.popularItemNbtJson(),
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
                    packet.popularItemNbtJson(),
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
                    packet.franchiseName(),
                    packet.placedByCreative(),
                    packet.adminShopMode(),
                    packet.floatingIconMode(),
                    packet.floatingIconItem(),
                    packet.linkedStorages(),
                    packet.savedConfigNames());
            if (packet.owner()) {
                // Owner MANAGE view is unchanged (Phase 5 rebuild). Data resends (buy/config acks)
                // update an already-open block screen in place; it reads live client state.
                if (!(mc.screen instanceof PlayerShopBlockScreen)) {
                    // Pass current screen as parent for back-button navigation (Items 4, 9)
                    mc.setScreen(new PlayerShopBlockScreen(mc.screen));
                }
            } else {
                // Visitor STOREFRONT view. A resend while the storefront is already open (e.g. a
                // buy ack re-sending the shop payload) refreshes it in place — preserving the
                // browse/detail state and any open confirmation modal — instead of popping a fresh
                // screen over it.
                if (mc.screen instanceof PlayerStorefrontScreen storefront) {
                    storefront.refreshAfterDataUpdate();
                } else if (mc.screen instanceof com.enviouse.futureshops.client.screen.PlayerShopBarterScreen
                        || mc.screen instanceof com.enviouse.futureshops.client.screen.PlayerShopSellScreen) {
                    // A barter / sell-to-shop just completed and re-sent the payload. That child
                    // screen will return to its parent storefront (which reads live client state)
                    // when the player backs out — don't stack a fresh storefront over it.
                } else {
                    mc.setScreen(new PlayerStorefrontScreen(mc.screen));
                }
            }
        });
    }

    public static void handlePlayerShopResult(S2CPlayerShopResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            PlayerShopClientState.setResultCode(packet.code());

            // Update ConfirmationModal on open screens
            boolean isSuccess = packet.success();
            // chatMessage is legacy/optional (the server now sends localized chat via
            // sendSystemMessage); when absent, localize the result CODE on the client
            // instead of showing the raw wire code (e.g. "INSUFFICIENT_FUNDS").
            String msg = packet.chatMessage() != null && !packet.chatMessage().isBlank()
                    ? packet.chatMessage()
                    : Component.translatable("gui.futureshops.player_shop.result."
                            + packet.code().toLowerCase(java.util.Locale.ROOT)).getString();
            if (mc.screen instanceof PlayerShopBlockScreen psScreen) {
                psScreen.onTransactionResult(isSuccess, msg);
            } else if (mc.screen instanceof PlayerShopBarterScreen barterScreen) {
                barterScreen.onTransactionResult(isSuccess, msg);
            } else if (mc.screen instanceof PlayerStorefrontScreen storefront) {
                storefront.onTransactionResult(isSuccess, msg);
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

            if (packet.success()) {
                // Refresh owned counts so the Sell button lights up immediately after buying,
                // without the player having to close and reopen the item.
                ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(
                        ShopClientState.getActiveShopId()));
            }
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

            if (packet.success()) {
                ShopPackets.CHANNEL.sendToServer(new com.enviouse.futureshops.network.packets.C2SInventorySyncPacket(
                        ShopClientState.getActiveShopId()));
            }

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

    /**
     * Handles the ack for an in-GUI admin shop edit — localizes the string code and shows it in
     * the status strip (the shop screen renders {@link ShopClientState#getStatus()} every frame,
     * so no screen plumbing is needed). {@code arg} feeds the key's %s slot (e.g. ADDED count).
     */
    public static void handleAdminEditAck(S2CAdminEditAckPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // A freshly-created barter target opens its ingredient editor directly, keyed by the
            // new listingId the server returned in `arg` (the OP then adds ingredients one at a time).
            if (packet.success() && "BARTER_TARGET_CREATED".equals(packet.code()) && !packet.arg().isBlank()) {
                net.minecraft.client.gui.screens.Screen back = mc.screen;
                mc.setScreen(new com.enviouse.futureshops.client.screen.BarterRecipeEditorScreen(back, packet.arg()));
                ShopClientState.setStatus(
                        Component.translatable("gui.futureshops.admin_edit.result.barter_target_created"), true);
                return;
            }
            if (packet.success() && "BARTER_TARGETS_CREATED".equals(packet.code()) && !packet.arg().isBlank()) {
                java.util.List<String> listingIds = java.util.Arrays.stream(packet.arg().split(","))
                        .map(String::trim)
                        .filter(id -> !id.isBlank())
                        .toList();
                if (!listingIds.isEmpty()) {
                    net.minecraft.client.gui.screens.Screen back = mc.screen;
                    mc.setScreen(new com.enviouse.futureshops.client.screen.BarterRecipeEditorScreen(back, listingIds));
                    ShopClientState.setStatus(
                            Component.translatable("gui.futureshops.admin_edit.result.barter_targets_created",
                                    listingIds.size()), true);
                    return;
                }
            }
            ShopClientState.setStatus(
                    Component.translatable(
                            "gui.futureshops.admin_edit.result." + packet.code().toLowerCase(java.util.Locale.ROOT),
                            packet.arg()),
                    packet.success());
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

    /**
     * Maps a typed server result code to the chat-log lang key used for buy/sell/barter
     * transaction feedback. The switch is exhaustive over {@link ShopResultCode} so adding
     * a new constant without a matching case is a compile error (or, for codes that don't
     * apply to transaction chat, an intentional fall-through to the generic server-error
     * key via the {@code default} branch).
     */
    private static String errorKey(ShopResultCode code) {
        return switch (code) {
            case OUT_OF_STOCK -> "command.futureshops.buy.error.out_of_stock";
            case INVENTORY_FULL -> "command.futureshops.buy.error.inventory_full";
            case SHOP_CLOSED -> "command.futureshops.buy.error.shop_closed";
            case COOLDOWN -> "command.futureshops.buy.error.cooldown";
            case INVALID_ITEM, INVALID_RECIPE -> "command.futureshops.buy.error.invalid_item";
            case INSUFFICIENT_FUNDS -> "command.futureshops.error.insufficient_funds";
            case INVALID_AMOUNT -> "command.futureshops.error.invalid_amount";
            case MAX_BALANCE_EXCEEDED -> "command.futureshops.error.max_balance_exceeded";
            case MISSING_ITEMS -> "gui.futureshops.status.sell.error.missing_items";
            case MISSING_INGREDIENTS -> "gui.futureshops.status.barter.error.missing_ingredients";
            // Codes not specific to transaction chat — fall back to the generic server-error
            // line so nothing ever renders the raw enum name to the player.
            case OK, BOUGHT, SOLD, CONFIG_SAVED, CONFIG_COPIED, DEPARTMENT_SET, PROMO_SET, PROMO_CLEARED,
                 LINKED, BARTER_LINKED, LINK_PENDING, LINK_NONE, BARTER_LINK_PENDING,
                 DESC_PENDING, LISTING_DESC_PENDING, NOT_OWNER, HOLD_ITEM, LISTING_LIMIT,
                 NO_LISTING, UNCONFIGURED, NOT_SINGLE_MODE, USE_SET_DEPARTMENT_ACTION,
                 NO_LINK, BAD_LINK_TARGET, RS_NOT_CONTROLLER, STORAGE_FULL,
                 MISSING_BARTER_ITEMS, ROLLBACK, NOTHING_TO_CLAIM, CLAIM_FAILED,
                 PROMO_FAILED, NO_CLIPBOARD, INVALID_REQUEST, INVALID_TARGET, SERVER_ERROR,
                 CANCELLED_BY_EVENT, SHOP_OUT_OF_MONEY, BUYBACK_CAP_REACHED
                    -> "command.futureshops.error.server";
        };
    }
}
