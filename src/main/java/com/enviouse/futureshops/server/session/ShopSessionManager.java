package com.enviouse.futureshops.server.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopSessionManager {
    private static final Map<UUID, ShopSession> SESSIONS = new ConcurrentHashMap<>();

    private ShopSessionManager() {
    }

    public static ShopSession open(UUID playerUUID, String shopId) {
        ShopSession session = new ShopSession(playerUUID, shopId, System.currentTimeMillis());
        SESSIONS.put(playerUUID, session);
        return session;
    }

    public static Optional<ShopSession> get(UUID playerUUID) {
        return Optional.ofNullable(SESSIONS.get(playerUUID));
    }

    public static void close(UUID playerUUID) {
        SESSIONS.remove(playerUUID);
    }

    public static void clear() {
        SESSIONS.clear();
    }
}

