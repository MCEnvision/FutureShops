package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDepartmentUiSourceTest {
    @Test
    void marketsReuseTheShopDepartmentAndSegmentedControls()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java"));

        assertTrue(source.contains("ShopUiUtil.renderDeptRow("));
        assertTrue(source.contains("ShopUiUtil.renderSegmented("));
        assertTrue(source.contains(
                "gui.futureshops.shop_main.departments"));
        assertFalse(source.contains(
                "gui.futureshops.market.status.ready"));
    }

    @Test
    void escrowFailuresUseChatAndAnExplicitRetryControl()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/client/screen/MarketModuleScreen.java"));

        assertTrue(source.contains("warnMarketRetry("));
        assertTrue(source.contains("displayClientMessage("));
        assertTrue(source.contains(
                "message.futureshops.market.retry"));
        assertTrue(source.contains("this::refreshMarketState"));
    }
}
