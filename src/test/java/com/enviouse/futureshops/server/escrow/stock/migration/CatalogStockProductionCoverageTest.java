package com.enviouse.futureshops.server.escrow.stock.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStockProductionCoverageTest {
    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void productionCoverageIsCompleteOnlyWithLegacyMutationsAbsent()
            throws IOException {
        List<Path> files;
        try (var stream = Files.walk(MAIN)) {
            files = stream.filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
        for (Path file : files) {
            String source = Files.readString(file);
            assertFalse(source.contains("ShopCatalog.setStock("),
                    file.toString());
            assertFalse(source.contains("ShopCatalog.reserveStock("),
                    file.toString());
            assertFalse(source.contains("ShopCatalog.restoreStock("),
                    file.toString());
            assertFalse(source.contains("ShopCatalog.incrementStock("),
                    file.toString());
        }
        assertTrue(CatalogStockActivationCoverage
                .productionCutover().complete());
    }

    @Test
    void startupRestartReloadSchedulerAdminAndApiUseDurableRuntime()
            throws IOException {
        String lifecycle = read("com/enviouse/futureshops/Futureshops.java");
        String runtime = read("com/enviouse/futureshops/server/escrow/stock/migration/CatalogStockRuntime.java");
        String cutover = read("com/enviouse/futureshops/server/escrow/stock/migration/CatalogStockCutoverCoordinator.java");
        String scheduler = read("com/enviouse/futureshops/server/shop/StockRefreshScheduler.java");
        String api = read("com/enviouse/futureshops/api/ShopModAPI.java");
        String command = read("com/enviouse/futureshops/command/ShopAdminCommand.java");
        String writer = read("com/enviouse/futureshops/catalog/AdminShopConfigWriter.java");

        assertTrue(lifecycle.contains("CatalogStockRuntime.initialize("));
        assertTrue(lifecycle.contains("CatalogStockRuntime.tick("));
        assertTrue(cutover.contains("verifyRestoredLineage"));
        assertTrue(runtime.contains("ShopCatalog.reloadDurable("));
        assertTrue(runtime.contains("CatalogStockProductionIds.reload("));
        assertTrue(scheduler.contains("CatalogStockRuntime.refreshStock("));
        assertTrue(scheduler.contains("public static void trigger("));
        assertEquals(2, count(api, "CatalogStockRuntime.setStock("));
        assertTrue(api.contains("StockRefreshScheduler.trigger("));
        assertTrue(command.contains("CatalogStockRuntime.reload("));
        assertFalse(command.contains("ShopCatalog.reload("));
        assertEquals(22, count(writer, "CatalogStockRuntime.reload("));
        assertFalse(writer.contains("ShopCatalog.reload(server)"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
