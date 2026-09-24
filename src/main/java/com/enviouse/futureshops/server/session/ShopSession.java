package com.enviouse.futureshops.server.session;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of an active shop session.
 *
 * @param shopBlockPos  position of the shop block that triggered this session,
 *                      or {@code null} when the session was opened via command.
 *                      Used by the distance auto-close check.
 */
public record ShopSession(UUID playerUUID, UUID sessionId, String shopId,
                          BlockPos shopBlockPos, long openedAtMillis,
                          long snapshotRevision) {
    public ShopSession(UUID playerUUID, String shopId, BlockPos shopBlockPos,
                       long openedAtMillis) {
        this(playerUUID, UUID.randomUUID(), shopId, shopBlockPos,
                openedAtMillis, 0L);
    }

    public ShopSession {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(shopId, "shopId");
        if (snapshotRevision < 0L) {
            throw new IllegalArgumentException("snapshotRevision is invalid");
        }
    }

    public ShopSession withSnapshotRevision(long revision) {
        return new ShopSession(playerUUID, sessionId, shopId, shopBlockPos,
                openedAtMillis, revision);
    }
}
