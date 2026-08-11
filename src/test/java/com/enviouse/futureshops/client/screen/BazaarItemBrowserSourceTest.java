package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarItemBrowserSourceTest {
    @Test
    void playerBrowserIncludesEveryRegisteredBaseItemAndSearchMode()
            throws Exception {
        String browser = read(
                "src/main/java/com/enviouse/futureshops/client/screen/BazaarItemBrowserScreen.java");

        assertTrue(browser.contains("for (Item item : ForgeRegistries.ITEMS)"));
        assertTrue(browser.contains("if (item == Items.AIR)"));
        assertTrue(browser.contains("builtInRegistryHolder().tags()"));
        assertTrue(browser.contains("AdminItemSearchPolicy.matches("));
        assertTrue(browser.contains("selectBazaarRegistryItem(itemId)"));
        assertTrue(browser.contains("selectedNamespace.equals(entry.namespace())"));

        String market = read(
                "src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java");
        assertTrue(market.contains(
                "|| PENDING_ACTIONS.anyPending())"));
    }

    @Test
    void serverResolvesTheSelectedRegistryItemWithoutUsingAHand()
            throws Exception {
        String service = read(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/BazaarActionService.java");
        int start = service.indexOf("public static void registerProduct(");
        int end = service.indexOf("static String playerProductId(", start);
        String registration = service.substring(start, end);

        assertTrue(registration.contains("packet.registryItemId()"));
        assertTrue(registration.contains("ForgeRegistries.ITEMS.getValue"));
        assertFalse(registration.contains("getMainHandItem"));
        assertFalse(registration.contains("held.hasTag"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
