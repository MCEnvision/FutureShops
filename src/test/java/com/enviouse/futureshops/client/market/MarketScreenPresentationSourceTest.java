package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketScreenPresentationSourceTest {
    @Test
    void marketHeaderUsesTheExactSharedShopShell() throws Exception {
        String source = screen();
        assertTrue(source.contains(
                "ShopUiUtil.renderShellHeader(graphics, font"));
        assertTrue(source.contains(
                "ShopUiUtil.headerLayout(font"));
        assertTrue(source.contains("marketTabLabels()"));
        assertTrue(source.contains(
                "MarketThemeResolver.resolve(MarketModule.SHOP"));
        assertTrue(source.contains("activeMarketTab()"));
        assertTrue(source.contains("openShopTab(true)"));
        assertTrue(source.contains("openShopTab(false)"));
    }

    @Test
    void detailAndEmptyStatesStayInTheirOwnContentRoutes()
            throws Exception {
        String source = screen();
        int detailRoute = source.indexOf("if (isDetailView())");
        int emptyCards = source.indexOf("if (page.cards().isEmpty())");
        assertTrue(detailRoute >= 0);
        assertTrue(emptyCards > detailRoute);
        assertTrue(source.contains(
                "gui.futureshops.market.detail.hero_subtitle"));
        assertTrue(source.contains(
                "gui.futureshops.market.detail.item_section"));
        assertTrue(source.contains(
                "gui.futureshops.market.detail.activity_section"));
        assertTrue(source.contains("renderPriceChart"));
        assertTrue(source.contains("MarketPriceChartModel.create"));
        assertTrue(source.contains("DETAIL_BLACK"));
        assertTrue(source.contains("ShopColors.ACCENT_GOLD"));
        assertTrue(source.contains("ShopColors.STATUS_SUCCESS"));
        assertTrue(source.contains("ShopColors.STATUS_DANGER"));
        assertTrue(source.contains(
                "gui.futureshops.market.empty.player_catalog_hint"));
    }

    @Test
    void createRouteAlwaysOffersAnAuctionCreationControl()
            throws Exception {
        String source = screen();
        int visibility = source.indexOf(
                "private boolean showCreateListingButton()");
        int opener = source.indexOf("private void openCreateWizard()");
        assertTrue(visibility >= 0);
        assertTrue(opener > visibility);
        String condition = source.substring(visibility, opener);
        assertTrue(condition.contains("\"create\".equals(packet.view())"));
        assertTrue(source.contains(
                "gui.futureshops.market.empty.create_title"));
        assertTrue(source.contains("this::openCreateWizard"));
        assertTrue(source.contains("accentActionButton"));
    }

    @Test
    void playerCatalogActionHasDedicatedFeedback() throws Exception {
        assertEquals("bazaar_register",
                MarketActionFeedback.actionKey("bazaar", "REGISTER"));
        String source = screen();
        assertTrue(source.contains(
                "showBazaarRegistrationButton() && toolbar.width() >= 650"));
        assertTrue(source.contains("this::openBazaarItemBrowser"));
        assertTrue(source.contains(
                "new C2SBazaarRegisterProductPacket("));
        String language = Files.readString(Path.of(
                "src/main/resources/assets/futureshops/lang/en_us.json"));
        assertTrue(language.contains(
                "gui.futureshops.market.action.success.bazaar_register"));
    }

    private static String screen() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java"));
    }
}
