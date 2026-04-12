package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class PlayerShopBlockService {
    private static final int MAX_LINK_DISTANCE = 8;
    private static final double LINK_RAYCAST_RANGE = 8.0D;
    private static final ConcurrentHashMap<Long, ReentrantLock> SHOP_LOCKS = new ConcurrentHashMap<>();

    private PlayerShopBlockService() {
    }

    public static void openFor(ServerPlayer player, BlockPos pos) {
        openFor(player, pos, false);
    }

    public static void openFor(ServerPlayer player, BlockPos pos, boolean forceVisitorView) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        boolean owner = !forceVisitorView && shop.getOwnerUuid() != null && shop.getOwnerUuid().equals(player.getUUID());
        int stock = countStock(player.level(), shop, pos);
        PlayerShopSettlementSavedData.Snapshot settlement = player.getServer() == null
                ? new PlayerShopSettlementSavedData.Snapshot(0L, 0L, List.of())
                : PlayerShopSettlementSavedData.get(player.getServer()).snapshot(
                        shop.getOwnerUuid() == null ? player.getUUID() : shop.getOwnerUuid(),
                        pos.asLong(),
                        6);
        String ownerName = shop.getOwnerUuid() == null
                ? "Unowned"
                : Optional.ofNullable(player.server.getPlayerList().getPlayer(shop.getOwnerUuid()))
                .map(p -> p.getName().getString())
                .orElse(shop.getOwnerUuid().toString().substring(0, 8));

        ShopPackets.sendToPlayer(player, new S2CPlayerShopDataPacket(
                pos,
                owner,
                ownerName,
                shop.getListedItemId(),
                shop.getTradeMode().name(),
                shop.getMoneyPriceMinor(),
                shop.getBarterItemId(),
                shop.getBarterItemCount(),
                stock,
                shop.getLinkedStoragePos() != null,
                settlement.pendingMinor(),
                settlement.lifetimeMinor(),
                settlement.rows()));
    }

    public static void applyOwnerAction(ServerPlayer player, BlockPos pos, String action, int amount) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return;
        }

        switch (action) {
            case "SET_LISTING_MAINHAND" -> {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, "HOLD_ITEM");
                    return;
                }
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key != null) {
                    shop.setListedItemId(key.toString());
                }
            }
            case "CLEAR_LISTING" -> shop.clearListing();
            case "TOGGLE_MODE" -> shop.setTradeMode(shop.getTradeMode() == ShopBlockEntity.TradeMode.MONEY
                    ? ShopBlockEntity.TradeMode.BARTER
                    : ShopBlockEntity.TradeMode.MONEY);
            case "PRICE_UP" -> shop.setMoneyPriceMinor(shop.getMoneyPriceMinor() + Math.max(1, amount));
            case "PRICE_DOWN" -> shop.setMoneyPriceMinor(Math.max(1L, shop.getMoneyPriceMinor() - Math.max(1, amount)));
            case "SET_BARTER_MAINHAND" -> {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, "HOLD_ITEM");
                    return;
                }
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key != null) {
                    shop.setBarterItemId(key.toString());
                    shop.setBarterItemCount(Math.max(1, amount));
                }
            }
            case "LINK_LOOKING" -> {
                PlayerShopLinkService.begin(player, pos);
                sendResult(player, true, "LINK_PENDING");
                return;
            }
            case "UNLINK" -> shop.setLinkedStoragePos(null);
            case "CLAIM_SETTLEMENT" -> {
                if (player.getServer() == null) {
                    sendResult(player, false, "SERVER_ERROR");
                    return;
                }
                long claimed = PlayerShopSettlementSavedData.get(player.getServer()).claim(player.getUUID(), pos.asLong());
                if (claimed <= 0L) {
                    sendResult(player, false, "NOTHING_TO_CLAIM");
                    return;
                }
                TransactionResult deposit = BalanceManager.getProvider().deposit(player.getUUID(), claimed);
                if (!deposit.success()) {
                    PlayerShopSettlementSavedData.get(player.getServer()).recordSale(player.getUUID(), pos.asLong(), claimed);
                    sendResult(player, false, "CLAIM_FAILED");
                    return;
                }
            }
            default -> {
                return;
            }
        }

        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, "OK");
    }

    public static void applyPromoAction(ServerPlayer player, BlockPos pos, boolean clear, String promoType, double promoValue,
                                        int buyX, int buyY, int startsInMinutes, int durationMinutes, boolean flash) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return;
        }
        if (shop.getListedItemId().isBlank()) {
            sendResult(player, false, "UNCONFIGURED");
            return;
        }

        boolean ok = clear
                ? ShopCatalog.clearRuntimePromo(shop.getShopId(), shop.getListedItemId())
                : ShopCatalog.setRuntimePromo(
                        shop.getShopId(),
                        shop.getListedItemId(),
                        promoType,
                        promoValue,
                        buyX,
                        buyY,
                        startsInMinutes,
                        durationMinutes,
                        flash);

        sendResult(player, ok, ok ? (clear ? "PROMO_CLEARED" : "PROMO_SET") : "PROMO_FAILED");
    }

    public static void buy(ServerPlayer buyer, BlockPos pos, int quantity) {
        if (!(buyer.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }

        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(pos.asLong(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            int qty = Math.max(1, Math.min(64, quantity));
            if (shop.getOwnerUuid() == null || shop.getListedItemId().isBlank()) {
                sendResult(buyer, false, "UNCONFIGURED");
                return;
            }

            Item saleItem = ShopTransactionUtil.resolveItem(shop.getListedItemId());
            if (saleItem == null || saleItem == Items.AIR) {
                sendResult(buyer, false, "INVALID_ITEM");
                return;
            }

            LinkedStorage linkedStorage = resolveLinkedStorage(buyer.level(), shop, pos);
            if (linkedStorage == null) {
                sendResult(buyer, false, "NO_LINK");
                return;
            }

            List<ItemStack> saleStacks = splitStacks(saleItem, qty);
            if (!ShopTransactionUtil.canFit(buyer.getInventory(), saleStacks)) {
                sendResult(buyer, false, "INVENTORY_FULL");
                return;
            }
            if (!canExtract(linkedStorage.handler(), saleItem, qty)) {
                sendResult(buyer, false, "OUT_OF_STOCK");
                return;
            }

            EconomyProvider provider = BalanceManager.getProvider();
            long cost = shop.getMoneyPriceMinor() * qty;
            boolean withdrewFromBuyer = false;
            boolean depositedToOwner = false;
            Item barterItem = null;
            int barterAmount = 0;
            List<ItemStack> insertedPayment = List.of();

            if (shop.getTradeMode() == ShopBlockEntity.TradeMode.MONEY) {
                TransactionResult withdraw = provider.withdraw(buyer.getUUID(), cost);
                if (!withdraw.success()) {
                    sendResult(buyer, false, "NO_MONEY");
                    return;
                }
                withdrewFromBuyer = true;
                if (buyer.getServer() == null) {
                    provider.deposit(buyer.getUUID(), cost);
                    sendResult(buyer, false, "SERVER_ERROR");
                    return;
                }
                PlayerShopSettlementSavedData.get(buyer.getServer()).recordSale(shop.getOwnerUuid(), pos.asLong(), cost);
                depositedToOwner = true;
            } else {
                barterItem = ShopTransactionUtil.resolveItem(shop.getBarterItemId());
                barterAmount = shop.getBarterItemCount() * qty;
                if (barterItem == null || barterItem == Items.AIR || ShopTransactionUtil.countItems(buyer.getInventory(), barterItem) < barterAmount) {
                    sendResult(buyer, false, "MISSING_BARTER_ITEMS");
                    return;
                }
                List<ItemStack> paymentStacks = splitStacks(barterItem, barterAmount);
                if (!canInsertAll(linkedStorage.handler(), paymentStacks)) {
                    sendResult(buyer, false, "STORAGE_FULL");
                    return;
                }
                if (!ShopTransactionUtil.removeItems(buyer.getInventory(), barterItem, barterAmount)) {
                    sendResult(buyer, false, "MISSING_BARTER_ITEMS");
                    return;
                }
                if (!insertAll(linkedStorage.handler(), paymentStacks)) {
                    ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), paymentStacks);
                    sendResult(buyer, false, "STORAGE_FULL");
                    return;
                }
                insertedPayment = paymentStacks;
            }

            List<ItemStack> extracted = extract(linkedStorage.handler(), saleItem, qty);
            if (extracted.isEmpty() || !ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), extracted)) {
                if (!extracted.isEmpty()) {
                    insertAll(linkedStorage.handler(), extracted);
                }
                if (shop.getTradeMode() == ShopBlockEntity.TradeMode.MONEY) {
                    if (depositedToOwner && buyer.getServer() != null) {
                        PlayerShopSettlementSavedData.get(buyer.getServer()).rollbackPending(shop.getOwnerUuid(), pos.asLong(), cost);
                    }
                    if (withdrewFromBuyer) {
                        provider.deposit(buyer.getUUID(), cost);
                    }
                } else {
                    rollbackBarterPayment(linkedStorage.handler(), buyer, barterItem, barterAmount, insertedPayment);
                }
                sendResult(buyer, false, "ROLLBACK");
                return;
            }

            openFor(buyer, pos);
            sendResult(buyer, true, "BOUGHT");
        } finally {
            lock.unlock();
        }
    }

    public static int confirmLink(ServerPlayer player) {
        PlayerShopLinkService.PendingLink pending = PlayerShopLinkService.consume(player.getUUID());
        if (pending == null) {
            sendResult(player, false, "LINK_NONE");
            return 0;
        }
        BlockPos shopPos = pending.shopPos();
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop)) {
            sendResult(player, false, "LINK_NONE");
            return 0;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return 0;
        }
        BlockPos target = resolveLookedBlockPos(player);
        if (!isValidLinkTarget(player.level(), shopPos, target)) {
            sendResult(player, false, "BAD_LINK_TARGET");
            return 0;
        }
        shop.setLinkedStoragePos(target);
        shop.setChanged();
        openFor(player, shopPos);
        sendResult(player, true, "LINKED");
        return 1;
    }

    public static void sendSettlementHistoryPage(ServerPlayer player, BlockPos shopPos, int page, int pageSize,
                                                 SettlementHistoryRow.SettlementFilter filter,
                                                 long fromEpochSeconds,
                                                 long toEpochSeconds) {
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop) || player.getServer() == null) {
            return;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return;
        }
        PlayerShopSettlementSavedData data = PlayerShopSettlementSavedData.get(player.getServer());
        int safePageSize = Math.max(1, pageSize);
        int totalPages = data.getTotalPages(player.getUUID(), shopPos.asLong(), safePageSize, filter, fromEpochSeconds, toEpochSeconds);
        int safePage = Math.max(1, Math.min(page, totalPages));
        List<SettlementHistoryRow> rows = data.getPage(player.getUUID(), shopPos.asLong(), safePage, safePageSize, filter, fromEpochSeconds, toEpochSeconds);
        ShopPackets.sendToPlayer(player, new S2CSettlementHistoryPacket(shopPos, safePage, totalPages, rows));
    }

    private static void rollbackBarterPayment(IItemHandler handler, ServerPlayer buyer, Item barterItem, int barterAmount, List<ItemStack> insertedPayment) {
        if (barterItem == null || insertedPayment.isEmpty()) {
            return;
        }
        int recovered = extractAmount(handler, barterItem, barterAmount);
        if (recovered > 0) {
            ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), splitStacks(barterItem, recovered));
        }
    }

    private static int extractAmount(IItemHandler handler, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || probe.getItem() != item) {
                continue;
            }
            ItemStack taken = handler.extractItem(i, remaining, false);
            remaining -= taken.getCount();
        }
        return amount - remaining;
    }

    public static int countStock(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        Item item = ShopTransactionUtil.resolveItem(shop.getListedItemId());
        if (item == null || item == Items.AIR) {
            return 0;
        }
        LinkedStorage linkedStorage = resolveLinkedStorage(level, shop, shopPos);
        if (linkedStorage == null) {
            return 0;
        }
        int total = 0;
        IItemHandler handler = linkedStorage.handler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static LinkedStorage resolveLinkedStorage(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        BlockPos linkedPos = shop.getLinkedStoragePos();
        if (linkedPos == null || linkedPos.equals(shopPos) || !level.hasChunkAt(linkedPos)) {
            return null;
        }
        BlockEntity linked = level.getBlockEntity(linkedPos);
        if (linked == null || linked instanceof ShopBlockEntity) {
            return null;
        }
        IItemHandler handler = linked.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) {
            return null;
        }
        return new LinkedStorage(linkedPos, handler);
    }

    private static boolean isValidLinkTarget(Level level, BlockPos shopPos, BlockPos target) {
        if (target == null || target.equals(shopPos) || target.distManhattan(shopPos) > MAX_LINK_DISTANCE) {
            return false;
        }
        if (!level.hasChunkAt(target)) {
            return false;
        }
        BlockEntity targetBe = level.getBlockEntity(target);
        if (targetBe == null || targetBe instanceof ShopBlockEntity) {
            return false;
        }
        return targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().isPresent();
    }

    private static BlockPos resolveLookedBlockPos(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(LINK_RAYCAST_RANGE));
        BlockHitResult hitResult = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hitResult.getType() == HitResult.Type.BLOCK ? hitResult.getBlockPos() : null;
    }

    private static boolean canExtract(IItemHandler handler, Item item, int count) {
        int remaining = count;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || probe.getItem() != item) {
                continue;
            }
            remaining -= probe.getCount();
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> extract(IItemHandler handler, Item item, int count) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || probe.getItem() != item) {
                continue;
            }
            ItemStack real = handler.extractItem(i, remaining, false);
            if (!real.isEmpty()) {
                remaining -= real.getCount();
                result.add(real);
            }
        }
        return remaining <= 0 ? result : List.of();
    }

    private static boolean canInsertAll(IItemHandler handler, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, true);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean insertAll(IItemHandler handler, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> splitStacks(Item item, int totalCount) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = totalCount;
        int max = Math.max(1, item.getMaxStackSize());
        while (remaining > 0) {
            int count = Math.min(max, remaining);
            stacks.add(new ItemStack(item, count));
            remaining -= count;
        }
        return stacks;
    }

    private static void sendResult(ServerPlayer player, boolean success, String code) {
        ShopPackets.sendToPlayer(player, new S2CPlayerShopResultPacket(success, code));
    }

    private record LinkedStorage(BlockPos pos, IItemHandler handler) {
    }
}
