package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.data.PlayerShopPromoData;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
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
import java.util.UUID;
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
        PlayerShopSettlementSavedData.Snapshot settlement = player.getServer() == null
                ? new PlayerShopSettlementSavedData.Snapshot(0L, 0L, List.of())
                : PlayerShopSettlementSavedData.get(player.getServer()).snapshot(
                shop.getOwnerUuid() == null ? player.getUUID() : shop.getOwnerUuid(),
                pos.asLong(),
                6);
        UUID ownerUuid = shop.getOwnerUuid() == null ? new UUID(0L, 0L) : shop.getOwnerUuid();
        String ownerName = resolveOwnerName(player, ownerUuid);
        List<PlayerShopListingData> listings = shop.getListings().stream()
                .map(listing -> toData(player.level(), shop, pos, listing))
                .toList();

        ShopPackets.sendToPlayer(player, new S2CPlayerShopDataPacket(
                pos,
                owner,
                ownerUuid,
                ownerName,
                listings,
                shop.getLinkedStoragePos() != null,
                settlement.pendingMinor(),
                settlement.lifetimeMinor(),
                settlement.rows(),
                shop.getShopName(),
                shop.isSingleItemMode(),
                shop.isBarterStorageSame()));
    }

    public static void applyConfig(ServerPlayer player, BlockPos pos, String shopName, boolean singleItemMode, boolean barterStorageSame) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return;
        }
        String safeName = shopName == null ? "" : shopName.trim();
        if (safeName.length() > 32) {
            safeName = safeName.substring(0, 32);
        }
        shop.setShopName(safeName);
        shop.setSingleItemMode(singleItemMode);
        shop.setBarterStorageSame(barterStorageSame);
        if (singleItemMode && shop.getListings().size() > 1) {
            while (shop.getListings().size() > 1) {
                shop.removeListing(shop.getListings().size() - 1);
            }
        }
        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, "CONFIG_SAVED");
    }

    public static void applyOwnerAction(ServerPlayer player, BlockPos pos, String action, int listingIndex, int amount) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return;
        }

        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        switch (action) {
            case "ADD_LISTING_MAINHAND" -> {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, "HOLD_ITEM");
                    return;
                }
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key == null || shop.addOrSelectListing(key.toString()) < 0) {
                    sendResult(player, false, "LISTING_LIMIT");
                    return;
                }
            }
            case "REMOVE_LISTING", "CLEAR_LISTING" -> {
                if (!shop.removeListing(listingIndex)) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
            }
            case "TOGGLE_MODE" -> {
                if (listing == null) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
                // Cycle: MONEY → BARTER → BOTH → MONEY
                listing.setTradeMode(switch (listing.tradeMode()) {
                    case MONEY -> ShopBlockEntity.TradeMode.BARTER;
                    case BARTER -> ShopBlockEntity.TradeMode.BOTH;
                    case BOTH -> ShopBlockEntity.TradeMode.MONEY;
                });
            }
            case "SET_PRICE" -> {
                if (listing == null) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
                listing.setMoneyPriceMinor(Math.max(1L, amount));
            }
            case "PRICE_UP" -> {
                if (listing == null) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
                listing.setMoneyPriceMinor(listing.moneyPriceMinor() + Math.max(1, amount));
            }
            case "PRICE_DOWN" -> {
                if (listing == null) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
                listing.setMoneyPriceMinor(Math.max(1L, listing.moneyPriceMinor() - Math.max(1, amount)));
            }
            case "SET_BARTER_MAINHAND" -> {
                if (listing == null) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, "HOLD_ITEM");
                    return;
                }
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key != null) {
                    listing.setBarterItemId(key.toString());
                    listing.setBarterItemCount(Math.max(1, amount));
                }
            }
            case "SET_BARTER_COUNT" -> {
                if (listing == null) {
                    sendResult(player, false, "NO_LISTING");
                    return;
                }
                listing.setBarterItemCount(amount);
            }
            case "LINK_LOOKING" -> {
                PlayerShopLinkService.begin(player, pos);
                sendResult(player, true, "LINK_PENDING");
                return;
            }
            case "LINK_BARTER_LOOKING" -> {
                PlayerShopLinkService.beginBarter(player, pos);
                sendResult(player, true, "BARTER_LINK_PENDING");
                return;
            }
            case "UNLINK" -> shop.setLinkedStoragePos(null);
            case "UNLINK_BARTER" -> shop.setBarterStoragePos(null);
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

    public static void applyPromoAction(ServerPlayer player, BlockPos pos, int listingIndex, boolean clear, String promoType, double promoValue,
                                        int buyX, int buyY, int startsInMinutes, int durationMinutes, boolean flash) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
            sendResult(player, false, "NOT_OWNER");
            return;
        }
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        if (listing == null || listing.itemId().isBlank()) {
            sendResult(player, false, "UNCONFIGURED");
            return;
        }

        if (clear) {
            listing.promo().clear();
            shop.setChanged();
            openFor(player, pos);
            sendResult(player, true, "PROMO_CLEARED");
            return;
        }

        String normalizedType = promoType == null ? "" : promoType.trim().toUpperCase(java.util.Locale.ROOT);
        boolean valid = switch (normalizedType) {
            case "PERCENTAGE", "FLAT", "FLASH" -> promoValue > 0.0D;
            case "BUY_X_GET_Y" -> buyX > 0 && buyY > 0;
            default -> false;
        };
        if (!valid) {
            sendResult(player, false, "PROMO_FAILED");
            return;
        }

        long now = System.currentTimeMillis() / 1000L;
        long startEpoch = startsInMinutes <= 0 ? now : now + (long) startsInMinutes * 60L;
        long endEpoch = durationMinutes <= 0 ? 0L : startEpoch + (long) durationMinutes * 60L;
        listing.promo().configure(flash ? "FLASH" : normalizedType, promoValue, buyX, buyY, startEpoch, endEpoch, flash);
        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, "PROMO_SET");
    }

    public static void buy(ServerPlayer buyer, BlockPos pos, int listingIndex, int quantity) {
        if (!(buyer.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }

        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(pos.asLong(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            int qty = Math.max(1, quantity); // No hard 64 cap — excess drops on floor
            ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
            if (shop.getOwnerUuid() == null || listing == null || listing.itemId().isBlank()) {
                sendResult(buyer, false, "UNCONFIGURED");
                return;
            }

            Item saleItem = ShopTransactionUtil.resolveItem(listing.itemId());
            if (saleItem == null || saleItem == Items.AIR) {
                sendResult(buyer, false, "INVALID_ITEM");
                return;
            }

            LinkedStorage linkedStorage = resolveLinkedStorage(buyer.level(), shop, pos);
            if (linkedStorage == null) {
                sendResult(buyer, false, "NO_LINK");
                return;
            }

            if (!canExtract(linkedStorage.handler(), saleItem, qty)) {
                sendResult(buyer, false, "OUT_OF_STOCK");
                return;
            }

            EconomyProvider provider = BalanceManager.getProvider();
            long cost = Math.max(0L, listing.calculatePrice(qty));
            boolean withdrewFromBuyer = false;
            boolean recordedSale = false;
            Item barterItem = null;
            int barterAmount = 0;
            List<ItemStack> insertedPayment = List.of();

            // Determine trade path: BOTH mode tries money first, falls back to barter
            boolean barterTrade;
            if (listing.tradeMode() == ShopBlockEntity.TradeMode.BOTH) {
                // Try money first: if buyer has enough balance, use money
                long balance = provider.getBalance(buyer.getUUID());
                barterTrade = balance < cost; // Fall back to barter if not enough money
            } else {
                barterTrade = listing.tradeMode() == ShopBlockEntity.TradeMode.BARTER;
            }

            if (!barterTrade) {
                TransactionResult withdraw = provider.withdraw(buyer.getUUID(), cost);
                if (!withdraw.success()) {
                    // If BOTH mode, try barter as fallback
                    if (listing.tradeMode() == ShopBlockEntity.TradeMode.BOTH) {
                        barterTrade = true;
                    } else {
                        sendResult(buyer, false, "NO_MONEY");
                        return;
                    }
                } else {
                    withdrewFromBuyer = true;
                    if (buyer.getServer() == null) {
                        provider.deposit(buyer.getUUID(), cost);
                        sendResult(buyer, false, "SERVER_ERROR");
                        return;
                    }
                    PlayerShopSettlementSavedData.get(buyer.getServer()).recordSale(shop.getOwnerUuid(), pos.asLong(), cost);
                    recordedSale = true;
                }
            }

            if (barterTrade) {
                barterItem = ShopTransactionUtil.resolveItem(listing.barterItemId());
                barterAmount = listing.barterItemCount() * qty;
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
                if (buyer.getServer() != null) {
                    PlayerShopSettlementSavedData.get(buyer.getServer()).recordSale(shop.getOwnerUuid(), pos.asLong(), 0L);
                    recordedSale = true;
                }
            }

            List<ItemStack> extracted = extract(linkedStorage.handler(), saleItem, qty);
            if (extracted.isEmpty()) {
                if (!barterTrade) {
                    if (recordedSale && buyer.getServer() != null) {
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

            // Try to insert into inventory; any overflow drops on the floor
            if (!ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), extracted)) {
                // Drop remaining items at buyer's feet
                for (ItemStack stack : extracted) {
                    if (!stack.isEmpty()) {
                        buyer.drop(stack, false);
                    }
                }
            }

            if (buyer.getServer() != null) {
                TransactionHistoryService.record(
                        buyer,
                        shop.getShopId(),
                        barterTrade ? "BARTER" : "BUY",
                        listing.itemId(),
                        qty,
                        barterTrade ? 0L : cost,
                        barterTrade ? "PLAYER_SHOP_BARTER" : "PLAYER_SHOP");
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
        if (pending.barterLink()) {
            shop.setBarterStoragePos(target);
        } else {
            shop.setLinkedStoragePos(target);
        }
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

    public static int countStock(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        return shop.getListings().stream().mapToInt(listing -> countStock(level, shop, shopPos, listing)).sum();
    }

    public static int countStock(Level level, ShopBlockEntity shop, BlockPos shopPos, ShopBlockEntity.Listing listing) {
        Item item = listing == null ? null : ShopTransactionUtil.resolveItem(listing.itemId());
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

    private static PlayerShopListingData toData(Level level, ShopBlockEntity shop, BlockPos shopPos, ShopBlockEntity.Listing listing) {
        ShopBlockEntity.Promo promo = listing.promo();
        return new PlayerShopListingData(
                listing.itemId(),
                listing.tradeMode().name(),
                listing.moneyPriceMinor(),
                listing.effectiveUnitPriceMinor(),
                listing.barterItemId(),
                listing.barterItemCount(),
                countStock(level, shop, shopPos, listing),
                promo.configured()
                        ? new PlayerShopPromoData(promo.active(), promo.promoType(), promo.promoValue(), promo.buyX(), promo.buyY(), promo.flash())
                        : PlayerShopPromoData.NONE);
    }

    private static String resolveOwnerName(ServerPlayer viewer, UUID ownerUuid) {
        if (ownerUuid == null || ownerUuid.equals(new UUID(0L, 0L))) {
            return "Unowned";
        }
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(ownerUuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return viewer.server.getProfileCache().get(ownerUuid)
                .map(profile -> profile.getName())
                .orElse(ownerUuid.toString().substring(0, 8));
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
