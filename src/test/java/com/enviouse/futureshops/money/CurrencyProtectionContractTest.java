package com.enviouse.futureshops.money;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the intentionally asymmetric internal/foreign physical-currency security contract. */
class CurrencyProtectionContractTest {

    @Test
    void generatedTomlContainsExplicitForeignCurrencyWarning() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/Config.java"));
        assertTrue(config.contains(
                "WARNING. Changing the currency provider from futureshops disables all FutureShops physical currency duplication protection. Currency items from other mods are spawned and accepted without mint ids, checksums, or spent mint tracking."));
    }

    @Test
    void protectedAndForeignAtmRoutesRemainSeparated() throws Exception {
        String protectedPlan = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ProtectedAtmWithdrawalPlan.java"));
        String foreignCommit = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ForeignAtmWithdrawalCommit.java"));

        assertTrue(protectedPlan.contains("ProtectedMoneyMintBridge.plan("));
        assertTrue(protectedPlan.contains("ProtectedMintJournalEvent.issue("));
        assertTrue(protectedPlan.contains("ClaimKind.PROTECTED_CASH"));

        assertTrue(foreignCommit.contains("LedgerAccountType.FOREIGN_CURRENCY_SINK"));
        assertTrue(foreignCommit.contains("ClaimKind.FOREIGN_CASH"));
        assertTrue(foreignCommit.contains("EscrowProtectionLevel.EXTERNAL"));
        assertFalse(foreignCommit.contains("ProtectedMintJournalEvent"));
        assertFalse(foreignCommit.contains("MoneyNbtKeys"));
    }

    @Test
    void everyLiveWithdrawalEntryPointUsesTheEscrowService() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/money/CurrencyWithdrawalService.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/command/WithdrawCommand.java"));
        String atm = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/economy/AtmService.java"));

        assertFalse(service.contains("withdrawAutomatic("));
        assertFalse(service.contains("withdrawSelected("));
        assertFalse(service.contains("getInventory().add("));
        assertTrue(command.contains("AtmService.withdrawAutomatic("));
        assertTrue(atm.contains("EscrowAtmWithdrawalService.withdraw("));
        assertTrue(atm.contains(
                "EscrowAtmWithdrawalService.withdrawAutomatic("));
    }
}
