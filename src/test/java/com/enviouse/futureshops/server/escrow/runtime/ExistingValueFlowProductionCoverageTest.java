package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistingValueFlowProductionCoverageTest {
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void everyServerShopTradeUsesItsEscrowOrchestrator()
            throws IOException {
        String buy = read(
                "com/enviouse/futureshops/server/transaction/ShopBuyService.java");
        String sell = read(
                "com/enviouse/futureshops/server/transaction/ShopSellService.java");
        String barter = read(
                "com/enviouse/futureshops/server/transaction/ShopBarterService.java");

        assertTrue(buy.contains("ServerShopPurchaseService.purchase"));
        assertTrue(sell.contains("ServerShopSellService.sell"));
        assertTrue(barter.contains("ServerShopBarterService.barter"));
        for (String source : new String[]{buy, sell, barter}) {
            assertFalse(source.contains("PurchasePaymentService"));
            assertFalse(source.contains("provider.deposit("));
            assertFalse(source.contains("provider.withdraw("));
            assertFalse(source.contains("ShopTransactionUtil.remove"));
        }
    }

    @Test
    void everyPlayerShopTradeUsesTheLiveEscrowDriver()
            throws IOException {
        String entrypoints = read(
                "com/enviouse/futureshops/server/shop/PlayerShopBlockService.java");
        String trades = read(
                "com/enviouse/futureshops/server/shop/PlayerShopEscrowTransactionService.java");
        String settlement = read(
                "com/enviouse/futureshops/server/escrow/runtime/PlayerShopSettlementEscrowService.java");

        assertTrue(entrypoints.contains(
                "PlayerShopEscrowTransactionService.buy"));
        assertTrue(entrypoints.contains(
                "PlayerShopEscrowTransactionService.sell"));
        assertTrue(entrypoints.contains(
                "PlayerShopSettlementEscrowService.collect"));
        assertTrue(trades.contains("PlayerShopLiveEscrowService.execute"));
        assertTrue(trades.contains("PlayerShopEscrowIntent"));
        assertTrue(settlement.contains(
                "new PlayerShopEscrowOrchestrator"));
        for (String source : new String[]{entrypoints, trades,
                settlement}) {
            assertFalse(source.contains("PurchasePaymentService"));
            assertFalse(source.contains("provider.deposit("));
            assertFalse(source.contains("provider.withdraw("));
            assertFalse(source.contains("settlementData.recordSale"));
        }
    }

    @Test
    void commandsAndPhysicalCurrencyUseEscrowServices()
            throws IOException {
        String pay = read(
                "com/enviouse/futureshops/command/PayCommand.java");
        String atm = read(
                "com/enviouse/futureshops/server/economy/AtmService.java");
        String deposit = read(
                "com/enviouse/futureshops/command/DepositCommand.java");
        String moneyItem = read(
                "com/enviouse/futureshops/money/MoneyItem.java");

        assertTrue(pay.contains("PlayerPaymentService.pay"));
        assertFalse(pay.contains("BalanceManager.transfer("));
        assertTrue(atm.contains("EscrowAtmWithdrawalService.withdraw"));
        assertTrue(atm.contains("EscrowCashDepositService.deposit"));
        assertTrue(deposit.contains("EscrowCashDepositService.deposit"));
        assertTrue(moneyItem.contains("EscrowCashDepositService.deposit"));
    }

    @Test
    void administrativeBalanceEntryPointsUseAuditedEscrow()
            throws IOException {
        String api = read(
                "com/enviouse/futureshops/api/ShopModAPI.java");
        String command = read(
                "com/enviouse/futureshops/command/ShopAdminCommand.java");

        for (String source : new String[]{api, command}) {
            assertTrue(source.contains(
                    "AdministrativeBalanceMutationService"));
            assertFalse(source.contains("BalanceManager.deposit("));
            assertFalse(source.contains("BalanceManager.withdraw("));
            assertFalse(source.contains("BalanceManager.setBalance("));
            assertFalse(source.contains("BalanceManager.transfer("));
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
