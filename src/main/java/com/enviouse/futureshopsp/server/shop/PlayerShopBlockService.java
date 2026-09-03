package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.economy.ClaimRecord;
import com.enviouse.futureshopsp.server.economy.ClaimState;
import com.enviouse.futureshopsp.server.economy.EconomyTransactionCoordinator;
import com.enviouse.futureshopsp.data.PlayerShopListingData;
import com.enviouse.futureshopsp.data.PlayerShopPromoData;
import com.enviouse.futureshopsp.data.SettlementHistoryRow;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshopsp.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshopsp.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.TransactionResult;
import com.enviouse.futureshopsp.server.transaction.NbtMatchUtil;
import com.enviouse.futureshopsp.server.transaction.ShopTransactionUtil;
import com.enviouse.futureshopsp.server.transaction.TransactionHistoryService;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
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
    private static final ConcurrentHashMap<UUID, BlockPos> PENDING_DESC = new ConcurrentHashMap<>();
    /** Per-listing description pending entries: stores listing index (-1 = shop-level). */
    private static final ConcurrentHashMap<UUID, PendingDescEntry> PENDING_LISTING_DESC = new ConcurrentHashMap<>();
    private record PendingDescEntry(BlockPos shopPos, int listingIndex) {}

    private PlayerShopBlockService() {
    }

    public static void openFor(ServerPlayer player, BlockPos pos) {
        openFor(player, pos, false);
    }

    public static void openFor(ServerPlayer player, BlockPos pos, boolean forceVisitorView) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }

        // Fire ShopOpenEvent on the initial open (not data resends after buy/config)
        String playerShopId = "player_shop:" + pos.asLong();
        boolean isFirstOpen = com.enviouse.futureshopsp.server.session.ShopSessionManager.get(player.getUUID()).isEmpty();
        if (isFirstOpen) {
            var openEvent = new com.enviouse.futureshopsp.event.ShopOpenEvent(player, playerShopId);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(openEvent);
            if (openEvent.isCanceled()) return;
            com.enviouse.futureshopsp.server.session.ShopSessionManager.open(player.getUUID(), playerShopId, pos);
        }

        boolean owner = !forceVisitorView && isOwnerOrFranchiseMember(shop, player);
        PlayerShopSettlementSavedData.Snapshot settlement = player.getServer() == null
                ? new PlayerShopSettlementSavedData.Snapshot(0L, 0L, List.of())
                : PlayerShopSettlementSavedData.get(player.getServer()).snapshot(
                shop.getOwnerUuid() == null ? player.getUUID() : shop.getOwnerUuid(),
                pos.asLong(),
                6);
        UUID ownerUuid = shop.getOwnerUuid() == null ? new UUID(0L, 0L) : shop.getOwnerUuid();
        String ownerName = resolveOwnerName(player, ownerUuid);

        // In single-item mode, visitors see only the visible listing; owners see all with visibility flag
        List<PlayerShopListingData> listings;
        if (shop.isSingleItemMode() && !owner) {
            int visIdx = shop.getVisibleListingIndex();
            ShopBlockEntity.Listing vis = shop.getListing(visIdx);
            if (vis != null) {
                listings = List.of(toData(player.level(), shop, pos, vis, true));
            } else {
                listings = List.of();
            }
        } else {
            int visIdx = shop.getVisibleListingIndex();
            listings = new ArrayList<>();
            for (int i = 0; i < shop.getListings().size(); i++) {
                boolean visible = !shop.isSingleItemMode() || i == visIdx;
                listings.add(toData(player.level(), shop, pos, shop.getListings().get(i), visible));
            }
            listings = List.copyOf(listings);
        }

        // Resolve franchise name for display
        FranchiseSavedData franchiseData = FranchiseSavedData.get(player.getServer());
        FranchiseSavedData.Franchise franchise = franchiseData.getFranchise(ownerUuid);
        String franchiseName = franchise != null ? franchise.name : "";

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
                shop.isBarterStorageSame(),
                shop.getDescription(),
                franchiseName,
                shop.isPlacedByCreative(),
                shop.isAdminShopMode()));
    }

    public static void applyConfig(ServerPlayer player, BlockPos pos, String shopName, boolean singleItemMode, boolean barterStorageSame, int selectedListingIndex) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        String safeName = shopName == null ? "" : shopName.trim();
        if (safeName.length() > 32) {
            safeName = safeName.substring(0, 32);
        }
        shop.setShopName(safeName);
        // Item 19: DON'T delete listings when switching to single-item mode — just hide them
        boolean wasSingleItem = shop.isSingleItemMode();
        shop.setSingleItemMode(singleItemMode);
        if (singleItemMode && !wasSingleItem) {
            // LGB#24: Use the client's currently selected listing, clamped to valid range
            int idx = Math.max(0, Math.min(selectedListingIndex, Math.max(0, shop.getListings().size() - 1)));
            shop.setVisibleListingIndex(idx);
        } else if (!singleItemMode) {
            shop.setVisibleListingIndex(-1); // show all
        }
        shop.setBarterStorageSame(barterStorageSame);
        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, ShopResultCode.CONFIG_SAVED);
    }

    public static void applyOwnerAction(ServerPlayer player, BlockPos pos, String action, int listingIndex, int amount) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }

        // VISIT action is allowed for any player (used by nearby shop tab)
        if ("VISIT".equals(action)) {
            openFor(player, pos, true); // Always open as visitor
            return;
        }

        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }

        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        switch (action) {
            case "ADD_LISTING_MAINHAND" -> {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, ShopResultCode.HOLD_ITEM);
                    return;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
                if (key == null || "minecraft:air".equals(key.toString())) {
                    // Some mods (e.g. Create's staged multi-blocks) briefly expose their
                    // primary item as air. Reject so it never lands in a listing.
                    sendResult(player, false, ShopResultCode.HOLD_ITEM);
                    return;
                }
                // Pass the held item's NBT so addOrSelectListing can distinguish
                // variants (Tacz guns, enchanted books, etc.) instead of
                // collapsing them onto the first listing with a matching id.
                // The block entity also auto-enables NBT-awareness on fresh
                // listings that carry a tag so the manage-screen icon and the
                // minted buy-output both keep their original NBT.
                DataComponentPatch heldTag = held.getComponentsPatch();
                int idx = shop.addOrSelectListing(key.toString(), heldTag);
                if (idx < 0) {
                    sendResult(player, false, ShopResultCode.LISTING_LIMIT);
                    return;
                }
                // No setNbtTag() stomp here — that used to silently rewrite the
                // existing listing's tag every time the player clicked Add with
                // the same item in hand. Identity is now (itemId, nbt) so we
                // either matched the exact listing (no rewrite needed) or
                // added a fresh one (already populated by addOrSelectListing).
                // Reopen so the client sees the new/updated listing immediately
                // — without this the screen sometimes still shows the stale
                // HOLD_ITEM banner from a prior failed click.
                openFor(player, pos);
            }
            case "ADD_BUNDLE_ITEM_MAINHAND" -> {
                // Item 11: Add held item to listing's bundle outputs
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, ShopResultCode.HOLD_ITEM);
                    return;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
                if (key == null || "minecraft:air".equals(key.toString())) {
                    sendResult(player, false, ShopResultCode.INVALID_ITEM);
                    return;
                }
                DataComponentPatch heldNbt = held.getComponentsPatch();
                listing.addBundleOutput(key.toString(), Math.max(1, amount), heldNbt);
            }
            case "REMOVE_BUNDLE_ITEM" -> {
                // Item 11: Remove a bundle entry by index (amount = bundle index)
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.removeBundleOutput(amount);
            }
            case "REMOVE_LISTING", "CLEAR_LISTING" -> {
                if (!shop.removeListing(listingIndex)) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
            }
            case "DISPLAY_Y_UP" -> {
                int steps = Math.max(1, amount);
                shop.adjustDisplayYOffset(steps * ShopBlockEntity.DISPLAY_Y_OFFSET_STEP);
            }
            case "DISPLAY_Y_DOWN" -> {
                int steps = Math.max(1, amount);
                shop.adjustDisplayYOffset(-steps * ShopBlockEntity.DISPLAY_Y_OFFSET_STEP);
            }
            case "DISPLAY_SCALE_UP" -> {
                int steps = Math.max(1, amount);
                shop.adjustDisplayScale(steps * ShopBlockEntity.DISPLAY_SCALE_STEP);
            }
            case "DISPLAY_SCALE_DOWN" -> {
                int steps = Math.max(1, amount);
                shop.adjustDisplayScale(-steps * ShopBlockEntity.DISPLAY_SCALE_STEP);
            }
            case "TOGGLE_NAMEPLATE" -> shop.toggleNameplateHidden();
            case "TOGGLE_MODE" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                // Cycle: MONEY → BARTER → BOTH → MONEY_AND_BARTER → MONEY
                listing.setTradeMode(switch (listing.tradeMode()) {
                    case MONEY -> ShopBlockEntity.TradeMode.BARTER;
                    case BARTER -> ShopBlockEntity.TradeMode.BOTH;
                    case BOTH -> ShopBlockEntity.TradeMode.MONEY_AND_BARTER;
                    case MONEY_AND_BARTER -> ShopBlockEntity.TradeMode.MONEY;
                });
            }
            case "SET_PRICE" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setMoneyPriceMinor(Math.max(1L, amount));
            }
            case "PRICE_UP" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setMoneyPriceMinor(listing.moneyPriceMinor() + Math.max(1, amount));
            }
            case "PRICE_DOWN" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setMoneyPriceMinor(Math.max(1L, listing.moneyPriceMinor() - Math.max(1, amount)));
            }
            case "SET_BARTER_MAINHAND" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    sendResult(player, false, ShopResultCode.HOLD_ITEM);
                    return;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
                if (key != null) {
                    listing.setBarterItemId(key.toString());
                    listing.setBarterItemCount(Math.max(1, amount));
                    // NBT-strict barter payment: snapshot the barter item's NBT at
                    // registration so the buyer can't pay with a partially-full tank
                    // or an enchanted chestplate when the owner posted the plain variant.
                    listing.setBarterNbtAware(!held.getComponentsPatch().isEmpty());
                    listing.setBarterNbtPatch(held.getComponentsPatch());
                }
            }
            case "SET_BARTER_COUNT" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setBarterItemCount(amount);
            }
            case "SET_BASE_QTY" -> {
                // Item 32: Set base quantity per listing. 0 = not configured (purchase blocked)
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setBaseQuantity(Math.max(0, amount));
            }
            case "TOGGLE_NBT_AWARE" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setNbtAware(!listing.nbtAware());
            }
            case "SET_DEPARTMENT" -> {
                // Custom department classification — amount encodes the string via a separate field
                // The department name comes from the action packet's extra string field
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                // Department name is passed in the action string as "SET_DEPARTMENT:DeptName"
                // We handle it through a dedicated method below
                sendResult(player, false, ShopResultCode.USE_SET_DEPARTMENT_ACTION);
                return;
            }
            case "SELECT_VISIBLE_LISTING" -> {
                // Item 20: Select which listing is visible in single-item mode
                if (!shop.isSingleItemMode()) {
                    sendResult(player, false, ShopResultCode.NOT_SINGLE_MODE);
                    return;
                }
                if (listingIndex < 0 || listingIndex >= shop.getListings().size()) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                shop.setVisibleListingIndex(listingIndex);
            }
            case "LINK_LOOKING" -> {
                PlayerShopLinkService.begin(player, pos);
                sendResult(player, true, ShopResultCode.LINK_PENDING);
                return;
            }
            case "LINK_BARTER_LOOKING" -> {
                PlayerShopLinkService.beginBarter(player, pos);
                sendResult(player, true, ShopResultCode.BARTER_LINK_PENDING);
                return;
            }
            case "PENDING_DESC" -> {
                PENDING_DESC.put(player.getUUID(), pos);
                sendResult(player, true, ShopResultCode.DESC_PENDING);
                return;
            }
            case "PENDING_LISTING_DESC" -> {
                PENDING_LISTING_DESC.put(player.getUUID(), new PendingDescEntry(pos, listingIndex));
                sendResult(player, true, ShopResultCode.LISTING_DESC_PENDING);
                return;
            }
            case "UNLINK" -> shop.setLinkedStoragePos(null);
            case "UNLINK_BARTER" -> shop.setBarterStoragePos(null);
            case "COPY_CONFIG" -> {
                ShopConfigClipboard.store(player.getUUID(), shop.exportConfigSnapshot());
                shop.setChanged();
                openFor(player, pos);
                sendResult(player, true, ShopResultCode.CONFIG_COPIED);
                return;
            }
            case "PASTE_CONFIG" -> {
                net.minecraft.nbt.CompoundTag snap = ShopConfigClipboard.snapshot(player.getUUID());
                if (snap == null) {
                    sendResult(player, false, ShopResultCode.NO_CLIPBOARD);
                    return;
                }
                shop.applyConfigSnapshot(snap);
                shop.setChanged();
                openFor(player, pos);
                sendResult(player, true, ShopResultCode.CONFIG_SAVED);
                return;
            }
            case "CLAIM_SETTLEMENT" -> {
                if (player.getServer() == null) {
                    sendResult(player, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                PlayerShopSettlementSavedData settlements = PlayerShopSettlementSavedData.get(player.getServer());
                PlayerShopSettlementSavedData.SettlementClaim settlementClaim =
                        settlements.beginClaim(player.getUUID(), pos.asLong());
                if (settlementClaim == null || settlementClaim.amountMinor() <= 0L) {
                    sendResult(player, false, ShopResultCode.NOTHING_TO_CLAIM);
                    return;
                }
                RequestId requestId = new RequestId(settlementClaim.requestId());
                EconomyTransactionCoordinator coordinator = BalanceManager.getCoordinator();
                String description = "player shop settlement " + pos.asLong();
                ClaimRecord claim = coordinator.claim(requestId).orElseGet(() ->
                        coordinator.createClaim(requestId, player.getUUID(), settlementClaim.amountMinor(), description));
                MutationRequest depositRequest = MutationRequest.forPlayer(requestId, player.getUUID(),
                        settlementClaim.amountMinor(), MutationKind.DEPOSIT);
                ProviderResult<?> deposit = coordinator.deposit(depositRequest);
                if (!deposit.confirmed()) {
                    sendResult(player, false, mapProviderError(deposit));
                    return;
                }
                if (claim.state() != ClaimState.RESOLVED) {
                    coordinator.deliverClaim(requestId);
                    coordinator.resolveClaim(requestId);
                }
                if (!settlements.completeClaim(player.getUUID(), pos.asLong(), settlementClaim.requestId(),
                        settlementClaim.amountMinor())) {
                    sendResult(player, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                // Record claim funds in transaction history
                TransactionHistoryService.record(
                        player,
                        shop.getShopId(),
                        "CART_CLAIM",
                        "",
                        0,
                        settlementClaim.amountMinor(),
                        "SETTLEMENT_CLAIM");
            }
            default -> {
                return;
            }
        }

        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, ShopResultCode.OK);
    }

    /**
     * Custom department classification: sets the department on a listing and registers it globally.
     */
    public static void setDepartment(ServerPlayer player, BlockPos pos, int listingIndex, String department) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        if (listing == null) {
            sendResult(player, false, ShopResultCode.NO_LISTING);
            return;
        }
        String safeDept = department == null ? "" : department.trim();
        if (safeDept.length() > 48) safeDept = safeDept.substring(0, 48);
        listing.setDepartment(safeDept);
        // Register the department globally for search/discovery
        if (!safeDept.isBlank() && player.getServer() != null) {
            DepartmentSavedData.get(player.getServer()).addDepartment(safeDept);
        }
        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, ShopResultCode.DEPARTMENT_SET);
    }

    public static void applyPromoAction(ServerPlayer player, BlockPos pos, int listingIndex, boolean clear, String promoType, double promoValue,
                                        int buyX, int buyY, int startsInMinutes, int durationMinutes, boolean flash) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
        if (listing == null || listing.itemId().isBlank()) {
            sendResult(player, false, ShopResultCode.UNCONFIGURED);
            return;
        }

        if (clear) {
            listing.promo().clear();
            shop.setChanged();
            openFor(player, pos);
            sendResult(player, true, ShopResultCode.PROMO_CLEARED);
            return;
        }

        String normalizedType = promoType == null ? "" : promoType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Double.isFinite(promoValue) || promoValue < 0.0D) {
            sendResult(player, false, ShopResultCode.PROMO_FAILED);
            return;
        }
        boolean valid = switch (normalizedType) {
            case "PERCENTAGE", "FLAT", "FLASH" -> isValidPromoValue(normalizedType, promoValue);
            case "BUY_X_GET_Y" -> buyX > 0 && buyY > 0;
            default -> false;
        };
        if (!valid) {
            sendResult(player, false, ShopResultCode.PROMO_FAILED);
            return;
        }

        long now = System.currentTimeMillis() / 1000L;
        long startEpoch = startsInMinutes <= 0 ? now : now + (long) startsInMinutes * 60L;
        long endEpoch = durationMinutes <= 0 ? 0L : startEpoch + (long) durationMinutes * 60L;
        listing.promo().configure(flash ? "FLASH" : normalizedType, promoValue, buyX, buyY, startEpoch, endEpoch, flash);
        shop.setChanged();
        openFor(player, pos);
        sendResult(player, true, ShopResultCode.PROMO_SET);
    }

    static boolean isValidPromoValue(String promoType, double promoValue) {
        if (!Double.isFinite(promoValue) || promoValue <= 0.0D) {
            return false;
        }
        return switch (promoType) {
            case "PERCENTAGE", "FLASH" -> promoValue <= 100.0D;
            case "FLAT" -> promoValue <= Long.MAX_VALUE
                    / Math.pow(10.0D, com.enviouse.futureshopsp.Config.economyCurrencyDecimals);
            default -> false;
        };
    }

    public static void buy(ServerPlayer buyer, BlockPos pos, int listingIndex, int quantity, String paymentMethod) {
        if (!(buyer.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }

        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(pos.asLong(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (quantity <= 0 || quantity > ShopTransactionUtil.MAX_BUY_QUANTITY) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            int qty = quantity;
            ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
            if (shop.getOwnerUuid() == null || listing == null || listing.itemId().isBlank()) {
                sendResult(buyer, false, ShopResultCode.UNCONFIGURED);
                return;
            }

            Item saleItem = ShopTransactionUtil.resolveItem(listing.itemId());
            if (saleItem == null || saleItem == Items.AIR) {
                sendResult(buyer, false, ShopResultCode.INVALID_ITEM);
                return;
            }

            // Item 32 fix: Reject purchase if base quantity is 0 (not yet configured by owner)
            if (listing.baseQuantity() <= 0) {
                sendResult(buyer, false, ShopResultCode.UNCONFIGURED);
                return;
            }

            // NBT-aware matching context
            boolean nbtAware = listing.nbtAware();
            DataComponentPatch nbtPatch = listing.nbtPatch();

            // Check if this is a bundle listing (Item 11)
            boolean isBundle = !listing.bundleOutputs().isEmpty();

            int deliverCount = checkedDeliveryCount(listing.baseQuantity(), qty);
            if (deliverCount <= 0) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }

            // ═══ Admin shop short-circuit ═══
            // Infinite stock, money sunk on buys, barter inputs voided. No
            // linked-storage or barter-storage required.
            if (shop.isAdminShopMode()) {
                handleAdminShopBuy(buyer, shop, pos, listing, qty, deliverCount, saleItem, paymentMethod);
                return;
            }

            LinkedStorage linkedStorage = resolveLinkedStorage(buyer.level(), shop, pos);
            if (linkedStorage == null) {
                sendResult(buyer, false, ShopResultCode.NO_LINK);
                return;
            }

            // ═══ Stock check — NBT-aware + bundle support ═══
            if (isBundle) {
                // Bundle: check each output entry
                for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                    Item bundleItem = ShopTransactionUtil.resolveItem(entry.itemId());
                    if (bundleItem == null || bundleItem == Items.AIR) {
                        sendResult(buyer, false, ShopResultCode.INVALID_ITEM);
                        return;
                    }
                    int needed = checkedDeliveryCount(entry.count(), qty);
                    if (needed <= 0) {
                        sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                        return;
                    }
                    if (!canExtractNbt(linkedStorage, bundleItem, needed, entry.nbtPatch() != null, entry.nbtPatch())) {
                        sendResult(buyer, false, ShopResultCode.OUT_OF_STOCK);
                        return;
                    }
                }
            } else {
                if (!canExtractNbt(linkedStorage, saleItem, deliverCount, nbtAware, nbtPatch)) {
                    sendResult(buyer, false, ShopResultCode.OUT_OF_STOCK);
                    return;
                }
            }

            EconomyTransactionCoordinator coordinator = BalanceManager.getCoordinator();
            RequestId transactionId = RequestId.random();
            RequestId custodyId = null;
            String custodyItem = "player-shop:" + pos.asLong() + ":" + listing.itemId();
            long custodyQuantity = deliveryEntitlementQuantity(listing, qty, deliverCount);
            if (custodyQuantity <= 0L) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            String custodyHash = deliveryEntitlementHash(listing, qty, custodyQuantity);
            boolean custodyHeld = false;
            long cost = listing.calculatePrice(qty);
            if (cost < 0L) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            boolean withdrewFromBuyer = false;
            boolean recordedSale = false;
            Item barterItem = null;
            int barterAmount = 0;
            List<ItemStack> insertedPayment = List.of();
            PlayerShopBarterEscrowSavedData barterEscrow = null;
            UUID barterEscrowRequestId = transactionId.child("barter item escrow").value();
            boolean barterEscrowPrepared = false;
            List<ItemStack> escrowPreview = List.of();

            boolean barterTrade;
            boolean compoundTrade = listing.tradeMode() == ShopBlockEntity.TradeMode.MONEY_AND_BARTER;
            if (compoundTrade) {
                barterTrade = false;
            } else if (listing.tradeMode() == ShopBlockEntity.TradeMode.BOTH) {
                // LGB#BOTH-guard: BOTH listings *must* carry an explicit payment tag from
                // the client. A blank paymentMethod is a legacy-ctor regression (pre-LGB#4
                // callers) — log + reject rather than silently balance-checking, which was
                // the source of the "clicked Barter but got charged money" bug.
                if (paymentMethod == null || paymentMethod.isBlank()) {
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "[FutureShops] BOTH-mode buy from {} at {} missing paymentMethod — rejecting.",
                            buyer.getGameProfile().getName(), pos);
                    sendResult(buyer, false, ShopResultCode.INVALID_REQUEST);
                    return;
                }
                // LGB#4: Respect client-provided payment preference.
                if ("BARTER".equalsIgnoreCase(paymentMethod)) {
                    barterTrade = true;
                } else if ("MONEY".equalsIgnoreCase(paymentMethod)) {
                    barterTrade = false;
                } else {
                    // Unknown tag (e.g. "MONEY_AND_BARTER" sent for a BOTH listing) — reject.
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "[FutureShops] BOTH-mode buy from {} at {} with unexpected paymentMethod='{}' — rejecting.",
                            buyer.getGameProfile().getName(), pos, paymentMethod);
                    sendResult(buyer, false, ShopResultCode.INVALID_REQUEST);
                    return;
                }
            } else {
                barterTrade = listing.tradeMode() == ShopBlockEntity.TradeMode.BARTER;
            }

            // ═══ Fire ShopTransactionEvent.Pre (spec §33) ═══
            String tradeTypeForEvent = compoundTrade ? "MONEY_AND_BARTER" : (barterTrade ? "BARTER" : "BUY");
            String shopIdForEvent = "player_shop:" + pos.asLong();
            if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
                var preEvent = new com.enviouse.futureshopsp.event.ShopTransactionEvent.Pre(
                        buyer, shopIdForEvent, listing.itemId(), qty, tradeTypeForEvent, cost);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(preEvent);
                if (preEvent.isCanceled()) {
                    sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                    return;
                }
                // Allow external mods to modify price
                cost = preEvent.getPriceMinor();
            }
            if (cost < 0L) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            if (buyer.getServer() != null && !PlayerShopSettlementSavedData.get(buyer.getServer())
                    .canRecordSale(shop.getOwnerUuid(), pos.asLong(), cost)) {
                sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                return;
            }

            // ═══ Item 16/17: Pre-validate barter payment insertion (overflow detection) ═══
            // Item 24: Use effective barter count (promo-adjusted, rounds up)
            // Item 31: Use resolveBarterStorage() for barter payment destination
            LinkedStorage barterStorage = null;
            if (compoundTrade || barterTrade) {
                barterItem = ShopTransactionUtil.resolveItem(listing.barterItemId());
                barterAmount = listing.effectiveBarterTotal(qty);
                if (barterAmount < 0) {
                    sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                    return;
                }
                // NBT-strict barter payment: if the listing captured an NBT tag for the barter
                // item (e.g. an "empty" tank, a specific enchanted chestplate), only stacks
                // matching that tag exactly count as valid payment. Prevents the exploit where
                // a buyer paid with a half-full tank and got accepted as if it were empty.
                boolean barterNbtAware = listing.barterNbtAware();
                DataComponentPatch barterNbtPatch = listing.barterNbtPatch();
                if (barterItem == null || barterItem == Items.AIR || ShopTransactionUtil.countItems(buyer.getInventory(), barterItem, barterNbtAware, barterNbtPatch) < barterAmount) {
                    if (compoundTrade) {
                        sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, "§cTrade cancelled: you don't have enough barter items.");
                        return;
                    }
                    // BOTH mode: if barter fails, it was fallback — send result
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, "§cTrade cancelled: you don't have enough barter items.");
                    return;
                }
                barterStorage = resolveBarterStorage(buyer.level(), shop, pos);
                if (barterStorage == null) {
                    sendResultWithChat(buyer, false, ShopResultCode.NO_LINK, "§cTrade cancelled: barter storage is not linked.");
                    return;
                }
                // Pre-flight insert check uses a synthesized stack list so we can validate
                // capacity without touching the buyer's inventory. The actual payment stacks
                // (with the buyer's real NBT preserved) are collected later via
                // ShopTransactionUtil.collectAndRemoveItems so the owner receives exactly
                // what the buyer paid with — enchanted chestplate in, enchanted chestplate
                // out, rather than a freshly-minted plain one.
                List<ItemStack> previewPayment = ShopTransactionUtil.snapshotMatchingItems(
                        buyer.getInventory(), barterItem, barterAmount, listing.barterNbtAware(), listing.barterNbtPatch());
                escrowPreview = previewPayment;
                if (previewPayment.isEmpty()) {
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, "§cTrade cancelled: barter items could not be reserved.");
                    return;
                }
                boolean canInsertPayment = barterStorage.hasAdapter()
                        ? barterStorage.adapter().canInsert(barterStorage.blockEntity(), previewPayment)
                        : canInsertAll(barterStorage.handler(), previewPayment);
                if (!canInsertPayment) {
                    // Item 18: Close UI + chat message for storage full
                    sendResultWithChat(buyer, false, ShopResultCode.STORAGE_FULL, "§cTrade cancelled: the shop's storage is full and cannot accept your barter items.");
                    return;
                }
            }

            if (compoundTrade || barterTrade) {
                if (buyer.getServer() == null || escrowPreview.isEmpty()
                        || !((barterEscrow = PlayerShopBarterEscrowSavedData.get(buyer.getServer())).prepare(
                        barterEscrowRequestId, buyer.getUUID(), pos.asLong(),
                        buyer.level().dimension().location().toString(), listing.itemId(), barterAmount,
                        escrowPreview, buyer.level().registryAccess()))) {
                    sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                barterEscrowPrepared = true;
            }

            // ═══ MONEY_AND_BARTER compound path ═══
            if (compoundTrade) {
                // LGB#8: Skip money withdrawal if cost is 0 (100% discount)
                if (cost > 0L) {
                    custodyId = custodyIdFor(transactionId, "buyer compound debit");
                    ProviderResult<MutationReceipt> debit = coordinatorMutationWithCustody(coordinator,
                            transactionId, "buyer compound debit", buyer.getUUID(), cost,
                            custodyItem, custodyQuantity, custodyHash);
                    if (!debit.confirmed()) {
                        if (barterEscrowPrepared) {
                            barterEscrow.markRefunded(barterEscrowRequestId);
                        }
                        sendResultWithChat(buyer, false, mapProviderResultCode(debit), "§cTrade cancelled: the economy provider could not confirm this payment.");
                        return;
                    }
                    custodyHeld = true;
                    withdrewFromBuyer = true;
                }

                // Fire BarterTradeEvent.Pre (spec §33) for barter portion
                if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
                    var barterPre = new com.enviouse.futureshopsp.event.BarterTradeEvent.Pre(
                            buyer.getUUID(), shopIdForEvent, listing.itemId() + ":compound",
                            listing.itemId(), qty,
                            List.of(new com.enviouse.futureshopsp.event.BarterTradeEvent.IngredientEntry(listing.barterItemId(), barterAmount)));
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(barterPre);
                    if (barterPre.isCanceled()) {
                        if (barterEscrowPrepared) {
                            barterEscrow.markRefunded(barterEscrowRequestId);
                        }
                        if (withdrewFromBuyer) {
                            TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                                    "buyer compound refund", buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                            if (refund.success()) {
                                releaseCustody(coordinator, custodyId, custodyHeld);
                            }
                        }
                        sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                        return;
                    }
                }

                // Transfer the buyer's ACTUAL stacks (NBT intact) to the owner's storage,
                // instead of creating fresh plain stacks — so an enchanted / tagged payment
                // arrives as an enchanted / tagged item in storage.
                List<ItemStack> paymentStacks = ShopTransactionUtil.collectAndRemoveItems(
                        buyer.getInventory(), barterItem, barterAmount,
                        listing.barterNbtAware(), listing.barterNbtPatch());
                if (paymentStacks.isEmpty()) {
                    if (barterEscrowPrepared) {
                        barterEscrow.markRefunded(barterEscrowRequestId);
                    }
                    TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                            "buyer payment refund", buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                    if (refund.success()) {
                        releaseCustody(coordinator, custodyId, custodyHeld);
                    }
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, "§cTrade cancelled: barter items could not be taken.");
                    return;
                }
                if (barterEscrowPrepared && !barterEscrow.markRemoved(barterEscrowRequestId, paymentStacks,
                        buyer.level().registryAccess())) {
                    restorePaymentToBuyer(buyer, paymentStacks);
                    barterEscrow.markRefunded(barterEscrowRequestId);
                    TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                            "buyer escrow mismatch refund", buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                    if (refund.success()) {
                        releaseCustody(coordinator, custodyId, custodyHeld);
                    }
                    sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                // Item 31: Insert barter payment into barter-specific storage
                boolean insertedOk = barterStorage.hasAdapter()
                        ? barterStorage.adapter().insert(barterStorage.blockEntity(), paymentStacks)
                        : insertAll(barterStorage.handler(), paymentStacks);
                if (!insertedOk) {
                    restorePaymentToBuyer(buyer, paymentStacks);
                    if (barterEscrowPrepared) {
                        barterEscrow.markRefunded(barterEscrowRequestId);
                    }
                    TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                            "buyer storage refund", buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                    if (refund.success()) {
                        releaseCustody(coordinator, custodyId, custodyHeld);
                    }
                    sendResultWithChat(buyer, false, ShopResultCode.STORAGE_FULL, "§cTrade cancelled: the shop's storage is full.");
                    return;
                }
                insertedPayment = paymentStacks;
                if (barterEscrowPrepared && !barterEscrow.markStored(barterEscrowRequestId)) {
                    barterEscrow.markRecoveryRequired(barterEscrowRequestId);
                    sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                if (buyer.getServer() != null) {
                    boolean recorded = PlayerShopSettlementSavedData.get(buyer.getServer())
                            .recordSale(shop.getOwnerUuid(), pos.asLong(), cost, listing.itemId(), qty);
                    if (!recorded) {
                        TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                                "buyer settlement overflow refund", buyer.getUUID(), null, cost,
                                MutationKind.DEPOSIT);
                        if (refund.success()) {
                            releaseCustody(coordinator, custodyId, custodyHeld);
                            rollbackBarterPayment(barterStorage, buyer, barterItem, barterAmount,
                                    insertedPayment, listing.barterNbtAware(), listing.barterNbtPatch(),
                                    barterEscrow, barterEscrowRequestId);
                        }
                        sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                        return;
                    }
                    recordedSale = true;
                }
            } else if (!barterTrade) {
                // LGB#8: Skip money withdrawal if cost is 0 (100% discount)
                if (cost <= 0L) {
                    // Free item — no withdrawal needed
                    if (buyer.getServer() != null) {
                        boolean recorded = PlayerShopSettlementSavedData.get(buyer.getServer())
                                .recordSale(shop.getOwnerUuid(), pos.asLong(), 0L, listing.itemId(), qty);
                        if (!recorded) {
                            sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                            return;
                        }
                        recordedSale = true;
                    }
                } else {
                    custodyId = custodyIdFor(transactionId, "buyer debit");
                    ProviderResult<MutationReceipt> debit = coordinatorMutationWithCustody(coordinator,
                            transactionId, "buyer debit", buyer.getUUID(), cost,
                            custodyItem, custodyQuantity, custodyHash);
                    if (!debit.confirmed()) {
                        if (listing.tradeMode() == ShopBlockEntity.TradeMode.BOTH
                                && debit.error() == ProviderError.INSUFFICIENT_FUNDS) {
                            barterTrade = true;
                        } else {
                            sendResult(buyer, false, mapProviderResultCode(debit));
                            return;
                        }
                    } else {
                        custodyHeld = true;
                        withdrewFromBuyer = true;
                        if (buyer.getServer() == null) {
                            TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                                    "buyer server refund", buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                            if (refund.success()) {
                                releaseCustody(coordinator, custodyId, custodyHeld);
                            }
                            sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                            return;
                        }
                        boolean recorded = PlayerShopSettlementSavedData.get(buyer.getServer())
                                .recordSale(shop.getOwnerUuid(), pos.asLong(), cost, listing.itemId(), qty);
                        if (!recorded) {
                            TransactionResult refund = coordinatorMutation(coordinator, transactionId,
                                    "buyer settlement overflow refund", buyer.getUUID(), null, cost,
                                    MutationKind.DEPOSIT);
                            if (refund.success()) {
                                releaseCustody(coordinator, custodyId, custodyHeld);
                            }
                            sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                            return;
                        }
                        recordedSale = true;
                    }
                }
            }

            if (barterTrade) {
                if (barterItem == null || barterAmount <= 0) {
                    barterItem = ShopTransactionUtil.resolveItem(listing.barterItemId());
                    barterAmount = listing.effectiveBarterTotal(qty);
                    if (barterAmount < 0) {
                        sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                        return;
                    }
                    if (barterItem == null || barterItem == Items.AIR
                            || ShopTransactionUtil.countItems(buyer.getInventory(), barterItem,
                            listing.barterNbtAware(), listing.barterNbtPatch()) < barterAmount) {
                        sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS,
                                "§cTrade cancelled: you don't have enough barter items.");
                        return;
                    }
                }
                // Item 31: Use barterStorage for barter payment insertion
                if (barterStorage == null) {
                    barterStorage = resolveBarterStorage(buyer.level(), shop, pos);
                }
                if (barterStorage == null) {
                    sendResultWithChat(buyer, false, ShopResultCode.NO_LINK, "§cTrade cancelled: barter storage is not linked.");
                    return;
                }
                if (!barterEscrowPrepared) {
                    escrowPreview = ShopTransactionUtil.snapshotMatchingItems(
                            buyer.getInventory(), barterItem, barterAmount,
                            listing.barterNbtAware(), listing.barterNbtPatch());
                    boolean canInsertPayment = barterStorage.hasAdapter()
                            ? barterStorage.adapter().canInsert(barterStorage.blockEntity(), escrowPreview)
                            : canInsertAll(barterStorage.handler(), escrowPreview);
                    if (escrowPreview.isEmpty() || !canInsertPayment || buyer.getServer() == null
                            || !((barterEscrow = PlayerShopBarterEscrowSavedData.get(buyer.getServer())).prepare(
                            barterEscrowRequestId, buyer.getUUID(), pos.asLong(),
                            buyer.level().dimension().location().toString(), listing.itemId(), barterAmount,
                            escrowPreview, buyer.level().registryAccess()))) {
                        sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                        return;
                    }
                    barterEscrowPrepared = true;
                }

                // Fire BarterTradeEvent.Pre (spec §33) for pure barter
                if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
                    var barterPre = new com.enviouse.futureshopsp.event.BarterTradeEvent.Pre(
                            buyer.getUUID(), shopIdForEvent, listing.itemId() + ":barter",
                            listing.itemId(), qty,
                            List.of(new com.enviouse.futureshopsp.event.BarterTradeEvent.IngredientEntry(listing.barterItemId(), barterAmount)));
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(barterPre);
                    if (barterPre.isCanceled()) {
                        if (barterEscrowPrepared) {
                            barterEscrow.markRefunded(barterEscrowRequestId);
                        }
                        sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                        return;
                    }
                }

                // Transfer the buyer's ACTUAL stacks (NBT intact) to the owner's storage.
                List<ItemStack> paymentStacks = ShopTransactionUtil.collectAndRemoveItems(
                        buyer.getInventory(), barterItem, barterAmount,
                        listing.barterNbtAware(), listing.barterNbtPatch());
                if (paymentStacks.isEmpty()) {
                    if (barterEscrowPrepared) {
                        barterEscrow.markRefunded(barterEscrowRequestId);
                    }
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, "§cTrade cancelled: barter items could not be taken.");
                    return;
                }
                if (barterEscrowPrepared && !barterEscrow.markRemoved(barterEscrowRequestId, paymentStacks,
                        buyer.level().registryAccess())) {
                    restorePaymentToBuyer(buyer, paymentStacks);
                    barterEscrow.markRefunded(barterEscrowRequestId);
                    sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                boolean insertedOk = barterStorage.hasAdapter()
                        ? barterStorage.adapter().insert(barterStorage.blockEntity(), paymentStacks)
                        : insertAll(barterStorage.handler(), paymentStacks);
                if (!insertedOk) {
                    restorePaymentToBuyer(buyer, paymentStacks);
                    if (barterEscrowPrepared) {
                        barterEscrow.markRefunded(barterEscrowRequestId);
                    }
                    sendResultWithChat(buyer, false, ShopResultCode.STORAGE_FULL, "§cTrade cancelled: the shop's storage is full.");
                    return;
                }
                insertedPayment = paymentStacks;
                if (barterEscrowPrepared && !barterEscrow.markStored(barterEscrowRequestId)) {
                    barterEscrow.markRecoveryRequired(barterEscrowRequestId);
                    sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                if (buyer.getServer() != null) {
                    boolean recorded = PlayerShopSettlementSavedData.get(buyer.getServer())
                            .recordSale(shop.getOwnerUuid(), pos.asLong(), 0L, listing.itemId(), qty);
                    if (!recorded) {
                        rollbackBarterPayment(barterStorage, buyer, barterItem, barterAmount, insertedPayment,
                                listing.barterNbtAware(), listing.barterNbtPatch(), barterEscrow, barterEscrowRequestId);
                        sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                        return;
                    }
                    recordedSale = true;
                }
            }

            // ═══ Extract sale items — NBT-aware + bundle support ═══
            List<ItemStack> extracted;
            if (isBundle) {
                extracted = new ArrayList<>();
                for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                    Item bundleItem = ShopTransactionUtil.resolveItem(entry.itemId());
                    int needed = checkedDeliveryCount(entry.count(), qty);
                    if (needed <= 0) {
                        rollbackAll(linkedStorage, barterStorage, buyer, coordinator, transactionId, custodyId, custodyHeld,
                                withdrewFromBuyer, cost, recordedSale,
                                shop.getOwnerUuid(), pos, barterItem, barterAmount, insertedPayment, compoundTrade, barterTrade,
                                listing.barterNbtAware(), listing.barterNbtPatch(), barterEscrow, barterEscrowRequestId);
                        sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                        return;
                    }
                    List<ItemStack> part = extractNbt(linkedStorage, bundleItem, needed, entry.nbtPatch() != null, entry.nbtPatch());
                    if (part.isEmpty()) {
                        // Rollback already-extracted items
                        for (ItemStack ex : extracted) {
                            if (!ex.isEmpty()) {
                                reinsert(linkedStorage, ex);
                            }
                        }
                        rollbackAll(linkedStorage, barterStorage, buyer, coordinator, transactionId, custodyId, custodyHeld,
                                withdrewFromBuyer, cost, recordedSale,
                                shop.getOwnerUuid(), pos, barterItem, barterAmount, insertedPayment, compoundTrade, barterTrade,
                                listing.barterNbtAware(), listing.barterNbtPatch(), barterEscrow, barterEscrowRequestId);
                        sendResult(buyer, false, ShopResultCode.ROLLBACK);
                        return;
                    }
                    extracted.addAll(part);
                }
            } else {
                extracted = extractNbt(linkedStorage, saleItem, deliverCount, nbtAware, nbtPatch);
            }

            if (extracted.isEmpty()) {
                rollbackAll(linkedStorage, barterStorage, buyer, coordinator, transactionId, custodyId, custodyHeld,
                        withdrewFromBuyer, cost, recordedSale,
                        shop.getOwnerUuid(), pos, barterItem, barterAmount, insertedPayment, compoundTrade, barterTrade,
                        listing.barterNbtAware(), listing.barterNbtPatch(), barterEscrow, barterEscrowRequestId);
                sendResult(buyer, false, ShopResultCode.ROLLBACK);
                return;
            }

            // Try to insert into inventory; overflow drops on the floor
            if (!ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), extracted)) {
                for (ItemStack stack : extracted) {
                    if (!stack.isEmpty()) {
                        buyer.drop(stack, false);
                    }
                }
            }
            if (custodyHeld) {
                try {
                    coordinator.deliverCustody(custodyId);
                    coordinator.claimCustody(custodyId);
                } catch (RuntimeException exception) {
                    sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
            }
            if (barterEscrowPrepared && !barterEscrow.markComplete(barterEscrowRequestId)) {
                barterEscrow.markRecoveryRequired(barterEscrowRequestId);
                sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                return;
            }

            if (buyer.getServer() != null) {
                String tradeType = compoundTrade ? "MONEY_AND_BARTER" : (barterTrade ? "BARTER" : "BUY");
                String source = compoundTrade ? "PLAYER_SHOP_COMPOUND" : (barterTrade ? "PLAYER_SHOP_BARTER" : "PLAYER_SHOP");
                String historyNote = (barterTrade || compoundTrade) && barterItem != null
                        ? "paid=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(barterItem) + "\u00d7" + barterAmount
                        : source;
                TransactionHistoryService.record(
                        buyer,
                        shop.getShopId(),
                        tradeType,
                        listing.itemId(),
                        qty,
                        barterTrade ? 0L : cost,
                        historyNote);

                // ═══ Fire ShopTransactionEvent.Post (spec §33) ═══
                if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
                    long eventCost = barterTrade ? 0L : cost;
                    coordinator.balance(buyer.getUUID()).value().ifPresent(balance ->
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                    new com.enviouse.futureshopsp.event.ShopTransactionEvent.Post(
                                            buyer.getUUID(), shopIdForEvent, listing.itemId(),
                                            qty, tradeType, eventCost, balance.balanceMinorUnits())));

                    // Fire BarterTradeEvent.Post for barter/compound trades
                    if (barterTrade || compoundTrade) {
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                new com.enviouse.futureshopsp.event.BarterTradeEvent.Post(
                                        buyer.getUUID(), shopIdForEvent,
                                        listing.itemId() + (compoundTrade ? ":compound" : ":barter"),
                                        listing.itemId(), qty,
                                        List.of(new com.enviouse.futureshopsp.event.BarterTradeEvent.IngredientEntry(
                                                listing.barterItemId(), barterAmount))));
                    }
                }
            }
            openFor(buyer, pos, isOwnerOrFranchiseMember(shop, buyer));
            sendResult(buyer, true, ShopResultCode.BOUGHT);
        } finally {
            lock.unlock();
        }
    }

    public static int confirmLink(ServerPlayer player) {
        PlayerShopLinkService.PendingLink pending = PlayerShopLinkService.consume(player.getUUID());
        if (pending == null) {
            sendResult(player, false, ShopResultCode.LINK_NONE);
            return 0;
        }
        BlockPos shopPos = pending.shopPos();
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop)) {
            sendResult(player, false, ShopResultCode.LINK_NONE);
            return 0;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return 0;
        }
        BlockPos target = resolveLookedBlockPos(player);
        LinkTargetResult linkResult = validateLinkTarget(player.level(), shopPos, target);
        if (linkResult != LinkTargetResult.OK) {
            sendResult(player, false, linkResult == LinkTargetResult.RS_NOT_CONTROLLER ? ShopResultCode.RS_NOT_CONTROLLER : ShopResultCode.BAD_LINK_TARGET);
            return 0;
        }
        if (pending.barterLink()) {
            shop.setBarterStoragePos(target);
        } else {
            shop.setLinkedStoragePos(target);
        }
        shop.setChanged();
        openFor(player, shopPos);
        // LGB#21: Differentiate barter link success message
        sendResult(player, true, pending.barterLink() ? ShopResultCode.BARTER_LINKED : ShopResultCode.LINKED);
        return 1;
    }

    /**
     * Applies a shop description from the /desc command.
     * Consumes the pending-desc entry created when the owner clicked the Desc button.
     */
    public static int applyDescription(ServerPlayer player, String description) {
        // Check for listing-level pending first
        PendingDescEntry listingEntry = PENDING_LISTING_DESC.remove(player.getUUID());
        if (listingEntry != null) {
            if (!(player.level().getBlockEntity(listingEntry.shopPos()) instanceof ShopBlockEntity shop)) {
                return 0;
            }
            if (!isOwnerOrFranchiseMember(shop, player)) {
                return 0;
            }
            ShopBlockEntity.Listing listing = shop.getListing(listingEntry.listingIndex());
            if (listing != null) {
                listing.setListingDescription(description);
                shop.setChanged();
                return 2; // 2 = listing-level description
            }
            return 0;
        }
        // Fall back to shop-level description
        BlockPos shopPos = PENDING_DESC.remove(player.getUUID());
        if (shopPos == null) {
            return 0;
        }
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop)) {
            return 0;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            return 0;
        }
        shop.setDescription(description);
        return 1;
    }

    public static void sendSettlementHistoryPage(ServerPlayer player, BlockPos shopPos, int page, int pageSize,
                                                 SettlementHistoryRow.SettlementFilter filter,
                                                 long fromEpochSeconds,
                                                 long toEpochSeconds) {
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop) || player.getServer() == null) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        PlayerShopSettlementSavedData data = PlayerShopSettlementSavedData.get(player.getServer());
        int safePageSize = Math.max(1, pageSize);
        int totalPages = data.getTotalPages(player.getUUID(), shopPos.asLong(), safePageSize, filter, fromEpochSeconds, toEpochSeconds);
        int safePage = Math.max(1, Math.min(page, totalPages));
        List<SettlementHistoryRow> rows = data.getPage(player.getUUID(), shopPos.asLong(), safePage, safePageSize, filter, fromEpochSeconds, toEpochSeconds);
        ShopPackets.sendToPlayer(player, new S2CSettlementHistoryPacket(shopPos, safePage, totalPages, rows));
    }

    /**
     * Admin-shop buy path: infinite stock, money sunk (owner not credited),
     * barter inputs voided. Mirrors the validation of {@link #buy} (mode
     * resolution, payment-method dispatch, inventory full handling) but skips
     * all storage interactions and settlement bookkeeping.
     */
    private static void handleAdminShopBuy(ServerPlayer buyer, ShopBlockEntity shop, BlockPos pos,
                                           ShopBlockEntity.Listing listing, int qty, int deliverCount,
                                           Item saleItem, String paymentMethod) {
        EconomyTransactionCoordinator coordinator = BalanceManager.getCoordinator();
        RequestId transactionId = RequestId.random();
        long cost = listing.calculatePrice(qty);
        if (cost < 0L) {
            sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
            return;
        }
        boolean isBundle = !listing.bundleOutputs().isEmpty();

        if (isBundle) {
            for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                Item bundleItem = ShopTransactionUtil.resolveItem(entry.itemId());
                if (bundleItem == null || bundleItem == Items.AIR) {
                    sendResult(buyer, false, ShopResultCode.INVALID_ITEM);
                    return;
                }
                if (checkedDeliveryCount(entry.count(), qty) <= 0) {
                    sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                    return;
                }
            }
        }

        // Resolve trade-mode dispatch identically to the normal path.
        boolean compoundTrade = listing.tradeMode() == ShopBlockEntity.TradeMode.MONEY_AND_BARTER;
        boolean barterTrade;
        if (compoundTrade) {
            barterTrade = false;
        } else if (listing.tradeMode() == ShopBlockEntity.TradeMode.BOTH) {
            if (paymentMethod == null || paymentMethod.isBlank()) {
                sendResult(buyer, false, ShopResultCode.INVALID_REQUEST);
                return;
            }
            if ("BARTER".equalsIgnoreCase(paymentMethod)) {
                barterTrade = true;
            } else if ("MONEY".equalsIgnoreCase(paymentMethod)) {
                barterTrade = false;
            } else {
                sendResult(buyer, false, ShopResultCode.INVALID_REQUEST);
                return;
            }
        } else {
            barterTrade = listing.tradeMode() == ShopBlockEntity.TradeMode.BARTER;
        }

        // Money requires price > 0 for MONEY trades.
        if (!barterTrade && !compoundTrade && cost <= 0L
                && listing.tradeMode() == ShopBlockEntity.TradeMode.MONEY) {
            sendResult(buyer, false, ShopResultCode.UNCONFIGURED);
            return;
        }

        // Fire pre-event so external mods still get a hook.
        String tradeTypeForEvent = compoundTrade ? "MONEY_AND_BARTER" : (barterTrade ? "BARTER" : "BUY");
        String shopIdForEvent = "player_shop:" + pos.asLong();
        if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
            var preEvent = new com.enviouse.futureshopsp.event.ShopTransactionEvent.Pre(
                    buyer, shopIdForEvent, listing.itemId(), qty, tradeTypeForEvent, cost);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(preEvent);
            if (preEvent.isCanceled()) {
                sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                return;
            }
            cost = preEvent.getPriceMinor();
        }
        if (cost < 0L) {
            sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
            return;
        }

        // Validate barter availability without consuming yet.
        Item barterItem = null;
        int barterAmount = 0;
        if (compoundTrade || barterTrade) {
            barterItem = ShopTransactionUtil.resolveItem(listing.barterItemId());
            barterAmount = listing.effectiveBarterTotal(qty);
            if (barterAmount < 0) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            if (barterItem == null || barterItem == Items.AIR
                    || ShopTransactionUtil.countItems(buyer.getInventory(), barterItem,
                            listing.barterNbtAware(), listing.barterNbtPatch()) < barterAmount) {
                sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS,
                        "§cTrade cancelled: you don't have enough barter items.");
                return;
            }
        }

        // Charge money (sunk — never deposited to owner).
        boolean withdrewFromBuyer = false;
        if ((compoundTrade || !barterTrade) && cost > 0L) {
            TransactionResult withdraw = coordinatorMutation(coordinator, transactionId, "admin buyer debit",
                    buyer.getUUID(), null, cost, MutationKind.WITHDRAW);
            if (!withdraw.success()) {
                sendResult(buyer, false, ShopResultCode.INSUFFICIENT_FUNDS);
                return;
            }
            withdrewFromBuyer = true;
        }

        // Consume barter items — voided (NOT inserted into any storage).
        if (compoundTrade || barterTrade) {
            List<ItemStack> paymentStacks = ShopTransactionUtil.collectAndRemoveItems(
                    buyer.getInventory(), barterItem, barterAmount,
                    listing.barterNbtAware(), listing.barterNbtPatch());
            if (paymentStacks.isEmpty()) {
                if (withdrewFromBuyer) {
                    coordinatorMutation(coordinator, transactionId, "admin buyer refund",
                            buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                }
                sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS,
                        "§cTrade cancelled: barter items could not be taken.");
                return;
            }
            // Stacks are intentionally discarded — admin shop voids them.
        }

        // Deliver freshly minted sale items.
        List<ItemStack> delivered = new ArrayList<>();
        if (isBundle) {
            for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                Item bundleItem = ShopTransactionUtil.resolveItem(entry.itemId());
                int needed = checkedDeliveryCount(entry.count(), qty);
                delivered.addAll(splitStacks(bundleItem, needed, entry.nbtPatch()));
            }
        } else {
            delivered.addAll(splitStacks(saleItem, deliverCount, listing.nbtAware() ? listing.nbtPatch() : null));
        }

        if (!ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), delivered)) {
            for (ItemStack stack : delivered) {
                if (!stack.isEmpty()) buyer.drop(stack, false);
            }
        }

        // History: record the buyer's spend but no owner settlement.
        if (buyer.getServer() != null) {
            String historyNote = (barterTrade || compoundTrade) && barterItem != null
                    ? "paid=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(barterItem)
                            + "\u00d7" + barterAmount + " [ADMIN_SHOP]"
                    : "ADMIN_SHOP";
            TransactionHistoryService.record(
                    buyer,
                    shop.getShopId(),
                    tradeTypeForEvent,
                    listing.itemId(),
                    qty,
                    barterTrade ? 0L : cost,
                    historyNote);

            if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
                long eventCost = barterTrade ? 0L : cost;
                coordinator.balance(buyer.getUUID()).value().ifPresent(balance ->
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                                new com.enviouse.futureshopsp.event.ShopTransactionEvent.Post(
                                        buyer.getUUID(), shopIdForEvent, listing.itemId(),
                                        qty, tradeTypeForEvent, eventCost, balance.balanceMinorUnits())));
            }
        }

        openFor(buyer, pos, isOwnerOrFranchiseMember(shop, buyer));
        sendResult(buyer, true, ShopResultCode.BOUGHT);
    }

    public static int countStock(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        return shop.getListings().stream().mapToInt(listing -> countStock(level, shop, shopPos, listing)).sum();
    }

    public static int countStock(Level level, ShopBlockEntity shop, BlockPos shopPos, ShopBlockEntity.Listing listing) {
        if (listing == null) return 0;

        // Admin shops have infinite stock — short-circuit before storage lookup.
        if (shop.isAdminShopMode()) {
            return Integer.MAX_VALUE;
        }

        // Bundle listing: stock = min count across all bundle entries
        if (!listing.bundleOutputs().isEmpty()) {
            int minStock = Integer.MAX_VALUE;
            for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                Item bundleItem = ShopTransactionUtil.resolveItem(entry.itemId());
                if (bundleItem == null || bundleItem == Items.AIR) return 0;
                int raw = countSingleItemStock(level, shop, shopPos, bundleItem, entry.nbtPatch() != null, entry.nbtPatch());
                int sets = entry.count() > 0 ? raw / entry.count() : 0;
                minStock = Math.min(minStock, sets);
            }
            return minStock == Integer.MAX_VALUE ? 0 : minStock;
        }

        Item item = ShopTransactionUtil.resolveItem(listing.itemId());
        if (item == null || item == Items.AIR) return 0;
        int raw = countSingleItemStock(level, shop, shopPos, item, listing.nbtAware(), listing.nbtPatch());
        // Item 32: Stock count is in units of baseQuantity
        return listing.baseQuantity() > 1 ? raw / listing.baseQuantity() : raw;
    }

    private static int countSingleItemStock(Level level, ShopBlockEntity shop, BlockPos shopPos,
                                            Item item, boolean nbtAware,  DataComponentPatch nbtPatch) {
        LinkedStorage linkedStorage = resolveLinkedStorage(level, shop, shopPos);
        if (linkedStorage == null) return 0;
        if (linkedStorage.hasAdapter()) {
            return linkedStorage.adapter().countItem(linkedStorage.blockEntity(), item, nbtAware, nbtPatch);
        }
        int total = 0;
        IItemHandler handler = linkedStorage.handler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (NbtMatchUtil.matches(stack, item, nbtAware, nbtPatch)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static PlayerShopListingData toData(Level level, ShopBlockEntity shop, BlockPos shopPos,
                                                ShopBlockEntity.Listing listing, boolean visible) {
        ShopBlockEntity.Promo promo = listing.promo();
        String nbtJson = NbtMatchUtil.patchToSnbt(level.registryAccess(), listing.nbtPatch());
        String barterNbtJson = NbtMatchUtil.patchToSnbt(level.registryAccess(), listing.barterNbtPatch());
        
        // Build bundle output data
        List<PlayerShopListingData.BundleOutputData> bundleData = listing.bundleOutputs().stream()
                .map(e -> new PlayerShopListingData.BundleOutputData(
                        e.itemId(), e.count(), NbtMatchUtil.patchToSnbt(level.registryAccess(), e.nbtPatch())))
                .toList();
        return new PlayerShopListingData(
                listing.itemId(),
                listing.tradeMode().name(),
                listing.moneyPriceMinor(),
                listing.effectiveUnitPriceMinor(),
                listing.barterItemId(),
                listing.effectiveBarterItemCount(), // Item 24: send promo-adjusted barter count
                countStock(level, shop, shopPos, listing),
                promo.configured()
                        ? new PlayerShopPromoData(promo.active(), promo.promoType(), promo.promoValue(), promo.buyX(), promo.buyY(), promo.flash(),
                                promo.startEpochSeconds(), promo.endEpochSeconds())
                        : PlayerShopPromoData.NONE,
                listing.nbtAware(),
                nbtJson,
                visible,
                bundleData,
                listing.department(),
                listing.baseQuantity(),
                listing.barterItemCount(), // LGB#1: base (undiscounted) barter count for display
                listing.listingDescription(),
                listing.barterNbtAware(),
                barterNbtJson,
                listing.direction().name(),
                listing.buybackPriceMinor(),
                listing.buybackCap(),
                listing.buybackRemaining());
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

    /**
     * Unified rollback for buy() failures after payment was taken.
     */
    private static void rollbackAll(LinkedStorage linkedStorage, LinkedStorage barterStorage, ServerPlayer buyer,
                                    EconomyTransactionCoordinator coordinator, RequestId transactionId,
                                    RequestId custodyId, boolean custodyHeld,
                                    boolean withdrewFromBuyer, long cost, boolean recordedSale,
                                    UUID ownerUuid, BlockPos pos,
                                    Item barterItem, int barterAmount, List<ItemStack> insertedPayment,
                                    boolean compoundTrade, boolean barterTrade,
                                    boolean barterNbtAware, DataComponentPatch barterNbtPatch,
                                    PlayerShopBarterEscrowSavedData barterEscrow, UUID barterEscrowRequestId) {
        if (compoundTrade || !barterTrade) {
            if (recordedSale && buyer.getServer() != null) {
                PlayerShopSettlementSavedData.get(buyer.getServer()).rollbackPending(ownerUuid, pos.asLong(), cost);
            }
            if (withdrewFromBuyer) {
                TransactionResult refund = coordinatorMutation(coordinator, transactionId, "buyer rollback",
                        buyer.getUUID(), null, cost, MutationKind.DEPOSIT);
                if (refund.success()) {
                    releaseCustody(coordinator, custodyId, custodyHeld);
                }
            }
        }
        if (compoundTrade || barterTrade) {
            rollbackBarterPayment(barterStorage, buyer, barterItem, barterAmount, insertedPayment, barterNbtAware,
                    barterNbtPatch, barterEscrow, barterEscrowRequestId);
        }
    }

    private static void rollbackBarterPayment(LinkedStorage storage, ServerPlayer buyer, Item barterItem, int barterAmount,
                                              List<ItemStack> insertedPayment,
                                              boolean nbtAware, DataComponentPatch nbtPatch,
                                              PlayerShopBarterEscrowSavedData barterEscrow, UUID barterEscrowRequestId) {
        if (storage == null || barterItem == null || insertedPayment.isEmpty()) {
            return;
        }
        List<ItemStack> recovered = extractNbt(storage, barterItem, barterAmount, nbtAware, nbtPatch);
        if (!recovered.isEmpty()) {
            restorePaymentToBuyer(buyer, recovered);
            if (barterEscrow != null && barterEscrowRequestId != null) {
                barterEscrow.markRefunded(barterEscrowRequestId);
            }
        }
    }

    private static void restorePaymentToBuyer(ServerPlayer buyer, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        if (ShopTransactionUtil.canFit(buyer.getInventory(), stacks)) {
            ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), stacks);
            return;
        }
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                buyer.drop(stack, false);
            }
        }
    }

    private static void reinsert(LinkedStorage storage, ItemStack stack) {
        if (storage.hasAdapter()) {
            storage.adapter().insert(storage.blockEntity(), List.of(stack));
        } else if (storage.handler() != null) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < storage.handler().getSlots() && !remaining.isEmpty(); i++) {
                remaining = storage.handler().insertItem(i, remaining, false);
            }
        }
    }

    // ═══ NBT-aware extraction helpers ═══

    private static boolean canExtractNbt(LinkedStorage storage, Item item, int count,
                                          boolean nbtAware,  DataComponentPatch nbtPatch) {
        if (storage.hasAdapter()) {
            return storage.adapter().canExtract(storage.blockEntity(), item, count, nbtAware, nbtPatch);
        }
        return canExtract(storage.handler(), item, count, nbtAware, nbtPatch);
    }

    private static List<ItemStack> extractNbt(LinkedStorage storage, Item item, int count,
                                               boolean nbtAware,  DataComponentPatch nbtPatch) {
        if (storage.hasAdapter()) {
            return storage.adapter().extract(storage.blockEntity(), item, count, nbtAware, nbtPatch);
        }
        return extract(storage.handler(), item, count, nbtAware, nbtPatch);
    }

    private static int extractAmount(IItemHandler handler, Item item, int amount,
                                     boolean nbtAware,  DataComponentPatch nbtPatch) {
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, nbtPatch)) {
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
        ExternalStorageAdapter externalAdapter = ExternalStorageRegistry.findAdapter(linked);
        if (externalAdapter != null) {
            return new LinkedStorage(linkedPos, null, externalAdapter, linked);
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, linkedPos, null);
        if (handler == null) {
            return null;
        }
        return new LinkedStorage(linkedPos, handler, null, linked);
    }

    /**
     * Item 31: Resolves the barter storage for payment insertion.
     * When barterStorageSame is false and a separate barter storage pos is set, uses that;
     * otherwise falls back to the main linked storage.
     */
    private static LinkedStorage resolveBarterStorage(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        if (shop.isBarterStorageSame() || shop.getBarterStoragePos() == null) {
            return resolveLinkedStorage(level, shop, shopPos);
        }
        BlockPos barterPos = shop.getBarterStoragePos();
        if (barterPos.equals(shopPos) || !level.hasChunkAt(barterPos)) {
            return resolveLinkedStorage(level, shop, shopPos);
        }
        BlockEntity barterBe = level.getBlockEntity(barterPos);
        if (barterBe == null || barterBe instanceof ShopBlockEntity) {
            return resolveLinkedStorage(level, shop, shopPos);
        }
        ExternalStorageAdapter externalAdapter = ExternalStorageRegistry.findAdapter(barterBe);
        if (externalAdapter != null) {
            return new LinkedStorage(barterPos, null, externalAdapter, barterBe);
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, barterPos, null);
        if (handler == null) {
            return resolveLinkedStorage(level, shop, shopPos);
        }
        return new LinkedStorage(barterPos, handler, null, barterBe);
    }

    /** Result of link-target validation. OK means valid. */
    enum LinkTargetResult { OK, BAD_LINK_TARGET, RS_NOT_CONTROLLER }

    private static LinkTargetResult validateLinkTarget(Level level, BlockPos shopPos, BlockPos target) {
        if (target == null || target.equals(shopPos) || target.distManhattan(shopPos) > MAX_LINK_DISTANCE) {
            return LinkTargetResult.BAD_LINK_TARGET;
        }
        if (!level.hasChunkAt(target)) {
            return LinkTargetResult.BAD_LINK_TARGET;
        }
        BlockEntity targetBe = level.getBlockEntity(target);
        if (targetBe == null || targetBe instanceof ShopBlockEntity) {
            return LinkTargetResult.BAD_LINK_TARGET;
        }
        // RS block check: if the block belongs to Refined Storage, only allow Controller blocks
        if (isRSBlock(targetBe)) {
            if (isRSController(targetBe)) {
                return LinkTargetResult.OK;
            }
            return LinkTargetResult.RS_NOT_CONTROLLER;
        }
        if (ExternalStorageRegistry.hasAdapter(targetBe)) {
            return LinkTargetResult.OK;
        }
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, target, null) != null) {
            return LinkTargetResult.OK;
        }
        return LinkTargetResult.BAD_LINK_TARGET;
    }

    private static boolean isValidLinkTarget(Level level, BlockPos shopPos, BlockPos target) {
        return validateLinkTarget(level, shopPos, target) == LinkTargetResult.OK;
    }

    /** Checks if a block entity belongs to Refined Storage (any RS mod namespace). */
    private static boolean isRSBlock(BlockEntity be) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (key != null) {
            String ns = key.getNamespace();
            return "refinedstorage".equals(ns) || "refinedstorage2".equals(ns) || "refinedstorageaddons".equals(ns);
        }
        return be.getClass().getName().startsWith("com.refinedmods.refinedstorage");
    }

    /** Checks if an RS block entity is a Controller (normal or creative). */
    private static boolean isRSController(BlockEntity be) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (key != null) {
            String path = key.getPath();
            return "controller".equals(path) || "creative_controller".equals(path);
        }
        // Fallback: class name check
        String className = be.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        return className.contains("controller");
    }

    private static BlockPos resolveLookedBlockPos(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(LINK_RAYCAST_RANGE));
        BlockHitResult hitResult = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hitResult.getType() == HitResult.Type.BLOCK ? hitResult.getBlockPos() : null;
    }

    private static boolean canExtract(IItemHandler handler, Item item, int count,
                                      boolean nbtAware,  DataComponentPatch nbtPatch) {
        int remaining = count;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, nbtPatch)) {
                continue;
            }
            remaining -= probe.getCount();
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> extract(IItemHandler handler, Item item, int count,
                                           boolean nbtAware,  DataComponentPatch nbtPatch) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, nbtPatch)) {
                continue;
            }
            ItemStack real = handler.extractItem(i, remaining, false);
            if (!real.isEmpty()) {
                remaining -= real.getCount();
                result.add(real);
            }
        }
        if (remaining <= 0) {
            return result;
        }
        for (ItemStack stack : result) {
            ItemStack pending = stack.copy();
            for (int i = 0; i < handler.getSlots() && !pending.isEmpty(); i++) {
                pending = handler.insertItem(i, pending, false);
            }
        }
        return List.of();
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
        return splitStacks(item, totalCount, null);
    }

    private static List<ItemStack> splitStacks(Item item, int totalCount,  DataComponentPatch nbtPatch) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = totalCount;
        int max = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        while (remaining > 0) {
            int count = Math.min(max, remaining);
            ItemStack stack = new ItemStack(item, count);
            if (nbtPatch != null && !nbtPatch.isEmpty()) stack.applyComponents(nbtPatch);
            stacks.add(stack);
            remaining -= count;
        }
        return stacks;
    }

    private static void sendResult(ServerPlayer player, boolean success, ShopResultCode code) {
        ShopPackets.sendToPlayer(player, new S2CPlayerShopResultPacket(success, code.wire(), ""));
    }

    static int checkedDeliveryCount(int unitCount, int purchaseQuantity) {
        if (unitCount <= 0 || purchaseQuantity <= 0) {
            return -1;
        }
        try {
            return Math.multiplyExact(unitCount, purchaseQuantity);
        } catch (ArithmeticException exception) {
            return -1;
        }
    }

    static long deliveryEntitlementQuantity(ShopBlockEntity.Listing listing, int purchaseQuantity,
                                            int singleItemQuantity) {
        if (listing == null || purchaseQuantity <= 0 || singleItemQuantity <= 0) {
            return -1L;
        }
        if (listing.bundleOutputs().isEmpty()) {
            return singleItemQuantity;
        }
        try {
            long total = 0L;
            for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                total = Math.addExact(total, Math.multiplyExact((long) entry.count(), purchaseQuantity));
            }
            return total;
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    static String deliveryEntitlementHash(ShopBlockEntity.Listing listing, int purchaseQuantity,
                                           long entitlementQuantity) {
        StringBuilder descriptor = new StringBuilder()
                .append(listing.itemId()).append('|')
                .append(purchaseQuantity).append('|')
                .append(entitlementQuantity).append('|')
                .append(listing.nbtAware()).append('|')
                .append(listing.nbtPatch());
        for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
            descriptor.append('|').append(entry.itemId()).append('|').append(entry.count())
                    .append('|').append(entry.nbtPatch());
        }
        return com.enviouse.futureshopsp.server.economy.EconomyRecordChecksum.sha256(descriptor.toString());
    }

    private static ShopResultCode mapProviderError(ProviderResult<?> result) {
        if (result == null) {
            return ShopResultCode.CLAIM_FAILED;
        }
        return result.error() == ProviderError.INSUFFICIENT_FUNDS
                ? ShopResultCode.INSUFFICIENT_FUNDS : ShopResultCode.CLAIM_FAILED;
    }

    private static TransactionResult coordinatorMutation(EconomyTransactionCoordinator coordinator,
                                                         RequestId rootRequest, String role,
                                                         UUID actor, UUID counterparty, long amount,
                                                         MutationKind kind) {
        if (amount <= 0L) {
            return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, 0L);
        }
        try {
            MutationRequest request = new MutationRequest(rootRequest.child(role), actor,
                    counterparty == null ? Optional.empty() : Optional.of(counterparty), amount, kind);
            ProviderResult<MutationReceipt> result = kind == MutationKind.DEPOSIT
                    || kind == MutationKind.TRANSFER_CREDIT
                    || kind == MutationKind.COMPENSATION
                    ? coordinator.deposit(request) : coordinator.withdraw(request);
            long balance = result.receipt().flatMap(receipt -> receipt.resultingBalanceMinorUnits().isPresent()
                    ? Optional.of(receipt.resultingBalanceMinorUnits().getAsLong()) : Optional.empty()).orElse(0L);
            return result.confirmed()
                    ? TransactionResult.ok(balance)
                    : TransactionResult.error(mapProviderResultCode(result), balance);
        } catch (RuntimeException exception) {
            return TransactionResult.error(ShopResultCode.SERVER_ERROR, 0L);
        }
    }

    private static ProviderResult<MutationReceipt> coordinatorMutationWithCustody(
            EconomyTransactionCoordinator coordinator, RequestId rootRequest, String role,
            UUID actor, long amount, String itemKey, long quantity, String contentHash) {
        if (amount <= 0L) {
            return ProviderResult.rejected(ProviderError.INVALID_AMOUNT, "amount must be positive");
        }
        try {
            MutationRequest request = MutationRequest.forPlayer(rootRequest.child(role), actor, amount,
                    MutationKind.WITHDRAW);
            return coordinator.executeWithCustody(request, actor, itemKey, quantity, contentHash,
                    com.enviouse.futureshopsp.server.economy.CustodyState.HELD);
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.UNKNOWN, "custodied mutation could not be prepared");
        }
    }

    private static RequestId custodyIdFor(RequestId rootRequest, String role) {
        return rootRequest.child(role).child("custody");
    }

    private static void releaseCustody(EconomyTransactionCoordinator coordinator, RequestId custodyId,
                                       boolean custodyHeld) {
        if (!custodyHeld) {
            return;
        }
        try {
            coordinator.releaseCustody(custodyId);
        } catch (RuntimeException ignored) {
            // The lifecycle or an existing terminal transition keeps the recovery record authoritative.
        }
    }

    private static ShopResultCode mapProviderResultCode(ProviderResult<?> result) {
        if (result == null) {
            return ShopResultCode.SERVER_ERROR;
        }
        return switch (result.error()) {
            case INSUFFICIENT_FUNDS -> ShopResultCode.INSUFFICIENT_FUNDS;
            case INVALID_AMOUNT -> ShopResultCode.INVALID_AMOUNT;
            default -> ShopResultCode.SERVER_ERROR;
        };
    }

    /**
     * Item 18: Send result with an optional chat message displayed to the buyer.
     */
    private static void sendResultWithChat(ServerPlayer player, boolean success, ShopResultCode code, String chatMessage) {
        ShopPackets.sendToPlayer(player, new S2CPlayerShopResultPacket(success, code.wire(), chatMessage));
    }

    private static boolean isOwnerOrFranchiseMember(ShopBlockEntity shop, ServerPlayer player) {
        UUID ownerUuid = shop.getOwnerUuid();
        if (ownerUuid == null) return false;
        if (ownerUuid.equals(player.getUUID())) return true;
        if (player.getServer() != null) {
            return FranchiseSavedData.get(player.getServer()).isFranchiseMember(ownerUuid, player.getUUID());
        }
        return false;
    }

    private record LinkedStorage(BlockPos pos, IItemHandler handler, ExternalStorageAdapter adapter, BlockEntity blockEntity) {
        boolean hasAdapter() {
            return adapter != null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Buyback / sell-to-shop (visitor sells to the shop)
    // ─────────────────────────────────────────────────────────────────────

    /** Owner-side: apply the per-listing direction + buyback price/cap settings. */
    public static void applyBuybackConfig(ServerPlayer player,
                                          com.enviouse.futureshopsp.network.packets.C2SPlayerShopBuybackConfigPacket packet) {
        if (!(player.level().getBlockEntity(packet.shopPos()) instanceof ShopBlockEntity shop)) return;
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        ShopBlockEntity.Listing listing = shop.getListing(packet.listingIndex());
        if (listing == null) {
            sendResult(player, false, ShopResultCode.NO_LISTING);
            return;
        }
        ShopBlockEntity.Direction dir;
        try {
            dir = ShopBlockEntity.Direction.valueOf(packet.direction());
        } catch (IllegalArgumentException ignored) {
            dir = ShopBlockEntity.Direction.SELL;
        }
        listing.setDirection(dir);
        listing.setBuybackPriceMinor(Math.max(0L, packet.buybackPriceMinor()));
        listing.setBuybackCap(Math.max(0, packet.buybackCap()));
        shop.setChanged();
        openFor(player, packet.shopPos());
        sendResult(player, true, ShopResultCode.CONFIG_SAVED);
    }

    /** Visitor-side: handle a "Sell to Shop" buyback transaction. */
    public static void handleSell(ServerPlayer seller,
                                  com.enviouse.futureshopsp.network.packets.C2SPlayerShopSellPacket packet) {
        BlockPos pos = packet.shopPos();
        if (!(seller.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) return;

        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(pos.asLong(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (shop.getOwnerUuid() == null) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            ShopBlockEntity.Listing listing = shop.getListing(packet.listingIndex());
            if (listing == null || listing.itemId().isBlank() || !listing.allowsBuy()) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            Item saleItem = ShopTransactionUtil.resolveItem(listing.itemId());
            if (saleItem == null || saleItem == Items.AIR) {
                sendResult(seller, false, ShopResultCode.INVALID_ITEM);
                return;
            }
            int qty = Math.max(1, Math.min(packet.quantity(), ShopTransactionUtil.MAX_SELL_QUANTITY));
            int baseQty = listing.baseQuantity();
            if (baseQty <= 0) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            int needItems = checkedDeliveryCount(baseQty, qty);
            if (needItems <= 0) {
                sendResult(seller, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            boolean nbtAware = listing.nbtAware();
            DataComponentPatch nbtPatch = listing.nbtPatch();
            int have = ShopTransactionUtil.countItems(seller.getInventory(), saleItem, nbtAware, nbtPatch);
            if (have < needItems) {
                sendResultWithChat(seller, false, ShopResultCode.MISSING_ITEMS,
                        "§cYou don't have enough of that item.");
                return;
            }
            long total = listing.calculateBuybackTotal(qty);
            if (total <= 0L) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            if (listing.buybackRemaining() < qty) {
                sendResultWithChat(seller, false, ShopResultCode.BUYBACK_CAP_REACHED,
                        "§cThe shop has reached its buy-back cap for this item.");
                return;
            }

            String shopIdForEvent = "player_shop:" + pos.asLong();
            if (com.enviouse.futureshopsp.Config.eventsTransactionEnabled) {
                var pre = new com.enviouse.futureshopsp.event.ShopTransactionEvent.Pre(
                        seller, shopIdForEvent, listing.itemId(), qty, "SELL_TO_SHOP", total);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(pre);
                if (pre.isCanceled()) {
                    sendResult(seller, false, ShopResultCode.CANCELLED_BY_EVENT);
                    return;
                }
                total = pre.getPriceMinor();
            }
            if (total <= 0L) {
                sendResult(seller, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }

            EconomyTransactionCoordinator coordinator = BalanceManager.getCoordinator();
            RequestId transactionId = RequestId.random();

            if (shop.isAdminShopMode()) {
                // Void the items, mint the money.
                List<ItemStack> taken = ShopTransactionUtil.collectAndRemoveItems(
                        seller.getInventory(), saleItem, needItems, nbtAware, nbtPatch);
                if (taken.isEmpty()) {
                    sendResultWithChat(seller, false, ShopResultCode.MISSING_ITEMS,
                            "§cYou don't have enough of that item.");
                    return;
                }
                TransactionResult dep = coordinatorMutation(coordinator, transactionId, "admin seller credit",
                        seller.getUUID(), shop.getOwnerUuid(), total, MutationKind.DEPOSIT);
                if (!dep.success()) {
                    // Roll back: return items to seller.
                    ShopTransactionUtil.insertIntoInventory(seller.getInventory(), taken);
                    sendResult(seller, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                incrementBuyback(listing, qty);
                recordSellHistory(seller, shop, listing, qty, total, "ADMIN_SHOP_BUYBACK");
                firePostSellEvent(seller, shopIdForEvent, listing.itemId(), qty, total, coordinator);
                shop.setChanged();
                openFor(seller, pos, false);
                sendResultWithChat(seller, true, ShopResultCode.SOLD, "§a✓ Sold to shop.");
                return;
            }

            // ── Player shop path ──
            UUID ownerUuid = shop.getOwnerUuid();
            long requiredFunds = total;
            if (coordinator.balance(ownerUuid).value().map(balance -> balance.balanceMinorUnits() < requiredFunds).orElse(true)) {
                sendResultWithChat(seller, false, ShopResultCode.SHOP_OUT_OF_MONEY,
                        "§cThe shop owner can't afford this — try again later or sell less.");
                return;
            }
            LinkedStorage storage = resolveLinkedStorage(seller.level(), shop, pos);
            if (storage == null) {
                sendResultWithChat(seller, false, ShopResultCode.NO_LINK,
                        "§cThis shop has no linked storage.");
                return;
            }

            // Collect actual stacks (NBT intact) from seller — but only after we've verified insertion is possible.
            List<ItemStack> paymentStacks = ShopTransactionUtil.collectAndRemoveItems(
                    seller.getInventory(), saleItem, needItems, nbtAware, nbtPatch);
            if (paymentStacks.isEmpty()) {
                sendResultWithChat(seller, false, ShopResultCode.MISSING_ITEMS,
                        "§cYou don't have enough of that item.");
                return;
            }
            // Capacity check, then atomic insert.
            boolean canInsert = storage.hasAdapter()
                    ? storage.adapter().canInsert(storage.blockEntity(), paymentStacks)
                    : canInsertAll(storage.handler(), paymentStacks);
            if (!canInsert) {
                ShopTransactionUtil.insertIntoInventory(seller.getInventory(), paymentStacks);
                sendResultWithChat(seller, false, ShopResultCode.STORAGE_FULL,
                        "§cThe shop's storage is full and can't accept those items.");
                return;
            }
            boolean inserted = storage.hasAdapter()
                    ? storage.adapter().insert(storage.blockEntity(), paymentStacks)
                    : insertAll(storage.handler(), paymentStacks);
            if (!inserted) {
                ShopTransactionUtil.insertIntoInventory(seller.getInventory(), paymentStacks);
                sendResultWithChat(seller, false, ShopResultCode.STORAGE_FULL,
                        "§cThe shop's storage is full and can't accept those items.");
                return;
            }

            // Withdraw from owner.
            TransactionResult ownerWd = coordinatorMutation(coordinator, transactionId, "owner buyback debit",
                    ownerUuid, seller.getUUID(), total, MutationKind.WITHDRAW);
            if (!ownerWd.success()) {
                // Roll back the inserted items.
                rollbackInsertedItems(storage, seller, saleItem, needItems, nbtAware, nbtPatch);
                sendResultWithChat(seller, false, ShopResultCode.SHOP_OUT_OF_MONEY,
                        "§cThe shop owner can't afford this — try again later or sell less.");
                return;
            }

            // Credit seller.
            TransactionResult sellerDep = coordinatorMutation(coordinator, transactionId, "seller buyback credit",
                    seller.getUUID(), ownerUuid, total, MutationKind.DEPOSIT);
            if (!sellerDep.success()) {
                // Refund owner, undo storage.
                coordinatorMutation(coordinator, transactionId, "owner buyback refund",
                        ownerUuid, seller.getUUID(), total, MutationKind.DEPOSIT);
                rollbackInsertedItems(storage, seller, saleItem, needItems, nbtAware, nbtPatch);
                sendResult(seller, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                return;
            }

            incrementBuyback(listing, qty);
            recordSellHistory(seller, shop, listing, qty, total, "BUYBACK");
            firePostSellEvent(seller, shopIdForEvent, listing.itemId(), qty, total, coordinator);
            shop.setChanged();
            openFor(seller, pos, false);
            sendResultWithChat(seller, true, ShopResultCode.SOLD, "§a✓ Sold to shop.");
        } finally {
            lock.unlock();
        }
    }

    private static void incrementBuyback(ShopBlockEntity.Listing listing, int qty) {
        int newCount = listing.buybackBought() + qty;
        if (listing.buybackCap() > 0) {
            newCount = Math.min(newCount, listing.buybackCap());
        }
        listing.setBuybackBought(newCount);
    }

    private static void rollbackInsertedItems(LinkedStorage storage, ServerPlayer seller, Item item, int amount,
                                              boolean nbtAware,  DataComponentPatch nbtPatch) {
        if (storage.hasAdapter()) {
            List<ItemStack> recovered = storage.adapter().extract(storage.blockEntity(), item, amount, nbtAware, nbtPatch);
            if (!recovered.isEmpty()) {
                ShopTransactionUtil.insertIntoInventory(seller.getInventory(), recovered);
            }
        } else if (storage.handler() != null) {
            int got = extractAmount(storage.handler(), item, amount, nbtAware, nbtPatch);
            if (got > 0) {
                ShopTransactionUtil.insertIntoInventory(seller.getInventory(),
                        splitStacks(item, got, nbtPatch));
            }
        }
    }

    private static void recordSellHistory(ServerPlayer seller, ShopBlockEntity shop,
                                          ShopBlockEntity.Listing listing, int qty, long total, String note) {
        if (seller.getServer() == null) return;
        TransactionHistoryService.record(
                seller,
                shop.getShopId(),
                "SELL_TO_SHOP",
                listing.itemId(),
                qty,
                total,
                note);
    }

    private static void firePostSellEvent(ServerPlayer seller, String shopIdForEvent, String itemId,
                                          int qty, long total, EconomyTransactionCoordinator coordinator) {
        if (!com.enviouse.futureshopsp.Config.eventsTransactionEnabled) return;
        coordinator.balance(seller.getUUID()).value().ifPresent(balance ->
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new com.enviouse.futureshopsp.event.ShopTransactionEvent.Post(
                                seller.getUUID(), shopIdForEvent, itemId, qty, "SELL_TO_SHOP", total,
                                balance.balanceMinorUnits())));
    }
}
