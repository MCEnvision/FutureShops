package com.enviouse.futureshopsp.server.economy;

import net.minecraft.server.MinecraftServer;

import java.util.List;
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

    public static TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        return getProvider().transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits);
    }

    public static List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return getProvider().getTopBalances(page, pageSize);
    }
}
