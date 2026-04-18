package com.enviouse.futureshops.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * Fired after the shop catalog is reloaded (spec §33). Non-cancellable.
 */
public class ShopReloadEvent extends Event {
    private final int shopCount;

    public ShopReloadEvent(int shopCount) {
        this.shopCount = shopCount;
    }

    public int getShopCount() { return shopCount; }
}

