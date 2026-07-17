package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityBugRegressionTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void playerShopDetailHasBackAndCloseWithDifferentDestinations() throws Exception {
        String source = read("src/main/java/com/enviouse/futureshops/client/screen/PlayerStorefrontScreen.java");
        assertTrue(source.contains("gui.futureshops.player_shop_block.visitor.back"));
        assertTrue(source.contains("returnToParent();"));
        assertTrue(source.contains("this.minecraft.setScreen(null);"));
    }

    @Test
    void historyUsesArrowClaimAndConfigurableClock() throws Exception {
        String language = read("src/main/resources/assets/futureshops/lang/en_us.json");
        String history = read("src/main/java/com/enviouse/futureshops/client/screen/TransactionHistoryScreen.java");
        String config = read("src/main/java/com/enviouse/futureshops/ClientConfig.java");
        assertTrue(language.contains("\"gui.futureshops.history.back\": \"§7← Back\""));
        assertTrue(language.contains("\"gui.futureshops.history.filter.cart_claim\": \"CLAIM\""));
        assertFalse(language.contains("\"gui.futureshops.history.filter.cart_claim\": \"FUNDS\""));
        assertTrue(history.contains("ClientConfig.use12HourTime()"));
        assertTrue(history.contains("MM-dd h:mm a"));
        assertTrue(config.contains("ui.use_12_hour_time"));
    }

    @Test
    void barterEditorSupportsPickerHeldItemsAndSteppers() throws Exception {
        String shop = read("src/main/java/com/enviouse/futureshops/client/screen/ShopMainScreen.java");
        String editor = read("src/main/java/com/enviouse/futureshops/client/screen/BarterRecipeEditorScreen.java");
        String picker = read("src/main/java/com/enviouse/futureshops/client/screen/AdminItemPickerScreen.java");
        assertTrue(shop.contains("gui.futureshops.admin_edit.add_barter_items"));
        assertTrue(shop.contains("gui.futureshops.admin_edit.add_barter_held"));
        assertTrue(editor.contains("openIngredientPicker"));
        assertTrue(editor.contains("forBarterIngredients"));
        assertTrue(editor.contains("setIngredientCount"));
        assertTrue(editor.contains("ShopUiUtil.renderStepper"));
        assertTrue(picker.contains("LinkedHashSet<String> selectedIds"));
    }

    @Test
    void modSearchIsPredictiveBeforeNamespaceIsComplete() throws Exception {
        String picker = read("src/main/java/com/enviouse/futureshops/client/screen/AdminItemPickerScreen.java");
        assertTrue(picker.contains("searchQuery.startsWith(\"@\")"));
        assertTrue(picker.contains("startsWith(wantedNamespace)"));
    }

    @Test
    void profileBackPreservesContextAndCloseOpensDefaultShop() throws Exception {
        String profile = read("src/main/java/com/enviouse/futureshops/client/screen/BalanceOverviewScreen.java");
        assertTrue(profile.contains("Component.translatable(\"gui.futureshops.local.back\")"));
        assertTrue(profile.contains("this.minecraft.setScreen(parent);"));
        assertTrue(profile.contains("new C2SOpenShopPacket(\"default\")"));
        assertFalse(profile.contains("balance.storefront"));
    }

    @Test
    void serverShopDefaultsToAllAndShowsCartFeedback() throws Exception {
        String shop = read("src/main/java/com/enviouse/futureshops/client/screen/ShopMainScreen.java");
        String state = read("src/main/java/com/enviouse/futureshops/client/ShopClientState.java");
        assertTrue(shop.contains("private int tradeFilter;"));
        assertTrue(shop.contains("gui.futureshops.shell.seg_all"));
        assertTrue(shop.contains("default -> item.hasBarterRecipes() || item.buyPrice() > 0 || item.sellPrice() > 0"));
        assertTrue(shop.contains("ShopClientState.getCartTotalQuantity()"));
        assertTrue(state.contains("gui.futureshops.status.cart.added"));
    }
}
