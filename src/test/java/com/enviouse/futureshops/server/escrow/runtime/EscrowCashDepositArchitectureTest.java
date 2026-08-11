package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowCashDepositArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void everyPublicDepositEntryUsesTheEscrowService() {
        List<Path> entries = List.of(
                MAIN.resolve("com/enviouse/futureshops/command/DepositCommand.java"),
                MAIN.resolve("com/enviouse/futureshops/money/MoneyItem.java"),
                MAIN.resolve("com/enviouse/futureshops/money/CurrencyEvents.java"),
                MAIN.resolve("com/enviouse/futureshops/server/economy/AtmService.java"));

        for (Path entry : entries) {
            String source = read(entry);
            assertTrue(source.contains("EscrowCashDepositService.deposit("));
            assertFalse(source.contains("new MoneyDepositEvent"));
            assertFalse(source.contains("validateAndConsume("));
            assertFalse(source.contains("consumeUpTo("));
            assertFalse(source.contains("provider.deposit("));
        }
    }

    @Test
    void directRoutesReleaseConfigurationLeasesBeforeServiceMonitor() {
        for (Path entry : List.of(
                MAIN.resolve("com/enviouse/futureshops/command/DepositCommand.java"),
                MAIN.resolve("com/enviouse/futureshops/money/MoneyItem.java"),
                MAIN.resolve("com/enviouse/futureshops/money/CurrencyEvents.java"))) {
            String source = read(entry);
            int searchFrom = 0;
            while (true) {
                int lease = source.indexOf(
                        "CurrencyManager.acquireConfigurationReadLease()",
                        searchFrom);
                if (lease < 0) {
                    break;
                }
                int bodyStart = source.indexOf('{', lease);
                String leaseBody = block(source, bodyStart);
                assertTrue(leaseBody.contains(
                        "EscrowCashDepositService.requestForCurrentState("));
                assertFalse(leaseBody.contains(
                        "EscrowCashDepositService.deposit("));
                searchFrom = bodyStart + leaseBody.length();
            }
        }
    }

    @Test
    void replayPrecedesMutablePolicyAndInventoryInspection() {
        String service = depositMethod(read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java")));
        int replay = service.indexOf(
                "Optional<DepositResult> replay = replay(");

        assertTrue(replay >= 0);
        assertBefore(service, replay,
                "withConfigurationReadLease(");
        assertBefore(service, replay,
                "depositWithConfigurationLease(");
        String allSource = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));
        String configured = method(allSource,
                "private static DepositResult depositWithConfigurationLease(");
        int signature = configured.indexOf(
                "request.currencySignature().equals(catalog.signature())");
        assertBefore(configured, signature,
                "request.requestedMinorUnits().getAsLong() <= 0L");
        assertBefore(configured, signature,
                "player.getAbilities().instabuild");
        assertBefore(configured, signature,
                "inspectActiveEvidence(player)");
        assertBefore(configured, signature, "depositProtected(");
        assertBefore(configured, signature, "depositForeign(");
    }

    @Test
    void oneLeaseCoversProtectedAndForeignDepositMutationWindows() {
        String source = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));
        String deposit = depositMethod(source);
        String leaseHelper = method(source,
                "static <T> T withConfigurationReadLease(");
        String configured = method(source,
                "private static DepositResult depositWithConfigurationLease(");

        assertTrue(deposit.contains(
                "result = withConfigurationReadLease("));
        assertTrue(deposit.contains(
                "depositWithConfigurationLease("));
        assertEquals(1, occurrences(leaseHelper,
                "CurrencyManager.acquireConfigurationReadLease()"));
        assertBefore(configured,
                configured.indexOf("AtmCurrencyCatalog.capture("),
                "request.currencySignature().equals(catalog.signature())");
        assertBefore(configured,
                configured.indexOf("request.currencySignature().equals(catalog.signature())"),
                "Config.economyMaxBalanceMinorUnits");
        assertTrue(configured.contains("depositProtected("));
        assertTrue(configured.contains("depositForeign("));
        assertFalse(method(source,
                "private static DepositResult depositProtected(")
                .contains("Config."));
        assertFalse(method(source,
                "private static DepositResult depositForeign(")
                .contains("Config."));
    }

    @Test
    void oneCentralGateProtectsEveryDepositBeforeDurableWork() {
        String service = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));
        String deposit = depositMethod(service);
        String atm = read(MAIN.resolve(
                "com/enviouse/futureshops/server/economy/AtmService.java"));

        assertEquals(2, occurrences(service,
                "ServerRequestAction.ATM_DEPOSIT"));
        assertEquals(0, occurrences(atm,
                "ServerRequestAction.ATM_DEPOSIT"));
        assertBefore(deposit,
                deposit.indexOf("ServerRequestSecurityManager.tryAcquire("),
                "withConfigurationReadLease(");
        assertBefore(deposit,
                deposit.indexOf("ServerRequestSecurityManager.tryAcquire("),
                "depositWithConfigurationLease(");
        String configured = method(service,
                "private static DepositResult depositWithConfigurationLease(");
        assertTrue(configured.contains(
                "economy.getBalance(player.getUUID())"));
    }

    @Test
    void replayCannotPublishAnotherDepositEvent() throws IOException {
        Path servicePath = MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java");
        String service = read(servicePath);
        assertEquals(1, occurrences(service, "new MoneyDepositEvent"));
        assertFalse(method(service,
                "private static Optional<DepositResult> replay(")
                .contains("new MoneyDepositEvent"));
        assertFalse(method(service,
                "static DepositResult completedReplay(")
                .contains("new MoneyDepositEvent"));

        try (var files = Files.walk(MAIN)) {
            List<Path> publishers = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains(
                            "new MoneyDepositEvent"))
                    .toList();
            assertEquals(List.of(servicePath), publishers);
        }
    }

    @Test
    void workflowFailureWindowsRemainRecoverable() {
        for (String name : List.of(
                "ProtectedCashRedemptionWorkflow.java",
                "ForeignCashDepositWorkflow.java")) {
            String source = read(MAIN.resolve(
                    "com/enviouse/futureshops/server/escrow/runtime/"
                            + name));
            int lock = source.indexOf(
                    "if (!activePlayers.add(player.getUUID()))");
            int body = source.indexOf("try {", lock);
            assertTrue(lock >= 0 && body > lock);
            int lockEnd = source.lastIndexOf('}', body);
            assertTrue(lockEnd > lock);
            assertTrue(source.substring(lockEnd + 1, body).isBlank());
            assertBefore(source,
                    source.indexOf("intentPersistAttempted = true;"),
                    "intentStore.persistIntent(server, player, intent)");
            assertBefore(source,
                    source.indexOf("intentStore.persistIntent(server, player, intent)"),
                    "commit", source.indexOf(
                            "intentStore.persistIntent(server, player, intent)"));
            assertTrue(source.contains(
                    "CashDepositRecoveryEnqueueResult enqueueResult ="));
            assertTrue(source.contains(
                    "case NO_DURABLE_EVIDENCE -> discardMatchingIntent("));
            assertTrue(source.contains("case FAILED ->"));
            assertFalse(source.contains(
                    "catch (RuntimeException enqueueFailure) {\n"
                            + "            failure.addSuppressed(enqueueFailure);\n"
                            + "            return true;"));
            assertTrue(source.contains(
                    "activePlayers.remove(player.getUUID())"));
            assertTrue(source.contains("player.containerMenu.broadcastChanges()"));
            assertTrue(source.contains(
                    "throw new CashDepositCancellationCompletedException("));
        }
    }

    @Test
    void terminalSuccessAndReplayDoNotDependOnTheEconomyProvider() {
        String source = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));

        assertFalse(method(source,
                "private static Optional<DepositResult> replay(")
                .contains("BalanceManager"));
        assertFalse(method(source,
                "private static DepositResult success(")
                .contains("BalanceManager"));
        assertTrue(method(source,
                "static DepositResult resolveFailureDisposition(")
                .contains("case COMPLETED"));
        assertTrue(method(source,
                "static DepositResult resolveFailureDisposition(")
                .contains("Status.RECOVERY_REQUIRED"));
    }

    @Test
    void protectedAndForeignEvidenceConflictSymmetrically() {
        String protectedStore = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/redemption/ProtectedCashRedemptionIntentStore.java"));
        String foreignStore = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/ForeignCashDepositIntentStore.java"));
        String service = read(MAIN.resolve(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java"));

        assertTrue(protectedStore.contains(
                "CashDepositEvidenceKeys.hasConflict(persistent,"));
        assertTrue(foreignStore.contains(
                "CashDepositEvidenceKeys.hasConflict(persistent,"));
        assertTrue(service.contains(
                "evidence.playerId().equals(player.getUUID())"));
        assertEquals(2, occurrences(service,
                "evidence.playerId().equals(player.getUUID())"));
        assertTrue(service.contains(
                "return failure(Status.ESCROW_UNAVAILABLE, request,"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source", exception);
        }
    }

    private static String depositMethod(String source) {
        int start = source.indexOf("public static synchronized DepositResult deposit(");
        int end = source.indexOf("static <T> T withConfigurationReadLease(",
                start);
        return source.substring(start, end);
    }

    private static String block(String source, int open) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}' && --depth == 0) {
                return source.substring(open, index + 1);
            }
        }
        throw new IllegalArgumentException("Block is incomplete");
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}' && --depth == 0) {
                return source.substring(open, index + 1);
            }
        }
        throw new IllegalArgumentException("Method body is incomplete");
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private static void assertBefore(
            String source,
            int first,
            String second
    ) {
        int secondIndex = source.indexOf(second);
        assertTrue(first >= 0 && secondIndex > first);
    }

    private static void assertBefore(
            String source,
            int first,
            String second,
            int fromIndex
    ) {
        int secondIndex = source.indexOf(second, fromIndex);
        assertTrue(first >= 0 && secondIndex > first);
    }
}
