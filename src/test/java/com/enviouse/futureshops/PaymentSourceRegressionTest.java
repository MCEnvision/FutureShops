package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentSourceRegressionTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void purchasePacketsKeepTradeMethodAndPaymentSourceSeparate() throws Exception {
        String adminPacket = read("src/main/java/com/enviouse/futureshops/network/packets/C2SBuyRequestPacket.java");
        String playerPacket = read("src/main/java/com/enviouse/futureshops/network/packets/C2SPlayerShopBuyPacket.java");
        assertTrue(adminPacket.contains("String paymentSource"));
        assertTrue(adminPacket.contains("buffer.writeUtf(packet.paymentSource)"));
        assertTrue(playerPacket.contains("String paymentMethod"));
        assertTrue(playerPacket.contains("String paymentSource"));
        assertTrue(playerPacket.contains(
                "buffer.writeUtf(packet.paymentMethod(), MAX_PAYMENT_METHOD_LENGTH)"));
        assertTrue(playerPacket.contains(
                "buffer.writeUtf(packet.paymentSource(), MAX_PAYMENT_SOURCE_LENGTH)"));
    }

    @Test
    void everyMoneyPurchaseSurfaceUsesThePaymentChooser() throws Exception {
        for (String file : List.of(
                "ItemDetailScreen.java",
                "CartScreen.java",
                "PlayerShopBlockScreen.java",
                "PlayerStorefrontScreen.java",
                "PlayerShopBarterScreen.java",
                "PlayerShopCartScreen.java")) {
            String source = read("src/main/java/com/enviouse/futureshops/client/screen/" + file);
            assertTrue(source.contains("paymentSource"), file);
        }
        String modal = read("src/main/java/com/enviouse/futureshops/client/screen/ConfirmationModal.java");
        assertTrue(modal.contains("selectedPaymentSource"));
        assertTrue(modal.contains("selectedPaymentSource != null"));
        assertTrue(modal.contains("PaymentSource.PHYSICAL"));
        assertTrue(modal.contains("PaymentSource.WALLET"));
    }

    @Test
    void physicalPurchasesEnterDeterministicEscrowCustody() throws Exception {
        String service = read("src/main/java/com/enviouse/futureshops/server/escrow/runtime/PlayerShopLiveEscrowService.java");
        String deposit = read("src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java");
        String internal = read("src/main/java/com/enviouse/futureshops/money/InternalCurrencyAdapter.java");
        String foreign = read("src/main/java/com/enviouse/futureshops/money/ItemValueCurrencyAdapter.java");
        assertTrue(service.contains("depositForEscrow(actor"));
        assertTrue(service.contains("walletCreditMinorUnits() != 0L"));
        assertTrue(service.contains("overflowClaimMinorUnits() != total"));
        assertTrue(service.contains("physicalMoneyLedger"));
        assertTrue(service.contains("completePhysicalFundingClaims"));
        assertTrue(deposit.contains("claimOnly"));
        assertTrue(deposit.contains("economy.getBalance(player.getUUID())"));
        assertTrue(internal.contains("SpentMintsSavedData.ConsumeResult"));
        assertTrue(foreign.contains("consumeExact"));
    }

    @Test
    void fullWalletInventoryCashPurchaseUsesClaimCustody() throws Exception {
        String trade = read("src/main/java/com/enviouse/futureshops/server/shop/PlayerShopEscrowTransactionService.java");
        String live = read("src/main/java/com/enviouse/futureshops/server/escrow/runtime/PlayerShopLiveEscrowService.java");
        String deposit = read("src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowCashDepositService.java");

        assertTrue(trade.contains(
                "PlayerShopPaymentSource.INVENTORY_CASH"));
        assertTrue(trade.contains(
                "PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE"));
        assertFalse(trade.contains("Math.addExact(balance, cost)"));
        assertTrue(live.contains("depositForEscrow(actor"));
        assertTrue(live.contains("LedgerAccountType.PLAYER_CLAIM"));
        assertTrue(live.contains("overflowClaimMinorUnits() != total"));
        assertTrue(deposit.contains(
                "return claimOnly ? 0L"));
    }

    @Test
    void bothServerPurchaseEnginesHonorTheChosenSource() throws Exception {
        String admin = read("src/main/java/com/enviouse/futureshops/server/transaction/ShopBuyService.java");
        String bridge = read("src/main/java/com/enviouse/futureshops/server/transaction/ServerShopPhysicalFundingBridge.java");
        String commit = read("src/main/java/com/enviouse/futureshops/server/escrow/runtime/ServerShopPurchaseCommit.java");
        String player = read("src/main/java/com/enviouse/futureshops/server/shop/PlayerShopBlockService.java");
        String live = read("src/main/java/com/enviouse/futureshops/server/shop/PlayerShopEscrowTransactionService.java");
        assertTrue(admin.contains("ServerShopPurchaseService.purchase"));
        assertTrue(admin.contains("ServerShopPhysicalFundingBridge.fund"));
        assertTrue(admin.indexOf("ServerShopPurchaseService.resolveReplay")
                < admin.indexOf("ServerShopPhysicalFundingBridge.fund"));
        assertTrue(bridge.contains("CashDepositMode.INTERNAL_ESCROW"));
        assertTrue(bridge.contains("depositForEscrow(player, request)"));
        assertFalse(bridge.contains(
                "EscrowCashDepositService.deposit(player, request)"));
        assertTrue(commit.contains("claimAccount(value.claimId())"));
        assertTrue(commit.contains("physicalFundingDeliveryKey"));
        assertTrue(player.contains("PlayerShopEscrowTransactionService.buy"));
        assertTrue(live.contains("PlayerShopLiveEscrowService.execute"));
        assertTrue(live.contains("PlayerShopPaymentSource.INVENTORY_CASH"));
        assertFalse(player.contains("PurchasePaymentService.charge"));
        assertFalse(player.contains("PurchasePaymentService.refund"));
    }
}
