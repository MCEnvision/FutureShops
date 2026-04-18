package com.enviouse.futureshops.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * Fired after the stock refresh scheduler completes a batch refresh cycle (spec §31/§33).
 * Non-cancellable, informational only.
 *
 * <p>Listen on the Forge event bus:
 * <pre>{@code
 * MinecraftForge.EVENT_BUS.addListener((StockRefreshEvent event) -> {
 *     LOGGER.info("Refreshed {} items across {} shops", event.getItemsRefreshed(), event.getShopsAffected());
 * });
 * }</pre>
 */
public class StockRefreshEvent extends Event {
    private final int itemsRefreshed;
    private final int shopsAffected;

    public StockRefreshEvent(int itemsRefreshed, int shopsAffected) {
        this.itemsRefreshed = itemsRefreshed;
        this.shopsAffected = shopsAffected;
    }

    /** Number of individual items whose stock was reset this cycle. */
    public int getItemsRefreshed() { return itemsRefreshed; }

    /** Number of distinct shops that had at least one item refreshed. */
    public int getShopsAffected() { return shopsAffected; }
}

