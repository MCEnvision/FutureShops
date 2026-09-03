package com.enviouse.futureshopsp.api;

import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.catalog.ShopDefinition;
import com.enviouse.futureshopsp.money.MoneyValidationResult;
import com.enviouse.futureshopsp.money.MoneyValidationService;
import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.server.economy.TransactionResult;
import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.ShopDataService;
import com.enviouse.futureshopsp.server.shop.StockRefreshScheduler;
import com.enviouse.futureshopsp.server.transaction.TransactionHistorySavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public developer API for interacting with the FutureShops economy and shop systems (spec §33).
 * <p>
 * All methods are static for easy access. The server must be running (economy initialized)
 * before any method is called.
 *
 * <h3>Usage from another mod:</h3>
 * <pre>{@code
 * // Check a player's balance
 * long balance = ShopModAPI.getBalance(playerUuid);
 *
 * // Open a shop for a player
 * ShopModAPI.openShopForPlayer(serverPlayer, "default");
 *
 * // Validate a coin item
 * boolean valid = ShopModAPI.validateMoneyItem(stack);
 *
 * // Listen to events
 * NeoForge.EVENT_BUS.addListener((ShopTransactionEvent.Post event) -> {
 *     // log or award achievements
 * });
 * }</pre>
 */
public final class ShopModAPI {
    private ShopModAPI() {
    }

    // ═══════════════════════════════════════════════
    // Economy
    // ═══════════════════════════════════════════════

    /**
     * Returns the active economy provider. Never null while a server is running.
     */
    public static EconomyProvider getEconomy() {
        return BalanceManager.getProvider();
    }

    /**
     * Returns the balance of the given player in minor currency units.
     */
    public static long getBalance(UUID playerUUID) {
        return BalanceManager.getBalance(playerUUID);
    }

    /**
     * Queries a balance without converting an unavailable provider into zero.
     */
    public static ProviderResult<BalanceSnapshot> queryBalance(UUID playerUUID) {
        return BalanceManager.queryBalance(playerUUID);
    }

    /**
     * Withdraws currency from a player's balance.
     */
    public static TransactionResult withdraw(UUID playerUUID, long amountMinor) {
        return BalanceManager.getProvider().withdraw(playerUUID, amountMinor);
    }

    /**
     * Deposits currency into a player's balance.
     */
    public static TransactionResult deposit(UUID playerUUID, long amountMinor) {
        return BalanceManager.getProvider().deposit(playerUUID, amountMinor);
    }

    /**
     * Transfers currency between two players.
     */
    public static TransactionResult transfer(UUID fromPlayer, UUID toPlayer, long amountMinor) {
        return BalanceManager.transfer(fromPlayer, toPlayer, amountMinor);
    }

    // ═══════════════════════════════════════════════
    // Catalog
    // ═══════════════════════════════════════════════

    /**
     * Returns the shop definition for the given ID, or empty if not loaded.
     */
    public static Optional<ShopDefinition> getShopCatalog(String shopId) {
        return ShopCatalog.get(shopId);
    }

    /**
     * Returns all loaded shop definitions.
     */
    public static java.util.Collection<ShopDefinition> getAllShops() {
        return ShopCatalog.all();
    }

    // ═══════════════════════════════════════════════
    // Sessions
    // ═══════════════════════════════════════════════

    /**
     * Opens a shop GUI for a player. Equivalent to the player running {@code /shop <shopId>}.
     */
    public static void openShopForPlayer(ServerPlayer player, String shopId) {
        ShopDataService.openShop(player, shopId);
    }

    /**
     * Force-closes a player's shop session and dismisses their GUI.
     */
    public static void forceCloseShop(ServerPlayer player, String reason) {
        ShopSessionManager.closeAndForceClose(player, reason);
    }

    /**
     * Checks whether a player currently has an active shop session.
     */
    public static boolean hasActiveSession(UUID playerUUID) {
        return ShopSessionManager.get(playerUUID).isPresent();
    }

    // ═══════════════════════════════════════════════
    // History
    // ═══════════════════════════════════════════════

