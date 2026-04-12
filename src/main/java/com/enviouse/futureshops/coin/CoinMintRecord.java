package com.enviouse.futureshops.coin;

import java.util.UUID;

/**
 * Immutable record of a single coin withdrawal (mint event).
 * <p>
 * {@code consumedAt = 0} means the mint is still valid (not yet deposited).
 * Once a player deposits the coins, {@code consumedAt} is set to the current
 * epoch-second timestamp and future deposit attempts with the same mint ID
 * are rejected, closing the primary duplication vector.
 */
public record CoinMintRecord(
        String mintId,
        UUID playerUUID,
        long denomination,
        int count,
        long mintedAt,
        long consumedAt,
        String serverId) {

    /** Returns {@code true} if these coins have already been deposited. */
    public boolean consumed() {
        return consumedAt > 0L;
    }

    /** Produces a new record stamped with the given consumed timestamp. */
    public CoinMintRecord withConsumedAt(long timestamp) {
        return new CoinMintRecord(mintId, playerUUID, denomination, count, mintedAt, timestamp, serverId);
    }
}

