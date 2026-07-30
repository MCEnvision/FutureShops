package com.enviouse.futureshops.server.economy;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

public final class BalanceManager {
    private static InternalEconomyProvider provider;

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

    public static long getDisplayBalance(UUID playerUUID) {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider.getDisplayBalance(playerUUID);
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

    public static TransactionResult transfer(UUID requestId, UUID fromPlayerUUID,
                                             UUID toPlayerUUID, long amountMinorUnits,
                                             String reason) {
        return getInternalProvider().transfer(requestId, fromPlayerUUID,
                toPlayerUUID, amountMinorUnits, reason);
    }

    public static TransactionResult withdraw(UUID requestId, UUID playerUUID,
                                             long amountMinorUnits, String reason) {
        return getInternalProvider().withdraw(
                requestId, playerUUID, amountMinorUnits, reason);
    }

    public static TransactionResult deposit(UUID requestId, UUID playerUUID,
                                            long amountMinorUnits, String reason) {
        return getInternalProvider().deposit(
                requestId, playerUUID, amountMinorUnits, reason);
    }

    public static TransactionResult setBalance(UUID playerUUID,
                                               long amountMinorUnits,
                                               boolean allowNegative,
                                               String reason) {
        return setBalance(UUID.randomUUID(), playerUUID, amountMinorUnits,
                allowNegative, reason);
    }

    public static TransactionResult setBalance(UUID requestId, UUID playerUUID,
                                               long amountMinorUnits,
                                               boolean allowNegative,
                                               String reason) {
        return getInternalProvider().setBalance(requestId, playerUUID,
                amountMinorUnits, allowNegative, reason);
    }

    public static List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return getProvider().getTopBalances(page, pageSize);
    }

    private static InternalEconomyProvider getInternalProvider() {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider;
    }
}
