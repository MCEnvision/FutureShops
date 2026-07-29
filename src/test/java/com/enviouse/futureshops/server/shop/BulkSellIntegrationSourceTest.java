package com.enviouse.futureshops.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkSellIntegrationSourceTest {
    @Test
    void quoteAndCommitRemainServerAuthoritative() throws Exception {
        String service = read(
                "src/main/java/com/enviouse/futureshops/server/shop/BulkSellService.java");

        assertTrue(service.contains(
                "Map<MinecraftServer, Map<UUID, StoredQuote>>"));
        assertTrue(service.contains(
                ".get(player.getUUID())"));
        assertTrue(service.contains(
                "stored.quote.quoteId().equals(quoteId)"));
        assertTrue(service.contains(
                "stored.quote.expiresAtEpochMillis()"));
        assertTrue(service.contains(
                "new LinkedHashSet<>(selected).size()"));
        assertTrue(service.contains(
                "UUID.nameUUIDFromBytes"));
        assertTrue(service.contains(
                "ServerShopOfferService.executeBulkLine"));
        assertTrue(service.contains(
                ".executeBulkOffer("));
        assertTrue(service.contains(
                "line.quotedPayout"));
    }

    @Test
    void quotePreflightChecksDynamicDestinations() throws Exception {
        String service = read(
                "src/main/java/com/enviouse/futureshops/server/shop/BulkSellService.java");
        String admin = read(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ServerShopOfferService.java");
        String player = read(
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopEscrowTransactionService.java");

        assertTrue(service.contains(
                "ServerShopOfferService.canExecuteBulkLine"));
        assertTrue(service.contains(
                ".canExecuteBulkOffer("));
        assertTrue(service.contains(
                "Config.localListingsScanRadiusBlocks"));
        assertTrue(admin.contains(
                "public static boolean canExecuteBulkLine"));
        assertTrue(player.contains(
                "static boolean canExecuteBulkOffer"));
        assertTrue(player.contains(
                "quoteOffer(actor, packet, false)"));
        assertTrue(player.contains(
                "fireEvents && Config.eventsTransactionEnabled"));
        int previewStart = admin.indexOf(
                "public static boolean canExecuteBulkLine");
        int previewEnd = admin.indexOf(
                "private static Result executeInternal", previewStart);
        assertFalse(admin.substring(previewStart, previewEnd)
                .contains("ShopSessionManager"));
        assertTrue(service.contains(
                "candidate.option.itemInputs(), remaining, quantity"));
        assertTrue(service.contains(
                "if (!component.exactMatch())"));
    }

    @Test
    void screensUseAccessibleFutureShopsControls() throws Exception {
        String mode = read(
                "src/main/java/com/enviouse/futureshops/client/screen/BulkSellModeScreen.java");
        String confirmation = read(
                "src/main/java/com/enviouse/futureshops/client/screen/BulkSellConfirmationScreen.java");

        assertTrue(mode.contains("FutureShopsButton.styled"));
        assertTrue(mode.contains("Tooltip.create"));
        assertTrue(confirmation.contains(
                "FutureShopsButton.styled"));
        assertTrue(confirmation.contains("Tooltip.create"));
        assertTrue(confirmation.contains(
                "GLFW.GLFW_KEY_PAGE_DOWN"));
        assertTrue(confirmation.contains(
                "GLFW.GLFW_KEY_PAGE_UP"));
        assertTrue(confirmation.indexOf(
                "super.mouseClicked(mouseX, mouseY, button)")
                < confirmation.indexOf(
                "ShopUiUtil.dispatchClicks("));
    }

    @Test
    void lifecycleClearsMemoryOnlyQuotes() throws Exception {
        String lifecycle = read(
                "src/main/java/com/enviouse/futureshops/Futureshops.java");

        assertTrue(lifecycle.contains(
                "BulkSellService"));
        assertTrue(lifecycle.contains(
                ".clearPlayer(player);"));
        assertTrue(lifecycle.contains(
                ".clearServer(event.getServer());"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
