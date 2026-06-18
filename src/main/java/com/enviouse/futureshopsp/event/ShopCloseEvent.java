package com.enviouse.futureshopsp.event;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired when a shop session closes (spec §33). Non-cancellable, informational.
 */
public class ShopCloseEvent extends Event {
    private final UUID playerUUID;
    private final String shopId;
    private final String reason; // "PLAYER_CLOSED", "TOO_FAR", "DAMAGE", "SERVER_STOPPING", "SHOP_REMOVED"

    public ShopCloseEvent(UUID playerUUID, String shopId, String reason) {
        this.playerUUID = playerUUID;
        this.shopId = shopId;
        this.reason = reason;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getShopId() { return shopId; }
    public String getReason() { return reason; }
}

