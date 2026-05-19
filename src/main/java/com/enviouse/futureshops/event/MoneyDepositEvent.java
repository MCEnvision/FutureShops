package com.enviouse.futureshops.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Fired when coins are deposited (spec §33). Non-cancellable.
 */
public class MoneyDepositEvent extends Event {
    private final UUID playerUUID;
    private final long totalValueMinor;
    private final int coinsConsumed;

    public MoneyDepositEvent(UUID playerUUID, long totalValueMinor, int coinsConsumed) {
        this.playerUUID = playerUUID;
        this.totalValueMinor = totalValueMinor;
        this.coinsConsumed = coinsConsumed;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public long getTotalValueMinor() { return totalValueMinor; }
    public int getCoinsConsumed() { return coinsConsumed; }
}

