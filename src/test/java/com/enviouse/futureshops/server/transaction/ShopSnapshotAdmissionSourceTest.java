package com.enviouse.futureshops.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSnapshotAdmissionSourceTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void buyRejectsStaleSnapshotBeforeProviderOrFundingAdmission() throws Exception {
        String source = read(
                "src/main/java/com/enviouse/futureshops/server/transaction/ShopBuyService.java");
        int stale = source.indexOf("!packet.sessionId().equals(session.sessionId())");
        int provider = source.indexOf("BalanceManager.isInternalProviderSelected()", stale);
        int funding = source.indexOf("ServerShopFundingReleaseService.resolvePurchase", stale);
        assertTrue(stale >= 0);
        assertTrue(provider > stale);
        assertTrue(funding > stale);
        assertTrue(source.indexOf("ShopResultCode.STALE_REQUEST", stale) < provider);
    }

    @Test
    void sellRejectsStaleSnapshotBeforeProviderOrQuoteAdmission() throws Exception {
        String source = read(
                "src/main/java/com/enviouse/futureshops/server/transaction/ShopSellService.java");
        int stale = source.indexOf("!packet.sessionId().equals(session.sessionId())");
        int provider = source.indexOf("BalanceManager.isInternalProviderSelected()", stale);
        int quote = source.indexOf("prepareQuote(shopId", stale);
        assertTrue(stale >= 0);
        assertTrue(provider > stale);
        assertTrue(quote > stale);
        assertTrue(source.indexOf("ShopResultCode.STALE_REQUEST", stale) < provider);
    }

    @Test
    void staleResponsesRefreshAuthoritativeDataAndPreserveExistingFields() throws Exception {
        String buy = read(
                "src/main/java/com/enviouse/futureshops/server/transaction/ShopBuyService.java");
        String sell = read(
                "src/main/java/com/enviouse/futureshops/server/transaction/ShopSellService.java");
        assertTrue(buy.contains("ShopDataService.sendShopData(player, result.shopId(), false, false)"));
        assertTrue(sell.contains("ShopDataService.sendShopData(player, result.shopId(), false, false)"));
        assertTrue(buy.contains("packet.requestId()"));
        assertTrue(sell.contains("packet.requestId()"));
        assertTrue(buy.contains("packet.cartCheckout()"));
        assertTrue(buy.contains("packet.paymentSource()"));
    }
}
