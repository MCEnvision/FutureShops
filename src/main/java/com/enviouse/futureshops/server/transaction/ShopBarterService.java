package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.event.BarterTradeEvent;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.S2CBarterResponsePacket;
import com.enviouse.futureshops.server.session.ShopSession;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.InventorySyncService;
import com.enviouse.futureshops.server.shop.ShopDataService;
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
            TransactionHistoryService.record(player, result.shopId(), "BARTER", result.targetItemId(), result.outputQuantity(), 0L, packet.recipeId());
            // Fire ShopTransactionEvent.Post (spec §33) for barter transactions
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new ShopTransactionEvent.Post(player.getUUID(), result.shopId(), result.targetItemId(),
                            result.outputQuantity(), "BARTER", 0L, 0L));
            // Fire BarterTradeEvent.Post (spec §33) with ingredient details
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
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
            return BarterResult.error(shopId, "SHOP_CLOSED");
        }

        int multiplier = packet.multiplier();
        if (multiplier <= 0 || multiplier > ShopTransactionUtil.MAX_QUANTITY) {
            return BarterResult.error(shopId, "INVALID_RECIPE");
        }

        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return BarterResult.error(shopId, "COOLDOWN");
        }

        try {
            BarterRecipeDef recipe = ShopCatalog.getBarterRecipe(shopId, packet.recipeId()).orElse(null);
            if (recipe == null) {
                return BarterResult.error(shopId, "INVALID_RECIPE");
            }

            ItemDef targetDef = ShopCatalog.getItem(shopId, recipe.targetItemId()).orElse(null);
            if (targetDef == null) {
                return BarterResult.error(shopId, "INVALID_RECIPE");
            }

            int outputQuantity;
            try {
                outputQuantity = Math.multiplyExact(recipe.outputCount(), multiplier);
            } catch (ArithmeticException ex) {
                return BarterResult.error(shopId, "SERVER_ERROR");
            }

            int currentStock = ShopCatalog.getCurrentStock(shopId, recipe.targetItemId());
            if (currentStock >= 0 && currentStock < outputQuantity) {
                return BarterResult.error(shopId, "OUT_OF_STOCK");
            }

            Inventory inventory = player.getInventory();
            List<ItemStack> rewards = new ArrayList<>();
            Item rewardItem = ShopTransactionUtil.resolveItem(recipe.targetItemId());
            if (rewardItem == null) {
                return BarterResult.error(shopId, "INVALID_RECIPE");
            }
            rewards.add(new ItemStack(rewardItem, outputQuantity));
            if (!ShopTransactionUtil.canFit(inventory, rewards)) {
                return BarterResult.error(shopId, "INVENTORY_FULL");
            }

            Map<String, Integer> mergedIngredientCounts = new LinkedHashMap<>();
            for (BarterIngredientDef ingredient : recipe.ingredients()) {
                mergedIngredientCounts.merge(ingredient.itemId(), ingredient.count(), Integer::sum);
            }

            List<IngredientConsumption> required = new ArrayList<>();
            for (Map.Entry<String, Integer> ingredientEntry : mergedIngredientCounts.entrySet()) {
                Item ingredientItem = ShopTransactionUtil.resolveItem(ingredientEntry.getKey());
                if (ingredientItem == null) {
                    return BarterResult.error(shopId, "INVALID_RECIPE");
                }

                int needed;
                try {
                    needed = Math.multiplyExact(ingredientEntry.getValue(), multiplier);
                } catch (ArithmeticException ex) {
                    return BarterResult.error(shopId, "SERVER_ERROR");
                }

                if (ShopTransactionUtil.countItems(inventory, ingredientItem) < needed) {
                    return BarterResult.error(shopId, "MISSING_INGREDIENTS");
                }
                required.add(new IngredientConsumption(ingredientItem, needed));
            }

            // Build ingredient entries for event data
            List<BarterTradeEvent.IngredientEntry> ingredientEntries = required.stream()
                    .map(ic -> new BarterTradeEvent.IngredientEntry(
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ic.item()).toString(), ic.count()))
                    .toList();

            // Fire cancellable BarterTradeEvent.Pre (spec §33) — allows other mods to cancel the trade
            BarterTradeEvent.Pre preEvent = new BarterTradeEvent.Pre(
                    player.getUUID(), shopId, packet.recipeId(),
                    recipe.targetItemId(), outputQuantity, ingredientEntries);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent)) {
                return BarterResult.error(shopId, "CANCELLED_BY_EVENT");
            }

            // Fire cancellable ShopTransactionEvent.Pre (spec §33) for the barter-as-transaction
            ShopTransactionEvent.Pre txPreEvent = new ShopTransactionEvent.Pre(
                    player, shopId, recipe.targetItemId(), outputQuantity, "BARTER", 0L);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(txPreEvent)) {
                return BarterResult.error(shopId, "CANCELLED_BY_EVENT");
            }

            for (IngredientConsumption ingredient : required) {
                if (!ShopTransactionUtil.removeItems(inventory, ingredient.item(), ingredient.count())) {
                    return BarterResult.error(shopId, "MISSING_INGREDIENTS");
                }
            }

            if (!ShopCatalog.reserveStock(shopId, recipe.targetItemId(), outputQuantity)) {
                for (IngredientConsumption ingredient : required) {
                    ShopTransactionUtil.insertIntoInventory(inventory, List.of(new ItemStack(ingredient.item(), ingredient.count())));
                }
                return BarterResult.error(shopId, "OUT_OF_STOCK");
            }

            if (!ShopTransactionUtil.insertIntoInventory(inventory, rewards)) {
                ShopCatalog.restoreStock(shopId, recipe.targetItemId(), outputQuantity);
                for (IngredientConsumption ingredient : required) {
                    ShopTransactionUtil.insertIntoInventory(inventory, List.of(new ItemStack(ingredient.item(), ingredient.count())));
                }
                return BarterResult.error(shopId, "INVENTORY_FULL");
            }

            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            return BarterResult.success(shopId, recipe.targetItemId(), outputQuantity, ingredientEntries);
        } finally {
            lock.unlock();
        }
    }

    private record IngredientConsumption(Item item, int count) {
    }

    private record BarterResult(boolean success, String shopId, String errorCode, String targetItemId,
                               int outputQuantity, List<BarterTradeEvent.IngredientEntry> ingredientEntries) {
        private static BarterResult success(String shopId, String targetItemId, int outputQuantity,
                                            List<BarterTradeEvent.IngredientEntry> ingredientEntries) {
            return new BarterResult(true, shopId, "", targetItemId, outputQuantity, ingredientEntries);
        }

        private static BarterResult error(String shopId, String errorCode) {
            return new BarterResult(false, shopId, errorCode, "", 0, List.of());
        }
    }
}



