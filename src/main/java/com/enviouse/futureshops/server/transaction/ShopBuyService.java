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
                TransactionHistoryService.record(player, result.shopId(), "BUY", line.itemId(), line.quantity(), lineCost,
                        packet.cartCheckout() ? "CART" : "DETAIL");
                // Record buy activity for dynamic pricing (spec §30)
                DynamicPricingEngine.recordBuy(player.getServer(), result.shopId(), line.itemId(), line.quantity());
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
            return BuyResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), "SHOP_CLOSED");
        }

        Map<String, Integer> mergedLines = mergeLines(packet.lineItems());
        if (mergedLines.isEmpty()) {
            return BuyResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), "INVALID_ITEM");
        }

        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return BuyResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), "COOLDOWN");
        }

        try {
            EconomyProvider provider = BalanceManager.getProvider();
            Inventory inventory = player.getInventory();
            long totalCost = 0L;
            int totalQuantity = 0;
            List<PreparedLine> preparedLines = new ArrayList<>();
            List<ItemStack> rewards = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : mergedLines.entrySet()) {
                String itemId = entry.getKey();
                int quantity = entry.getValue();
                if (quantity <= 0 || quantity > ShopTransactionUtil.MAX_BUY_QUANTITY) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "INVALID_ITEM");
                }

                ItemDef itemDef = ShopCatalog.getItem(shopId, itemId).orElse(null);
                if (itemDef == null || itemDef.buyPriceMinorUnits() <= 0L) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "INVALID_ITEM");
                }

                int currentStock = ShopCatalog.getCurrentStock(shopId, itemId);
                if (currentStock >= 0 && currentStock < quantity) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "OUT_OF_STOCK");
                }

                Item item = ShopTransactionUtil.resolveItem(itemId);
                if (item == null) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "INVALID_ITEM");
                }

                long lineCost = ShopCatalog.calculateLineCost(shopId, itemId, quantity);
                if (lineCost <= 0L) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "INVALID_ITEM");
                }

                try {
                    totalCost = Math.addExact(totalCost, lineCost);
                } catch (ArithmeticException ex) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "SERVER_ERROR");
                }

                totalQuantity += quantity;
                ItemStack rewardStack = new ItemStack(item, quantity);
                rewards.add(rewardStack.copy());
                preparedLines.add(new PreparedLine(itemId, quantity, lineCost));
            }

            if (!ShopTransactionUtil.canFit(inventory, rewards)) {
                return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "INVENTORY_FULL");
            }

            if (provider.getBalance(player.getUUID()) < totalCost) {
                return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "INSUFFICIENT_FUNDS");
            }

            // Fire cancellable ShopTransactionEvent.Pre (spec §33) — allows other mods to cancel or modify price
            for (PreparedLine line : preparedLines) {
                ShopTransactionEvent.Pre preEvent = new ShopTransactionEvent.Pre(
                        player, shopId, line.itemId(), line.quantity(), "BUY", line.lineCost());
                if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent)) {
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "CANCELLED_BY_EVENT");
                }
            }

            TransactionResult withdrawal = provider.withdraw(player.getUUID(), totalCost, "BUY");
            if (!withdrawal.success()) {
                return BuyResult.error(shopId, withdrawal.resultingBalance(), withdrawal.errorCode());
            }

            List<PreparedLine> reserved = new ArrayList<>();
            for (PreparedLine line : preparedLines) {
                if (!ShopCatalog.reserveStock(shopId, line.itemId(), line.quantity())) {
                    for (PreparedLine rollback : reserved) {
                        ShopCatalog.restoreStock(shopId, rollback.itemId(), rollback.quantity());
                    }
                    provider.deposit(player.getUUID(), totalCost, "BUY");
                    return BuyResult.error(shopId, provider.getBalance(player.getUUID()), "OUT_OF_STOCK");
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

    private static Map<String, Integer> mergeLines(List<C2SBuyRequestPacket.LineItem> lineItems) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (C2SBuyRequestPacket.LineItem lineItem : lineItems) {
            if (lineItem == null || lineItem.itemId() == null || lineItem.itemId().isBlank()) {
                continue;
            }
            merged.merge(lineItem.itemId(), lineItem.quantity(), Integer::sum);
        }
        return merged;
    }

    private record PreparedLine(String itemId, int quantity, long lineCost) {
    }

    private record BuyResult(boolean success, String shopId, String errorCode, long resultingBalance, long totalCost, int totalQuantity,
                             List<PreparedLine> lines) {
        private static BuyResult success(String shopId, long resultingBalance, long totalCost, int totalQuantity, List<PreparedLine> lines) {
            return new BuyResult(true, shopId, "", resultingBalance, totalCost, totalQuantity, lines);
        }

        private static BuyResult error(String shopId, long resultingBalance, String errorCode) {
            return new BuyResult(false, shopId, errorCode, resultingBalance, 0L, 0, List.of());
        }
    }
}


