package com.enviouse.futureshopsp.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopModAPISafetySourceTest {
    @Test
    void physicalCoinAggregationUsesCheckedArithmetic() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "api", "ShopModAPI.java")));

        assertTrue(source.contains("Math.addExact(total"));
        assertTrue(source.contains("Math.multiplyExact(result.denominationMinorUnits(), (long) stack.getCount())"));
        assertTrue(!source.contains("total += result.denominationMinorUnits() * stack.getCount()"));
    }

    @Test
    void publicEconomyMutationsUseTheCoordinatorBoundary() throws Exception {
        Path root = projectDirectory();
        String api = Files.readString(root.resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "api", "ShopModAPI.java")));
        String balanceManager = Files.readString(root.resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "economy", "BalanceManager.java")));
        String admin = Files.readString(root.resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "command", "ShopAdminCommand.java")));

        assertTrue(api.contains("return BalanceManager.withdraw(playerUUID, amountMinor)"));
        assertTrue(api.contains("return BalanceManager.deposit(playerUUID, amountMinor)"));
        assertFalse(api.contains("BalanceManager.getProvider().withdraw"));
        assertFalse(api.contains("BalanceManager.getProvider().deposit"));
        assertFalse(admin.contains("provider.deposit("));
        assertFalse(admin.contains("provider.withdraw("));
        assertFalse(balanceManager.contains("return getProvider().transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits)"));
        assertTrue(balanceManager.contains("return mapMutationResult(coordinator().transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits))"));
    }

    @Test
    void gameplayConsumersDoNotReadTheLegacyProviderHandle() throws Exception {
        Path root = projectDirectory();
        String[] consumers = {
                "command/WithdrawCommand.java",
                "command/DepositCommand.java",
                "command/PayCommand.java",
                "command/BalanceCommand.java",
                "command/BalTopCommand.java",
                "command/ShopAdminCommand.java",
                "money/MoneyItem.java",
                "server/shop/MarketplaceAnalyticsService.java",
                "server/shop/ShopDataService.java"
        };
        for (String consumer : consumers) {
            String source = Files.readString(root.resolve(Path.of(
                    "src", "main", "java", "com", "enviouse", "futureshopsp", consumer)));
            assertFalse(source.contains("BalanceManager.getProvider()"), consumer);
        }
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
