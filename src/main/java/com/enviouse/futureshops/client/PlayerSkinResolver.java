package com.enviouse.futureshops.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class PlayerSkinResolver {
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final ConcurrentMap<UUID, ResourceLocation> CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> LAST_REQUEST =
            new ConcurrentHashMap<>();

    private PlayerSkinResolver() {
    }

    public static ResourceLocation resolve(UUID playerUuid, String playerName) {
        if (playerUuid == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerUuid);
            if (info != null) {
                ResourceLocation onlineSkin = info.getSkinLocation();
                CACHE.put(playerUuid, onlineSkin);
                return onlineSkin;
            }
        }

        ResourceLocation cached = CACHE.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        long now = System.nanoTime();
        if (claimRequest(playerUuid, now)) {
            request(playerUuid, playerName);
        }
        return DefaultPlayerSkin.getDefaultSkin(playerUuid);
    }

    private static boolean claimRequest(UUID playerUuid, long now) {
        Long previous = LAST_REQUEST.putIfAbsent(playerUuid, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < RETRY_NANOS) {
            return false;
        }
        return LAST_REQUEST.replace(playerUuid, previous, now);
    }

    private static void request(UUID playerUuid, String playerName) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            GameProfile profile = new GameProfile(playerUuid,
                    playerName == null ? "" : playerName.trim());
            minecraft.getSkinManager().registerSkins(profile,
                    (type, location, texture) -> {
                        if (type == MinecraftProfileTexture.Type.SKIN
                                && location != null) {
                            CACHE.put(playerUuid, location);
                            LAST_REQUEST.remove(playerUuid);
                        }
                    }, false);
        } catch (Throwable throwable) {
            LAST_REQUEST.remove(playerUuid);
        }
    }
}
