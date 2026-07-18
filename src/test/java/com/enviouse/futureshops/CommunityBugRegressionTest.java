package com.enviouse.futureshops;

import com.enviouse.futureshops.client.screen.AdminItemSearchPolicy;
import com.enviouse.futureshops.client.screen.ClientNavigationPolicy;
import com.enviouse.futureshops.client.screen.HistoryTimestampFormatter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityBugRegressionTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void playerShopDetailHasBackAndCloseWithDifferentDestinations() throws Exception {
        String source = read("src/main/java/com/enviouse/futureshops/client/screen/PlayerStorefrontScreen.java");
        String block = read("src/main/java/com/enviouse/futureshops/client/screen/PlayerShopBlockScreen.java");
        assertTrue(source.contains("gui.futureshops.player_shop_block.visitor.back"));
        assertTrue(source.contains("returnToParent();"));
        assertTrue(source.contains("this.minecraft.setScreen(null);"));
        assertTrue(block.contains("renderBackButton(graphics);"));
        assertTrue(block.contains("if (parent == null)"));
        assertTrue(block.contains("this::closeCompletely"));
    }

    @Test
    void historyUsesArrowClaimAndConfigurableClock() throws Exception {
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");
        String config = read("src/main/java/com/enviouse/futureshops/ClientConfig.java");
        assertTrue(language.contains("\"gui.futureshops.history.back\": \"§7← Back\""));
        assertTrue(language.contains("\"gui.futureshops.history.filter.cart_claim\": \"CLAIM\""));
        assertFalse(language.contains("\"gui.futureshops.history.filter.cart_claim\": \"FUNDS\""));
        assertTrue(config.contains("ui.use_12_hour_time"));
        assertTrue(config.contains("ui.currency_symbol"));

        long timestamp = Instant.parse("2026-07-17T13:05:00Z")
                .getEpochSecond();
        assertEquals("07-17 13:05", HistoryTimestampFormatter.format(
                timestamp, false, ZoneId.of("UTC")));
        assertEquals("07-17 1:05 PM", HistoryTimestampFormatter.format(
                timestamp, true, ZoneId.of("UTC")));
    }

    @Test
    void barterEditorSupportsPickerHeldItemsAndSteppers() throws Exception {
        String shop = read("src/main/java/com/enviouse/futureshops/client/screen/ShopMainScreen.java");
        String editor = read("src/main/java/com/enviouse/futureshops/client/screen/BarterRecipeEditorScreen.java");
        String picker = read("src/main/java/com/enviouse/futureshops/client/screen/AdminItemPickerScreen.java");
        assertTrue(shop.contains("gui.futureshops.admin_edit.add_barter_items"));
        assertTrue(shop.contains("gui.futureshops.admin_edit.add_barter_held"));
        assertTrue(shop.contains("if (tradeFilter == 0)"));
        assertTrue(shop.contains("this, activeCategoryId(), true"));
        assertTrue(editor.contains("openIngredientPicker"));
        assertTrue(editor.contains("forBarterIngredients"));
        assertTrue(editor.contains("setIngredientCount"));
        assertTrue(editor.contains("ShopUiUtil.renderStepper"));
        assertTrue(picker.contains("LinkedHashSet<String> selectedIds"));
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");
        assertTrue(language.contains(
                "\"gui.futureshops.barter_editor.hold_label\": \"Held Item:\""));
    }

    @Test
    void modSearchIsPredictiveBeforeNamespaceIsComplete() throws Exception {
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");
        assertTrue(language.contains("Search by name, id, or @mod"));
        assertTrue(AdminItemSearchPolicy.matches(
                "minecraft:diamond", "minecraft:diamond diamond", "@mine"));
        assertFalse(AdminItemSearchPolicy.matches(
                "minecraft:diamond", "minecraft:diamond diamond", "@create"));
        assertTrue(AdminItemSearchPolicy.matches(
                "ae2:certus_quartz_crystal", "certus quartz",
                "Applied Energistics 2", "@Applied"));
        assertTrue(AdminItemSearchPolicy.matches(
                "mcwdoors:oak_barn_door", "oak barn door",
                "Macaw's Doors", "@Macaw"));
    }

    @Test
    void sharedHeaderAndMarketChromeStayDiscoverable() throws Exception {
        String ui = read("src/main/java/com/enviouse/futureshops/client/screen/ShopUiUtil.java");
        String market = read("src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java");
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");
        assertTrue(ui.contains("renderPlayerFace(g, playerUuid"));
        assertTrue(ui.contains("renderShellHeaderTooltip"));
        assertTrue(language.contains("Marketplace Profile"));
        assertTrue(market.contains("MarketBackNavigationPolicy.show"));
        assertTrue(market.contains("ShopUiUtil.renderBreadcrumb"));
        assertTrue(market.contains("createWizardWorkspace"));
    }

    @Test
    void atmDepositGuidanceWrapsAndBazaarExplainsCatalogControl()
            throws Exception {
        String atm = read("src/main/java/com/enviouse/futureshops/client/screen/AtmScreen.java");
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");
        assertTrue(atm.contains("drawWrappedDepositText"));
        assertTrue(atm.contains("this.font.split"));
        assertTrue(language.contains("Admin catalog mode"));
        assertTrue(language.contains("Player catalog mode"));
    }

    @Test
    void profileBackPreservesContextAndCloseOpensDefaultShop() {
        assertEquals(ClientNavigationPolicy.ProfileAction.RETURN_TO_PARENT,
                ClientNavigationPolicy.profileBack());
        assertEquals(ClientNavigationPolicy.ProfileAction.OPEN_DEFAULT_SHOP,
                ClientNavigationPolicy.profileClose());
    }

    @Test
    void serverShopDefaultsToAllAndShowsCartFeedback() throws Exception {
        String shop = read("src/main/java/com/enviouse/futureshops/client/screen/ShopMainScreen.java");
        String state = read("src/main/java/com/enviouse/futureshops/client/ShopClientState.java");
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");

        assertTrue(shop.contains(
                "ServerShopTradeFilterPolicy.defaultFilter().ordinal()"));
        assertTrue(shop.contains("ShopClientState.getCartTotalQuantity()"));
        assertTrue(state.contains(
                "gui.futureshops.status.cart.added"));
        assertTrue(language.contains(
                "Added %s× %s to your cart."));
    }

    @Test
    void marketHeaderUsesValidEntryViewsAndSearchText() throws Exception {
        String shop = read("src/main/java/com/enviouse/futureshops/client/screen/ShopMainScreen.java");
        String market = read("src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java");

        assertTrue(shop.contains("String view = target.rootView();"));
        assertFalse(shop.contains("String view = \"\";"));
        assertTrue(market.contains("String querySearch = normalizedSearch();"));
        assertTrue(market.contains("return observedSearch.strip();"));
    }
}
