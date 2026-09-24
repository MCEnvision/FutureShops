package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import com.enviouse.futureshops.api.economy.ProviderResolution;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

public final class BalanceManager {
    private static InternalEconomyProvider internalProvider;
    private static EconomyProvider provider;
    private static com.enviouse.futureshops.api.economy.EconomyProvider publicProvider;
    private static ProviderSelectionSnapshot selection =
            new ProviderSelectionSnapshot("", "", false, false, "");

    private BalanceManager() {
    }

    public static void initialize(MinecraftServer server) {
        internalProvider = new InternalEconomyProvider(server);
        selection = ProviderSelectionManager.resolveAtStartup(Config.economyProviderId);
        if (EconomyApi.INTERNAL_PROVIDER_ID.equals(selection.activeProviderId())) {
            provider = internalProvider;
            publicProvider = new PublicInternalEconomyProvider(internalProvider);
            return;
        }
        ProviderResolution resolution = EconomyProviderRegistry.resolve(
                selection.activeProviderId(),
                new com.enviouse.futureshops.api.economy.EconomyProviderContext(server));
        ProviderLifecycle lifecycle = resolution.lifecycle();
        String diagnostic = resolution.diagnostic().isBlank()
                ? "external mutation coordinator is not ready" : resolution.diagnostic();
        provider = new UnavailableEconomyProvider(
                selection.activeProviderId(), lifecycle.name(), diagnostic);
        publicProvider = new UnavailablePublicEconomyProvider(
                selection.activeProviderId(),
                lifecycle == ProviderLifecycle.READY ? ProviderLifecycle.FROZEN : lifecycle,
                diagnostic);
    }

    public static void clear() {
        internalProvider = null;
        provider = null;
        publicProvider = null;
        selection = new ProviderSelectionSnapshot("", "", false, false, "");
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
        if (!usesInternalProvider()) {
            throw new EconomyUnavailableException(selection.activeProviderId(),
                    "UNAVAILABLE", "selected provider is not admitted");
        }
        return internalProvider.getDisplayBalance(playerUUID);
    }

    public static EconomyProvider getProvider() {
        if (provider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return provider;
    }

    /** Returns the server selected public API projection. */
    public static com.enviouse.futureshops.api.economy.EconomyProvider getPublicProvider() {
        if (publicProvider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return publicProvider;
    }

    public static ProviderSelectionSnapshot selection() {
        return selection;
    }

    public static TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        return getProvider().transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits);
    }

    public static TransactionResult transfer(UUID requestId, UUID fromPlayerUUID,
                                             UUID toPlayerUUID, long amountMinorUnits,
                                             String reason) {
        if (!usesInternalProvider()) {
            return unavailableMutation();
        }
        return getInternalProvider().transfer(requestId, fromPlayerUUID,
                toPlayerUUID, amountMinorUnits, reason);
    }

    public static TransactionResult withdraw(UUID requestId, UUID playerUUID,
                                             long amountMinorUnits, String reason) {
        if (!usesInternalProvider()) {
            return unavailableMutation();
        }
        return getInternalProvider().withdraw(
                requestId, playerUUID, amountMinorUnits, reason);
    }

    public static TransactionResult deposit(UUID requestId, UUID playerUUID,
                                            long amountMinorUnits, String reason) {
        if (!usesInternalProvider()) {
            return unavailableMutation();
        }
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
        if (!usesInternalProvider()) {
            return unavailableMutation();
        }
        return getInternalProvider().setBalance(requestId, playerUUID,
                amountMinorUnits, allowNegative, reason);
    }

    public static List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return getProvider().getTopBalances(page, pageSize);
    }

    private static InternalEconomyProvider getInternalProvider() {
        if (internalProvider == null) {
            throw new IllegalStateException("BalanceManager accessed before initialization.");
        }
        return internalProvider;
    }

    private static boolean usesInternalProvider() {
        return provider == internalProvider && internalProvider != null;
    }

    private static TransactionResult unavailableMutation() {
        return TransactionResult.error(
                com.enviouse.futureshops.server.shop.ShopResultCode.SERVER_ERROR, 0L);
    }
}
