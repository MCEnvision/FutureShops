package com.enviouse.futureshopsp.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired around shop buy/sell/barter transactions (spec §33).
 * <p>
 * {@code Pre} is cancellable — cancelling prevents the transaction.
 * {@code Post} fires after a successful transaction for logging/webhooks.
 */
public abstract class ShopTransactionEvent extends Event {
    private final UUID playerUUID;
    private final String shopId;
    private final String itemId;
    private final int quantity;
    private final String transactionType; // "BUY", "SELL", "BARTER", "MONEY_AND_BARTER"
    private long priceMinor;

    protected ShopTransactionEvent(UUID playerUUID, String shopId, String itemId,
                                   int quantity, String transactionType, long priceMinor) {
        this.playerUUID = playerUUID;
        this.shopId = shopId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.transactionType = transactionType;
        this.priceMinor = priceMinor;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getShopId() { return shopId; }
    public String getItemId() { return itemId; }
    public int getQuantity() { return quantity; }
    public String getTransactionType() { return transactionType; }
    public long getPriceMinor() { return priceMinor; }

    /**
     * Cancellable event fired before a transaction is committed.
     * Cancel to block the transaction. Modify {@link #setPriceMinor(long)} to change the price.
     */
        public static class Pre extends ShopTransactionEvent implements ICancellableEvent {
        private final ServerPlayer player;

        public Pre(ServerPlayer player, String shopId, String itemId,
                   int quantity, String transactionType, long priceMinor) {
            super(player.getUUID(), shopId, itemId, quantity, transactionType, priceMinor);
            this.player = player;
        }

        public ServerPlayer getPlayer() { return player; }

        /** Allows other mods to modify the transaction price. */
        public void setPriceMinor(long priceMinor) {
            ((ShopTransactionEvent) this).priceMinor = priceMinor;
        }
    }

    /**
     * Non-cancellable event fired after a successful transaction.
     */
    public static class Post extends ShopTransactionEvent {
        private final long resultingBalance;

        public Post(UUID playerUUID, String shopId, String itemId,
                    int quantity, String transactionType, long priceMinor, long resultingBalance) {
            super(playerUUID, shopId, itemId, quantity, transactionType, priceMinor);
            this.resultingBalance = resultingBalance;
        }

        public long getResultingBalance() { return resultingBalance; }
    }
}

