package com.enviouse.futureshopsp.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

/**
 * Fired when a player opens a shop (spec §33). Cancellable.
 */
public class ShopOpenEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final String shopId;

    public ShopOpenEvent(ServerPlayer player, String shopId) {
        this.player = player;
        this.shopId = shopId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getShopId() { return shopId; }
}

