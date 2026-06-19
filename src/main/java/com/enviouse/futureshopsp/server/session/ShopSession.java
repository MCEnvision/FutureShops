package com.enviouse.futureshopsp.server.session;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Immutable snapshot of an active shop session.
 *
 * @param shopBlockPos  position of the shop block that triggered this session,
 *                      or {@code null} when the session was opened via command.
 *                      Used by the distance auto-close check.
 */
public record ShopSession(UUID playerUUID, String shopId, BlockPos shopBlockPos, long openedAtMillis) {
}
