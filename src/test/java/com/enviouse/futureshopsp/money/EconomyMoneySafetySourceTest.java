package com.enviouse.futureshopsp.money;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyMoneySafetySourceTest {
    @Test
    void depositDoesNotRestoreCoinsAfterUnknownProviderOutcome() throws Exception {
        String source = read("src/main/java/com/enviouse/futureshopsp/command/DepositCommand.java");

        assertTrue(source.contains("mutation.status() != ProviderResultStatus.AMBIGUOUS"));
        assertTrue(source.contains("mutation.status() != ProviderResultStatus.RECOVERY_REQUIRED"));
        assertTrue(source.contains("deposit mutation requires recovery"));
        assertTrue(source.contains("Math.addExact(totalAvailableMinor"));
        assertTrue(source.contains("Math.multiplyExact(p.denomination, toTake)"));
    }

    @Test
    void moneyAndBillDeliveryFailuresFreezeWithHeldCustody() throws Exception {
        String moneySource = read("src/main/java/com/enviouse/futureshopsp/money/MoneyItem.java");
        String withdrawSource = read("src/main/java/com/enviouse/futureshopsp/command/WithdrawCommand.java");

        assertTrue(moneySource.contains("money deposit compensation requires recovery"));
        assertTrue(moneySource.contains("releaseCustody(requestId.child(\"custody\"))"));
        assertTrue(moneySource.contains("money deposit custody finalization requires recovery"));
        assertTrue(withdrawSource.contains("ShopTransactionUtil.insertIntoInventory(player.getInventory(), mintedStacks)"));
        assertTrue(withdrawSource.contains("withdraw delivery requires recovery"));
        assertTrue(withdrawSource.contains("withdraw custody finalization requires recovery"));
    }

    @Test
    void adminOfflineViewsUseTypedBalanceResults() throws Exception {
        String source = read("src/main/java/com/enviouse/futureshopsp/command/ShopAdminCommand.java");

        assertTrue(source.contains("ProviderResult<BalanceSnapshot> balanceResult = BalanceManager.queryBalance(targetUuid)"));
        assertTrue(source.contains("if (!balanceResult.confirmed())"));
        assertTrue(!source.contains("long balance = provider.getBalance(targetUuid)"));
        assertTrue(source.contains("Math.addExact(activeValue"));
        assertTrue(source.contains("Math.multiplyExact(mint.denomination(), (long) mint.remainingCount())"));
        assertTrue(source.contains("command.futureshops.admin.coinaudit.overflow"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(projectDirectory().resolve(relativePath));
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}
