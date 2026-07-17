package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowMaterializerArchitectureTest {
    @Test
    void domainMaterializersAreOnlyCalledByTheJournalApplier() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> forbidden = List.of(
                "transactions.applyCommitted(",
                "ledger.applyCommitted(",
                "claims.createCommitted(",
                "claims.deliverCommitted(",
                "claims.quarantineCommitted(",
                "administrativeAudit.append(",
                "custody.applyCommitted(",
                "custody.prepareCommitted(",
                "custody.applyBatchCommit(",
                "protectedMints.applyCommitted(");
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("EscrowSavedDataMutationApplier.java"))
                    .filter(path -> containsAny(read(path), forbidden))
                    .toList();
            assertEquals(List.of(), violations);
        }
    }

    @Test
    void runtimeExposesOnlyScopedCustodyExecution() {
        Set<String> publicMethods = java.util.Arrays.stream(
                        EscrowRuntimeService.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertTrue(publicMethods.contains("executeCustodyBatch"));
        assertFalse(publicMethods.contains("commitCustodyBatch"));
        assertFalse(publicMethods.contains("commitCustodyMutation"));
        assertFalse(publicMethods.contains("prepareCustodyOperation"));
        assertFalse(com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitter.class
                .isAssignableFrom(EscrowRuntimeService.class));
    }

    @Test
    void runtimeDoesNotExposeRawDomainCommitMethods() {
        Set<String> publicMethods = java.util.Arrays.stream(
                        EscrowRuntimeService.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertFalse(publicMethods.contains("commitTransaction"));
        assertFalse(publicMethods.contains("commitLedger"));
        assertFalse(publicMethods.contains("createClaim"));
        assertFalse(publicMethods.contains("settleMoneyClaim"));
        assertFalse(publicMethods.contains("quarantineClaim"));
        assertFalse(publicMethods.contains("commitAdministrativeAudit"));
        assertFalse(publicMethods.contains("commitProtectedMint"));
        assertFalse(publicMethods.contains("commitAtmWithdrawal"));
        assertFalse(publicMethods.contains("commitMaintenanceRepair"));

        Set<String> coordinatorPublicMethods = java.util.Arrays.stream(
                        EscrowRuntimeCoordinator.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        assertFalse(coordinatorPublicMethods.contains("commitAtmWithdrawal"));

        Set<String> mintPublicMethods = java.util.Arrays.stream(
                        ProtectedMintSavedData.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        assertFalse(mintPublicMethods.contains("issueCommitted"));
    }

    @Test
    void runtimeOpeningIsManagerScopedAndThreadGuarded() {
        Set<String> publicMethods = java.util.Arrays.stream(
                        EscrowRuntimeService.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        assertFalse(publicMethods.contains("open"));

        String service = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java"));
        assertBefore(service, "if (!server.isSameThread())",
                "EscrowRuntimeSavedData.get(server)");

        String manager = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeManager.java"));
        assertBefore(manager, "requireServerThread(server);",
                "EscrowRuntimeService.open(");
    }

    @Test
    void manualReviewWorkKeepsRuntimeInMaintenance() {
        String service = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java"));

        assertTrue(methodBody(service, "state")
                .contains("recoveryScheduler.hasManualReviewWork()"));
        assertTrue(methodBody(service, "domainMaintenanceActive")
                .contains("recoveryScheduler.hasManualReviewWork()"));
    }

    @Test
    void protectedCashEvidenceFailuresTakeMaintenancePrecedence() {
        String service = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java"));
        String state = methodBody(service, "state");

        assertBefore(state, "protectedCashDiscoveryFailure != null",
                "!protectedCashDiscoveryComplete");
        assertBefore(state, "protectedCashCleanupFailure != null",
                "!protectedCashCleanupWork.isEmpty()");
        assertBefore(state, "foreignCashDiscoveryFailure != null",
                "!foreignCashDiscoveryComplete");
        assertBefore(state, "foreignCashCleanupFailure != null",
                "!foreignCashCleanupWork.isEmpty()");
        assertTrue(methodBody(service, "domainMaintenanceActive")
                .contains("protectedCashCleanupFailure != null"));
        assertTrue(methodBody(service, "domainMaintenanceActive")
                .contains("foreignCashCleanupFailure != null"));
        assertTrue(methodBody(service, "maintenanceRecoveryClear")
                .contains("protectedCashCleanupWork.isEmpty()"));
        assertTrue(methodBody(service, "maintenanceRecoveryClear")
                .contains("foreignCashCleanupWork.isEmpty()"));
    }

    @Test
    void corruptEvidenceDoesNotDiscardLaterDiscoveryWork() {
        String service = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java"));

        String protectedDiscovery = methodBody(service,
                "recoverOneProtectedCashDiscovery");
        String foreignDiscovery = methodBody(service,
                "recoverOneForeignCashDiscovery");
        String recovery = methodBody(service, "recoverBatch");

        assertFalse(protectedDiscovery.contains(
                "protectedCashDiscoveryWork.clear()"));
        assertFalse(foreignDiscovery.contains(
                "foreignCashDiscoveryWork.clear()"));
        assertFalse(recovery.contains(
                "protectedCashDiscoveryFailure == null"));
        assertFalse(recovery.contains(
                "foreignCashDiscoveryFailure == null"));
        assertBefore(recovery, "!protectedCashDiscoveryComplete",
                "!protectedCashCleanupWork.isEmpty()");
        assertBefore(recovery, "!foreignCashDiscoveryComplete",
                "!foreignCashCleanupWork.isEmpty()");
    }

    @Test
    void protectedCashIntentAndTerminalEvidenceStayRecoverable() {
        String workflow = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ProtectedCashRedemptionWorkflow.java"));
        String handler = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ProtectedCashRedemptionRecoveryHandler.java"));
        String service = read(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java"));

        assertBefore(workflow, "intentStore.persistIntent(",
                "runtime.commitProtectedCashReservation(");
        assertTrue(workflow.contains(
                "runtime.enqueueProtectedCashIntentRecovery(evidence)"));
        assertTrue(workflow.contains(
                "runtime.scheduleProtectedCashCleanup("));
        assertTrue(handler.contains(
                "runtime.scheduleProtectedCashCleanup("));
        assertTrue(service.contains(
                "protectedCashIntentStore.discover(ownerServer)"));
        assertTrue(service.contains(
                "protectedCashIntentStore.cleanup(ownerServer"));
        assertTrue(java.util.Arrays.stream(
                        EscrowRuntimeService.class.getMethods())
                .anyMatch(method -> method.getName()
                        .equals("redeemProtectedCash")
                        && method.getReturnType()
                        == ProtectedCashRedemptionResult.class));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source", exception);
        }
    }

    private static boolean containsAny(String source, List<String> values) {
        return values.stream().anyMatch(source::contains);
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0 && secondIndex >= 0 && firstIndex < secondIndex);
    }

    private static String methodBody(String source, String methodName) {
        int method = source.indexOf("private boolean " + methodName + "(");
        if (method < 0) {
            method = source.indexOf(" " + methodName + "(");
        }
        if (method < 0) {
            throw new AssertionError("Missing method " + methodName);
        }
        int open = source.indexOf('{', method);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + methodName);
    }
}
