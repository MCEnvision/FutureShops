package com.enviouse.futureshops.server.session;

import java.util.UUID;

public record ShopSession(UUID playerUUID, String shopId, long openedAtMillis) {
}

