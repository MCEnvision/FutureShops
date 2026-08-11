package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CartTimeoutRecoverySourceTest {
    @Test
    void bothCartScreensExposeSafeOriginalResultChecks() throws Exception {
        String admin = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/client/screen/CartScreen.java"));
        String player = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/client/screen/PlayerShopCartScreen.java"));

        assertTrue(admin.contains("retryCartCheckout"));
        assertTrue(admin.contains("clearCartContents"));
        assertTrue(admin.contains("gui.futureshops.cart.check_result_btn"));
        assertTrue(player.contains("retryCheckout"));
        assertTrue(player.contains("clearEntries"));
        assertTrue(player.contains(
                "gui.futureshops.player_shop_cart.check_result"));
    }
}
