package com.enviouse.futureshops.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when a player opens a shop (spec §33). Cancellable.
 */
@Cancelable
public class ShopOpenEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;

    public ShopOpenEvent(ServerPlayer player, String shopId) {
        this.player = player;
        this.shopId = shopId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getShopId() { return shopId; }
}

