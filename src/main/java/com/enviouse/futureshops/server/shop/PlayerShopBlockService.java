package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.data.PlayerShopPromoData;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.money.PurchasePaymentService;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.transaction.NbtMatchUtil;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class PlayerShopBlockService {
    private static final double LINK_RAYCAST_RANGE = 8.0D;
    /**
     * Server-side reach bound for the keybind-initiated OPEN_LOOKING action
     * (squared, from the EYE position). Generous vs the ~5-block client pick
     * range to absorb latency and eye-offset — it only opens a read-only UI,
     * and the session distance auto-close still applies afterwards.
     */
    private static final double OPEN_LOOKING_MAX_DISTANCE_SQ = 12.0D * 12.0D;
    private static final ConcurrentHashMap<Long, ReentrantLock> SHOP_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BlockPos> PENDING_DESC = new ConcurrentHashMap<>();
    private static final ThreadLocal<BuyResponseContext> BUY_RESPONSE_CONTEXT = new ThreadLocal<>();
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
        boolean isFirstOpen = com.enviouse.futureshops.server.session.ShopSessionManager.get(player.getUUID()).isEmpty();
        if (isFirstOpen) {
            var openEvent = new com.enviouse.futureshops.event.ShopOpenEvent(player, playerShopId);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(openEvent);
            if (openEvent.isCanceled()) return;
            com.enviouse.futureshops.server.session.ShopSessionManager.open(player.getUUID(), playerShopId, pos);
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
                shop.hasLinkedStorage(),
                settlement.pendingMinor(),
                settlement.lifetimeMinor(),
                settlement.rows(),
                shop.getShopName(),
                shop.isSingleItemMode(),
                shop.isBarterStorageSame(),
                shop.getDescription(),
                franchiseName,
                shop.isPlacedByCreative(),
                shop.isAdminShopMode(),
                shop.getFloatingIconMode().name(),
                shop.getFloatingIconItem(),
                owner ? buildStorageEntries(player.level(), shop, pos) : List.of(),
                owner && player.getServer() != null
                        ? PlayerShopSavedConfigs.get(player.getServer()).names(player.getUUID())
                        : List.of()));
    }

    /**
     * Builds the owner Storage-tab list: one entry per linked position (index-aligned with
     * {@link ShopBlockEntity#getLinkedStoragePositions()} so {@code UNLINK_INDEX} maps directly),
     * each carrying the block registry id (client localizes the name) and the total item count.
     * Unresolvable/unloaded positions still appear (blank block id, count 0) so the owner can
     * see and unlink a stale link.
     */
    private static List<com.enviouse.futureshops.data.PlayerShopStorageEntry> buildStorageEntries(
            Level level, ShopBlockEntity shop, BlockPos shopPos) {
        List<BlockPos> positions = shop.getLinkedStoragePositions();
        if (positions.isEmpty()) {
            return List.of();
        }
        List<com.enviouse.futureshops.data.PlayerShopStorageEntry> out = new ArrayList<>(positions.size());
        for (BlockPos p : positions) {
            String blockId = "";
            int count = 0;
            if (level.hasChunkAt(p)) {
                var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock());
                if (key != null) blockId = key.toString();
                LinkedStorage s = resolveOneStorage(level, shopPos, p);
                if (s != null) count = totalItemCount(s);
            }
            out.add(new com.enviouse.futureshops.data.PlayerShopStorageEntry(p, blockId, count));
        }
        return out;
    }

    /**
     * Total item count (sum of stack sizes) held in one storage. Falls back to the block's
     * ITEM_HANDLER capability when {@code handler()} is null — which is the COMMON case, because
     * {@code ForgeCapabilityStorageAdapter} matches any capability-backed container (vanilla chests
     * included) so {@code resolveOneStorage} returns it via the adapter branch with a null handler.
     * Genuinely adapter-only storages that expose no plain handler (e.g. a bare RS controller)
     * still report 0 — a display-only limitation; per-listing stock uses {@code adapter.countItem}.
     */
    private static int totalItemCount(LinkedStorage s) {
        IItemHandler handler = s.handler();
        if (handler == null && s.blockEntity() != null) {
            handler = s.blockEntity().getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        }
        long total = 0;
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                total += handler.getStackInSlot(i).getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
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

    /** Owner-only: sets the floating-icon mode (and custom item) then re-syncs the block-top. */
    public static void applyFloatingIcon(ServerPlayer player, BlockPos pos, String iconMode, String iconItem) {
        if (!(player.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        shop.setFloatingIconMode(ShopBlockEntity.FloatingIconMode.byName(iconMode));
        shop.setFloatingIconItem(iconItem);
        shop.setChanged();
        shop.syncTopItemsToClients();
        openFor(player, pos);
        sendResult(player, true, ShopResultCode.CONFIG_SAVED);
    }

    /**
     * Owner-only: unlinks one stock storage BY POSITION (identity), for the Storage-tab per-entry
     * Unlink button. Race-free — a stale click either matches a currently-linked position or is a
     * no-op, and can never unlink a different container the way a positional index could.
     */
    public static void unlinkStorageByPos(ServerPlayer player, BlockPos shopPos, BlockPos storagePos) {
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        if (storagePos != null && shop.getLinkedStoragePositions().contains(storagePos)) {
            shop.removeLinkedStorage(storagePos);
            shop.setChanged();
        }
        openFor(player, shopPos);
    }

    /**
     * Owner-only: named saved-config operations (Payouts tab). op = SAVE / APPLY / DELETE against
     * the player's persistent {@link PlayerShopSavedConfigs}. SAVE stores this shop's
     * {@code exportConfigSnapshot()}; APPLY stamps a named snapshot onto this shop (never touching
     * owner/pos/links, same as paste); DELETE removes it. No-ops safely on missing names.
     */
    public static void handleSavedConfig(ServerPlayer player, BlockPos shopPos, String op, String name) {
        if (!(player.level().getBlockEntity(shopPos) instanceof ShopBlockEntity shop)) {
            return;
        }
        if (!isOwnerOrFranchiseMember(shop, player)) {
            sendResult(player, false, ShopResultCode.NOT_OWNER);
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        PlayerShopSavedConfigs store = PlayerShopSavedConfigs.get(player.getServer());
        UUID uuid = player.getUUID();
        switch (op == null ? "" : op) {
            case "SAVE" -> {
                if (store.save(uuid, name, shop.exportConfigSnapshot())) {
                    sendResult(player, true, ShopResultCode.CONFIG_COPIED);
                } else {
                    sendResult(player, false, ShopResultCode.NO_CLIPBOARD);
                }
            }
            case "APPLY" -> {
                net.minecraft.nbt.CompoundTag snap = store.get(uuid, name);
                if (snap == null) {
                    sendResult(player, false, ShopResultCode.NO_CLIPBOARD);
                    return;
                }
                shop.applyConfigSnapshot(snap);
                shop.setChanged();
                sendResult(player, true, ShopResultCode.CONFIG_SAVED);
            }
            case "DELETE" -> store.delete(uuid, name);
            default -> {
                return;
            }
        }
        openFor(player, shopPos);
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

        // OPEN_LOOKING: keybind-initiated open of the shop block the player is
        // looking at (for held items that suppress right-click, e.g. TacZ guns).
        // Unlike right-click this arrives as a raw packet, so the server bounds
        // the distance itself; unlike VISIT it must not force the visitor view
        // so owners get the same manage UI a right-click gives them. It also
        // deliberately does NOT claim ownerless shops the way ShopBlock.use
        // does — a packet must never be able to grab ownership remotely.
        if ("OPEN_LOOKING".equals(action)) {
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > OPEN_LOOKING_MAX_DISTANCE_SQ) {
                return;
            }
            openFor(player, pos, amount == 1); // amount==1 → sneak: force visitor view
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
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
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
                CompoundTag heldTag = held.hasTag() ? held.getTag().copy() : null;
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
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key == null || "minecraft:air".equals(key.toString())) {
                    sendResult(player, false, ShopResultCode.INVALID_ITEM);
                    return;
                }
                CompoundTag heldNbt = held.hasTag() ? held.getTag().copy() : null;
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
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (key != null) {
                    listing.setBarterItemId(key.toString());
                    listing.setBarterItemCount(Math.max(1, amount));
                    // NBT-strict barter payment: snapshot the barter item's NBT at
                    // registration so the buyer can't pay with a partially-full tank
                    // or an enchanted chestplate when the owner posted the plain variant.
                    listing.setBarterNbtAware(held.hasTag());
                    listing.setBarterNbtTag(held.hasTag() ? held.getTag().copy() : null);
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
            case "TOGGLE_HIDDEN" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                // Hidden and showcase are mutually exclusive presentation states.
                listing.setHidden(!listing.hidden());
                if (listing.hidden()) listing.setShowcase(false);
            }
            case "TOGGLE_SHOWCASE" -> {
                if (listing == null) {
                    sendResult(player, false, ShopResultCode.NO_LISTING);
                    return;
                }
                listing.setShowcase(!listing.showcase());
                if (listing.showcase()) listing.setHidden(false);
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
            case "UNLINK" -> {
                // Remove the specific container the owner is looking at from the linked
                // set — never silently clear all. Back-compat: when exactly one storage
                // is linked, an unfocused Unlink clears that sole link (byte-identical to
                // the pre-multilink single-storage behaviour).
                BlockPos looked = resolveLookedBlockPos(player);
                List<BlockPos> linked = shop.getLinkedStoragePositions();
                if (looked != null && linked.contains(looked)) {
                    shop.removeLinkedStorage(looked);
                } else if (linked.size() == 1) {
                    shop.clearLinkedStorages();
                }
                // Multi-storage and not looking at a linked container → no-op.
            }
            // Storage-tab per-entry unlink is identity-based via C2SPlayerShopUnlinkStoragePacket
            // (unlinkStorageByPos), NOT a positional action — a stale index could unlink the wrong
            // container, so no UNLINK_INDEX action exists.
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
                long claimed = PlayerShopSettlementSavedData.get(player.getServer()).claim(player.getUUID(), pos.asLong());
                if (claimed <= 0L) {
                    sendResult(player, false, ShopResultCode.NOTHING_TO_CLAIM);
                    return;
                }
                TransactionResult deposit = BalanceManager.getProvider().deposit(player.getUUID(), claimed);
                if (!deposit.success()) {
                    PlayerShopSettlementSavedData.get(player.getServer()).recordSale(player.getUUID(), pos.asLong(), claimed, "", 0);
                    sendResult(player, false, ShopResultCode.CLAIM_FAILED);
                    return;
                }
                // Record claim funds in transaction history
                TransactionHistoryService.record(
                        player,
                        shop.getShopId(),
                        "CART_CLAIM",
                        "",
                        0,
                        claimed,
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
        boolean valid = switch (normalizedType) {
            case "PERCENTAGE", "FLAT", "FLASH" -> promoValue > 0.0D;
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

    /**
     * Resolves the raw listing index a VISITOR request targets. In single-item mode a visitor's
     * client only holds the one visible listing (index 0), but the owner may have chosen a
     * non-first listing to showcase — so a visitor index in single-item mode always means the
     * shop's {@code visibleListingIndex}, not the raw index sent. Owners see the full list, so
     * their indices pass through unchanged.
     */
    private static int resolveVisitorListingIndex(ShopBlockEntity shop, ServerPlayer player, int listingIndex) {
        if (shop.isSingleItemMode() && !isOwnerOrFranchiseMember(shop, player)) {
            return shop.getVisibleListingIndex();
        }
        return listingIndex;
    }

    private static PurchasePlan preparePurchasePlan(
            ShopBlockEntity.Listing listing,
            Item saleItem,
            int quantity
    ) throws PurchasePlanException {
        List<PurchaseOutput> outputs = new ArrayList<>();
        int totalItems = 0;
        int totalStacks = 0;
        if (listing.bundleOutputs().isEmpty()) {
            int count = PlayerShopBuyMath.checkedItemTotal(listing.baseQuantity(), quantity);
            totalItems = PlayerShopBuyMath.checkedAggregateItemCount(totalItems, count);
            totalStacks = PlayerShopBuyMath.checkedAggregateStackCount(
                    totalStacks,
                    PlayerShopBuyMath.checkedStackCount(count, saleItem.getMaxStackSize()));
            outputs.add(new PurchaseOutput(saleItem, count, listing.nbtAware(), listing.nbtTag()));
        } else {
            for (ShopBlockEntity.BundleEntry entry : listing.bundleOutputs()) {
                Item outputItem;
                try {
                    outputItem = ShopTransactionUtil.resolveItem(entry.itemId());
                } catch (RuntimeException exception) {
                    throw new PurchasePlanException(ShopResultCode.INVALID_ITEM);
                }
                if (outputItem == null || outputItem == Items.AIR) {
                    throw new PurchasePlanException(ShopResultCode.INVALID_ITEM);
                }
                int count = PlayerShopBuyMath.checkedItemTotal(entry.count(), quantity);
                totalItems = PlayerShopBuyMath.checkedAggregateItemCount(totalItems, count);
                totalStacks = PlayerShopBuyMath.checkedAggregateStackCount(
                        totalStacks,
                        PlayerShopBuyMath.checkedStackCount(count, outputItem.getMaxStackSize()));
                outputs.add(new PurchaseOutput(outputItem, count, entry.nbtTag() != null, entry.nbtTag()));
            }
        }
        if (totalItems <= 0 || totalStacks <= 0 || outputs.isEmpty()) {
            throw new IllegalArgumentException("purchase output must not be empty");
        }

        boolean promoActive = listing.promo().active();
        long basePrice = PlayerShopBuyMath.requireNonnegativePrice(listing.moneyPriceMinor());
        long effectiveUnitPrice = promoActive
                ? listing.promo().applyUnitPrice(basePrice)
                : basePrice;
        long cost = PlayerShopBuyMath.checkedPriceTotal(
                basePrice,
                effectiveUnitPrice,
                quantity,
                promoActive,
                listing.promo().promoType(),
                listing.promo().buyX(),
                listing.promo().buyY());
        return new PurchasePlan(List.copyOf(outputs), cost);
    }

    private static boolean exactOutputCount(List<ItemStack> stacks, PurchaseOutput output) {
        if (stacks == null || stacks.isEmpty()) {
            return false;
        }
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()
                    || !NbtMatchUtil.matches(stack, output.item(), output.nbtAware(), output.nbtTag())) {
                return false;
            }
            try {
                total = PlayerShopBuyMath.checkedAggregateItemCount(total, stack.getCount());
            } catch (ArithmeticException | IllegalArgumentException exception) {
                return false;
            }
            if (total > output.count()) {
                return false;
            }
        }
        return total == output.count();
    }

    private static void rollbackExtracted(List<LinkedStorage> storages, List<ItemStack> extracted) {
        if (extracted == null) {
            return;
        }
        for (ItemStack stack : extracted) {
            if (stack != null && !stack.isEmpty()) {
                reinsertComposite(storages, stack);
            }
        }
    }

    public static void buy(ServerPlayer buyer, BlockPos pos, int listingIndex, int quantity,
                           String paymentMethod, String paymentSourceWire) {
        buy(buyer, pos, listingIndex, quantity, paymentMethod, paymentSourceWire,
                new UUID(0L, 0L), 0);
    }

    public static void buy(ServerPlayer buyer, BlockPos pos, int listingIndex, int quantity,
                           String paymentMethod, String paymentSourceWire,
                           UUID requestId, int responseToken) {
        BuyResponseContext previous = BUY_RESPONSE_CONTEXT.get();
        BuyResponseContext current = new BuyResponseContext(
                requestId == null ? new UUID(0L, 0L) : requestId,
                responseToken);
        BUY_RESPONSE_CONTEXT.set(current);
        try {
            buyInternal(buyer, pos, listingIndex, quantity, paymentMethod, paymentSourceWire);
            if (!current.responded) {
                sendResult(buyer, false, ShopResultCode.INVALID_TARGET);
            }
        } finally {
            if (previous == null) {
                BUY_RESPONSE_CONTEXT.remove();
            } else {
                BUY_RESPONSE_CONTEXT.set(previous);
            }
        }
    }

    private static void buyInternal(ServerPlayer buyer, BlockPos pos, int listingIndex, int quantity,
                                    String paymentMethod, String paymentSourceWire) {
        if (!ShopTransactionUtil.isValidBuyQuantity(quantity)) {
            sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
            return;
        }
        if (!(buyer.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return;
        }

        PaymentSource paymentSource = PaymentSource.fromWire(paymentSourceWire).orElse(null);
        if (paymentSource == null) {
            sendResult(buyer, false, ShopResultCode.INVALID_REQUEST);
            return;
        }

        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(pos.asLong(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            int qty = quantity;
            listingIndex = resolveVisitorListingIndex(shop, buyer, listingIndex);
            ShopBlockEntity.Listing listing = shop.getListing(listingIndex);
            if (shop.getOwnerUuid() == null || listing == null || listing.itemId().isBlank()) {
                sendResult(buyer, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            // Redesign: hidden listings are concealed and showcase listings are display-only
            // ("visit in person") — neither is purchasable by visitors. This is the server
            // authority backstop; the client also hides/tags them. Owners/franchise members
            // may still transact (e.g. to test the listing).
            if ((listing.hidden() || listing.showcase()) && !isOwnerOrFranchiseMember(shop, buyer)) {
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

            PurchasePlan purchasePlan;
            try {
                purchasePlan = preparePurchasePlan(listing, saleItem, qty);
            } catch (PurchasePlanException exception) {
                sendResult(buyer, false, exception.code);
                return;
            } catch (ArithmeticException | IllegalArgumentException exception) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }

            // ═══ Admin shop short-circuit ═══
            // Infinite stock, money sunk on buys, barter inputs voided. No
            // linked-storage or barter-storage required.
            if (shop.isAdminShopMode()) {
                handleAdminShopBuy(buyer, shop, pos, listing, qty, purchasePlan,
                        paymentMethod, paymentSource);
                return;
            }

            if (buyer.getServer() == null) {
                sendResult(buyer, false, ShopResultCode.SERVER_ERROR);
                return;
            }

            List<LinkedStorage> linkedStorages = resolveLinkedStorages(buyer.level(), shop, pos);
            if (linkedStorages.isEmpty()) {
                sendResult(buyer, false, ShopResultCode.NO_LINK);
                return;
            }

            // ═══ Stock check — NBT-aware + bundle support, summed across all links ═══
            for (PurchaseOutput output : purchasePlan.outputs()) {
                if (!canExtractComposite(linkedStorages, output.item(), output.count(),
                        output.nbtAware(), output.nbtTag())) {
                    sendResult(buyer, false, ShopResultCode.OUT_OF_STOCK);
                    return;
                }
            }

            EconomyProvider provider = BalanceManager.getProvider();
            long cost = purchasePlan.cost();
            PurchasePaymentService.Receipt paymentReceipt = null;
            boolean recordedSale = false;
            Item barterItem = null;
            int barterAmount = 0;
            List<ItemStack> insertedPayment = List.of();

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
            if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
                var preEvent = new com.enviouse.futureshops.event.ShopTransactionEvent.Pre(
                        buyer, shopIdForEvent, listing.itemId(), qty, tradeTypeForEvent, cost);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent);
                if (preEvent.isCanceled()) {
                    sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                    return;
                }
                // Allow external mods to modify price
                cost = preEvent.getPriceMinor();
            }
            try {
                cost = PlayerShopBuyMath.requireNonnegativePrice(cost);
            } catch (IllegalArgumentException exception) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }

            PlayerShopSettlementSavedData settlementData = PlayerShopSettlementSavedData.get(buyer.getServer());
            long settlementAmount = barterTrade ? 0L : cost;
            if (!settlementData.canRecordSale(shop.getOwnerUuid(), pos.asLong(), settlementAmount)) {
                sendResult(buyer, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                return;
            }

            // ═══ Item 16/17: Pre-validate barter payment insertion (overflow detection) ═══
            // Item 24: Use effective barter count (promo-adjusted, rounds up)
            // Item 31: Use resolveBarterStorage() for barter payment destination
            LinkedStorage barterStorage = null;
            if (compoundTrade || barterTrade) {
                try {
                    barterItem = ShopTransactionUtil.resolveItem(listing.barterItemId());
                } catch (RuntimeException exception) {
                    sendResult(buyer, false, ShopResultCode.INVALID_ITEM);
                    return;
                }
                try {
                    barterAmount = PlayerShopBuyMath.checkedBarterTotal(
                            listing.effectiveBarterItemCount(), qty);
                    if (barterItem != null && barterItem != Items.AIR) {
                        PlayerShopBuyMath.checkedStackCount(barterAmount, barterItem.getMaxStackSize());
                    }
                } catch (ArithmeticException | IllegalArgumentException exception) {
                    sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                    return;
                }
                // NBT-strict barter payment: if the listing captured an NBT tag for the barter
                // item (e.g. an "empty" tank, a specific enchanted chestplate), only stacks
                // matching that tag exactly count as valid payment. Prevents the exploit where
                // a buyer paid with a half-full tank and got accepted as if it were empty.
                boolean barterNbtAware = listing.barterNbtAware();
                CompoundTag barterNbtTag = listing.barterNbtTag();
                if (barterItem == null || barterItem == Items.AIR || ShopTransactionUtil.countItems(buyer.getInventory(), barterItem, barterNbtAware, barterNbtTag) < barterAmount) {
                    if (compoundTrade) {
                        sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, Component.translatable("command.futureshops.shop.barter_not_enough"));
                        return;
                    }
                    // BOTH mode: if barter fails, it was fallback — send result
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, Component.translatable("command.futureshops.shop.barter_not_enough"));
                    return;
                }
                barterStorage = resolveBarterStorage(buyer.level(), shop, pos);
                if (barterStorage == null) {
                    sendResultWithChat(buyer, false, ShopResultCode.NO_LINK, Component.translatable("command.futureshops.shop.barter_storage_unlinked"));
                    return;
                }
                // Pre-flight insert check uses a synthesized stack list so we can validate
                // capacity without touching the buyer's inventory. The actual payment stacks
                // (with the buyer's real NBT preserved) are collected later via
                // ShopTransactionUtil.collectAndRemoveItems so the owner receives exactly
                // what the buyer paid with — enchanted chestplate in, enchanted chestplate
                // out, rather than a freshly-minted plain one.
                List<ItemStack> previewPayment = splitStacks(barterItem, barterAmount, barterNbtTag);
                boolean canInsertPayment = previewPayment.isEmpty()
                        || (barterStorage.hasAdapter()
                        ? barterStorage.adapter().canInsert(barterStorage.blockEntity(), previewPayment)
                        : canInsertAll(barterStorage.handler(), previewPayment));
                if (!canInsertPayment) {
                    // Item 18: Close UI + chat message for storage full
                    sendResultWithChat(buyer, false, ShopResultCode.STORAGE_FULL, Component.translatable("command.futureshops.shop.barter_storage_full"));
                    return;
                }
            }

            // ═══ MONEY_AND_BARTER compound path ═══
            if (compoundTrade) {
                // LGB#8: Skip money withdrawal if cost is 0 (100% discount)
                if (cost > 0L) {
                    PurchasePaymentService.Result moneyCheck = PurchasePaymentService.charge(
                            buyer, cost, paymentSource);
                    if (!moneyCheck.success()) {
                        sendResultWithChat(buyer, false, moneyCheck.code(), Component.translatable("command.futureshops.shop.compound_insufficient"));
                        return;
                    }
                    paymentReceipt = moneyCheck.receipt();
                }

                // Fire BarterTradeEvent.Pre (spec §33) for barter portion
                if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
                    var barterPre = new com.enviouse.futureshops.event.BarterTradeEvent.Pre(
                            buyer.getUUID(), shopIdForEvent, listing.itemId() + ":compound",
                            listing.itemId(), qty,
                            List.of(new com.enviouse.futureshops.event.BarterTradeEvent.IngredientEntry(listing.barterItemId(), barterAmount)));
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(barterPre);
                    if (barterPre.isCanceled()) {
                        PurchasePaymentService.refund(buyer, paymentReceipt);
                        sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                        return;
                    }
                }

                // Transfer the buyer's ACTUAL stacks (NBT intact) to the owner's storage,
                // instead of creating fresh plain stacks — so an enchanted / tagged payment
                // arrives as an enchanted / tagged item in storage.
                List<ItemStack> paymentStacks = barterAmount == 0
                        ? List.of()
                        : ShopTransactionUtil.collectAndRemoveItems(
                        buyer.getInventory(), barterItem, barterAmount,
                        listing.barterNbtAware(), listing.barterNbtTag());
                if (barterAmount > 0 && paymentStacks.isEmpty()) {
                    PurchasePaymentService.refund(buyer, paymentReceipt);
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, Component.translatable("command.futureshops.shop.barter_not_taken"));
                    return;
                }
                // Item 31: Insert barter payment into barter-specific storage
                boolean insertedOk = paymentStacks.isEmpty() || (barterStorage.hasAdapter()
                        ? barterStorage.adapter().insert(barterStorage.blockEntity(), paymentStacks)
                        : insertAll(barterStorage.handler(), paymentStacks));
                if (!insertedOk) {
                    ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), paymentStacks);
                    PurchasePaymentService.refund(buyer, paymentReceipt);
                    sendResultWithChat(buyer, false, ShopResultCode.STORAGE_FULL, Component.translatable("command.futureshops.shop.trade_storage_full"));
                    return;
                }
                insertedPayment = paymentStacks;
                if (!settlementData.recordSale(shop.getOwnerUuid(), pos.asLong(), cost,
                        listing.itemId(), qty, listingNbtJson(listing))) {
                    rollbackAll(barterStorage, buyer, paymentReceipt, cost, false,
                            shop.getOwnerUuid(), pos, insertedPayment, true, false);
                    sendResult(buyer, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                    return;
                }
                recordedSale = true;
            } else if (!barterTrade) {
                // LGB#8: Skip money withdrawal if cost is 0 (100% discount)
                if (cost <= 0L) {
                    // Free item — no withdrawal needed
                    if (!settlementData.recordSale(shop.getOwnerUuid(), pos.asLong(), 0L,
                            listing.itemId(), qty, listingNbtJson(listing))) {
                        sendResult(buyer, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                        return;
                    }
                    recordedSale = true;
                } else {
                    PurchasePaymentService.Result payment = PurchasePaymentService.charge(
                            buyer, cost, paymentSource);
                    if (!payment.success()) {
                        sendResult(buyer, false, payment.code());
                        return;
                    } else {
                        paymentReceipt = payment.receipt();
                        if (!settlementData.recordSale(shop.getOwnerUuid(), pos.asLong(), cost,
                                listing.itemId(), qty, listingNbtJson(listing))) {
                            PurchasePaymentService.refund(buyer, paymentReceipt);
                            sendResult(buyer, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                            return;
                        }
                        recordedSale = true;
                    }
                }
            }

            if (barterTrade) {
                // barterItem and barterAmount already set above from pre-validation
                // Item 31: Use barterStorage for barter payment insertion
                if (barterStorage == null) {
                    barterStorage = resolveBarterStorage(buyer.level(), shop, pos);
                }
                if (barterStorage == null) {
                    sendResultWithChat(buyer, false, ShopResultCode.NO_LINK, Component.translatable("command.futureshops.shop.barter_storage_unlinked"));
                    return;
                }

                // Fire BarterTradeEvent.Pre (spec §33) for pure barter
                if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
                    var barterPre = new com.enviouse.futureshops.event.BarterTradeEvent.Pre(
                            buyer.getUUID(), shopIdForEvent, listing.itemId() + ":barter",
                            listing.itemId(), qty,
                            List.of(new com.enviouse.futureshops.event.BarterTradeEvent.IngredientEntry(listing.barterItemId(), barterAmount)));
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(barterPre);
                    if (barterPre.isCanceled()) {
                        sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                        return;
                    }
                }

                // Transfer the buyer's ACTUAL stacks (NBT intact) to the owner's storage.
                List<ItemStack> paymentStacks = barterAmount == 0
                        ? List.of()
                        : ShopTransactionUtil.collectAndRemoveItems(
                        buyer.getInventory(), barterItem, barterAmount,
                        listing.barterNbtAware(), listing.barterNbtTag());
                if (barterAmount > 0 && paymentStacks.isEmpty()) {
                    sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS, Component.translatable("command.futureshops.shop.barter_not_taken"));
                    return;
                }
                boolean insertedOk = paymentStacks.isEmpty() || (barterStorage.hasAdapter()
                        ? barterStorage.adapter().insert(barterStorage.blockEntity(), paymentStacks)
                        : insertAll(barterStorage.handler(), paymentStacks));
                if (!insertedOk) {
                    ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), paymentStacks);
                    sendResultWithChat(buyer, false, ShopResultCode.STORAGE_FULL, Component.translatable("command.futureshops.shop.trade_storage_full"));
                    return;
                }
                insertedPayment = paymentStacks;
                if (!settlementData.recordSale(shop.getOwnerUuid(), pos.asLong(), 0L,
                        listing.itemId(), qty, listingNbtJson(listing))) {
                    rollbackBarterPayment(barterStorage, buyer, insertedPayment);
                    sendResult(buyer, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                    return;
                }
                recordedSale = true;
            }

            // ═══ Extract sale items — NBT-aware + bundle support ═══
            List<ItemStack> extracted = new ArrayList<>();
            for (PurchaseOutput output : purchasePlan.outputs()) {
                List<ItemStack> part = extractComposite(linkedStorages, output.item(), output.count(),
                        output.nbtAware(), output.nbtTag());
                boolean validPart = exactOutputCount(part, output);
                if (validPart) {
                    try {
                        PlayerShopBuyMath.checkedAggregateStackCount(extracted.size(), part.size());
                    } catch (ArithmeticException | IllegalArgumentException exception) {
                        validPart = false;
                    }
                }
                if (!validPart) {
                    rollbackExtracted(linkedStorages, part);
                    rollbackExtracted(linkedStorages, extracted);
                    rollbackAll(barterStorage, buyer, paymentReceipt, cost, recordedSale,
                            shop.getOwnerUuid(), pos, insertedPayment, compoundTrade, barterTrade);
                    sendResult(buyer, false, ShopResultCode.ROLLBACK);
                    return;
                }
                extracted.addAll(part);
            }

            if (extracted.isEmpty()) {
                rollbackAll(barterStorage, buyer, paymentReceipt, cost, recordedSale,
                        shop.getOwnerUuid(), pos, insertedPayment, compoundTrade, barterTrade);
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

            if (buyer.getServer() != null) {
                String tradeType = compoundTrade ? "MONEY_AND_BARTER" : (barterTrade ? "BARTER" : "BUY");
                String source = compoundTrade ? "PLAYER_SHOP_COMPOUND" : (barterTrade ? "PLAYER_SHOP_BARTER" : "PLAYER_SHOP");
                String historyNote = (barterTrade || compoundTrade) && barterItem != null
                        ? "paid=" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(barterItem) + "\u00d7" + barterAmount
                        : source;
                TransactionHistoryService.record(
                        buyer,
                        shop.getShopId(),
                        tradeType,
                        listing.itemId(),
                        qty,
                        barterTrade ? 0L : cost,
                        historyNote,
                        listingNbtJson(listing));

                // ═══ Fire ShopTransactionEvent.Post (spec §33) ═══
                if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
                    long resultingBal = provider.getBalance(buyer.getUUID());
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                            new com.enviouse.futureshops.event.ShopTransactionEvent.Post(
                                    buyer.getUUID(), shopIdForEvent, listing.itemId(),
                                    qty, tradeType, barterTrade ? 0L : cost, resultingBal));

                    // Fire BarterTradeEvent.Post for barter/compound trades
                    if (barterTrade || compoundTrade) {
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                                new com.enviouse.futureshops.event.BarterTradeEvent.Post(
                                        buyer.getUUID(), shopIdForEvent,
                                        listing.itemId() + (compoundTrade ? ":compound" : ":barter"),
                                        listing.itemId(), qty,
                                        List.of(new com.enviouse.futureshops.event.BarterTradeEvent.IngredientEntry(
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
            // Barter storage stays single — one dedicated bartered-goods container.
            shop.setBarterStoragePos(target);
        } else {
            // Stock storage is composite: ADD the looked-at container to the linked set.
            // Reject (reusing BAD_LINK_TARGET) when already linked or at the cap — the
            // distance/validity checks were already applied by validateLinkTarget above.
            if (shop.getLinkedStoragePositions().contains(target)) {
                sendResult(player, false, ShopResultCode.BAD_LINK_TARGET);
                return 0;
            }
            // Reject the other half of an already-linked double chest — both halves alias the
            // same combined inventory, so linking both would be a no-op after dedupe and only
            // desync the "N/cap" count. Compare by canonical inventory key.
            long targetKey = canonicalStorageKey(player.level(), target);
            for (BlockPos linked : shop.getLinkedStoragePositions()) {
                if (canonicalStorageKey(player.level(), linked) == targetKey) {
                    sendResult(player, false, ShopResultCode.BAD_LINK_TARGET);
                    return 0;
                }
            }
            if (!shop.addLinkedStorage(target)) {
                // addLinkedStorage only fails here for the cap (dupe handled above).
                sendResult(player, false, ShopResultCode.BAD_LINK_TARGET);
                return 0;
            }
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
                                           ShopBlockEntity.Listing listing, int qty,
                                           PurchasePlan purchasePlan, String paymentMethod,
                                           PaymentSource paymentSource) {
        EconomyProvider provider = BalanceManager.getProvider();
        long cost = purchasePlan.cost();

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
        if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
            var preEvent = new com.enviouse.futureshops.event.ShopTransactionEvent.Pre(
                    buyer, shopIdForEvent, listing.itemId(), qty, tradeTypeForEvent, cost);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent);
            if (preEvent.isCanceled()) {
                sendResult(buyer, false, ShopResultCode.CANCELLED_BY_EVENT);
                return;
            }
            cost = preEvent.getPriceMinor();
        }
        try {
            cost = PlayerShopBuyMath.requireNonnegativePrice(cost);
        } catch (IllegalArgumentException exception) {
            sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
            return;
        }

        // Validate barter availability without consuming yet.
        Item barterItem = null;
        int barterAmount = 0;
        if (compoundTrade || barterTrade) {
            try {
                barterItem = ShopTransactionUtil.resolveItem(listing.barterItemId());
            } catch (RuntimeException exception) {
                sendResult(buyer, false, ShopResultCode.INVALID_ITEM);
                return;
            }
            try {
                barterAmount = PlayerShopBuyMath.checkedBarterTotal(
                        listing.effectiveBarterItemCount(), qty);
                if (barterItem != null && barterItem != Items.AIR) {
                    PlayerShopBuyMath.checkedStackCount(barterAmount, barterItem.getMaxStackSize());
                }
            } catch (ArithmeticException | IllegalArgumentException exception) {
                sendResult(buyer, false, ShopResultCode.INVALID_AMOUNT);
                return;
            }
            if (barterItem == null || barterItem == Items.AIR
                    || ShopTransactionUtil.countItems(buyer.getInventory(), barterItem,
                            listing.barterNbtAware(), listing.barterNbtTag()) < barterAmount) {
                sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS,
                        Component.translatable("command.futureshops.shop.barter_not_enough"));
                return;
            }
        }

        // Charge money (sunk — never deposited to owner).
        PurchasePaymentService.Receipt paymentReceipt = null;
        if ((compoundTrade || !barterTrade) && cost > 0L) {
            PurchasePaymentService.Result payment = PurchasePaymentService.charge(
                    buyer, cost, paymentSource);
            if (!payment.success()) {
                sendResult(buyer, false, payment.code());
                return;
            }
            paymentReceipt = payment.receipt();
        }

        // Consume barter items — voided (NOT inserted into any storage).
        if (compoundTrade || barterTrade) {
            List<ItemStack> paymentStacks = barterAmount == 0
                    ? List.of()
                    : ShopTransactionUtil.collectAndRemoveItems(
                    buyer.getInventory(), barterItem, barterAmount,
                    listing.barterNbtAware(), listing.barterNbtTag());
            if (barterAmount > 0 && paymentStacks.isEmpty()) {
                PurchasePaymentService.refund(buyer, paymentReceipt);
                sendResultWithChat(buyer, false, ShopResultCode.MISSING_BARTER_ITEMS,
                        Component.translatable("command.futureshops.shop.barter_not_taken"));
                return;
            }
            // Stacks are intentionally discarded — admin shop voids them.
        }

        // Deliver freshly minted sale items.
        List<ItemStack> delivered = new ArrayList<>();
        for (PurchaseOutput output : purchasePlan.outputs()) {
            delivered.addAll(splitStacks(output.item(), output.count(), output.nbtTag()));
        }

        if (!ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), delivered)) {
            for (ItemStack stack : delivered) {
                if (!stack.isEmpty()) buyer.drop(stack, false);
            }
        }

        // History: record the buyer's spend but no owner settlement.
        if (buyer.getServer() != null) {
            String historyNote = (barterTrade || compoundTrade) && barterItem != null
                    ? "paid=" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(barterItem)
                            + "\u00d7" + barterAmount + " [ADMIN_SHOP]"
                    : "ADMIN_SHOP";
            TransactionHistoryService.record(
                    buyer,
                    shop.getShopId(),
                    tradeTypeForEvent,
                    listing.itemId(),
                    qty,
                    barterTrade ? 0L : cost,
                    historyNote,
                    listingNbtJson(listing));

            if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
                long resultingBal = provider.getBalance(buyer.getUUID());
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new com.enviouse.futureshops.event.ShopTransactionEvent.Post(
                                buyer.getUUID(), shopIdForEvent, listing.itemId(),
                                qty, tradeTypeForEvent, barterTrade ? 0L : cost, resultingBal));
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
                int raw = countSingleItemStock(level, shop, shopPos, bundleItem, entry.nbtTag() != null, entry.nbtTag());
                int sets = entry.count() > 0 ? raw / entry.count() : 0;
                minStock = Math.min(minStock, sets);
            }
            return minStock == Integer.MAX_VALUE ? 0 : minStock;
        }

        Item item = ShopTransactionUtil.resolveItem(listing.itemId());
        if (item == null || item == Items.AIR) return 0;
        int raw = countSingleItemStock(level, shop, shopPos, item, listing.nbtAware(), listing.nbtTag());
        // Item 32: Stock count is in units of baseQuantity
        return listing.baseQuantity() > 1 ? raw / listing.baseQuantity() : raw;
    }

    private static int countSingleItemStock(Level level, ShopBlockEntity shop, BlockPos shopPos,
                                            Item item, boolean nbtAware, @Nullable CompoundTag nbtTag) {
        List<LinkedStorage> storages = resolveLinkedStorages(level, shop, shopPos);
        if (storages.isEmpty()) return 0;
        return CompositeStorageOps.countAcross(storages,
                s -> countInStorage(s, item, nbtAware, nbtTag));
    }

    /** Counts matching items in a SINGLE resolved storage (adapter- or handler-backed). */
    private static int countInStorage(LinkedStorage storage, Item item, boolean nbtAware, @Nullable CompoundTag nbtTag) {
        if (storage.hasAdapter()) {
            return storage.adapter().countItem(storage.blockEntity(), item, nbtAware, nbtTag);
        }
        int total = 0;
        IItemHandler handler = storage.handler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (NbtMatchUtil.matches(stack, item, nbtAware, nbtTag)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static PlayerShopListingData toData(Level level, ShopBlockEntity shop, BlockPos shopPos,
                                                ShopBlockEntity.Listing listing, boolean visible) {
        ShopBlockEntity.Promo promo = listing.promo();
        String nbtJson = "";
        if (listing.nbtTag() != null) {
            nbtJson = listing.nbtTag().toString();
        }
        String barterNbtJson = "";
        if (listing.barterNbtTag() != null) {
            barterNbtJson = listing.barterNbtTag().toString();
        }
        // Build bundle output data
        List<PlayerShopListingData.BundleOutputData> bundleData = listing.bundleOutputs().stream()
                .map(e -> new PlayerShopListingData.BundleOutputData(
                        e.itemId(), e.count(), e.nbtTag() != null ? e.nbtTag().toString() : ""))
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
                listing.buybackRemaining(),
                listing.hidden(),
                listing.showcase());
    }

    /** The listing's SNBT for history/settlement rows; "" when the listing carries no tag. */
    private static String listingNbtJson(ShopBlockEntity.Listing listing) {
        CompoundTag tag = listing == null ? null : listing.nbtTag();
        return tag != null ? tag.toString() : "";
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
     * Unified rollback for buy() failures after payment was taken. The barter payment
     * is pulled back from the barter storage it was inserted into (which may differ
     * from the sale-item storage and may be adapter-backed).
     */
    private static void rollbackAll(LinkedStorage barterStorage, ServerPlayer buyer,
                                    PurchasePaymentService.Receipt paymentReceipt,
                                    long cost, boolean recordedSale,
                                    UUID ownerUuid, BlockPos pos,
                                    List<ItemStack> insertedPayment,
                                    boolean compoundTrade, boolean barterTrade) {
        if (compoundTrade || !barterTrade) {
            if (recordedSale && buyer.getServer() != null) {
                PlayerShopSettlementSavedData.get(buyer.getServer()).rollbackPending(ownerUuid, pos.asLong(), cost);
            }
            PurchasePaymentService.refund(buyer, paymentReceipt);
        }
        if (compoundTrade || barterTrade) {
            rollbackBarterPayment(barterStorage, buyer, insertedPayment);
        }
    }

    private static void rollbackBarterPayment(LinkedStorage barterStorage, ServerPlayer buyer,
                                              List<ItemStack> insertedPayment) {
        if (barterStorage == null || insertedPayment.isEmpty()) {
            return;
        }
        // Pull back the buyer's ACTUAL paid stacks — NBT-strict against each paid
        // stack's own tag (not the listing's), so tagged payments come back tagged
        // instead of being re-minted bare. MUST be best-effort: the all-or-nothing
        // extract() discards partially-pulled items on a shortfall, which on this
        // path would destroy the buyer's payment instead of refunding it.
        List<ItemStack> recovered = new ArrayList<>();
        for (ItemStack paid : insertedPayment) {
            if (paid.isEmpty()) {
                continue;
            }
            if (barterStorage.hasAdapter()) {
                // Adapter extract is all-or-nothing but non-destructive (reinserts
                // on shortfall): try the full amount, then fall back to what is
                // actually available so a one-item shortfall doesn't forfeit the
                // whole stack refund.
                List<ItemStack> got = barterStorage.adapter().extract(
                        barterStorage.blockEntity(), paid.getItem(), paid.getCount(), true, paid.getTag());
                if (got.isEmpty()) {
                    int available = barterStorage.adapter().countItem(
                            barterStorage.blockEntity(), paid.getItem(), true, paid.getTag());
                    if (available > 0) {
                        got = barterStorage.adapter().extract(barterStorage.blockEntity(),
                                paid.getItem(), Math.min(available, paid.getCount()), true, paid.getTag());
                    }
                }
                recovered.addAll(got);
            } else if (barterStorage.handler() != null) {
                recovered.addAll(extractStacks(barterStorage.handler(),
                        paid.getItem(), paid.getCount(), true, paid.getTag()));
            }
        }
        if (!recovered.isEmpty()) {
            ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), recovered);
        }
    }

    /**
     * Reinserts one stack into a SINGLE storage during rollback. Any remainder that will not
     * fit (storage filled concurrently, or an adapter that rejects the whole insert) is dropped
     * in-world at the storage rather than silently vanishing — rollback must never lose items.
     */
    private static void reinsertOne(LinkedStorage storage, ItemStack stack) {
        if (storage.hasAdapter()) {
            if (!storage.adapter().insert(storage.blockEntity(), List.of(stack))) {
                dropLeftover(storage, stack);
            }
        } else if (storage.handler() != null) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < storage.handler().getSlots() && !remaining.isEmpty(); i++) {
                remaining = storage.handler().insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) {
                dropLeftover(storage, remaining);
            }
        }
    }

    /**
     * Reinserts one stack across the composite, spilling to the next storage when one is full.
     * Anything the whole composite cannot absorb is dropped in-world (rollback must not lose items).
     */
    private static void reinsertComposite(List<LinkedStorage> storages, ItemStack stack) {
        List<ItemStack> leftover = CompositeStorageOps.spillInsert(storages, new ArrayList<>(List.of(stack)),
                (s, units) -> insertIntoOne(s, units));
        if (!leftover.isEmpty() && !storages.isEmpty()) {
            for (ItemStack ls : leftover) {
                dropLeftover(storages.get(0), ls);
            }
        }
    }

    /** Drops a stack as a world item at the storage's position — last-resort no-loss rollback. */
    private static void dropLeftover(LinkedStorage storage, ItemStack stack) {
        if (stack.isEmpty()) return;
        Level level = storage.blockEntity() != null ? storage.blockEntity().getLevel() : null;
        if (level != null && !level.isClientSide) {
            Block.popResource(level, storage.pos(), stack.copy());
        }
    }

    // ═══ NBT-aware composite extraction helpers ═══

    /** Composite stock probe: true when the summed matching stock across all links >= count. */
    private static boolean canExtractComposite(List<LinkedStorage> storages, Item item, int count,
                                               boolean nbtAware, @Nullable CompoundTag nbtTag) {
        return CompositeStorageOps.canExtractAcross(storages, count,
                s -> countInStorage(s, item, nbtAware, nbtTag));
    }

    /**
     * All-or-nothing extraction of {@code count} units across every linked storage in order.
     * Pulls best-effort from each storage (so no single storage's own all-or-nothing empties
     * it destructively), and if the composite falls short reinserts everything already pulled —
     * returning an empty list, exactly matching the single-storage all-or-nothing contract.
     * Extracted stacks keep their tags intact.
     */
    private static List<ItemStack> extractComposite(List<LinkedStorage> storages, Item item, int count,
                                                    boolean nbtAware, @Nullable CompoundTag nbtTag) {
        return CompositeStorageOps.extractAcross(
                storages,
                count,
                (s, remaining) -> takeBestEffort(s, item, remaining, nbtAware, nbtTag),
                ItemStack::getCount,
                PlayerShopBlockService::reinsertOne);
    }

    /**
     * Best-effort extraction from a SINGLE storage: returns the actual stacks (tags intact),
     * up to {@code remaining} units. Deliberately NON-destructive-on-shortfall — it never
     * empties a storage and returns nothing; the composite ({@link #extractComposite}) makes
     * the all-or-nothing decision and rolls back across storages if the total falls short.
     */
    private static List<ItemStack> takeBestEffort(LinkedStorage s, Item item, int remaining,
                                                  boolean nbtAware, @Nullable CompoundTag nbtTag) {
        if (remaining <= 0) return List.of();
        if (s.hasAdapter()) {
            // Adapter.extract is all-or-nothing (non-destructive), so never ask for more than
            // is present or it forfeits this storage's whole contribution.
            int avail = s.adapter().countItem(s.blockEntity(), item, nbtAware, nbtTag);
            int want = Math.min(avail, remaining);
            return want <= 0 ? List.of() : s.adapter().extract(s.blockEntity(), item, want, nbtAware, nbtTag);
        }
        return extractStacks(s.handler(), item, remaining, nbtAware, nbtTag);
    }

    /**
     * Inserts as much of {@code units} as fits into a SINGLE storage, returning the leftover.
     * Adapters are whole-list all-or-nothing (RS etc.): either every unit is accepted or none.
     */
    private static List<ItemStack> insertIntoOne(LinkedStorage s, List<ItemStack> units) {
        if (units.isEmpty()) return new ArrayList<>();
        if (s.hasAdapter()) {
            return s.adapter().insert(s.blockEntity(), units) ? new ArrayList<>() : new ArrayList<>(units);
        }
        IItemHandler handler = s.handler();
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack unit : units) {
            ItemStack remaining = unit.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) leftover.add(remaining);
        }
        return leftover;
    }

    /** Simulate-mode counterpart of {@link #insertIntoOne} — no mutation, returns the leftover. */
    private static List<ItemStack> simulateInsertIntoOne(LinkedStorage s, List<ItemStack> units) {
        if (units.isEmpty()) return new ArrayList<>();
        if (s.hasAdapter()) {
            return s.adapter().canInsert(s.blockEntity(), units) ? new ArrayList<>() : new ArrayList<>(units);
        }
        IItemHandler handler = s.handler();
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack unit : units) {
            ItemStack remaining = unit.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, true);
            }
            if (!remaining.isEmpty()) leftover.add(remaining);
        }
        return leftover;
    }

    /** Composite capacity pre-check: can the linked storages absorb ALL stacks (with spill)? */
    private static boolean canInsertComposite(List<LinkedStorage> storages, List<ItemStack> stacks) {
        return CompositeStorageOps.insertAllAcross(storages, stacks,
                PlayerShopBlockService::simulateInsertIntoOne);
    }

    /** Composite real insert with spill. @return true iff every stack was absorbed. */
    private static boolean insertComposite(List<LinkedStorage> storages, List<ItemStack> stacks) {
        return CompositeStorageOps.insertAllAcross(storages, stacks,
                PlayerShopBlockService::insertIntoOne);
    }

    /**
     * Best-effort, stack-returning extraction used by rollback paths: hands back the
     * ACTUAL extracted stacks (tags intact) so refunds never re-mint bare items. Unlike
     * {@link #extract}, a shortfall returns whatever was recovered rather than nothing.
     */
    private static List<ItemStack> extractStacks(IItemHandler handler, Item item, int amount,
                                                 boolean nbtAware, @Nullable CompoundTag nbtTag) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, nbtTag)) {
                continue;
            }
            ItemStack taken = handler.extractItem(i, remaining, false);
            if (!taken.isEmpty()) {
                remaining -= taken.getCount();
                result.add(taken);
            }
        }
        return result;
    }

    /**
     * Resolves ONE storage position into a {@link LinkedStorage}, or {@code null} when the
     * position is invalid, unloaded, the shop itself, or exposes no item storage. Shared by
     * the composite stock resolver and the single barter-storage resolver.
     */
    @Nullable
    private static LinkedStorage resolveOneStorage(Level level, BlockPos shopPos, @Nullable BlockPos storagePos) {
        if (storagePos == null || storagePos.equals(shopPos) || !level.hasChunkAt(storagePos)) {
            return null;
        }
        BlockEntity linked = level.getBlockEntity(storagePos);
        if (linked == null || linked instanceof ShopBlockEntity) {
            return null;
        }
        ExternalStorageAdapter externalAdapter = ExternalStorageRegistry.findAdapter(linked);
        if (externalAdapter != null) {
            return new LinkedStorage(storagePos, null, externalAdapter, linked);
        }
        IItemHandler handler = linked.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) {
            return null;
        }
        return new LinkedStorage(storagePos, handler, null, linked);
    }

    /**
     * Resolves the COMPOSITE of every linked stock storage, in link order, skipping any that
     * are unloaded/invalid. May be empty when nothing resolves. This is the shop's stock:
     * counts sum across the list, buys pull across it (all-or-nothing), sells spill across it.
     */
    private static List<LinkedStorage> resolveLinkedStorages(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        List<BlockPos> positions = shop.getLinkedStoragePositions();
        if (positions.isEmpty()) {
            return List.of();
        }
        List<LinkedStorage> resolved = new ArrayList<>(positions.size());
        // Dedupe storages that ALIAS one underlying inventory. The classic case: a vanilla
        // double chest — Forge exposes the full 54-slot combined handler from BOTH halves, so
        // linking both positions would count/insert/extract the same slots twice (phantom stock,
        // duplicated sells). canonicalStorageKey() collapses both halves to one key.
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (BlockPos p : positions) {
            long key = canonicalStorageKey(level, p);
            if (seen.contains(key)) {
                continue;
            }
            LinkedStorage s = resolveOneStorage(level, shopPos, p);
            if (s != null) {
                resolved.add(s);
                seen.add(key);
            }
        }
        return resolved;
    }

    /**
     * A stable identity for the underlying inventory a linked position resolves to, used to
     * dedupe aliased links. For a vanilla double chest (either {@link ChestType#LEFT} or
     * {@code RIGHT} half — trapped chests included, since {@code TrappedChestBlock extends
     * ChestBlock}) both halves expose the same combined handler, so both must map to ONE key:
     * the smaller {@code asLong()} of the two half positions. Every other container is its own
     * position. Modded blocks that independently expose a shared multi-block inventory from more
     * than one position are not collapsed here (rare; the owner links their own containers).
     */
    private static long canonicalStorageKey(Level level, BlockPos pos) {
        // NEVER force-load a chunk here: this runs for every linked position on the hot stock
        // path (shop open, every buy/sell). An unloaded storage won't resolve anyway, so its
        // dedup key is irrelevant — return its own position without touching the world.
        if (!level.hasChunkAt(pos)) {
            return pos.asLong();
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
            return Math.min(pos.asLong(), partner.asLong());
        }
        return pos.asLong();
    }

    /**
     * Item 31: Resolves the barter storage for payment insertion — ALWAYS a single container.
     * When a dedicated barter storage is configured (barterStorageSame == false) that pos is
     * used; otherwise barter falls back to the PRIMARY (first) linked stock storage, keeping
     * bartered goods in one chest by design even when stock spans multiple linked storages.
     */
    private static LinkedStorage resolveBarterStorage(Level level, ShopBlockEntity shop, BlockPos shopPos) {
        if (shop.isBarterStorageSame() || shop.getBarterStoragePos() == null) {
            return resolveOneStorage(level, shopPos, shop.getLinkedStoragePos());
        }
        LinkedStorage dedicated = resolveOneStorage(level, shopPos, shop.getBarterStoragePos());
        // Fall back to the primary stock storage if the dedicated barter container is
        // invalid/unloaded — matches the pre-multilink fallback behaviour.
        return dedicated != null ? dedicated : resolveOneStorage(level, shopPos, shop.getLinkedStoragePos());
    }

    /** Result of link-target validation. OK means valid. */
    enum LinkTargetResult { OK, BAD_LINK_TARGET, RS_NOT_CONTROLLER }

    private static LinkTargetResult validateLinkTarget(Level level, BlockPos shopPos, BlockPos target) {
        if (target == null || target.equals(shopPos)
                || target.distManhattan(shopPos) > com.enviouse.futureshops.Config.playerShopMaxLinkDistanceBlocks) {
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
        if (targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().isPresent()) {
            return LinkTargetResult.OK;
        }
        return LinkTargetResult.BAD_LINK_TARGET;
    }

    private static boolean isValidLinkTarget(Level level, BlockPos shopPos, BlockPos target) {
        return validateLinkTarget(level, shopPos, target) == LinkTargetResult.OK;
    }

    /** Checks if a block entity belongs to Refined Storage (any RS mod namespace). */
    private static boolean isRSBlock(BlockEntity be) {
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        if (key != null) {
            String ns = key.getNamespace();
            return "refinedstorage".equals(ns) || "refinedstorage2".equals(ns) || "refinedstorageaddons".equals(ns);
        }
        return be.getClass().getName().startsWith("com.refinedmods.refinedstorage");
    }

    /** Checks if an RS block entity is a Controller (normal or creative). */
    private static boolean isRSController(BlockEntity be) {
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
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

    /**
     * All-or-nothing insert of every stack in {@code stacks} into {@code handler}. If the whole
     * list does not fit, everything already committed is extracted back out and {@code false} is
     * returned — so a partial commit can NEVER happen. This matters because the barter payment
     * path refunds the buyer the full payment on a {@code false} return; a leftover partial commit
     * would then duplicate the committed portion into existence. Rolls back via the real handler's
     * own {@code extractItem} (per-slot delta), so it stays correct for filtered/limited handlers
     * where a simulated pre-check ({@link #canInsertAll}) can diverge from the real sequential insert.
     */
    private static boolean insertAll(IItemHandler handler, List<ItemStack> stacks) {
        int slots = handler.getSlots();
        int[] added = new int[slots];
        boolean allFit = true;
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < slots && !remaining.isEmpty(); i++) {
                int before = remaining.getCount();
                remaining = handler.insertItem(i, remaining, false);
                added[i] += before - remaining.getCount();
            }
            if (!remaining.isEmpty()) {
                allFit = false;
                break;
            }
        }
        if (allFit) {
            return true;
        }
        // Roll back every unit committed above so the caller's full refund can't dupe.
        for (int i = 0; i < slots; i++) {
            int n = added[i];
            while (n > 0) {
                ItemStack pulled = handler.extractItem(i, n, false);
                if (pulled.isEmpty()) {
                    break;
                }
                n -= pulled.getCount();
            }
        }
        return false;
    }

    private static List<ItemStack> splitStacks(Item item, int totalCount) {
        return splitStacks(item, totalCount, null);
    }

    private static List<ItemStack> splitStacks(Item item, int totalCount, @Nullable CompoundTag nbtTag) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = totalCount;
        int max = Math.max(1, item.getMaxStackSize());
        while (remaining > 0) {
            int count = Math.min(max, remaining);
            ItemStack stack = new ItemStack(item, count);
            if (nbtTag != null) stack.setTag(nbtTag.copy());
            stacks.add(stack);
            remaining -= count;
        }
        return stacks;
    }

    private static void sendResult(ServerPlayer player, boolean success, ShopResultCode code) {
        BuyResponseContext responseContext = BUY_RESPONSE_CONTEXT.get();
        UUID requestId = responseContext == null
                ? new UUID(0L, 0L)
                : responseContext.requestId;
        int responseToken = responseContext == null ? 0 : responseContext.responseToken;
        if (responseContext != null) {
            responseContext.responded = true;
        }
        ShopPackets.sendToPlayer(player, new S2CPlayerShopResultPacket(
                success, code.wire(), "", requestId, responseToken));
    }

    /**
     * Item 18: Send result with a chat message displayed to the player.
     *
     * <p>Server → player messages must localize in each recipient's own language, so the
     * human-readable text travels as an unresolved {@link Component} via
     * {@link ServerPlayer#sendSystemMessage(Component)} rather than a baked English String
     * inside the result packet. The result packet still carries the {@link ShopResultCode},
     * which the client localizes for the on-screen status line (and confirmation modal).
     */
    private static void sendResultWithChat(ServerPlayer player, boolean success, ShopResultCode code, Component chatMessage) {
        sendResult(player, success, code);
        player.sendSystemMessage(chatMessage);
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

    private static final class BuyResponseContext {
        private final UUID requestId;
        private final int responseToken;
        private boolean responded;

        private BuyResponseContext(UUID requestId, int responseToken) {
            this.requestId = requestId;
            this.responseToken = responseToken;
        }
    }

    private record PurchaseOutput(Item item, int count, boolean nbtAware, @Nullable CompoundTag nbtTag) {
    }

    private record PurchasePlan(List<PurchaseOutput> outputs, long cost) {
    }

    private static final class PurchasePlanException extends Exception {
        private final ShopResultCode code;

        private PurchasePlanException(ShopResultCode code) {
            this.code = code;
        }
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
                                          com.enviouse.futureshops.network.packets.C2SPlayerShopBuybackConfigPacket packet) {
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
                                  com.enviouse.futureshops.network.packets.C2SPlayerShopSellPacket packet) {
        BlockPos pos = packet.shopPos();
        if (!(seller.level().getBlockEntity(pos) instanceof ShopBlockEntity shop)) return;

        ReentrantLock lock = SHOP_LOCKS.computeIfAbsent(pos.asLong(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (shop.getOwnerUuid() == null) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            // Single-item visitors only ever see index 0 → resolve to the showcased listing.
            ShopBlockEntity.Listing listing = shop.getListing(
                    resolveVisitorListingIndex(shop, seller, packet.listingIndex()));
            if (listing == null || listing.itemId().isBlank() || !listing.allowsBuy()) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            // Hidden/showcase listings do not transact for visitors (see buy()).
            if ((listing.hidden() || listing.showcase()) && !isOwnerOrFranchiseMember(shop, seller)) {
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
            int needItems = baseQty * qty;
            boolean nbtAware = listing.nbtAware();
            CompoundTag nbtTag = listing.nbtTag();
            int have = ShopTransactionUtil.countItems(seller.getInventory(), saleItem, nbtAware, nbtTag);
            if (have < needItems) {
                sendResultWithChat(seller, false, ShopResultCode.MISSING_ITEMS,
                        Component.translatable("command.futureshops.shop.missing_items"));
                return;
            }
            long total = listing.calculateBuybackTotal(qty);
            if (total <= 0L) {
                sendResult(seller, false, ShopResultCode.UNCONFIGURED);
                return;
            }
            if (listing.buybackRemaining() < qty) {
                sendResultWithChat(seller, false, ShopResultCode.BUYBACK_CAP_REACHED,
                        Component.translatable("command.futureshops.shop.buyback_cap_reached"));
                return;
            }

            String shopIdForEvent = "player_shop:" + pos.asLong();
            if (com.enviouse.futureshops.Config.eventsTransactionEnabled) {
                var pre = new com.enviouse.futureshops.event.ShopTransactionEvent.Pre(
                        seller, shopIdForEvent, listing.itemId(), qty, "SELL_TO_SHOP", total);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(pre);
                if (pre.isCanceled()) {
                    sendResult(seller, false, ShopResultCode.CANCELLED_BY_EVENT);
                    return;
                }
                total = pre.getPriceMinor();
            }

            EconomyProvider provider = BalanceManager.getProvider();

            if (shop.isAdminShopMode()) {
                // Void the items, mint the money.
                List<ItemStack> taken = ShopTransactionUtil.collectAndRemoveItems(
                        seller.getInventory(), saleItem, needItems, nbtAware, nbtTag);
                if (taken.isEmpty()) {
                    sendResultWithChat(seller, false, ShopResultCode.MISSING_ITEMS,
                            Component.translatable("command.futureshops.shop.missing_items"));
                    return;
                }
                TransactionResult dep = provider.deposit(seller.getUUID(), total, "SELL_TO_SHOP");
                if (!dep.success()) {
                    // Roll back: return items to seller.
                    ShopTransactionUtil.insertIntoInventory(seller.getInventory(), taken);
                    sendResult(seller, false, ShopResultCode.SERVER_ERROR);
                    return;
                }
                incrementBuyback(listing, qty);
                recordSellHistory(seller, shop, listing, qty, total, "ADMIN_SHOP_BUYBACK");
                firePostSellEvent(seller, shopIdForEvent, listing.itemId(), qty, total, provider);
                shop.setChanged();
                openFor(seller, pos, false);
                sendResultWithChat(seller, true, ShopResultCode.SOLD, Component.translatable("command.futureshops.shop.sold_to_shop"));
                return;
            }

            // ── Player shop path ──
            UUID ownerUuid = shop.getOwnerUuid();
            if (provider.getBalance(ownerUuid) < total) {
                sendResultWithChat(seller, false, ShopResultCode.SHOP_OUT_OF_MONEY,
                        Component.translatable("command.futureshops.shop.shop_out_of_money"));
                return;
            }
            List<LinkedStorage> storages = resolveLinkedStorages(seller.level(), shop, pos);
            if (storages.isEmpty()) {
                sendResultWithChat(seller, false, ShopResultCode.NO_LINK,
                        Component.translatable("command.futureshops.shop.no_linked_storage"));
                return;
            }

            // Collect actual stacks (NBT intact) from seller — but only after we've verified insertion is possible.
            List<ItemStack> paymentStacks = ShopTransactionUtil.collectAndRemoveItems(
                    seller.getInventory(), saleItem, needItems, nbtAware, nbtTag);
            if (paymentStacks.isEmpty()) {
                sendResultWithChat(seller, false, ShopResultCode.MISSING_ITEMS,
                        Component.translatable("command.futureshops.shop.missing_items"));
                return;
            }
            // Composite capacity check (spill across links), then insert.
            if (!canInsertComposite(storages, paymentStacks)) {
                ShopTransactionUtil.insertIntoInventory(seller.getInventory(), paymentStacks);
                sendResultWithChat(seller, false, ShopResultCode.STORAGE_FULL,
                        Component.translatable("command.futureshops.shop.storage_full_sell"));
                return;
            }
            if (!insertComposite(storages, paymentStacks)) {
                ShopTransactionUtil.insertIntoInventory(seller.getInventory(), paymentStacks);
                sendResultWithChat(seller, false, ShopResultCode.STORAGE_FULL,
                        Component.translatable("command.futureshops.shop.storage_full_sell"));
                return;
            }

            // Withdraw from owner.
            TransactionResult ownerWd = provider.withdraw(ownerUuid, total, "BUYBACK");
            if (!ownerWd.success()) {
                // Roll back the inserted items across the composite.
                rollbackInsertedItems(storages, seller, saleItem, needItems, nbtAware, nbtTag);
                sendResultWithChat(seller, false, ShopResultCode.SHOP_OUT_OF_MONEY,
                        Component.translatable("command.futureshops.shop.shop_out_of_money"));
                return;
            }

            // Credit seller.
            TransactionResult sellerDep = provider.deposit(seller.getUUID(), total, "SELL_TO_SHOP");
            if (!sellerDep.success()) {
                // Refund owner, undo storage.
                provider.deposit(ownerUuid, total, "BUYBACK_REFUND");
                rollbackInsertedItems(storages, seller, saleItem, needItems, nbtAware, nbtTag);
                sendResult(seller, false, ShopResultCode.MAX_BALANCE_EXCEEDED);
                return;
            }

            incrementBuyback(listing, qty);
            recordSellHistory(seller, shop, listing, qty, total, "BUYBACK");
            firePostSellEvent(seller, shopIdForEvent, listing.itemId(), qty, total, provider);
            shop.setChanged();
            openFor(seller, pos, false);
            sendResultWithChat(seller, true, ShopResultCode.SOLD, Component.translatable("command.futureshops.shop.sold_to_shop"));
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

    /**
     * Rolls a just-inserted sell payment back out of the composite and into the seller.
     * Best-effort across every link in order: pulls the ACTUAL stacks (tags intact) so refunds
     * never re-mint bare items. Because the payment was just inserted, the composite is
     * guaranteed to hold at least {@code amount} matching units, and the item is fungible /
     * tag-matched, so recovering {@code amount} of it makes the seller whole without touching
     * the owner's pre-existing stock economically.
     */
    private static void rollbackInsertedItems(List<LinkedStorage> storages, ServerPlayer seller, Item item, int amount,
                                              boolean nbtAware, @Nullable CompoundTag nbtTag) {
        List<ItemStack> recovered = new ArrayList<>();
        int remaining = amount;
        for (LinkedStorage storage : storages) {
            if (remaining <= 0) break;
            List<ItemStack> got = takeBestEffort(storage, item, remaining, nbtAware, nbtTag);
            for (ItemStack g : got) {
                if (!g.isEmpty()) {
                    remaining -= g.getCount();
                    recovered.add(g);
                }
            }
        }
        if (!recovered.isEmpty()) {
            ShopTransactionUtil.insertIntoInventory(seller.getInventory(), recovered);
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
                note,
                listingNbtJson(listing));
    }

    private static void firePostSellEvent(ServerPlayer seller, String shopIdForEvent, String itemId,
                                          int qty, long total, EconomyProvider provider) {
        if (!com.enviouse.futureshops.Config.eventsTransactionEnabled) return;
        long bal = provider.getBalance(seller.getUUID());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.enviouse.futureshops.event.ShopTransactionEvent.Post(
                        seller.getUUID(), shopIdForEvent, itemId, qty, "SELL_TO_SHOP", total, bal));
    }
}
