package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.S2CBuyResponsePacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshops.server.session.ShopSession;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.InventorySyncService;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Server-authoritative buy transaction engine for detail purchases and cart checkout.
 */
public final class ShopBuyService {
    private ShopBuyService() {
    }

    public static void handleBuyRequest(ServerPlayer player, C2SBuyRequestPacket packet) {
        BuyResult result = execute(player, packet);
        ShopPackets.sendToPlayer(player, new S2CBuyResponsePacket(
                result.success(),
                packet.cartCheckout(),
                result.shopId(),
                result.errorCode(),
                result.resultingBalance(),
                result.totalQuantity(),
                result.totalCost()));

        if (result.success() && player.getServer() != null) {
            for (PreparedLine line : result.lines()) {
                long lineCost = line.lineCost();
                // History + the public ShopTransactionEvent carry the registry itemId (a valid
                // ResourceLocation the history screen resolves to a display name, and what downstream
                // mods expect). Dynamic pricing is keyed by listingId so per-variant pricing stays
                // independent — and == itemId for legacy entries, so existing pricing state is preserved.
                TransactionHistoryService.record(player, result.shopId(), "BUY", line.itemId(), line.quantity(), lineCost,
                        packet.cartCheckout() ? "CART" : "DETAIL", line.nbtJson());
                // Record buy activity for dynamic pricing (spec §30)
                DynamicPricingEngine.recordBuy(player.getServer(), result.shopId(), line.listingId(), line.quantity());
                // Fire ShopTransactionEvent.Post (spec §33)
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new ShopTransactionEvent.Post(player.getUUID(), result.shopId(), line.itemId(),
                                line.quantity(), "BUY", lineCost, result.resultingBalance()));
            }
            InventorySyncService.sendOwnedCounts(player, result.shopId());
            ShopDataService.resendSessionsViewingShop(player.getServer(), result.shopId());
        }
    }

    private static BuyResult execute(ServerPlayer player, C2SBuyRequestPacket packet) {
        String shopId = ShopDataService.resolveShopId(packet.shopId());
        ShopSession session = ShopSessionManager.get(player.getUUID()).orElse(null);
        if (session == null || !session.shopId().equals(shopId)) {
            return BuyResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.SHOP_CLOSED);
        }

        Map<String, Integer> mergedLines = mergeLines(packet.lineItems());
        if (mergedLines.isEmpty()) {
            return BuyResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
        }

        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return BuyResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.COOLDOWN);
        }

        try {
            EconomyProvider provider = BalanceManager.getProvider();
            Inventory inventory = player.getInventory();
            long totalCost = 0L;
            int totalQuantity = 0;
            List<PreparedLine> preparedLines = new ArrayList<>();
            List<ItemStack> rewards = new ArrayList<>();

            long nowSec = System.currentTimeMillis() / 1000L;
            for (Map.Entry<String, Integer> entry : mergedLines.entrySet()) {
                // The wire line carries a listingId (catalog resolution key), NOT necessarily a
                // registry id. Resolve the exact listing by it, then mint from the def's registry itemId.
                String listingId = entry.getKey();
                int quantity = entry.getValue();
                if (quantity <= 0 || quantity > ShopTransactionUtil.MAX_BUY_QUANTITY) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }
                if (listingId == null || listingId.isBlank()) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }

                ItemDef itemDef = ShopCatalog.getItem(shopId, listingId).orElse(null);
                if (itemDef == null || itemDef.buyPriceMinorUnits() <= 0L) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }
                // Availability-window re-check: the client may hold a stale catalog in which this
                // listing was still live. Never sell an expired listing.
                if (itemDef.isExpired(nowSec)) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }

                // Registry id is what we mint/resolve; reject Air here (a listingId is never "air").
                String itemId = itemDef.itemId();
                if (itemId == null || itemId.isBlank() || "minecraft:air".equals(itemId)) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }

                int currentStock = ShopCatalog.getCurrentStock(shopId, listingId);
                if (currentStock >= 0 && currentStock < quantity) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.OUT_OF_STOCK);
                }

                Item item = ShopTransactionUtil.resolveItem(itemId);
                if (item == null) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }

                long lineCost = ShopCatalog.calculateLineCost(shopId, listingId, quantity);
                if (lineCost <= 0L) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
                }

                try {
                    totalCost = Math.addExact(totalCost, lineCost);
                } catch (ArithmeticException ex) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.SERVER_ERROR);
                }

                totalQuantity += quantity;
                ItemStack rewardStack = new ItemStack(item, quantity);
                // Apply saved NBT (SNBT) when present so enchanted books, Tacz guns,
                // named/lored items round-trip through /shop buys with their tag
                // intact. Empty / unparseable nbt falls through to the bare stack.
                String nbt = itemDef.nbtJson();
                if (nbt != null && !nbt.isBlank()) {
                    try {
                        net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbt);
                        rewardStack.setTag(tag);
                    } catch (Exception ignored) {
                        // Invalid SNBT — log once and deliver the bare item rather than fail the buy.
                        com.mojang.logging.LogUtils.getLogger().warn(
                                "[FutureShops] Invalid SNBT for catalog item '{}' (listing '{}') in shop '{}' — delivering bare item.",
                                itemId, listingId, shopId);
                    }
                }
                rewards.add(rewardStack.copy());
                preparedLines.add(new PreparedLine(listingId, itemId, quantity, lineCost, nbt == null ? "" : nbt));
            }

            if (!ShopTransactionUtil.canFit(inventory, rewards)) {
                return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVENTORY_FULL);
            }

            if (provider.getBalance(player.getUUID()) < totalCost) {
                return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INSUFFICIENT_FUNDS);
            }

            // Fire cancellable ShopTransactionEvent.Pre (spec §33) — allows other mods to cancel or modify price
            for (PreparedLine line : preparedLines) {
                ShopTransactionEvent.Pre preEvent = new ShopTransactionEvent.Pre(
                        player, shopId, line.itemId(), line.quantity(), "BUY", line.lineCost());
                if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent)) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.CANCELLED_BY_EVENT);
                }
            }

            TransactionResult withdrawal = provider.withdraw(player.getUUID(), totalCost, "BUY");
            if (!withdrawal.success()) {
                return BuyResult.error(shopId, withdrawal.resultingBalance(), withdrawal.errorCode());
            }

            List<PreparedLine> reserved = new ArrayList<>();
            for (PreparedLine line : preparedLines) {
                if (!ShopCatalog.reserveStock(shopId, line.listingId(), line.quantity())) {
                    for (PreparedLine rollback : reserved) {
                        ShopCatalog.restoreStock(shopId, rollback.listingId(), rollback.quantity());
                    }
                    provider.deposit(player.getUUID(), totalCost, "BUY");
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.OUT_OF_STOCK);
                }
                reserved.add(line);
            }

            if (!ShopTransactionUtil.insertIntoInventory(inventory, rewards)) {
                // Drop any remaining items at the player's feet instead of rolling back
                for (ItemStack stack : rewards) {
                    if (!stack.isEmpty()) {
                        player.drop(stack, false);
                    }
                }
            }

            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            return BuyResult.success(shopId, withdrawal.resultingBalance(), totalCost, totalQuantity, List.copyOf(preparedLines));
        } finally {
            lock.unlock();
        }
    }

    // package-private for unit testing (multi-variant merge-by-listingId)
    static Map<String, Integer> mergeLines(List<C2SBuyRequestPacket.LineItem> lineItems) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (C2SBuyRequestPacket.LineItem lineItem : lineItems) {
            if (lineItem == null || lineItem.listingId() == null || lineItem.listingId().isBlank()) {
                continue;
            }
            // Merge by listingId: two listings sharing a registry itemId but distinct listingIds
            // must NOT collapse — that is the whole point of multi-variant NBT listings.
            merged.merge(lineItem.listingId(), lineItem.quantity(), Integer::sum);
        }
        return merged;
    }

    /** {@code listingId} is the catalog resolution key; {@code itemId} is the registry id to mint/log; {@code nbtJson} the listing's SNBT ("" = none). */
    private record PreparedLine(String listingId, String itemId, int quantity, long lineCost, String nbtJson) {
    }

    private record BuyResult(boolean success, String shopId, ShopResultCode errorCode, long resultingBalance, long totalCost, int totalQuantity,
                             List<PreparedLine> lines) {
        private static BuyResult success(String shopId, long resultingBalance, long totalCost, int totalQuantity, List<PreparedLine> lines) {
            return new BuyResult(true, shopId, ShopResultCode.OK, resultingBalance, totalCost, totalQuantity, lines);
        }

        private static BuyResult error(String shopId, long resultingBalance, ShopResultCode errorCode) {
            return new BuyResult(false, shopId, errorCode, resultingBalance, 0L, 0, List.of());
        }
    }
}


