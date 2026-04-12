package com.enviouse.futureshops.server.session;

import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CForceClosePacket;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopSessionManager {

    private static final Map<UUID, ShopSession> SESSIONS = new ConcurrentHashMap<>();

    private ShopSessionManager() {
    }

    // -------------------------------------------------------------------------
    // Open
    // -------------------------------------------------------------------------

    /** Opens a session without a block-position constraint (command-opened shop). */
    public static ShopSession open(UUID playerUUID, String shopId) {
        return open(playerUUID, shopId, null);
    }

    /** Opens a session anchored to a specific shop block (for distance auto-close). */
    public static ShopSession open(UUID playerUUID, String shopId, BlockPos shopBlockPos) {
        ShopSession session = new ShopSession(playerUUID, shopId, shopBlockPos, System.currentTimeMillis());
        SESSIONS.put(playerUUID, session);
        return session;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    public static Optional<ShopSession> get(UUID playerUUID) {
        return Optional.ofNullable(SESSIONS.get(playerUUID));
    }

    public static Map<UUID, ShopSession> snapshotSessions() {
        return Map.copyOf(new HashMap<>(SESSIONS));
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    /** Silently closes the session without notifying the client (e.g. on logout). */
    public static void close(UUID playerUUID) {
        SESSIONS.remove(playerUUID);
        TransactionHistoryService.clearSubscription(playerUUID);
    }

    /**
     * Closes the session AND sends an {@link S2CForceClosePacket} to the player
     * so the client can dismiss its open shop screen.
     */
    public static void closeAndForceClose(ServerPlayer player, String reason) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            TransactionHistoryService.clearSubscription(player.getUUID());
            ShopPackets.sendToPlayer(player, new S2CForceClosePacket(reason));
        }
    }

    /**
     * Closes every active session and sends {@link S2CForceClosePacket} to all
     * affected online players.  Used at server shutdown.
     */
    public static void closeAllAndForceClose(MinecraftServer server, String reason) {
        Set<UUID> keys = new HashSet<>(SESSIONS.keySet());
        SESSIONS.clear();
        for (UUID uuid : keys) {
            TransactionHistoryService.clearSubscription(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ShopPackets.sendToPlayer(player, new S2CForceClosePacket(reason));
            }
        }
    }

    /** Clears all sessions without sending any packets (used only in tests / hard reset). */
    public static void clear() {
        Set<UUID> keys = new HashSet<>(SESSIONS.keySet());
        SESSIONS.clear();
        keys.forEach(TransactionHistoryService::clearSubscription);
    }
}
