package com.enviouse.futureshopsp.vaultproof;

import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RegistrationResult;
import com.enviouse.futureshopsp.vaultproof.SqliteVaultProofBackend;
import com.enviouse.futureshopsp.vaultproof.SqliteVaultProofProvider;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

/** Disposable NeoForge registrant for exact hybrid provider proof only. */
@Mod("futureshops_vault_proof")
public final class VaultProofRegistrant {
    private static final UUID PROOF_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000410");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile SqliteVaultProofProvider provider;

    public VaultProofRegistrant(IEventBus ignoredBus, ModContainer ignoredContainer) {
        RegistrationResult result = EconomyProviderRegistry.registerVault(
                EconomyApi.COMPATIBILITY_VERSION,
                context -> {
                    provider = new SqliteVaultProofProvider(new SqliteVaultProofBackend(databasePath(), 100L));
                    return provider;
                });
        LOGGER.info("FutureShops Vault proof registration status={} provider={} database={}",
                result.status(), result.providerId(), databasePath());
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        SqliteVaultProofProvider active = provider;
        if (active == null) {
            LOGGER.error("FutureShops Vault proof provider was not resolved before server start");
            return;
        }
        MutationRequest request = MutationRequest.forPlayer(
                new com.enviouse.futureshopsp.api.economy.RequestId(
                        UUID.fromString("00000000-0000-0000-0000-000000000510")),
                PROOF_PLAYER, 25L, MutationKind.WITHDRAW);
        ProviderResult<?> precheck = active.precheck(request);
        ProviderResult<MutationReceipt> withdrawal = active.withdraw(request);
        ProviderResult<MutationReceipt> lookup = active.lookup(request.requestId());
        ProviderResult<MutationReceipt> retry = active.retry(request);
        LOGGER.info("FutureShops Vault proof transaction precheck={} withdrawal={} lookup={} retry={} balance={} database={}",
                precheck.status(), withdrawal.status(), lookup.status(), retry.status(),
                active.balance(PROOF_PLAYER).value().orElseThrow().balanceMinorUnits(), databasePath());
    }

    private static Path databasePath() {
        return Path.of(System.getProperty("user.dir"), "world", "data", "futureshops-vault-proof.sqlite");
    }
}
