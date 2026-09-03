package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderContext;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.ProviderResolution;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

public final class BalanceManager {
    private static EconomyProvider provider;
    private static EconomyTransactionCoordinator coordinator;
    private static EconomyLifecycleController lifecycleController;
    private static EconomyTransactionJournal journal;

    private BalanceManager() {
    }

    public static void initialize(MinecraftServer server) {
        EconomyProviderRegistry.freeze();
        ProviderSelectionSnapshot selection = ProviderSelectionManager.resolveAtStartup(Config.economyProviderId);
        journal = server.overworld() == null ? new InMemoryEconomyTransactionJournal()
                : EconomyJournalSavedData.get(server);
        boolean cleanMarkerValid = journal.cleanMarkerValid();
        journal.markUnclean();
        lifecycleController = new EconomyLifecycleController(selection.activeProviderId());
        if (EconomyApi.INTERNAL_PROVIDER_ID.equals(selection.activeProviderId())) {
            EconomyProvider legacy = new InternalEconomyProvider(server);
            com.enviouse.futureshopsp.api.economy.EconomyProvider publicProvider =
                    new PublicInternalEconomyProvider(legacy);
            lifecycleController.resolve(ProviderLifecycle.READY, "", cleanMarkerValid, journal.integrityValid(),
                    journal.hasIncompleteRecords());
            coordinator = new EconomyTransactionCoordinator(publicProvider, lifecycleController, journal);
            provider = new CoordinatedEconomyProvider(publicProvider, coordinator);
            return;
        }
        ProviderResolution resolution = EconomyProviderRegistry.resolve(
                selection.activeProviderId(), new EconomyProviderContext(server));
        if (resolution.provider().isPresent() && resolution.lifecycle() == ProviderLifecycle.READY) {
            com.enviouse.futureshopsp.api.economy.EconomyProvider publicProvider = resolution.provider().orElseThrow();
            lifecycleController.resolve(resolution.lifecycle(), resolution.diagnostic(), cleanMarkerValid, journal.integrityValid(),
                    journal.hasIncompleteRecords());
            coordinator = new EconomyTransactionCoordinator(publicProvider, lifecycleController, journal);
            provider = new ExternalLegacyEconomyProvider(publicProvider, coordinator);
        } else {
            lifecycleController.resolve(resolution.lifecycle(), resolution.diagnostic(), cleanMarkerValid, journal.integrityValid(),
                    journal.hasIncompleteRecords());
            provider = new UnavailableEconomyProvider(selection.activeProviderId(), resolution.lifecycle(),
                    resolution.diagnostic());
        }
    }

    public static void clear() {
        if (lifecycleController != null) {
            lifecycleController.beginDraining();
            if (journal != null) {
                journal.flush();
            }
            if (lifecycleController.writeCleanMarkerLast(true, true, true, true)) {
                journal.markCleanMarker();
            }
        }
        provider = null;
        coordinator = null;
        lifecycleController = null;
        journal = null;
    }

    public static void beginDraining() {
        if (lifecycleController != null) {
            lifecycleController.beginDraining();
        }
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

    public static EconomyTransactionCoordinator getCoordinator() {
        if (coordinator == null) {
            throw new IllegalStateException("Economy coordinator accessed before initialization.");
        }
        return coordinator;
    }

    public static EconomyLifecycleSnapshot getLifecycleSnapshot() {
        if (lifecycleController == null) {
            throw new IllegalStateException("Economy lifecycle accessed before initialization.");
        }
        return lifecycleController.snapshot();
    }

    public static TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        return getProvider().transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits);
    }

    public static List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return getProvider().getTopBalances(page, pageSize);
    }
}
