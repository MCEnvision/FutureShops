package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketScreenPresentationSourceTest {
    @Test
    void marketHeaderReservesBrandAndMeasuredTabSpace() throws Exception {
        String source = screen();
        assertTrue(source.contains("int brandWidth = Math.max(92"));
        assertTrue(source.contains("font.width(moduleLabel(target, true)"));
        assertTrue(source.contains(
                "tabX + tabsWidth <= headerControls.search().x() - 6"));
        assertTrue(source.contains(
                "showNavigation() && !headerModuleTabsVisible"));
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
                "gui.futureshops.market.detail.hero_meta"));
        assertTrue(source.contains(
                "gui.futureshops.market.detail.item_section"));
        assertTrue(source.contains(
                "gui.futureshops.market.empty.player_catalog_hint"));
    }

    @Test
    void playerCatalogActionHasDedicatedFeedback() throws Exception {
        assertEquals("bazaar_register",
                MarketActionFeedback.actionKey("bazaar", "REGISTER"));
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
