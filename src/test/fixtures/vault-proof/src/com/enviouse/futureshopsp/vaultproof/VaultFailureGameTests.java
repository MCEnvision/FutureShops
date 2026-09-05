package com.enviouse.futureshopsp.vaultproof;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProviderContext;
import com.enviouse.futureshopsp.api.economy.EconomyProviderRegistry;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.ProviderResultStatus;
import com.enviouse.futureshopsp.api.economy.RegistrationStatus;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

/** Disposable exact Vault failure and recovery proof for the separate registrant. */
@GameTestHolder(Futureshops.MODID)
public final class VaultFailureGameTests {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000430");
    private static final Logger LOGGER = LogUtils.getLogger();

    private VaultFailureGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vaultFailureAndRecoveryMatrix(GameTestHelper helper) {
        var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
        helper.assertTrue("vault".equals(lifecycle.providerId())
                        && lifecycle.lifecycle() == com.enviouse.futureshopsp.api.economy.ProviderLifecycle.READY,
                "the exact vault proof registrant must resolve READY before failure tests");

        ServerPlayer anchor = helper.makeMockServerPlayerInLevel();
        SqliteVaultProofBackend backend = new SqliteVaultProofBackend(
                Path.of(System.getProperty("user.dir"), "world", "data", "futureshops-vault-failure-matrix"), 1_000L);
        SqliteVaultProofProvider provider = new SqliteVaultProofProvider(backend);

        ProviderResult<MutationReceipt> serviceRequestResult;
        MutationRequest serviceRequest = request(MutationKind.WITHDRAW, 10L);
        backend.serviceUnavailable(true);
        ProviderResult<?> unavailableBalance = provider.balance(PLAYER);
        ProviderResult<?> unavailablePrecheck = provider.precheck(serviceRequest);
        serviceRequestResult = provider.withdraw(serviceRequest);
        ProviderResult<?> unavailableLookup = provider.lookup(serviceRequest.requestId());
        helper.assertTrue(unavailableBalance.status() == ProviderResultStatus.UNAVAILABLE
                        && unavailablePrecheck.status() == ProviderResultStatus.UNAVAILABLE
                        && serviceRequestResult.status() == ProviderResultStatus.UNAVAILABLE
                        && unavailableLookup.status() == ProviderResultStatus.UNAVAILABLE,
                "vault service loss must return typed unavailable results");
        backend.serviceUnavailable(false);
        ProviderResult<MutationReceipt> serviceRetry = provider.retry(serviceRequest);
        helper.assertTrue(serviceRetry.confirmed() && backend.balance(PLAYER) == 990L,
                "vault service recovery must retry one request exactly once");

        MutationRequest afterBalance = request(MutationKind.WITHDRAW, 10L);
        backend.interruptAfterBalanceUpdate(true);
        ProviderResult<MutationReceipt> afterBalanceResult = provider.withdraw(afterBalance);
        backend.interruptAfterBalanceUpdate(false);
        helper.assertTrue(afterBalanceResult.status() == ProviderResultStatus.RECOVERY_REQUIRED
                        && backend.balance(PLAYER) == 990L
                        && provider.lookup(afterBalance.requestId()).error() == ProviderError.RECEIPT_NOT_FOUND,
                "vault interruption after balance update must leave no durable effect");
        helper.assertTrue(provider.retry(afterBalance).confirmed() && backend.balance(PLAYER) == 980L,
                "vault retry after rolled back balance update must debit once");

        MutationRequest afterReceipt = request(MutationKind.WITHDRAW, 10L);
        backend.interruptAfterReceiptInsert(true);
        ProviderResult<MutationReceipt> afterReceiptResult = provider.withdraw(afterReceipt);
        backend.interruptAfterReceiptInsert(false);
        helper.assertTrue(afterReceiptResult.status() == ProviderResultStatus.RECOVERY_REQUIRED
                        && backend.balance(PLAYER) == 980L,
                "vault interruption after receipt insert must roll back both records");
        helper.assertTrue(provider.retry(afterReceipt).confirmed() && backend.balance(PLAYER) == 970L,
                "vault retry after rolled back receipt insert must debit once");

        MutationRequest beforeCommit = request(MutationKind.WITHDRAW, 10L);
        backend.interruptBeforeCommit(true);
        ProviderResult<MutationReceipt> beforeCommitResult = provider.withdraw(beforeCommit);
        backend.interruptBeforeCommit(false);
        helper.assertTrue(beforeCommitResult.status() == ProviderResultStatus.RECOVERY_REQUIRED
                        && backend.balance(PLAYER) == 970L,
                "vault interruption before commit must roll back both records");
        helper.assertTrue(provider.retry(beforeCommit).confirmed() && backend.balance(PLAYER) == 960L,
                "vault retry after rolled back commit must debit once");

        MutationRequest afterCommit = request(MutationKind.WITHDRAW, 10L);
        backend.interruptAfterCommit(true);
        ProviderResult<MutationReceipt> afterCommitResult = provider.withdraw(afterCommit);
        backend.interruptAfterCommit(false);
        ProviderResult<MutationReceipt> afterCommitLookup = provider.lookup(afterCommit.requestId());
        long afterCommitBalance = backend.balance(PLAYER);
        ProviderResult<MutationReceipt> afterCommitRetry = provider.retry(afterCommit);
        helper.assertTrue(afterCommitResult.status() == ProviderResultStatus.RECOVERY_REQUIRED
                        && afterCommitLookup.confirmed()
                        && afterCommitRetry.confirmed()
                        && afterCommitBalance == 950L
                        && backend.balance(PLAYER) == 950L,
                "vault interruption after commit must reconcile by receipt without a second debit");

        ProviderResult<MutationReceipt> duplicate = provider.withdraw(afterCommit);
        helper.assertTrue(duplicate.confirmed() && duplicate.receipt().equals(afterCommitLookup.receipt())
                        && backend.balance(PLAYER) == 950L,
                "vault duplicate request must return durable receipt without a second effect");

        var duplicateRegistration = EconomyProviderRegistry.registerVault(
                EconomyApi.COMPATIBILITY_VERSION, ignored -> provider);
        var lateRegistration = EconomyProviderRegistry.register(
                "late_vault_fixture", EconomyApi.COMPATIBILITY_VERSION, ignored -> provider);
        var missingResolution = EconomyProviderRegistry.resolve(
                "missing_vault_fixture", new EconomyProviderContext(anchor.getServer()));
        LOGGER.info("FutureShops Vault failure matrix registration duplicate={} late={} missing_state={}",
                duplicateRegistration.status(), lateRegistration.status(), missingResolution.lifecycle());
        helper.assertTrue(duplicateRegistration.status() == RegistrationStatus.LATE
                        && lateRegistration.status() == RegistrationStatus.LATE
                        && missingResolution.lifecycle() == com.enviouse.futureshopsp.api.economy.ProviderLifecycle.MISSING,
                "vault duplicate, late, and missing bridge states must fail closed");

        LOGGER.info("FutureShops Vault failure matrix provider=vault service_balance={} service_precheck={} service_mutation={} service_lookup={} service_retry={} after_balance={} after_receipt={} before_commit={} after_commit={} after_commit_lookup={} after_commit_retry={} duplicate={} duplicate_registration={} late_registration={} missing_state={} balance={} receipt_count={} journal_mode={} synchronous_mode={} lifecycle={}",
                unavailableBalance.status(), unavailablePrecheck.status(), serviceRequestResult.status(),
                unavailableLookup.status(), serviceRetry.status(), afterBalanceResult.status(),
                afterReceiptResult.status(), beforeCommitResult.status(), afterCommitResult.status(),
                afterCommitLookup.status(), afterCommitRetry.status(), duplicate.status(),
                duplicateRegistration.status(), lateRegistration.status(), missingResolution.lifecycle(),
                backend.balance(PLAYER), backend.receiptCount(), backend.journalMode(),
                backend.synchronousMode(), lifecycle.lifecycle());
        helper.succeed();
    }

    private static MutationRequest request(MutationKind kind, long amount) {
        return MutationRequest.forPlayer(RequestId.random(), PLAYER, amount, kind);
    }
}
