package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.event.ShopTransactionEvent;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshops.network.packets.S2CSellResponsePacket;
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

import java.util.concurrent.locks.ReentrantLock;

/** Server-authoritative sell transaction path. */
public final class ShopSellService {
    private ShopSellService() {
    }

    public static void handleSellRequest(ServerPlayer player, C2SSellRequestPacket packet) {
        SellResult result = execute(player, packet);
        // Resolve the registry id for the client echo, history, and the public event (all want a valid
        // ResourceLocation). Dynamic pricing stays keyed by the listingId. Fall back to the listingId
        // if the listing vanished between request and now.
        String registryItemId = ShopCatalog.getItem(result.shopId(), packet.listingId())
                .map(ItemDef::itemId).orElse(packet.listingId());
        ShopPackets.sendToPlayer(player, new S2CSellResponsePacket(
                result.success(),
                result.shopId(),
                registryItemId,
                result.errorCode(),
                result.resultingBalance(),
                packet.quantity(),
                result.totalValue()));

        if (result.success() && player.getServer() != null) {
            TransactionHistoryService.record(player, result.shopId(), "SELL", registryItemId, packet.quantity(), result.totalValue(), "DETAIL");
            // Record sell activity for dynamic pricing (spec §30) — keyed by listingId.
            DynamicPricingEngine.recordSell(player.getServer(), result.shopId(), packet.listingId(), packet.quantity());
            // Fire ShopTransactionEvent.Post (spec §33)
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new ShopTransactionEvent.Post(player.getUUID(), result.shopId(), registryItemId,
                            packet.quantity(), "SELL", result.totalValue(), result.resultingBalance()));
            InventorySyncService.sendOwnedCounts(player, result.shopId());
            ShopDataService.resendSessionsViewingShop(player.getServer(), result.shopId());
        }
    }

    private static SellResult execute(ServerPlayer player, C2SSellRequestPacket packet) {
        String shopId = ShopDataService.resolveShopId(packet.shopId());
        ShopSession session = ShopSessionManager.get(player.getUUID()).orElse(null);
        if (session == null || !session.shopId().equals(shopId)) {
            return SellResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.SHOP_CLOSED);
        }

        int quantity = packet.quantity();
        if (quantity <= 0 || quantity > ShopTransactionUtil.MAX_SELL_QUANTITY) {
            return SellResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
        }
        // The wire line carries a listingId (catalog resolution key), not necessarily a registry id.
        if (packet.listingId() == null || packet.listingId().isBlank()) {
            return SellResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
        }

        ReentrantLock lock = ShopTransactionUtil.lockFor(player.getUUID());
        if (!lock.tryLock()) {
            return SellResult.error(shopId, BalanceManager.getProvider().getBalance(player.getUUID()), ShopResultCode.COOLDOWN);
        }

        try {
            EconomyProvider provider = BalanceManager.getProvider();
            ItemDef itemDef = ShopCatalog.getItem(shopId, packet.listingId()).orElse(null);
            if (itemDef == null || itemDef.sellPriceMinorUnits() <= 0L) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
            }
            // Availability-window re-check (stale client catalog).
            if (itemDef.isExpired(System.currentTimeMillis() / 1000L)) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
            }

            // Reject Air outright. Some modded bundles report their primary item as air until fully
            // constructed (Create's locomotive-like assemblies) — those must never be a valid
            // sell-target or the transaction row would immortalize a bogus itemId. Resolve from the
            // listing's registry id, not the listingId (which need not be a ResourceLocation).
            String itemId = itemDef.itemId();
            if (itemId == null || itemId.isBlank() || "minecraft:air".equals(itemId)) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
            }
            Item item = ShopTransactionUtil.resolveItem(itemId);
            if (item == null) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.INVALID_ITEM);
            }

            // NBT-aware match: for an NBT-keyed listing (enchanted book, Tacz gun, …) the player must
            // hand over the exact tagged variant; for a bare listing requiredTag stays null so only
            // plain/tag-less stacks match (prevents dumping enchanted/damaged gear as a plain item).
            net.minecraft.nbt.CompoundTag requiredTag = null;
            String nbt = itemDef.nbtJson();
            if (nbt != null && !nbt.isBlank()) {
                try {
                    requiredTag = net.minecraft.nbt.TagParser.parseTag(nbt);
                } catch (Exception ignored) {
                    requiredTag = null;
                }
            }

            Inventory inventory = player.getInventory();
            if (ShopTransactionUtil.countItems(inventory, item, true, requiredTag) < quantity) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.MISSING_ITEMS);
            }

            long totalValue;
            try {
                totalValue = Math.multiplyExact(itemDef.sellPriceMinorUnits(), quantity);
            } catch (ArithmeticException ex) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.SERVER_ERROR);
            }

            // Fire cancellable ShopTransactionEvent.Pre (spec §33) — registry itemId for API consumers.
            ShopTransactionEvent.Pre preEvent = new ShopTransactionEvent.Pre(
                    player, shopId, itemId, quantity, "SELL", totalValue);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent)) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.CANCELLED_BY_EVENT);
            }
            // Allow event listeners to modify the price
            totalValue = preEvent.getPriceMinor();

            if (!ShopTransactionUtil.removeItems(inventory, item, quantity, true, requiredTag)) {
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.MISSING_ITEMS);
            }

            TransactionResult deposit = provider.deposit(player.getUUID(), totalValue, "SELL");
            if (!deposit.success()) {
                ShopTransactionUtil.insertIntoInventory(inventory, java.util.List.of(refundStack(item, quantity, requiredTag)));
                inventory.setChanged();
                return SellResult.error(shopId, deposit.resultingBalance(), deposit.errorCode());
            }

            if (!ShopCatalog.incrementStock(shopId, packet.listingId(), quantity)) {
                provider.withdraw(player.getUUID(), totalValue, "SELL");
                ShopTransactionUtil.insertIntoInventory(inventory, java.util.List.of(refundStack(item, quantity, requiredTag)));
                inventory.setChanged();
                return SellResult.error(shopId, provider.getBalance(player.getUUID()), ShopResultCode.SERVER_ERROR);
            }

            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            return SellResult.success(shopId, deposit.resultingBalance(), totalValue);
        } finally {
            lock.unlock();
        }
    }

    /** Refund stack carrying the listing's NBT (when any), used to return items on a failed payout. */
    private static net.minecraft.world.item.ItemStack refundStack(Item item, int quantity, net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, quantity);
        if (tag != null) {
            stack.setTag(tag.copy());
        }
        return stack;
    }

    private record SellResult(boolean success, String shopId, ShopResultCode errorCode, long resultingBalance, long totalValue) {
        private static SellResult success(String shopId, long resultingBalance, long totalValue) {
            return new SellResult(true, shopId, ShopResultCode.OK, resultingBalance, totalValue);
        }

        private static SellResult error(String shopId, long resultingBalance, ShopResultCode errorCode) {
            return new SellResult(false, shopId, errorCode, resultingBalance, 0L);
        }
    }
}


