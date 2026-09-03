package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderContext;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.ProviderResolution;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

public final class BalanceManager {
    private static EconomyProvider provider;

    private BalanceManager() {
    }

    public static void initialize(MinecraftServer server) {
        EconomyProviderRegistry.freeze();
        ProviderSelectionSnapshot selection = ProviderSelectionManager.resolveAtStartup(Config.economyProviderId);
        if (EconomyApi.INTERNAL_PROVIDER_ID.equals(selection.activeProviderId())) {
            provider = new InternalEconomyProvider(server);
            return;
        }
        ProviderResolution resolution = EconomyProviderRegistry.resolve(
                selection.activeProviderId(), new EconomyProviderContext(server));
        provider = new UnavailableEconomyProvider(selection.activeProviderId(), resolution.lifecycle(),
                resolution.diagnostic());
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
