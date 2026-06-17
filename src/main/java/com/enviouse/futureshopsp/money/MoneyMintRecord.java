package com.enviouse.futureshopsp.money;

import java.util.UUID;

/**
 * Immutable record of a single coin withdrawal (batch mint event).
 * <p>
 * A mint authorizes {@code authorizedCount} coins sharing one {@code mintId};
 * deposits decrement {@code remainingCount} until it reaches zero, at which
 * point {@code consumedAt} is stamped with the epoch-second timestamp.
 * <p>
 * Any further deposit attempts referencing this mint ID (after
 * {@code remainingCount == 0}) are rejected — this closes the primary
 * duplication vector while still allowing partial/split-stack deposits.
 */
public record MoneyMintRecord(
        String mintId,
        UUID playerUUID,
        long denomination,
        int authorizedCount,
        int remainingCount,
        long mintedAt,
        long consumedAt,
        String serverId) {

    /** Returns {@code true} once every coin authorized by this mint has been deposited. */
    public boolean consumed() {
        return remainingCount <= 0;
    }

    /** Produces a new record with the given remaining count. */
    public MoneyMintRecord withRemainingCount(int newRemaining) {
        return new MoneyMintRecord(mintId, playerUUID, denomination, authorizedCount,
                newRemaining, mintedAt, consumedAt, serverId);
    }

    /** Produces a new record stamped with the given consumed timestamp. */
    public MoneyMintRecord withConsumedAt(long timestamp) {
        return new MoneyMintRecord(mintId, playerUUID, denomination, authorizedCount,
                remainingCount, mintedAt, timestamp, serverId);
    }
}
