package com.enviouse.futureshopsp.event;

import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired around balance mutations (spec §33).
 * <p>
 * {@code Pre} is cancellable — cancelling blocks the balance change.
 * {@code Post} fires after the balance has been updated.
 */
public abstract class BalanceChangeEvent extends Event {
    private final UUID playerUUID;
    private final long amountMinor;
    private final String reason; // "BUY", "SELL", "TRANSFER", "WITHDRAW", "DEPOSIT", "ADMIN"

    protected BalanceChangeEvent(UUID playerUUID, long amountMinor, String reason) {
        this.playerUUID = playerUUID;
        this.amountMinor = amountMinor;
        this.reason = reason;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public long getAmountMinor() { return amountMinor; }
    public String getReason() { return reason; }

        public static class Pre extends BalanceChangeEvent implements ICancellableEvent {
        private final long balanceBefore;

        public Pre(UUID playerUUID, long amountMinor, String reason, long balanceBefore) {
            super(playerUUID, amountMinor, reason);
            this.balanceBefore = balanceBefore;
        }

        public long getBalanceBefore() { return balanceBefore; }
    }

    public static class Post extends BalanceChangeEvent {
        private final long balanceAfter;

        public Post(UUID playerUUID, long amountMinor, String reason, long balanceAfter) {
            super(playerUUID, amountMinor, reason);
            this.balanceAfter = balanceAfter;
        }

        public long getBalanceAfter() { return balanceAfter; }
    }
}

