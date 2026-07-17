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
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Server-authoritative barter transaction path. */
public final class ShopBarterService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

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
            TransactionHistoryService.record(player, result.shopId(), "BARTER", result.targetItemId(),
                    result.outputQuantity(), 0L, paid.toString(), result.targetNbtJson());
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
            // A recipe with no ingredients would cost nothing — the collect/consume loop no-ops and
            // the reward still mints (a free item). The OP's barter editor can leave a recipe empty
            // mid-setup, so reject empty recipes here rather than trusting them to never be authored.
            if (recipe.ingredients() == null || recipe.ingredients().isEmpty()) {
                return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
            }

            // Recipe targets resolve listingId-FIRST (an exact listing, NBT variants included),
            // falling back to the first listing whose registry itemId matches (legacy configs).
            // All stock operations key off the resolved listing's resolutionKey, and its nbtJson
            // is stamped on the minted reward, so a listingId target delivers that exact variant.
            ItemDef targetDef = ShopCatalog.resolveBarterTarget(shopId, recipe.targetItemId()).orElse(null);
            if (targetDef == null) {
                return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
            }
            String targetKey = targetDef.resolutionKey();
            // Always a real registry id (recipe.targetItemId() may be a listingId) — used for
            // minting, events and history.
            String targetRegistryId = targetDef.itemId();

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
            Item rewardItem = ShopTransactionUtil.resolveItem(targetRegistryId);
            if (rewardItem == null) {
                return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
            }
            ItemStack rewardStack = new ItemStack(rewardItem, outputQuantity);
            // Apply the resolved listing's saved NBT (SNBT) when present so tagged variants
            // (enchanted books, Tacz guns) round-trip through barter with their tag intact —
            // mirrors ShopBuyService. Stamped BEFORE canFit: tagged stacks don't merge with
            // bare ones, so fit must be evaluated against the tagged stack.
            String nbt = targetDef.nbtJson();
            if (nbt != null && !nbt.isBlank()) {
                try {
                    rewardStack.setTag(TagParser.parseTag(nbt));
                } catch (Exception ignored) {
                    // Invalid SNBT — log and deliver the bare item rather than fail the barter.
                    LOGGER.warn(
                            "[FutureShops] Invalid SNBT for barter target '{}' (listing '{}') in shop '{}' — delivering bare item.",
                            targetRegistryId, targetKey, shopId);
                }
            }
            rewards.add(rewardStack);
            if (!ShopTransactionUtil.canFit(inventory, rewards)) {
                return BarterResult.error(shopId, ShopResultCode.INVENTORY_FULL);
            }

            // Merge by (itemId, nbtJson): two lines requiring the same item with different NBT
            // requirements stay distinct, while true duplicates still collapse into one count.
            Map<IngredientKey, Integer> mergedIngredientCounts = new LinkedHashMap<>();
            for (BarterIngredientDef ingredient : recipe.ingredients()) {
                mergedIngredientCounts.merge(
                        new IngredientKey(ingredient.itemId(), ingredient.nbtJson() == null ? "" : ingredient.nbtJson()),
                        ingredient.count(), Integer::sum);
            }

            List<IngredientConsumption> required = new ArrayList<>();
            for (Map.Entry<IngredientKey, Integer> ingredientEntry : mergedIngredientCounts.entrySet()) {
                IngredientKey key = ingredientEntry.getKey();
                Item ingredientItem = ShopTransactionUtil.resolveItem(key.itemId());
                if (ingredientItem == null) {
                    return BarterResult.error(shopId, ShopResultCode.INVALID_RECIPE);
                }

                int needed;
                try {
                    needed = Math.multiplyExact(ingredientEntry.getValue(), multiplier);
                } catch (ArithmeticException ex) {
                    return BarterResult.error(shopId, ShopResultCode.SERVER_ERROR);
                }

                // A configured ingredient nbt means NBT-STRICT matching (exact tag), mirroring
                // the sell path. Blank keeps the legacy lenient identity match: any variant of
                // the item (damaged, enchanted, semi-full tanks, etc.) qualifies — matches
                // vanilla villager behavior and the player's intuition. Parsed once here; both
                // the count gate and the consumption below use the same (nbtAware, tag) pair.
                CompoundTag requiredTag = null;
                if (!key.nbtJson().isBlank()) {
                    try {
                        requiredTag = TagParser.parseTag(key.nbtJson());
                    } catch (Exception ex) {
                        // Invalid SNBT — warn and fall back to lenient matching for this line.
                        LOGGER.warn(
                                "[FutureShops] Invalid SNBT for barter ingredient '{}' in recipe '{}' (shop '{}') — matching leniently.",
                                key.itemId(), recipe.recipeId(), shopId);
                    }
                }
                boolean nbtAware = requiredTag != null;

                if (ShopTransactionUtil.countItems(inventory, ingredientItem, nbtAware, requiredTag) < needed) {
                    return BarterResult.error(shopId, ShopResultCode.MISSING_INGREDIENTS);
                }
                required.add(new IngredientConsumption(ingredientItem, needed, nbtAware, requiredTag));
            }

            // Build ingredient entries for event data
            List<BarterTradeEvent.IngredientEntry> ingredientEntries = required.stream()
                    .map(ic -> new BarterTradeEvent.IngredientEntry(
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ic.item()).toString(), ic.count()))
                    .toList();

            // Fire cancellable BarterTradeEvent.Pre (spec §33) — allows other mods to cancel the trade
            BarterTradeEvent.Pre preEvent = new BarterTradeEvent.Pre(
                    player.getUUID(), shopId, packet.recipeId(),
                    targetRegistryId, outputQuantity, ingredientEntries);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent)) {
                return BarterResult.error(shopId, ShopResultCode.CANCELLED_BY_EVENT);
            }

            // Fire cancellable ShopTransactionEvent.Pre (spec §33) for the barter-as-transaction
            ShopTransactionEvent.Pre txPreEvent = new ShopTransactionEvent.Pre(
                    player, shopId, targetRegistryId, outputQuantity, "BARTER", 0L);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(txPreEvent)) {
                return BarterResult.error(shopId, ShopResultCode.CANCELLED_BY_EVENT);
            }

            // NBT-strict lines must consume before lenient ones: a lenient line matches ANY
            // variant, including strictly-tagged stacks, so consuming it first could eat
            // stacks a later strict line needs and fail a feasible trade order-dependently.
            // (Sorted after ingredientEntries is built so event/history order stays recipe order.)
            required.sort(java.util.Comparator.comparing(ic -> !ic.nbtAware()));

            // Consume via collectAndRemoveItems so rollbacks can refund the player's
            // ACTUAL stacks (tags intact) instead of freshly-minted bare ones.
            List<ItemStack> consumed = new ArrayList<>();
            for (IngredientConsumption ingredient : required) {
                if (ingredient.count() <= 0) {
                    continue;
                }
                List<ItemStack> taken = ShopTransactionUtil.collectAndRemoveItems(
                        inventory, ingredient.item(), ingredient.count(), ingredient.nbtAware(), ingredient.requiredTag());
                if (taken.isEmpty()) {
                    // Refund ingredients already consumed for earlier recipe lines.
                    ShopTransactionUtil.insertIntoInventory(inventory, consumed);
                    return BarterResult.error(shopId, ShopResultCode.MISSING_INGREDIENTS);
                }
                consumed.addAll(taken);
            }

            if (!ShopCatalog.reserveStock(shopId, targetKey, outputQuantity)) {
                ShopTransactionUtil.insertIntoInventory(inventory, consumed);
                return BarterResult.error(shopId, ShopResultCode.OUT_OF_STOCK);
            }

            if (!ShopTransactionUtil.insertIntoInventory(inventory, rewards)) {
                ShopCatalog.restoreStock(shopId, targetKey, outputQuantity);
                ShopTransactionUtil.insertIntoInventory(inventory, consumed);
                return BarterResult.error(shopId, ShopResultCode.INVENTORY_FULL);
            }

            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            return BarterResult.success(shopId, targetRegistryId, targetDef.nbtJson(), outputQuantity, ingredientEntries);
        } finally {
            lock.unlock();
        }
    }

    /** Merge key for ingredient lines — same item with different NBT requirements stays distinct. */
    private record IngredientKey(String itemId, String nbtJson) {
    }

    /**
     * One resolved ingredient line. {@code nbtAware}/{@code requiredTag} carry the pre-parsed
     * NBT-strict matching requirement ({@code false}/{@code null} = lenient identity matching).
     */
    private record IngredientConsumption(Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
    }

    private record BarterResult(boolean success, String shopId, ShopResultCode errorCode, String targetItemId,
                               String targetNbtJson, int outputQuantity,
                               List<BarterTradeEvent.IngredientEntry> ingredientEntries) {
        private static BarterResult success(String shopId, String targetItemId, String targetNbtJson,
                                            int outputQuantity,
                                            List<BarterTradeEvent.IngredientEntry> ingredientEntries) {
            return new BarterResult(true, shopId, ShopResultCode.OK, targetItemId,
                    targetNbtJson == null ? "" : targetNbtJson, outputQuantity, ingredientEntries);
        }

        private static BarterResult error(String shopId, ShopResultCode errorCode) {
            return new BarterResult(false, shopId, errorCode, "", "", 0, List.of());
        }
    }
}



