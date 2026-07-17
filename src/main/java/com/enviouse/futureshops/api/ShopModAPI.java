package com.enviouse.futureshops.api;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.money.MoneyValidationResult;
import com.enviouse.futureshops.money.MoneyValidationService;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.shop.StockRefreshScheduler;
import com.enviouse.futureshops.server.transaction.TransactionHistorySavedData;
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
 * MinecraftForge.EVENT_BUS.addListener((ShopTransactionEvent.Post event) -> {
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
     * Withdraws currency from a player's balance.
     */
    public static TransactionResult withdraw(UUID playerUUID, long amountMinor) {
        return BalanceManager.getProvider().withdraw(playerUUID, amountMinor);
    }

    public static TransactionResult withdraw(UUID requestId, UUID playerUUID,
                                             long amountMinor) {
        return BalanceManager.withdraw(
                requestId, playerUUID, amountMinor, "WITHDRAW");
    }

    /**
     * Deposits currency into a player's balance.
     */
    public static TransactionResult deposit(UUID playerUUID, long amountMinor) {
        return BalanceManager.getProvider().deposit(playerUUID, amountMinor);
    }

    public static TransactionResult deposit(UUID requestId, UUID playerUUID,
                                            long amountMinor) {
        return BalanceManager.deposit(
                requestId, playerUUID, amountMinor, "DEPOSIT");
    }

    /**
     * Transfers currency between two players.
     */
    public static TransactionResult transfer(UUID fromPlayer, UUID toPlayer, long amountMinor) {
        return BalanceManager.transfer(fromPlayer, toPlayer, amountMinor);
    }

    public static TransactionResult transfer(UUID requestId, UUID fromPlayer,
                                             UUID toPlayer, long amountMinor) {
        return BalanceManager.transfer(
                requestId, fromPlayer, toPlayer, amountMinor, "TRANSFER");
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
     * Sums the total physical currency value a player is carrying (minor units).
     * Respects the configured {@code currency.provider}: with a foreign provider
     * active this values that mod's items at their configured face value instead
     * of FutureShops bills.
     */
    public static long getPhysicalCoinValue(ServerPlayer player) {
        var currency = com.enviouse.futureshops.money.CurrencyManager.getOrNull();
        if (currency != null) {
            return currency.availableValueMinor(player);
        }
        long total = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            MoneyValidationResult result = MoneyValidationService.validate(stack);
            if (result.valid()) {
                total += result.denominationMinorUnits() * stack.getCount();
            }
        }
        return total;
    }

    // ═══════════════════════════════════════════════
    // Admin Balance
    // ═══════════════════════════════════════════════

    /**
     * Sets a player's balance directly (admin override). Bypasses max_balance checks.
     * Fires {@link com.enviouse.futureshops.event.BalanceChangeEvent.Post} with reason "ADMIN".
     */
    public static void setBalance(MinecraftServer server, UUID playerUUID, long amountMinor) {
        TransactionResult result = setBalanceResult(
                UUID.randomUUID(), server, playerUUID, amountMinor);
        if (!result.success()) {
            throw new IllegalStateException(
                    "FutureShops balance update failed with " + result.errorCode());
        }
    }

    public static TransactionResult setBalanceResult(
            UUID requestId, MinecraftServer server, UUID playerUUID,
            long amountMinor
    ) {
        java.util.Objects.requireNonNull(server, "server");
        return BalanceManager.setBalance(
                requestId,
                playerUUID,
                amountMinor,
                com.enviouse.futureshops.Config.economyAllowNegative,
                "ADMIN");
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
     * <p>
     * NOTE: stock is keyed by listingId, so a registry {@code itemId} only reaches legacy
     * single-variant listings (where {@code listingId == itemId}). Multi-variant NBT listings
     * carry a distinct listingId — use {@link #getAdminShopStockByListingId(String, String)}.
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
     * <p>
     * NOTE: stock is keyed by listingId, so a registry {@code itemId} only reaches legacy
     * single-variant listings (where {@code listingId == itemId}). Multi-variant NBT listings
     * carry a distinct listingId — use {@link #setAdminShopStockByListingId(String, String, int)}.
     */
    public static void setAdminShopStock(String shopId, String itemId, int newStock) {
        ShopCatalog.setStock(shopId, itemId, newStock);
    }

    /**
     * Returns the current stock level for a specific listing in an admin shop, resolved by its
     * stable listingId ({@link com.enviouse.futureshops.catalog.ItemDef#resolutionKey()}). This
     * reaches every listing: for legacy single-variant listings the listingId equals the registry
     * itemId, while multi-variant NBT listings carry a distinct id of their own.
     *
     * @param shopId the shop ID
     * @param listingId the listing's stable id
     * @return current stock, or -1 for unlimited (or unknown listing)
     */
    public static int getAdminShopStockByListingId(String shopId, String listingId) {
        return ShopCatalog.getCurrentStock(shopId, listingId);
    }

    /**
     * Sets the stock level for a specific listing in an admin shop, resolved by its stable
     * listingId. No-op for unlimited-stock or unknown listings.
     */
    public static void setAdminShopStockByListingId(String shopId, String listingId, int newStock) {
        ShopCatalog.setStock(shopId, listingId, newStock);
    }

    // ═══════════════════════════════════════════════
    // Session Queries
    // ═══════════════════════════════════════════════

    /**
     * Returns the shop ID of a player's current session, or empty if none.
     */
    public static Optional<String> getActiveShopId(UUID playerUUID) {
        return ShopSessionManager.get(playerUUID)
                .map(com.enviouse.futureshops.server.session.ShopSession::shopId);
    }

    /**
     * Returns the number of currently active shop sessions.
     */
    public static int getActiveSessionCount() {
        return ShopSessionManager.snapshotSessions().size();
    }
}

