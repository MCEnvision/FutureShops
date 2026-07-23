package com.enviouse.futureshops.server.economy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyWalletCutoverContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void productionBalanceWritesUseEscrowWalletFacade() throws Exception {
        String provider = read("src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java");
        String api = read("src/main/java/com/enviouse/futureshops/api/ShopModAPI.java");
        String admin = read("src/main/java/com/enviouse/futureshops/command/ShopAdminCommand.java");

        for (String source : List.of(provider, api, admin)) {
            assertFalse(source.contains("InternalBalanceSavedData"));
        }
        assertTrue(provider.contains("EscrowWalletService.live()"));
        assertTrue(api.contains("AdministrativeBalanceMutationService.live()"));
        assertTrue(admin.contains("AdministrativeBalanceMutationService.live()"));
        assertFalse(api.contains("BalanceManager.setBalance("));
        assertFalse(admin.contains("BalanceManager.setBalance("));
    }

    @Test
    void legacyBalanceStorageIsIsolatedToMigration() throws Exception {
        Path production = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(production)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(item -> item.toString().endsWith(".java"))
                    .toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.endsWith("InternalBalanceSavedData.java")
                        || normalized.contains("/server/economy/migration/")) {
                    continue;
                }
                if (Files.readString(path).contains("InternalBalanceSavedData")) {
                    violations.add(normalized);
                }
            }
        }

        assertTrue(violations.isEmpty(), violations.toString());
    }

    @Test
    void migrationCompletesBeforeStartingGrantOrWalletReads() throws Exception {
        String provider = read("src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java");

        assertTrue(provider.contains("LegacyBalanceMigrationManager.requireComplete()"));
        int gate = provider.indexOf("LegacyBalanceMigrationManager.requireComplete()", provider.indexOf("initializedBalance"));
        int grant = provider.indexOf("WalletInitializationIds.startingGrant", gate);
        assertTrue(gate >= 0 && grant > gate);
    }

    @Test
    void shopBrowsingUsesReadOnlyBalanceDuringCutover() throws Exception {
        String provider = read(
                "src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java");
        String shop = read(
                "src/main/java/com/enviouse/futureshops/server/shop/ShopDataService.java");

        assertTrue(provider.contains("getDisplayBalance(UUID playerUUID)"));
        assertTrue(provider.contains(
                "LegacyBalanceMigrationManager.displayBalance("));
        assertTrue(provider.contains("EscrowWalletService.storedBalance("));
        assertTrue(shop.contains(
                "BalanceManager.getDisplayBalance(player.getUUID())"));
    }

    @Test
    void transferPostsBothPreEventsBeforeOneWalletCommit() throws Exception {
        String provider = read("src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java");
        String guard = read("src/main/java/com/enviouse/futureshops/server/economy/WalletMutationGuard.java");
        int senderPre = provider.indexOf("BalanceChangeEvent.Pre senderPre");
        int recipientPre = provider.indexOf("BalanceChangeEvent.Pre recipientPre");
        int senderDispatch = provider.indexOf("postPre(senderPre)", senderPre);
        int recipientDispatch = provider.indexOf("postPre(recipientPre)", recipientPre);
        int commit = provider.indexOf("wallet.transfer(", recipientDispatch);
        int postGate = provider.indexOf("result.status() == WalletMutationStatus.APPLIED", commit);
        int senderPost = provider.indexOf("fromPlayerUUID, senderDelta", postGate);
        int recipientPost = provider.indexOf("toPlayerUUID, amountMinorUnits", senderPost);

        assertTrue(senderPre >= 0 && recipientPre > senderPre);
        assertTrue(senderDispatch > recipientPre);
        assertTrue(recipientDispatch > senderDispatch);
        assertTrue(commit > recipientDispatch);
        assertTrue(postGate > commit);
        assertTrue(senderPost > postGate && recipientPost > senderPost);
        assertTrue(provider.contains("WalletMutationGuard.tryAcquire"));
        assertTrue(guard.contains("ACTIVE_ACCOUNTS"));
    }

    @Test
    void keyedRetriesReachWalletBeforeValidationAndEvents() throws Exception {
        String provider = read("src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java");

        assertReplayBeforeWork(provider,
                "public TransactionResult withdraw(UUID requestId",
                "return map(wallet.debit(",
                "long before = initializedBalance");
        assertReplayBeforeWork(provider,
                "public TransactionResult deposit(UUID requestId",
                "return map(wallet.credit(",
                "long before = initializedBalance");
        assertReplayBeforeWork(provider,
                "public TransactionResult transfer(UUID requestId",
                "return map(wallet.transfer(",
                "long fromBefore = initializedBalance");
        assertReplayBeforeWork(provider,
                "public TransactionResult setBalance(UUID requestId",
                "return map(wallet.setBalance(",
                "long before = initializedBalance");
    }

    @Test
    void unsafeDefaultTransferIsGone() throws Exception {
        String economy = read("src/main/java/com/enviouse/futureshops/server/economy/EconomyProvider.java");

        assertFalse(economy.contains("TransactionResult withdrawal ="));
        assertFalse(economy.contains("EconomyProvider.super.transfer"));
        assertTrue(economy.contains("TransactionResult transfer(UUID fromPlayerUUID"));
    }

    @Test
    void topBalancesUseDeterministicWalletSnapshot() throws Exception {
        String provider = read("src/main/java/com/enviouse/futureshops/server/economy/InternalEconomyProvider.java");
        int method = provider.indexOf("getTopBalances(int page, int pageSize)");
        int snapshot = provider.indexOf("snapshotBalances()", method);
        int valueSort = provider.indexOf("comparingByValue", snapshot);
        int uuidTie = provider.indexOf("entry.getKey().toString()", valueSort);
        int longSkip = provider.indexOf("long skip", method);

        assertTrue(method >= 0 && snapshot > method);
        assertTrue(valueSort > snapshot && uuidTie > valueSort);
        assertTrue(longSkip > method && longSkip < snapshot);
    }

    @Test
    void publicKeyedOperationsReachAuditedAdministrativeService() throws Exception {
        String api = read("src/main/java/com/enviouse/futureshops/api/ShopModAPI.java");
        String service = read("src/main/java/com/enviouse/futureshops/server/escrow/admin/balance/AdministrativeBalanceMutationService.java");

        assertTrue(api.contains("withdraw(UUID requestId, UUID playerUUID"));
        assertTrue(api.contains("deposit(UUID requestId, UUID playerUUID"));
        assertTrue(api.contains("transfer(UUID requestId, UUID fromPlayer"));
        assertTrue(api.contains("setBalanceResult("));
        assertTrue(api.contains("AdministrativeBalanceMutationService.live()"));
        assertTrue(service.contains("commitIntent(mutation)"));
    }

    private static void assertReplayBeforeWork(String source,
                                               String methodSignature,
                                               String replayCall,
                                               String firstWork) {
        int method = source.indexOf(methodSignature);
        int gate = source.indexOf(
                "LegacyBalanceMigrationManager.requireComplete()", method);
        int replayCheck = source.indexOf(
                "wallet.wasRequestApplied(requestId)", gate);
        int replay = source.indexOf(replayCall, replayCheck);
        int work = source.indexOf(firstWork, replay);

        assertTrue(method >= 0);
        assertTrue(gate > method);
        assertTrue(replayCheck > gate);
        assertTrue(replay > replayCheck);
        assertTrue(work > replay);
    }
}
