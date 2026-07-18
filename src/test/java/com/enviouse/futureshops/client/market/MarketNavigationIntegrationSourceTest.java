package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketNavigationIntegrationSourceTest {
    @Test
    void packetHandlerOwnsTheSessionCoordinatorAndRejectsStaleOpens()
            throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/enviouse/futureshops/client/ShopClientPacketHandler.java"));

        assertTrue(source.contains(
            "MarketSessionPreferences.from(ClientConfig.settings())"));
        assertTrue(source.contains("activeMarketNavigation()"));
        assertTrue(source.contains("coordinator.acceptOpenResponse("));
        assertTrue(source.contains("current.prepareNavigationHandoff()"));
        assertTrue(source.contains("MARKET_PREFERENCES.hasRememberedTab(module)"));
        assertTrue(source.contains("MARKET_PREFERENCES.reset()"));
        assertTrue(source.contains(
            "new C2SCloseMarketSessionPacket("));
    }

    @Test
    void marketScreenRoutesAllNavigationAndContentThroughCoordinator()
            throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java"));

        assertTrue(source.contains(
            "MarketClientNavigationCoordinator navigation"));
        assertTrue(source.contains("navigation.acceptResponse("));
        assertTrue(source.contains("navigation.beginResponseRequest("));
        assertTrue(source.contains("navigation.beginSwitchModule("));
        assertTrue(source.contains("navigation.back(UUID.randomUUID())"));
        assertTrue(source.contains("navigation.escape(UUID.randomUUID())"));
        assertTrue(source.contains("synchronizeRoute()"));
        assertTrue(source.contains("closeNavigation(true)"));
        assertTrue(source.contains("new C2SOpenShopPacket(\"default\")"));
        assertTrue(source.contains("navigation.paymentSource()"));
        assertTrue(source.contains(
            "cancelMarketOpen(pendingOpenRequest)"));
        assertTrue(source.contains(
            "registerHit(card, () -> openDetail(cardIndex, data))"));
        assertTrue(source.contains("navigation.beginDetail("));
        assertTrue(source.contains("navigation.rememberDetail(card)"));
        assertTrue(source.contains("activateFocusedCard()"));
        assertTrue(source.contains("GLFW.GLFW_KEY_KP_ENTER"));
        assertTrue(source.contains("MarketRoute.detailView(module)"));
        assertTrue(source.contains("renderSecondaryTabs("));
        assertTrue(source.contains("renderCategoryDrawer("));
        assertTrue(source.contains("MarketCompactPager.nextOffset("));
        assertTrue(source.contains("handleCategoryDrawerKey(keyCode)"));
        assertTrue(source.contains("cycleLocalView(hasShiftDown())"));
        assertTrue(source.contains("headerControls.balance()"));
        assertTrue(source.contains("headerControls.profile()"));
        assertTrue(source.contains("headerControls.notifications()"));
        assertTrue(source.contains("headerControls.claims()"));
        assertTrue(source.contains("font.plainSubstrByWidth(label"));
        assertTrue(source.contains(
                "gui.futureshops.market.view."));
        assertTrue(source.contains(
                "gui.futureshops.market.sort."));
        assertTrue(source.contains(
                "gui.futureshops.market.empty.no_results"));
        assertTrue(source.contains(
                "gui.futureshops.market.all_categories"));
        assertFalse(source.contains("case \"seller\" -> \"Seller\""));
        assertFalse(source.contains("No matching market entries"));
        // Protocol 45 (plan §8/§9/§12): the shell's action surface sends the auction/bazaar
        // mutation packets — every send goes through sendMarketAction with a fresh request id
        // and the current route nonce (the read-only-shell era pinned the opposite here).
        assertTrue(source.contains("C2SBazaarOrderPacket"));
        assertTrue(source.contains("C2SAuctionBidPacket"));
        assertTrue(source.contains("sendMarketAction("));
        assertFalse(source.contains("new MarketRequestGate("));
    }

    @Test
    void serverAcceptsReadOnlyDetailRoutesThroughTheExistingOpenPacket()
            throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/enviouse/futureshops/server/market/MarketModuleService.java"));

        assertTrue(source.contains("\"listing_detail\""));
        assertTrue(source.contains("\"product_detail\""));
        assertFalse(source.contains("C2SMarketDetail"));
    }
}
