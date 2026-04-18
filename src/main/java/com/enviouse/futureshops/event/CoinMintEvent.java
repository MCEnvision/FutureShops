package com.enviouse.futureshops.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Fired when coins are minted via /withdraw (spec §33). Non-cancellable.
 */
public class CoinMintEvent extends Event {
    private final UUID playerUUID;
    private final long denomination;
    private final int count;
    private final String mintId;

    public CoinMintEvent(UUID playerUUID, long denomination, int count, String mintId) {
        this.playerUUID = playerUUID;
        this.denomination = denomination;
        this.count = count;
        this.mintId = mintId;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public long getDenomination() { return denomination; }
    public int getCount() { return count; }
    public String getMintId() { return mintId; }
}

