package com.enviouse.futureshops.server.shop;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerShopLinkService {
    private static final long EXPIRES_MS = 30_000L;
    private static final Map<UUID, PendingLink> PENDING = new ConcurrentHashMap<>();

    private PlayerShopLinkService() {
    }

    public static void begin(net.minecraft.server.level.ServerPlayer player, BlockPos shopPos) {
        PENDING.put(player.getUUID(), new PendingLink(shopPos, System.currentTimeMillis() + EXPIRES_MS));
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("command.futureshops.link.start"));
    }

    public static PendingLink consume(UUID uuid) {
        PendingLink pending = PENDING.remove(uuid);
        if (pending == null) {
            return null;
        }
        if (pending.expiresAtMs() < System.currentTimeMillis()) {
            return null;
        }
        return pending;
    }

    public record PendingLink(BlockPos shopPos, long expiresAtMs) {
    }
}

