package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.data.NearbyShopEntry;
import com.enviouse.futureshops.event.ShopOpenEvent;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.session.ShopSession;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared authoritative helper for assembling and sending shop data packets.
 * Centralizes the logic used by commands, open-shop packets, reload handling, and buy refreshes.
 */
public final class ShopDataService {
    private ShopDataService() {
    }

    public static String resolveShopId(String requestedShopId) {
        String raw = requestedShopId == null || requestedShopId.isBlank() ? "default" : requestedShopId;
        return ShopCatalog.getOrDefault(raw).map(ShopDefinition::shopId).orElse("default");
    }

    public static void openShop(ServerPlayer player, String requestedShopId) {
        String shopId = resolveShopId(requestedShopId);

        // Fire cancellable ShopOpenEvent (spec §33)
        ShopOpenEvent event = new ShopOpenEvent(player, shopId);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;

        ShopSessionManager.open(player.getUUID(), shopId);
        sendShopData(player, shopId, true);
        InventorySyncService.sendOwnedCounts(player, shopId);
    }

    public static void sendShopData(ServerPlayer player, String requestedShopId) {
        sendShopData(player, requestedShopId, true);
    }

    /**
     * Sends shop data. When {@code includeNearbyShops} is false, the expensive nearby-shop
     * scan is skipped — use this for post-transaction refreshes where nearby shops haven't changed.
     */
    public static void sendShopData(ServerPlayer player, String requestedShopId, boolean includeNearbyShops) {
        String shopId = resolveShopId(requestedShopId);
        EconomyProvider provider = BalanceManager.getProvider();
        long balance = provider.getBalance(player.getUUID());

        // Check admin shop toggle
        boolean adminEnabled = player.getServer() != null
                && AdminShopToggleSavedData.get(player.getServer()).isAdminShopEnabled();

        // Scan nearby player shops only when requested (skipped for post-transaction refreshes)
        List<NearbyShopEntry> nearbyShops = includeNearbyShops && player.getServer() != null
                ? NearbyShopScanner.scanNearby(player, NearbyShopScanner.DEFAULT_RADIUS)
                : List.of();

        ShopPackets.sendToPlayer(player, new S2CShopDataPacket(
                shopId,
                balance,
                provider.getCurrencyName(),
                provider.getDecimalPlaces(),
                adminEnabled ? ShopCatalog.buildCategories(shopId, player.getServer()) : List.of(),
                adminEnabled ? ShopCatalog.buildItems(shopId, player.getServer()) : List.of(),
                adminEnabled ? ShopCatalog.buildPromos(shopId) : List.of(),
                adminEnabled ? ShopCatalog.buildBarterRecipes(shopId) : List.of(),
                adminEnabled,
                nearbyShops));
    }

    public static void resendActiveSessions(MinecraftServer server) {
        for (Map.Entry<UUID, ShopSession> entry : ShopSessionManager.snapshotSessions().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            ShopSession session = entry.getValue();
            if (ShopCatalog.get(session.shopId()).isPresent()) {
                sendShopData(player, session.shopId());
            } else {
                ShopSessionManager.closeAndForceClose(player, "SHOP_REMOVED");
            }
        }
    }

    public static void resendSessionsViewingShop(MinecraftServer server, String requestedShopId) {
        String shopId = resolveShopId(requestedShopId);
        for (Map.Entry<UUID, ShopSession> entry : ShopSessionManager.snapshotSessions().entrySet()) {
            if (!entry.getValue().shopId().equals(shopId)) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                // Post-transaction refresh — skip the nearby-shop scan.
                sendShopData(player, shopId, false);
            }
        }
    }
}



