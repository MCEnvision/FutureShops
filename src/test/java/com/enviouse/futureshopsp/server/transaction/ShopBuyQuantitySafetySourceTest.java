package com.enviouse.futureshopsp.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBuyQuantitySafetySourceTest {
    @Test
    void overflowingDuplicateListingIsRejected() {
        assertEquals(Map.of(), ShopBuyService.mergeLines(List.of(
                new com.enviouse.futureshopsp.network.packets.C2SBuyRequestPacket.LineItem("variant", Integer.MAX_VALUE),
                new com.enviouse.futureshopsp.network.packets.C2SBuyRequestPacket.LineItem("variant", 1))));
    }

    @Test
    void buyQuantitiesUseCheckedArithmeticBeforeMutation() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopBuyService.java")));

        assertTrue(source.contains("totalQuantity = Math.addExact(totalQuantity, quantity);"));
        assertTrue(source.contains("merged.merge(lineItem.listingId(), lineItem.quantity(), Math::addExact);"));
        assertTrue(source.contains("return Map.of();"));
        assertTrue(source.indexOf("totalQuantity = Math.addExact(totalQuantity, quantity);")
                < source.indexOf("coordinator.executeWithCustody(debitRequest"));
    }

    @Test
    void malformedCatalogNbtIsRejectedBeforeRewardsAreBuilt() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopBuyService.java")));

        assertTrue(source.contains("return BuyResult.error(shopId, balanceView(player.getUUID()), ShopResultCode.INVALID_ITEM);"));
        assertTrue(source.contains("Invalid catalog SNBT"));
        assertTrue(!source.contains("delivering bare item"));
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
