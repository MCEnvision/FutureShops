package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.session.ShopSession;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        ShopSessionManager.open(player.getUUID(), shopId);
        sendShopData(player, shopId);
        InventorySyncService.sendOwnedCounts(player, shopId);
    }

    public static void sendShopData(ServerPlayer player, String requestedShopId) {
        String shopId = resolveShopId(requestedShopId);
        EconomyProvider provider = BalanceManager.getProvider();
        long balance = provider.getBalance(player.getUUID());

        ShopPackets.sendToPlayer(player, new S2CShopDataPacket(
                shopId,
                balance,
                provider.getCurrencyName(),
                provider.getDecimalPlaces(),
                ShopCatalog.buildCategories(shopId),
                ShopCatalog.buildItems(shopId),
                ShopCatalog.buildPromos(shopId),
                ShopCatalog.buildBarterRecipes(shopId)));
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
                sendShopData(player, shopId);
            }
        }
    }
}



