package com.enviouse.futureshopsp.server.economy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps every monetary entry point on the capability gated coordinator boundary.
 */
class ExternalEconomyMutationSurfaceSourceTest {
    private static final List<String> DIRECT_COORDINATOR_ROUTES = List.of(
            "src/main/java/com/enviouse/futureshopsp/server/transaction/ShopBuyService.java",
            "src/main/java/com/enviouse/futureshopsp/server/transaction/ShopSellService.java",
            "src/main/java/com/enviouse/futureshopsp/server/shop/PlayerShopBlockService.java",
            "src/main/java/com/enviouse/futureshopsp/command/WithdrawCommand.java",
            "src/main/java/com/enviouse/futureshopsp/command/DepositCommand.java",
            "src/main/java/com/enviouse/futureshopsp/money/MoneyItem.java");

    @Test
    void directMutationRoutesUseCoordinatorCapabilityGate() throws Exception {
        for (String route : DIRECT_COORDINATOR_ROUTES) {
            String source = read(route);
            assertTrue(source.contains("BalanceManager.getCoordinator()"),
                    () -> route + " must use the capability gated coordinator");
            assertTrue(source.contains("executeWithCustody") || source.contains("preflight")
                            || source.contains(".deposit(") || source.contains(".withdraw("),
                    () -> route + " must preflight before a provider mutation");
        }
    }

    @Test
    void facadeAndCommandRoutesDelegateToBalanceManager() throws Exception {
        String api = read("src/main/java/com/enviouse/futureshopsp/api/ShopModAPI.java");
        assertTrue(api.contains("BalanceManager.withdraw"));
        assertTrue(api.contains("BalanceManager.deposit"));
        assertTrue(api.contains("BalanceManager.transfer"));

        String pay = read("src/main/java/com/enviouse/futureshopsp/command/PayCommand.java");
        assertTrue(pay.contains("BalanceManager.transfer"));

        String admin = read("src/main/java/com/enviouse/futureshopsp/command/ShopAdminCommand.java");
        assertTrue(admin.contains("BalanceManager.deposit"));
        assertTrue(admin.contains("BalanceManager.withdraw"));
        assertTrue(admin.contains("BalanceManager.setInternalBalance"));
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
