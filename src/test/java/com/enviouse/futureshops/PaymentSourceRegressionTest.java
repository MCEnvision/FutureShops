package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertTrue(playerPacket.contains("String paymentMethod, String paymentSource"));
        assertTrue(playerPacket.contains("buffer.writeUtf(packet.paymentMethod())"));
        assertTrue(playerPacket.contains("buffer.writeUtf(packet.paymentSource())"));
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
    void physicalPurchasesAreExactProtectedAndRefundable() throws Exception {
        String service = read("src/main/java/com/enviouse/futureshops/money/PurchasePaymentService.java");
        String internal = read("src/main/java/com/enviouse/futureshops/money/InternalCurrencyAdapter.java");
        String foreign = read("src/main/java/com/enviouse/futureshops/money/ItemValueCurrencyAdapter.java");
        assertTrue(service.contains("destroyCounterfeit(player)"));
        assertTrue(service.contains("consumeExact(player, amountMinor)"));
        assertTrue(service.contains("refundExact(player, receipt.physicalPayment())"));
        assertTrue(internal.contains("SpentMintsSavedData.ConsumeResult"));
        assertTrue(internal.contains("refundExact"));
        assertTrue(foreign.contains("consumeExact"));
        assertTrue(foreign.contains("refundableStack"));
    }

    @Test
    void bothServerPurchaseEnginesChargeAndRollbackTheChosenSource() throws Exception {
        String admin = read("src/main/java/com/enviouse/futureshops/server/transaction/ShopBuyService.java");
        String player = read("src/main/java/com/enviouse/futureshops/server/shop/PlayerShopBlockService.java");
        assertTrue(admin.contains("PurchasePaymentService.charge"));
        assertTrue(admin.contains("PurchasePaymentService.refund"));
        assertTrue(player.contains("PurchasePaymentService.charge"));
        assertTrue(player.contains("PurchasePaymentService.refund"));
    }
}
