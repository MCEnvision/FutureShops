package com.enviouse.futureshopsp.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBarterSafetySourceTest {
    @Test
    void pureBarterRestoresExactInventoryOnMutationFailure() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopBarterService.java")));

        assertTrue(source.contains("List<ItemStack> inventorySnapshot = ShopTransactionUtil.snapshotInventorySlots(inventory)"));
        assertTrue(source.contains("private static boolean restoreInventory(ServerPlayer player, List<ItemStack> snapshot)"));
        assertTrue(source.contains("ShopTransactionUtil.restoreInventorySlots(player.getInventory(), snapshot)"));
        assertTrue(source.contains("restored ? ShopResultCode.OUT_OF_STOCK : ShopResultCode.SERVER_ERROR"));
        assertTrue(source.contains("restored ? ShopResultCode.INVENTORY_FULL : ShopResultCode.SERVER_ERROR"));
        assertFalse(source.contains("ShopTransactionUtil.insertIntoInventory(inventory, List.of(new ItemStack(ingredient.item(), ingredient.count())))"));
    }

    @Test
    void pureBarterRejectsInvalidAndOverflowingIngredientDefinitions() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopBarterService.java")));

        assertTrue(source.contains("if (ingredient.count() <= 0)"));
        assertTrue(source.contains("mergedIngredientCounts.merge(ingredient.itemId(), ingredient.count(), Math::addExact)"));
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}
