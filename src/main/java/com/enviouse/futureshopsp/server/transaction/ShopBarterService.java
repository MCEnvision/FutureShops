package com.enviouse.futureshopsp.server.transaction;

import com.enviouse.futureshopsp.catalog.BarterIngredientDef;
import com.enviouse.futureshopsp.catalog.BarterRecipeDef;
import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.event.BarterTradeEvent;
import com.enviouse.futureshopsp.event.ShopTransactionEvent;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshopsp.network.packets.S2CBarterResponsePacket;
import com.enviouse.futureshopsp.server.session.ShopSession;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.InventorySyncService;
import com.enviouse.futureshopsp.server.shop.ShopDataService;
import com.enviouse.futureshopsp.server.shop.ShopResultCode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Server-authoritative barter transaction path. */
public final class ShopBarterService {
    private ShopBarterService() {
    }

    public static void handleBarterRequest(ServerPlayer player, C2SBarterRequestPacket packet) {
        BarterResult result = execute(player, packet);
        ShopPackets.sendToPlayer(player, new S2CBarterResponsePacket(
                result.success(),
                result.shopId(),
                packet.recipeId(),
                result.errorCode(),
                packet.multiplier(),
                result.outputQuantity()));

        if (result.success() && player.getServer() != null) {
            // note: "paid=<itemId>×<n>[,<itemId>×<n>...]" so the history UI can show what the player handed over.
            StringBuilder paid = new StringBuilder("paid=");
            for (int i = 0; i < result.ingredientEntries().size(); i++) {
                BarterTradeEvent.IngredientEntry ing = result.ingredientEntries().get(i);
                if (i > 0) paid.append(',');
                paid.append(ing.itemId()).append('\u00d7').append(ing.count());
            }
            TransactionHistoryService.record(player, result.shopId(), "BARTER", result.targetItemId(), result.outputQuantity(), 0L, paid.toString());
            // Fire ShopTransactionEvent.Post (spec §33) for barter transactions
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new ShopTransactionEvent.Post(player.getUUID(), result.shopId(), result.targetItemId(),
                            result.outputQuantity(), "BARTER", 0L, 0L));
            // Fire BarterTradeEvent.Post (spec §33) with ingredient details
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new BarterTradeEvent.Post(player.getUUID(), result.shopId(), packet.recipeId(),
                            result.targetItemId(), result.outputQuantity(), result.ingredientEntries()));
            InventorySyncService.sendOwnedCounts(player, result.shopId());
            ShopDataService.resendSessionsViewingShop(player.getServer(), result.shopId());
        }
    }

    private static BarterResult execute(ServerPlayer player, C2SBarterRequestPacket packet) {
        String shopId = ShopDataService.resolveShopId(packet.shopId());
        ShopSession session = ShopSessionManager.get(player.getUUID()).orElse(null);
        if (session == null || !session.shopId().equals(shopId)) {
            return BarterResult.error(shopId, ShopResultCode.SHOP_CLOSED);
        }

        int multiplier = packet.multiplier();
        if (multiplier <= 0 || multiplier > ShopTransactionUtil.MAX_QUANTITY) {
            return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
        }

        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return BarterResult.error(shopId, ShopResultCode.COOLDOWN);
        }

        try {
            BarterRecipeDef recipe = ShopCatalog.getBarterRecipe(shopId, packet.recipeId()).orElse(null);
            if (recipe == null) {
                return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
            }

            // Barter recipes target a registry itemId. Resolve by registry id (works for legacy
            // single-variant items and, for fully multi-variant items, the first variant), then drive
            // all stock operations off the resolved listing's resolutionKey so they hit the right slot.
            ItemDef targetDef = ShopCatalog.getItemByRegistryId(shopId, recipe.targetItemId()).orElse(null);
            if (targetDef == null) {
                return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
            }
            String targetKey = targetDef.resolutionKey();

            int outputQuantity;
            try {
                outputQuantity = Math.multiplyExact(recipe.outputCount(), multiplier);
            } catch (ArithmeticException ex) {
                return BarterResult.error(shopId, ShopResultCode.SERVER_ERROR);
            }

            int currentStock = ShopCatalog.getCurrentStock(shopId, targetKey);
            if (currentStock >= 0 && currentStock < outputQuantity) {
                return BarterResult.error(shopId, ShopResultCode.OUT_OF_STOCK);
            }

            Inventory inventory = player.getInventory();
            List<ItemStack> rewards = new ArrayList<>();
            Item rewardItem = ShopTransactionUtil.resolveItem(recipe.targetItemId());
            if (rewardItem == null) {
                return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
            }
            rewards.add(new ItemStack(rewardItem, outputQuantity));
            if (!ShopTransactionUtil.canFit(inventory, rewards)) {
                return BarterResult.error(shopId, ShopResultCode.INVENTORY_FULL);
            }

            Map<String, Integer> mergedIngredientCounts = new LinkedHashMap<>();
            for (BarterIngredientDef ingredient : recipe.ingredients()) {
                if (ingredient.count() <= 0) {
                    return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
                }
                try {
                    mergedIngredientCounts.merge(ingredient.itemId(), ingredient.count(), Math::addExact);
                } catch (ArithmeticException ex) {
                    return BarterResult.error(shopId, ShopResultCode.SERVER_ERROR);
                }
            }

            List<IngredientConsumption> required = new ArrayList<>();
            for (Map.Entry<String, Integer> ingredientEntry : mergedIngredientCounts.entrySet()) {
                Item ingredientItem = ShopTransactionUtil.resolveItem(ingredientEntry.getKey());
                if (ingredientItem == null) {
                    return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
                }

                int needed;
                try {
                    needed = Math.multiplyExact(ingredientEntry.getValue(), multiplier);
                } catch (ArithmeticException ex) {
                    return BarterResult.error(shopId, ShopResultCode.SERVER_ERROR);
                }

                // NBT-agnostic: recipes only record ingredient item *ids*, so any variant
                // of the item (damaged, enchanted, semi-full tanks, etc.) qualifies —
                // matches vanilla villager behavior and the player's intuition.
                if (ShopTransactionUtil.countItems(inventory, ingredientItem, false, null) < needed) {
                    return BarterResult.error(shopId, ShopResultCode.MISSING_INGREDIENTS);
                }
                required.add(new IngredientConsumption(ingredientItem, needed));
            }

            // Build ingredient entries for event data
            List<BarterTradeEvent.IngredientEntry> ingredientEntries = required.stream()
                    .map(ic -> new BarterTradeEvent.IngredientEntry(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ic.item()).toString(), ic.count()))
                    .toList();

            // Fire cancellable BarterTradeEvent.Pre (spec §33) — allows other mods to cancel the trade
            BarterTradeEvent.Pre preEvent = new BarterTradeEvent.Pre(
                    player.getUUID(), shopId, packet.recipeId(),
                    recipe.targetItemId(), outputQuantity, ingredientEntries);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(preEvent);
            if (preEvent.isCanceled()) {
                return BarterResult.error(shopId, ShopResultCode.CANCELLED_BY_EVENT);
            }

            // Fire cancellable ShopTransactionEvent.Pre (spec §33) for the barter-as-transaction
            ShopTransactionEvent.Pre txPreEvent = new ShopTransactionEvent.Pre(
                    player, shopId, recipe.targetItemId(), outputQuantity, "BARTER", 0L);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(txPreEvent);
            if (txPreEvent.isCanceled()) {
                return BarterResult.error(shopId, ShopResultCode.CANCELLED_BY_EVENT);
            }

            List<ItemStack> inventorySnapshot = ShopTransactionUtil.snapshotInventorySlots(inventory);
            for (IngredientConsumption ingredient : required) {
                if (!ShopTransactionUtil.removeItems(inventory, ingredient.item(), ingredient.count(), false, null)) {
                    boolean restored = restoreInventory(player, inventorySnapshot);
                    return BarterResult.error(shopId,
                            restored ? ShopResultCode.MISSING_INGREDIENTS : ShopResultCode.SERVER_ERROR);
                }
            }

            if (!ShopCatalog.reserveStock(shopId, targetKey, outputQuantity)) {
                boolean restored = restoreInventory(player, inventorySnapshot);
                return BarterResult.error(shopId,
                        restored ? ShopResultCode.OUT_OF_STOCK : ShopResultCode.SERVER_ERROR);
            }

            if (!ShopTransactionUtil.insertIntoInventory(inventory, rewards)) {
                ShopCatalog.restoreStock(shopId, targetKey, outputQuantity);
                boolean restored = restoreInventory(player, inventorySnapshot);
                return BarterResult.error(shopId,
                        restored ? ShopResultCode.INVENTORY_FULL : ShopResultCode.SERVER_ERROR);
            }

            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            return BarterResult.success(shopId, recipe.targetItemId(), outputQuantity, ingredientEntries);
        } finally {
            lock.unlock();
        }
    }

    private static boolean restoreInventory(ServerPlayer player, List<ItemStack> snapshot) {
        boolean restored = ShopTransactionUtil.restoreInventorySlots(player.getInventory(), snapshot);
        if (restored) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return restored;
    }

    private record IngredientConsumption(Item item, int count) {
    }

    private record BarterResult(boolean success, String shopId, ShopResultCode errorCode, String targetItemId,
                               int outputQuantity, List<BarterTradeEvent.IngredientEntry> ingredientEntries) {
        private static BarterResult success(String shopId, String targetItemId, int outputQuantity,
                                            List<BarterTradeEvent.IngredientEntry> ingredientEntries) {
            return new BarterResult(true, shopId, ShopResultCode.OK, targetItemId, outputQuantity, ingredientEntries);
        }

        private static BarterResult error(String shopId, ShopResultCode errorCode) {
            return new BarterResult(false, shopId, errorCode, "", 0, List.of());
        }
    }
}


