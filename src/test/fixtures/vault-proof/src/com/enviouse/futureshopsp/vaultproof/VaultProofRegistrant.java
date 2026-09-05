package com.enviouse.futureshopsp.vaultproof;

import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.server.economy.ClaimRecord;
import com.enviouse.futureshopsp.server.economy.CustodyRecord;
import com.enviouse.futureshopsp.server.economy.CustodyState;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RegistrationResult;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
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
    private static final UUID WITHDRAW_REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000510");
    private static final UUID DEPOSIT_REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000511");
    private static final UUID REFUND_REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000512");
    private static final UUID COMPENSATION_REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000513");
    private static final UUID CUSTODY_REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000514");
    private static final UUID TRANSFER_SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000415");
    private static final UUID TRANSFER_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000416");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile SqliteVaultProofProvider provider;

    public VaultProofRegistrant(IEventBus ignoredBus, ModContainer ignoredContainer) {
        RegistrationResult result = EconomyProviderRegistry.registerVault(
                EconomyApi.COMPATIBILITY_VERSION,
                context -> {
                    try {
                        provider = new SqliteVaultProofProvider(new SqliteVaultProofBackend(databasePath(), 100L));
                        return provider;
                    } catch (RuntimeException exception) {
                        LOGGER.error("FutureShops Vault proof provider factory failed type={} message={}",
                                exception.getClass().getName(), exception.getMessage(), exception);
                        throw exception;
                    }
                });
        LOGGER.info("FutureShops Vault proof registration status={} provider={} database={}",
                result.status(), result.providerId(), databasePath());
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        SqliteVaultProofProvider active = provider;
        if (active == null) {
            var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
            LOGGER.error("FutureShops Vault proof provider was not resolved before server start provider={} lifecycle={} diagnostic={}",
                    lifecycle.providerId(), lifecycle.lifecycle(), lifecycle.diagnostic());
            return;
        }
        MutationRequest request = MutationRequest.forPlayer(
                new com.enviouse.futureshopsp.api.economy.RequestId(
                        WITHDRAW_REQUEST),
                PROOF_PLAYER, 25L, MutationKind.WITHDRAW);
        ProviderResult<?> providerPrecheck = active.precheck(request);
        ProviderResult<?> coordinatorPrecheck = BalanceManager.getCoordinator().preflight(request);
        ProviderResult<MutationReceipt> withdrawal = BalanceManager.getCoordinator().withdraw(request);
        ProviderResult<MutationReceipt> lookup = active.lookup(request.requestId());
        ProviderResult<MutationReceipt> retry = active.retry(request);
        MutationRequest depositRequest = MutationRequest.forPlayer(new com.enviouse.futureshopsp.api.economy.RequestId(
                DEPOSIT_REQUEST), PROOF_PLAYER, 5L, MutationKind.DEPOSIT);
        MutationRequest refundRequest = MutationRequest.forPlayer(new com.enviouse.futureshopsp.api.economy.RequestId(
                REFUND_REQUEST), PROOF_PLAYER, 3L, MutationKind.REFUND);
        MutationRequest compensationRequest = MutationRequest.forPlayer(new com.enviouse.futureshopsp.api.economy.RequestId(
                COMPENSATION_REQUEST), PROOF_PLAYER, 2L, MutationKind.COMPENSATION);
        ProviderResult<MutationReceipt> deposit = BalanceManager.getCoordinator().deposit(depositRequest);
        ProviderResult<MutationReceipt> refund = BalanceManager.getCoordinator().refund(refundRequest);
        ProviderResult<MutationReceipt> compensation = BalanceManager.getCoordinator().compensate(compensationRequest);

        MutationRequest custodyRequest = MutationRequest.forPlayer(new com.enviouse.futureshopsp.api.economy.RequestId(
                CUSTODY_REQUEST), PROOF_PLAYER, 4L, MutationKind.DEPOSIT);
        ProviderResult<MutationReceipt> custodyDeposit = BalanceManager.getCoordinator().executeWithCustody(
                custodyRequest, PROOF_PLAYER, "vault-proof-item", 1L, "sha256:vault-proof-item", CustodyState.CLAIMED);
        CustodyRecord custody = BalanceManager.getCoordinator().custody(custodyRequest.requestId().child("custody"))
                .orElseThrow();
        com.enviouse.futureshopsp.api.economy.RequestId claimRequest = custodyRequest.requestId().child("claim");
        ClaimRecord claim = BalanceManager.getCoordinator().createClaim(claimRequest, PROOF_PLAYER, 4L,
                "vault proof claim");
        ClaimRecord deliveredClaim = BalanceManager.getCoordinator().deliverClaim(claimRequest);
        ClaimRecord resolvedClaim = BalanceManager.getCoordinator().resolveClaim(claimRequest);
        long transferSourceBefore = active.balance(TRANSFER_SOURCE).value().orElseThrow().balanceMinorUnits();
        String transferStatus;
        if (transferSourceBefore == 100L) {
            transferStatus = BalanceManager.getCoordinator().transfer(TRANSFER_SOURCE, TRANSFER_TARGET, 6L)
                    .status().name();
        } else {
            transferStatus = "REPLAYED";
        }
        long transferSourceAfter = active.balance(TRANSFER_SOURCE).value().orElseThrow().balanceMinorUnits();
        long transferTargetAfter = active.balance(TRANSFER_TARGET).value().orElseThrow().balanceMinorUnits();
        LOGGER.info("FutureShops Vault proof transaction provider_precheck={} coordinator_precheck={} withdrawal={} lookup={} retry={} deposit={} refund={} compensation={} custody={} custody_state={} claim={} claim_state_initial={} claim_state_delivered={} claim_state_resolved={} transfer={} transfer_source_before={} transfer_source_after={} transfer_target_after={} balance={} database={}",
                providerPrecheck.status(), coordinatorPrecheck.status(), withdrawal.status(), lookup.status(), retry.status(),
                deposit.status(), refund.status(), compensation.status(), custodyDeposit.status(), custody.state(),
                claimRequest, claim.state(), deliveredClaim.state(), resolvedClaim.state(), transferStatus,
                transferSourceBefore, transferSourceAfter, transferTargetAfter,
                active.balance(PROOF_PLAYER).value().orElseThrow().balanceMinorUnits(), databasePath());
    }

    private static Path databasePath() {
        return Path.of(System.getProperty("user.dir"), "world", "data", "futureshops-vault-proof.sqlite");
    }
}
