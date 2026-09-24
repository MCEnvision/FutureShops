package com.enviouse.futureshopsp.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingQuoteSnapshotSourceTest {
    @Test
    void detailConfirmationsRetainTheDisplayedRevision() throws Exception {
        String source = screenSource("ItemDetailScreen.java");
        assertEquals(2, source.split("long quotedRevision = ShopClientState.getSnapshotRevision\\(\\);", -1).length - 1);
        assertEquals(2, source.split("quotedRevision\\)\\);", -1).length - 1);
        assertFalse(source.contains("ShopClientState.getSnapshotRevision()));"));
    }

    @Test
    void cartConfirmationRetainsBothLinesAndRevision() throws Exception {
        String source = screenSource("CartScreen.java");
        assertTrue(source.contains("List.copyOf(ShopClientState.getCartEntries())"));
        assertTrue(source.contains("sendCheckout(shopId, entries, quotedRevision)"));
        assertTrue(source.contains("C2SBuyRequestPacket.cart(shopId, lines, quotedRevision)"));
        assertFalse(source.contains("sendCheckout()"));
    }

    private static String screenSource(String filename) throws Exception {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path source = candidate.resolve("src/main/java/com/enviouse/futureshopsp/client/screen").resolve(filename);
            if (Files.isRegularFile(source)) return Files.readString(source);
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops screen source is unavailable");
    }
}
