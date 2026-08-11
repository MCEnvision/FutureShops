package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.catalog.CatalogStockAuthorityMode;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.event.StockRefreshEvent;
import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements spec §31 — Stock Refresh Scheduler.
 *
 * <p>Runs at a configurable interval (default 60 seconds = 1200 ticks, see
 * {@code stock_refresh.check_interval_sec} in config). For each item with
 * {@code stockRefreshSeconds > 0}, if the elapsed time since last refresh ≥ the
 * configured interval, resets {@code current_stock} to the catalog-defined value
 * and broadcasts to active sessions.
 *
 * <p>Fires {@link StockRefreshEvent} after each batch refresh cycle.
 * <p>Persists {@code last_refresh} timestamps via {@link StockRefreshSavedData} across restarts.
 */
public final class StockRefreshScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ticks since last check (atomic for cross-thread visibility). */
    private static final AtomicInteger tickCounter = new AtomicInteger(0);

    private StockRefreshScheduler() {
    }

    /**
     * Called every server tick. When the check interval has elapsed, evaluates all
     * items with stock refresh configured.
     */
    public static void onServerTick(MinecraftServer server) {
        if (!Config.stockRefreshEnabled
                || ShopCatalog.stockAuthorityMode()
                != CatalogStockAuthorityMode.DURABLE) {
            return;
        }

        int checkIntervalTicks = Config.stockRefreshCheckIntervalSec * 20;
        if (checkIntervalTicks <= 0) {
            checkIntervalTicks = 1200; // fallback
        }

        int current = tickCounter.incrementAndGet();
        if (current < checkIntervalTicks) {
            return;
        }
        tickCounter.set(0);
        evaluateRefreshes(server);
    }

    public static void trigger(MinecraftServer server) {
        if (!Config.stockRefreshEnabled
                || ShopCatalog.stockAuthorityMode()
                != CatalogStockAuthorityMode.DURABLE) {
            return;
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Stock refresh requires the server thread");
        }
        tickCounter.set(0);
        evaluateRefreshes(server);
    }

    /**
     * Evaluates and executes stock refreshes for all shops/items that are due.
     */
    private static void evaluateRefreshes(MinecraftServer server) {
        StockRefreshSavedData data = StockRefreshSavedData.get(server);
        long nowMillis = System.currentTimeMillis();
        int refreshed = 0;

        // Track which shops had at least one refresh to batch-broadcast.
        // evaluateRefreshes is only called from the server thread, so a plain HashSet is sufficient.
        Set<String> refreshedShops = new HashSet<>();

        for (ShopDefinition def : ShopCatalog.all()) {
            String shopId = def.shopId();
            for (ItemDef item : def.items()) {
                if (!item.hasStockRefresh()) {
                    continue;
                }

                // Key by the listing resolution key (== itemId for legacy entries, so the persisted
                // compositeKey is byte-identical and the STOCKS slot matches what transactions mutate).
                String listingKey = item.resolutionKey();
                String compositeKey = shopId + ":" + listingKey;
                long lastRefresh = data.getLastRefresh(compositeKey);

                // If never refreshed, initialize the timestamp to now (don't refresh immediately on first load)
                if (lastRefresh <= 0L) {
                    data.setLastRefresh(compositeKey, nowMillis);
                    continue;
                }

                long elapsedMillis = nowMillis - lastRefresh;
                long intervalMillis = (long) item.stockRefreshSeconds() * 1000L;

                if (elapsedMillis >= intervalMillis) {
                    int configuredStock = item.stock();

                    try {
                        int currentStock = ShopCatalog.getCurrentStock(
                                shopId, listingKey);
                        if (CatalogStockRuntime.refreshStock(shopId, item)) {
                            LOGGER.debug("[FutureShops] Stock refresh: {}:{} reset to {} (was {})",
                                    shopId, listingKey, configuredStock, currentStock);
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.error("[FutureShops] Stock refresh failed for {}:{}",
                                shopId, listingKey, exception);
                        continue;
                    }

                    data.setLastRefresh(compositeKey, nowMillis);
                    refreshedShops.add(shopId);
                    refreshed++;
                }
            }
        }

        // Batch broadcast updated catalog data to all players viewing affected shops
        if (refreshed > 0) {
            for (String shopId : refreshedShops) {
                ShopDataService.resendSessionsViewingShop(server, shopId);
            }
            LOGGER.debug("[FutureShops] Stock refresh: refreshed {} item(s) across {} shop(s).",
                    refreshed, refreshedShops.size());

            // Fire StockRefreshEvent for external mod hooks (spec §33)
            MinecraftForge.EVENT_BUS.post(new StockRefreshEvent(refreshed, refreshedShops.size()));
        }
    }

    /**
     * Resets the tick counter (e.g., on server start/stop).
     */
    public static void reset() {
        tickCounter.set(0);
    }
}
