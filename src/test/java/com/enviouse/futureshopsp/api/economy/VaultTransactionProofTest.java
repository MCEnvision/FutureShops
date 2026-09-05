package com.enviouse.futureshopsp.api.economy;

import com.enviouse.futureshopsp.vaultproof.SqliteVaultProofBackend;
import com.enviouse.futureshopsp.vaultproof.SqliteVaultProofProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proof fixture for the separately installed Vault bridge transaction contract. */
class VaultTransactionProofTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000410");

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        EconomyProviderRegistry.resetForTests();
    }

    @AfterEach
    void tearDown() {
        EconomyProviderRegistry.resetForTests();
    }

    @Test
    void registersThroughPublicVaultBoundaryAndCommitsBalanceWithReceipt() {
        SqliteVaultProofBackend backend = new SqliteVaultProofBackend(directory, 100L);
        EconomyProvider provider = new SqliteVaultProofProvider(backend);

        RegistrationResult registration = EconomyProviderRegistry.registerVault(
                EconomyApi.COMPATIBILITY_VERSION, ignored -> provider);

        assertEquals(RegistrationStatus.ACCEPTED, registration.status());
        assertTrue(EconomyProviderRegistry.snapshot().containsKey(EconomyApi.VAULT_PROVIDER_ID));

        MutationRequest request = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L,
                MutationKind.WITHDRAW);
        ProviderResult<MutationReceipt> result = provider.withdraw(request);

        assertTrue(result.confirmed());
        assertEquals(75L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        assertEquals(result.receipt(), provider.lookup(request.requestId()).receipt());
        assertEquals(result.receipt(), provider.retry(request).receipt());
        assertEquals(75L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());

        assertTrue(Files.exists(backend.databasePath()));
        assertEquals("delete", backend.journalMode());
        assertEquals("full", backend.synchronousMode());
        assertEquals(1, backend.receiptCount());
    }

    @Test
    void interruptedSqliteBoundariesLeaveAtomicStateAndRetryIsSafe() {
        SqliteVaultProofBackend backend = new SqliteVaultProofBackend(directory, 100L);
        EconomyProvider provider = new SqliteVaultProofProvider(backend);

        MutationRequest afterBalance = MutationRequest.forPlayer(RequestId.random(), PLAYER, 10L,
                MutationKind.WITHDRAW);
        backend.interruptAfterBalanceUpdate(true);
        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, provider.withdraw(afterBalance).status());
        assertEquals(100L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        assertEquals(ProviderError.RECEIPT_NOT_FOUND, provider.lookup(afterBalance.requestId()).error());
        backend.interruptAfterBalanceUpdate(false);
        assertTrue(provider.retry(afterBalance).confirmed());
        assertEquals(90L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());

        MutationRequest afterReceipt = MutationRequest.forPlayer(RequestId.random(), PLAYER, 10L,
                MutationKind.WITHDRAW);
        backend.interruptAfterReceiptInsert(true);
        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, provider.withdraw(afterReceipt).status());
        assertEquals(90L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        backend.interruptAfterReceiptInsert(false);
        assertTrue(provider.retry(afterReceipt).confirmed());
        assertEquals(80L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());

        MutationRequest beforeCommit = MutationRequest.forPlayer(RequestId.random(), PLAYER, 10L,
                MutationKind.WITHDRAW);
        backend.interruptBeforeCommit(true);
        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, provider.withdraw(beforeCommit).status());
        assertEquals(80L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        backend.interruptBeforeCommit(false);
        assertTrue(provider.retry(beforeCommit).confirmed());
        assertEquals(70L, provider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());

        SqliteVaultProofBackend reopened = new SqliteVaultProofBackend(directory, 100L);
        EconomyProvider reopenedProvider = new SqliteVaultProofProvider(reopened);
        MutationRequest afterCommit = MutationRequest.forPlayer(RequestId.random(), PLAYER, 10L,
                MutationKind.WITHDRAW);
        reopened.interruptAfterCommit(true);
        assertEquals(ProviderResultStatus.RECOVERY_REQUIRED, reopenedProvider.withdraw(afterCommit).status());
        assertEquals(60L, reopenedProvider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
        assertTrue(reopenedProvider.lookup(afterCommit.requestId()).confirmed());
        reopened.interruptAfterCommit(false);
        assertTrue(reopenedProvider.retry(afterCommit).confirmed());
        assertEquals(60L, reopenedProvider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
    }

    @Test
    void rejectsConflictingIdentityAndDeduplicatesConcurrentRequests() throws Exception {
        SqliteVaultProofBackend backend = new SqliteVaultProofBackend(directory, 100L);
        EconomyProvider provider = new SqliteVaultProofProvider(backend);
        RequestId requestId = RequestId.random();
        MutationRequest request = MutationRequest.forPlayer(requestId, PLAYER, 25L, MutationKind.WITHDRAW);

        ProviderResult<MutationReceipt> first = provider.withdraw(request);
        assertTrue(first.confirmed());
        assertEquals(ProviderError.INVALID_REQUEST, provider.withdraw(
                MutationRequest.forPlayer(requestId, PLAYER, 30L, MutationKind.WITHDRAW)).error());
        assertEquals(ProviderError.INVALID_REQUEST, provider.deposit(
                MutationRequest.forPlayer(requestId, PLAYER, 25L, MutationKind.DEPOSIT)).error());
        UUID otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000411");
        assertEquals(ProviderError.INVALID_REQUEST, provider.withdraw(
                MutationRequest.forPlayer(requestId, otherPlayer, 25L, MutationKind.WITHDRAW)).error());

        MutationRequest insufficient = MutationRequest.forPlayer(RequestId.random(), PLAYER, 1_000L,
                MutationKind.WITHDRAW);
        assertEquals(ProviderError.INSUFFICIENT_FUNDS, provider.withdraw(insufficient).error());
        assertEquals(ProviderError.INSUFFICIENT_FUNDS, provider.retry(insufficient).error());

        SqliteVaultProofBackend concurrentBackend = new SqliteVaultProofBackend(directory.resolve("concurrent"), 100L);
        EconomyProvider concurrentProvider = new SqliteVaultProofProvider(concurrentBackend);
        MutationRequest concurrentRequest = MutationRequest.forPlayer(RequestId.random(), PLAYER, 25L,
                MutationKind.WITHDRAW);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<ProviderResult<MutationReceipt>>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> concurrentProvider.withdraw(concurrentRequest)));
            }
            for (Future<ProviderResult<MutationReceipt>> future : futures) {
                ProviderResult<MutationReceipt> result = future.get();
                assertTrue(result.confirmed());
                assertEquals(concurrentRequest.requestId(), result.receipt().orElseThrow().requestId());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(75L, concurrentProvider.balance(PLAYER).value().orElseThrow().balanceMinorUnits());
    }
}
