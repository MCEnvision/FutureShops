package com.enviouse.futureshops.server.escrow.admin.balance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdministrativeBalanceProductionCoverageTest {
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void shopAdminMutationsUseExplicitAuditedRequests() throws IOException {
        String command = read(
                "com/enviouse/futureshops/command/ShopAdminCommand.java");

        assertTrue(command.contains(
                "AdministrativeBalanceMutationService.live()"));
        assertTrue(command.contains(
                "AdministrativeBalanceRequestIds.target("));
        assertTrue(command.contains(
                "AdministrativeBalanceConfirmation.EXPLICIT_COMMAND"));
        assertFalse(command.contains("provider.deposit("));
        assertFalse(command.contains("provider.withdraw("));
        assertFalse(command.contains("BalanceManager.deposit("));
        assertFalse(command.contains("BalanceManager.withdraw("));
        assertFalse(command.contains("BalanceManager.setBalance("));
        assertFalse(command.contains("BalanceManager.transfer("));
    }

    @Test
    void publicApiMutationsUseConfirmationAndDeprecatedWrappers()
            throws IOException {
        String api = read(
                "com/enviouse/futureshops/api/ShopModAPI.java");
        String normalized = api.replaceAll("\\s+", " ");

        assertTrue(normalized.contains(
                "String actor, String reason, boolean confirmed"));
        assertTrue(api.contains(
                "AdministrativeBalanceMutationService.live()"));
        assertTrue(api.contains(
                "AdministrativeBalanceConfirmation.LEGACY_API_INVOCATION"));
        assertTrue(api.contains(
                "AdministrativeBalanceConfirmation.UNCONFIRMED"));
        assertTrue(api.contains("LegacyEconomyProvider.INSTANCE"));
        assertTrue(count(api, "@Deprecated") >= 8);
        assertFalse(api.contains("return BalanceManager.getProvider();"));
        assertFalse(api.contains("getProvider().withdraw("));
        assertFalse(api.contains("getProvider().deposit("));
        assertFalse(api.contains("BalanceManager.withdraw("));
        assertFalse(api.contains("BalanceManager.deposit("));
        assertFalse(api.contains("BalanceManager.setBalance("));
        assertFalse(api.contains("BalanceManager.transfer("));
    }

    @Test
    void backendAndEvidenceUseExistingAdministrativeAuditJournal()
            throws IOException {
        String backend = read(
                "com/enviouse/futureshops/server/escrow/runtime/LiveAdministrativeBalanceBackend.java");
        String service = read(
                "com/enviouse/futureshops/server/escrow/admin/balance/AdministrativeBalanceMutationService.java");
        String runtime = read(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeService.java");
        String events = read(
                "com/enviouse/futureshops/server/escrow/runtime/EscrowJournalEventType.java");

        assertTrue(backend.contains("mutation.requestId()"));
        assertFalse(backend.contains("UUID.randomUUID("));
        assertTrue(service.indexOf("commitIntent(mutation)")
                < service.indexOf("backend.apply(mutation)"));
        assertTrue(service.contains(
                "EscrowAdministrativeAction.BALANCE_MUTATION"));
        assertTrue(runtime.contains("administrativeAuditRecord("));
        assertTrue(runtime.contains(
                "EscrowJournalEventType.ADMIN_AUDIT"));
        assertTrue(events.contains("ADMIN_AUDIT(7)"));
        assertFalse(events.contains("BALANCE_MUTATION("));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
