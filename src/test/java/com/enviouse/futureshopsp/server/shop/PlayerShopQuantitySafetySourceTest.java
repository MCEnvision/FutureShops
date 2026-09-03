package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopQuantitySafetySourceTest {
    @Test
    void playerShopQuantityPathsUseCheckedMultiplication() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        assertTrue(source.contains("int needed = checkedDeliveryCount(entry.count(), qty);"));
        assertTrue(source.contains("int needItems = checkedDeliveryCount(baseQty, qty);"));
        assertTrue(source.contains("quantity > ShopTransactionUtil.MAX_BUY_QUANTITY"));
        assertTrue(source.contains("int qty = quantity;"));
        assertTrue(source.contains("requestedQuantity <= 0 || requestedQuantity > ShopTransactionUtil.MAX_SELL_QUANTITY"));
        assertTrue(source.contains("int qty = requestedQuantity;"));
        assertFalse(source.contains("Math.max(1, Math.min(packet.quantity(), ShopTransactionUtil.MAX_SELL_QUANTITY))"));
        assertFalse(source.contains("entry.count() * qty"));
        assertFalse(source.contains("baseQty * qty"));
    }

    @Test
    void buybackCounterOverflowIsRejectedBeforeAnyValueLeg() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "shop",
                "PlayerShopBlockService.java")));

        int sellHandler = source.indexOf("public static void handleSell");
        int capCheck = source.indexOf("if (!canRecordBuyback(listing, qty))", sellHandler);
        int firstProviderPreflight = source.indexOf("coordinator.preflight", sellHandler);
        assertTrue(sellHandler >= 0);
        assertTrue(capCheck > sellHandler);
        assertTrue(firstProviderPreflight > capCheck);
        assertTrue(source.contains("Math.addExact(listing.buybackBought(), qty)"));
        assertFalse(source.contains("listing.buybackBought() + qty"));
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