    /**
     * Returns the most recent transaction history entries for a player.
     *
     * @param server the server instance
     * @param playerUUID the player's UUID
     * @param limit max entries to return
     */
    public static List<TransactionHistoryEntry> getHistory(MinecraftServer server, UUID playerUUID, int limit) {
        TransactionHistorySavedData data = TransactionHistorySavedData.get(server);
        return data.getPage(playerUUID, 1, limit,
                TransactionHistoryEntry.HistoryFilter.ALL, "",
                TransactionHistoryEntry.SortOrder.NEWEST,
                TransactionHistoryEntry.TimeWindow.ALL);
    }

    // ═══════════════════════════════════════════════
    // Coins
    // ═══════════════════════════════════════════════

    /**
     * Validates a MoneyItem stack. Returns true if the coin passes all integrity checks
     * (checksum, age, mint-ID uniqueness).
     */
    public static boolean validateMoneyItem(ItemStack stack) {
        return MoneyValidationService.validate(stack).valid();
    }

    /**
     * Returns detailed validation result for a coin item stack.
     */
    public static MoneyValidationResult validateMoneyItemDetailed(ItemStack stack) {
        return MoneyValidationService.validate(stack);
    }

    /**
     * Sums the total coin value a player is carrying in their inventory (minor units).
     */
    public static long getPhysicalCoinValue(ServerPlayer player) {
        long total = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            MoneyValidationResult result = MoneyValidationService.validate(stack);
            if (result.valid()) {
                try {
                    total = Math.addExact(total,
                            Math.multiplyExact(result.denominationMinorUnits(), (long) stack.getCount()));
                } catch (ArithmeticException exception) {
                    throw new IllegalStateException("physical coin value exceeds the supported range", exception);
                }
            }
        }
        return total;
    }

    // ═══════════════════════════════════════════════
    // Admin Balance
    // ═══════════════════════════════════════════════

    /**
     * Sets a player's internal balance through the durable economy coordinator.
     * This operation is available only while the internal provider is ready.
     */
    public static void setBalance(MinecraftServer server, UUID playerUUID, long amountMinor) {
        if (server == null) {
            throw new IllegalArgumentException("server is required");
        }
        ProviderResult<BalanceSnapshot> result = BalanceManager.setInternalBalance(playerUUID, amountMinor);
        if (!result.confirmed()) {
            throw new IllegalStateException(result.diagnostic());
        }
    }

    // ═══════════════════════════════════════════════
    // Stock Refresh
    // ═══════════════════════════════════════════════

    /**
     * Manually triggers a stock refresh evaluation cycle.
     * Useful for forcing immediate stock restocking from another mod.
     */
    public static void triggerStockRefresh(MinecraftServer server) {
        StockRefreshScheduler.reset();
        // Force an immediate check by calling onServerTick repeatedly would be fragile;
        // instead, the next tick will run the check since counter is 0 and interval is met.
    }

    /**
     * Returns the current stock level for a specific item in an admin shop.
     *
     * @param shopId the shop ID
     * @param itemId the item resource location string
     * @return current stock, or -1 for unlimited
     */
    public static int getAdminShopStock(String shopId, String itemId) {
        return ShopCatalog.getCurrentStock(shopId, itemId);
    }

    /**
     * Sets the stock level for a specific item in an admin shop.
     */
    public static void setAdminShopStock(String shopId, String itemId, int newStock) {
        ShopCatalog.setStock(shopId, itemId, newStock);
    }

    // ═══════════════════════════════════════════════
    // Session Queries
    // ═══════════════════════════════════════════════

    /**
     * Returns the shop ID of a player's current session, or empty if none.
     */
    public static Optional<String> getActiveShopId(UUID playerUUID) {
        return ShopSessionManager.get(playerUUID)
                .map(com.enviouse.futureshopsp.server.session.ShopSession::shopId);
    }

    /**
     * Returns the number of currently active shop sessions.
     */
    public static int getActiveSessionCount() {
        return ShopSessionManager.snapshotSessions().size();
    }
}
