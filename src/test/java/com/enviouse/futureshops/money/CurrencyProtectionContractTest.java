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
        assertTrue(config.contains("lose ALL FutureShops"));
        assertTrue(config.contains("WITHOUT checksums"));
        assertTrue(config.contains("spent-mint ledger"));
    }

    @Test
    void internalMintStillRegistersLedgerWhileForeignMintStaysPlain() throws Exception {
        String internal = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/money/InternalCurrencyAdapter.java"));
        String foreign = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/money/ItemValueCurrencyAdapter.java"));

        assertTrue(internal.contains("return true;"), "internal adapter must continue reporting protected mode");
        assertTrue(internal.contains("registerMint("), "internal ATM/command mints must enter the ledger");
        assertTrue(internal.contains("MoneyMintService.mintStack"), "internal bills must retain checksummed mint NBT");

        assertTrue(foreign.contains("new ItemStack(denomination.item(), count)"),
                "foreign ATM mints must be plain source-mod items");
        assertFalse(foreign.contains("registerMint("), "foreign items must never enter the FutureShops ledger");
        assertFalse(foreign.contains("MoneyNbtKeys"), "foreign items must not receive FutureShops protection tags");
    }

    @Test
    void withdrawalEngineRemovesPartialDeliveryBeforeRefund() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/money/CurrencyWithdrawalService.java"));
        int remove = service.indexOf("for (ItemStack template : inserted)");
        int refund = service.indexOf("provider.deposit(player.getUUID(), amount, \"WITHDRAW_ROLLBACK\")");
        assertTrue(remove >= 0 && refund > remove,
                "a failed delivery must remove already-inserted bills before refunding the balance");
    }
}
