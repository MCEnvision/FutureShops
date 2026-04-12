package com.enviouse.futureshops.server.economy;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class BalanceManager {
    private static EconomyProvider provider;

    private BalanceManager() {
    }

    public static void initialize(MinecraftServer server) {
        provider = new InternalEconomyProvider(server);
    }

    public static void clear() {
        provider = null;
    }

    public static long getBalance(UUID playerUUID) {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider.getBalance(playerUUID);
    }

    public static EconomyProvider getProvider() {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider;
    }
}

