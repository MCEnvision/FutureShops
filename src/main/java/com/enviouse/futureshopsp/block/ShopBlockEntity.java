package com.enviouse.futureshopsp.block;

import com.enviouse.futureshopsp.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import com.enviouse.futureshopsp.server.transaction.NbtMatchUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ShopBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** Default listing cap when maxListings is not overridden (-1 = unlimited). */
    public static final int DEFAULT_MAX_LISTINGS = -1;
    public static final int MAX_BUNDLE_OUTPUTS = 36;

    public enum TradeMode {
        MONEY,
        BARTER,
        BOTH,
        MONEY_AND_BARTER
    }

    /**
     * Item 11: A single entry in a bundle listing — represents one output item and its quantity.
     */
    public record BundleEntry(String itemId, int count,  DataComponentPatch nbtPatch) {
        public CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("ItemId", itemId);
            tag.putInt("Count", count);
            if (nbtPatch != null && !nbtPatch.isEmpty()) tag.put("NbtPatch", NbtMatchUtil.patchToTag(registries, nbtPatch));
            return tag;
        }

        public static BundleEntry load(CompoundTag tag, HolderLookup.Provider registries) {
            String id = tag.getString("ItemId");
            int count = Math.max(1, tag.getInt("Count"));
            DataComponentPatch patch = tag.contains("NbtPatch", Tag.TAG_COMPOUND) ? NbtMatchUtil.tagToPatch(registries, tag.getCompound("NbtPatch")) : (tag.contains("NbtTag", Tag.TAG_COMPOUND) ? NbtMatchUtil.legacyTagToPatch(registries, ResourceLocation.parse(id), tag.getCompound("NbtTag")) : DataComponentPatch.EMPTY);
            return new BundleEntry(id, count, patch);
        }
    }

    private UUID ownerUuid;
    /**
     * Owner's last-known username. Captured whenever {@link #setOwnerUuid(UUID, String)}
     * is called with a non-blank name. Synced to clients so the block-top
     * nameplate + head decal can show a label without the client resolving
     * UUIDs itself.
     */
    private String ownerName = "";
    private String shopId = "default";
    private String shopName = "";
    private String description = "";
    private boolean singleItemMode = false;
    private int visibleListingIndex = -1; // Item 19/20: -1 = show all (multi), >= 0 = visible listing in single mode
    private boolean barterStorageSame = true;
    private int maxListings = DEFAULT_MAX_LISTINGS; // -1 = unlimited
    private final List<Listing> listings = new ArrayList<>();
    private BlockPos linkedStoragePos;
    private BlockPos barterStoragePos;

    // Owner-tunable visuals for the spinning listing item on top of the block.
    // Bounds picked so the item stays visible inside a 1×1×1 block envelope:
    // Y offset is relative to the default "a bit above the hitbox top" anchor.
    public static final float DISPLAY_Y_OFFSET_MIN = -0.35F;
    public static final float DISPLAY_Y_OFFSET_MAX = 0.60F;
    public static final float DISPLAY_Y_OFFSET_STEP = 0.05F;
    public static final float DISPLAY_SCALE_MIN = 0.40F;
    public static final float DISPLAY_SCALE_MAX = 1.80F;
    public static final float DISPLAY_SCALE_STEP = 0.10F;
    private float displayYOffset = 0.0F;
    private float displayScale = 1.0F;
    /** When true the block-top nameplate is suppressed even while a player is looking at the shop. */
    private boolean nameplateHidden = false;
    /**
     * Admin Shop eligibility — stamped at placement time when a creative-mode
     * player placed the block. Persists with the block entity. Required to ever
     * enable {@link #adminShopMode}.
     */
    private boolean placedByCreative = false;
    /**
     * When true the shop is in Admin Shop mode: infinite stock, money sunk on
     * buys (owner not credited), barter/sell inputs voided. Only togglable when
     * {@link #placedByCreative} is true.
     */
    private boolean adminShopMode = false;

    public ShopBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SHOP_BLOCK_ENTITY.get(), pos, blockState);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        setOwnerUuid(ownerUuid, null);
    }

    public void setOwnerUuid(UUID ownerUuid, @Nullable String ownerName) {
        this.ownerUuid = ownerUuid;
        if (ownerName != null && !ownerName.isBlank()) {
            this.ownerName = ownerName;
        }
        setChanged();
        syncTopItemsToClients();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public float getDisplayYOffset() {
        return displayYOffset;
    }

    public float getDisplayScale() {
        return displayScale;
    }

    public void adjustDisplayYOffset(float delta) {
        displayYOffset = clamp(displayYOffset + delta, DISPLAY_Y_OFFSET_MIN, DISPLAY_Y_OFFSET_MAX);
        setChanged();
        syncTopItemsToClients();
    }

    public void adjustDisplayScale(float delta) {
        displayScale = clamp(displayScale + delta, DISPLAY_SCALE_MIN, DISPLAY_SCALE_MAX);
        setChanged();
        syncTopItemsToClients();
    }

    public boolean isNameplateHidden() {
        return nameplateHidden;
    }

    public void toggleNameplateHidden() {
        this.nameplateHidden = !this.nameplateHidden;
        setChanged();
        syncTopItemsToClients();
    }

    public boolean isPlacedByCreative() {
        return placedByCreative;
    }

    public void setPlacedByCreative(boolean placedByCreative) {
        this.placedByCreative = placedByCreative;
        if (!placedByCreative) {
            this.adminShopMode = false;
        }
        setChanged();
    }

    public boolean isAdminShopMode() {
        return adminShopMode;
    }

    /**
     * Sets admin-shop mode. No-ops and returns {@code false} when the block is
     * not creative-placed (admin mode requires {@link #isPlacedByCreative()}).
     */
    public boolean setAdminShopMode(boolean adminShopMode) {
        if (adminShopMode && !placedByCreative) {
            return false;
        }
        this.adminShopMode = adminShopMode;
        setChanged();
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
        setChanged();
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName == null ? "" : shopName;
        setChanged();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        String safe = description == null ? "" : description.trim();
        this.description = safe.length() > 256 ? safe.substring(0, 256) : safe;
        setChanged();
    }

    public boolean isSingleItemMode() {
        return singleItemMode;
    }

    public void setSingleItemMode(boolean singleItemMode) {
        this.singleItemMode = singleItemMode;
        setChanged();
    }

    public boolean isBarterStorageSame() {
        return barterStorageSame;
    }

    public void setBarterStorageSame(boolean barterStorageSame) {
        this.barterStorageSame = barterStorageSame;
        setChanged();
    }

    /** Per-block listing cap. -1 = unlimited. */
    public int getMaxListings() { return maxListings; }
    public void setMaxListings(int maxListings) {
        this.maxListings = maxListings;
        setChanged();
    }
    /** Effective cap: returns Integer.MAX_VALUE when unlimited (-1). */
    public int effectiveMaxListings() {
        return maxListings < 0 ? Integer.MAX_VALUE : maxListings;
    }

    public BlockPos getBarterStoragePos() {
        return barterStoragePos;
    }

    public void setBarterStoragePos(BlockPos barterStoragePos) {
        this.barterStoragePos = barterStoragePos;
        setChanged();
    }

    /** Item 19/20: Index of the listing visible to visitors in single-item mode. -1 = show all. */
    public int getVisibleListingIndex() { return visibleListingIndex; }
    public void setVisibleListingIndex(int visibleListingIndex) {
        this.visibleListingIndex = visibleListingIndex;
        setChanged();
    }

    public List<Listing> getListings() {
        return Collections.unmodifiableList(listings);
    }

    public Listing getListing(int index) {
        return index >= 0 && index < listings.size() ? listings.get(index) : null;
    }

    public int addOrSelectListing(String itemId) {
        return addOrSelectListing(itemId, null);
    }

    /**
     * Resolves the listing slot for {@code itemId} with optional NBT discriminator.
     *
     * <p>Two items with the same registry id but different NBT (Tacz guns, enchanted
     * books with different enchant sets, Create staged blocks, etc.) are treated as
     * distinct listings — without this an admin holding ten Tacz pistols with
     * different attachments would only ever populate one slot. When {@code nbtTag}
     * is {@code null} we fall back to itemId-only matching to keep legacy behaviour
     * for vanilla stackables.
     *
     * @return existing index if a matching listing is found, the new index if one
     *         was appended, or {@code -1} when the per-block cap is hit.
     */
    public int addOrSelectListing(String itemId,  DataComponentPatch nbtPatch) {
        if (itemId == null || itemId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < listings.size(); i++) {
            Listing existing = listings.get(i);
            if (!itemId.equals(existing.itemId())) continue;
            // Treat (itemId, nbt) as the listing identity. Two Tacz guns share an
            // itemId but their gun-id lives in NBT — collapsing them by itemId
            // alone breaks multi-listing for any modded item that encodes
            // variants in NBT, and silently overwrites the previous variant's
            // saved tag if we then re-stamp setNbtTag from the caller.
            if ((nbtPatch == null || nbtPatch.isEmpty()) && existing.nbtPatch().isEmpty()) return i;
            if (nbtPatch != null && nbtPatch.equals(existing.nbtPatch())) {
                return i;
            }
        }
        if (listings.size() >= effectiveMaxListings()) {
            return -1;
        }
        Listing fresh = new Listing(itemId);
        if (nbtPatch != null) {
            fresh.setNbtPatch(nbtPatch);
            // Auto-enable NBT-awareness whenever the listing was registered with
            // a non-null tag. Without this the manage-screen icon falls through
            // to the bare-itemId renderer (missing texture for Tacz guns and
            // similar NBT-keyed items), and minted output stacks ship without
            // their original NBT — i.e. a Tacz gun comes out of the shop as a
            // textureless ghost.
            fresh.setNbtAware(true);
        }
        listings.add(fresh);
        setChanged();
        syncTopItemsToClients();
        return listings.size() - 1;
    }

    public boolean removeListing(int index) {
        if (index < 0 || index >= listings.size()) {
            return false;
        }
        listings.remove(index);
        // Adjust visibleListingIndex after removal
        if (visibleListingIndex >= listings.size()) {
            visibleListingIndex = Math.max(0, listings.size() - 1);
        }
        setChanged();
        syncTopItemsToClients();
        return true;
    }

    public String getListedItemId() {
        Listing listing = getListing(0);
        return listing == null ? "" : listing.itemId();
    }

    public void setListedItemId(String listedItemId) {
        listings.clear();
        if (listedItemId != null && !listedItemId.isBlank()) {
            listings.add(new Listing(listedItemId));
        }
        setChanged();
        syncTopItemsToClients();
    }

    public TradeMode getTradeMode() {
        Listing listing = getListing(0);
        return listing == null ? TradeMode.MONEY : listing.tradeMode();
    }

    public void setTradeMode(TradeMode tradeMode) {
        Listing listing = getListing(0);
        if (listing != null) {
            listing.setTradeMode(tradeMode);
            setChanged();
        }
    }

    public long getMoneyPriceMinor() {
        Listing listing = getListing(0);
        return listing == null ? 100L : listing.moneyPriceMinor();
    }

    public void setMoneyPriceMinor(long moneyPriceMinor) {
        Listing listing = getListing(0);
        if (listing != null) {
            listing.setMoneyPriceMinor(moneyPriceMinor);
            setChanged();
        }
    }

    public String getBarterItemId() {
        Listing listing = getListing(0);
        return listing == null ? "" : listing.barterItemId();
    }

    public void setBarterItemId(String barterItemId) {
        Listing listing = getListing(0);
        if (listing != null) {
            listing.setBarterItemId(barterItemId);
            setChanged();
        }
    }

    public int getBarterItemCount() {
        Listing listing = getListing(0);
        return listing == null ? 1 : listing.barterItemCount();
    }

    public void setBarterItemCount(int barterItemCount) {
        Listing listing = getListing(0);
        if (listing != null) {
            listing.setBarterItemCount(barterItemCount);
            setChanged();
        }
    }

    public BlockPos getLinkedStoragePos() {
        return linkedStoragePos;
    }

    public void setLinkedStoragePos(BlockPos linkedStoragePos) {
        this.linkedStoragePos = linkedStoragePos;
        setChanged();
    }

    public void clearListing() {
        listings.clear();
        setChanged();
    }

    /**
     * Serializes only the owner-configurable portion of this shop (name, description, flags,
     * listings incl. promos + bundle outputs) — <em>not</em> ownership, position, or
     * linked-storage block positions. Used by the Copy Config / Paste Config buttons so
     * operators can stamp the same listing catalogue onto a new shop block in a different
     * location without pulling along the linked chest coordinates (which would be invalid
     * at the destination) or the owner UUID (which must stay whoever placed the block).
     */
    public CompoundTag exportConfigSnapshot() {
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag tag = new CompoundTag();
        tag.putString("ShopName", shopName);
        tag.putString("Description", description);
        tag.putBoolean("SingleItemMode", singleItemMode);
        tag.putInt("VisibleListingIndex", visibleListingIndex);
        tag.putBoolean("BarterStorageSame", barterStorageSame);
        tag.putInt("MaxListings", maxListings);
        tag.putBoolean("AdminShopMode", adminShopMode);
        ListTag listingTags = new ListTag();
        for (Listing listing : listings) {
            listingTags.add(listing.save(registries));
        }
        tag.put("Listings", listingTags);
        return tag;
    }

    /**
     * Applies a snapshot produced by {@link #exportConfigSnapshot()} to this shop.
     * Intentionally does not touch owner UUID, block position, or linked storage
     * positions — see {@link #exportConfigSnapshot()} for rationale.
     */
    public void applyConfigSnapshot(CompoundTag tag) {
        if (tag == null) return;
        HolderLookup.Provider registries = level.registryAccess();
        if (tag.contains("ShopName")) shopName = tag.getString("ShopName");
        if (tag.contains("Description")) description = tag.getString("Description");
        if (tag.contains("SingleItemMode")) singleItemMode = tag.getBoolean("SingleItemMode");
        if (tag.contains("VisibleListingIndex")) visibleListingIndex = tag.getInt("VisibleListingIndex");
        if (tag.contains("BarterStorageSame")) barterStorageSame = tag.getBoolean("BarterStorageSame");
        if (tag.contains("MaxListings")) maxListings = tag.getInt("MaxListings");
        if (tag.contains("AdminShopMode")) {
            // Pasting onto a non-creative-eligible shop forces admin mode off.
            adminShopMode = placedByCreative && tag.getBoolean("AdminShopMode");
        }
        if (tag.contains("Listings", Tag.TAG_LIST)) {
            listings.clear();
            ListTag listingTags = tag.getList("Listings", Tag.TAG_COMPOUND);
            for (Tag lt : listingTags) {
                listings.add(Listing.load((CompoundTag) lt, registries));
            }
            if (visibleListingIndex >= listings.size()) {
                visibleListingIndex = listings.isEmpty() ? -1 : 0;
            }
        }
        setChanged();
        syncTopItemsToClients();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        if (!ownerName.isEmpty()) {
            tag.putString("OwnerName", ownerName);
        }
        tag.putString("ShopId", shopId);
        tag.putString("ShopName", shopName);
        tag.putString("Description", description);
        tag.putBoolean("SingleItemMode", singleItemMode);
        tag.putInt("VisibleListingIndex", visibleListingIndex);
        tag.putBoolean("BarterStorageSame", barterStorageSame);
        tag.putInt("MaxListings", maxListings);
        tag.putFloat("DisplayYOffset", displayYOffset);
        tag.putFloat("DisplayScale", displayScale);
        tag.putBoolean("NameplateHidden", nameplateHidden);
        tag.putBoolean("PlacedByCreative", placedByCreative);
        tag.putBoolean("AdminShopMode", adminShopMode);
        ListTag listingTags = new ListTag();
        for (Listing listing : listings) {
            listingTags.add(listing.save(registries));
        }
        tag.put("Listings", listingTags);
        if (linkedStoragePos != null) {
            tag.putLong("LinkedStoragePos", linkedStoragePos.asLong());
        }
        if (barterStoragePos != null) {
            tag.putLong("BarterStoragePos", barterStoragePos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerUuid = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        ownerName = tag.getString("OwnerName");
        shopId = tag.getString("ShopId");
        if (shopId.isBlank()) {
            shopId = "default";
        }
        shopName = tag.getString("ShopName");
        description = tag.getString("Description");
        singleItemMode = tag.getBoolean("SingleItemMode");
        visibleListingIndex = tag.contains("VisibleListingIndex") ? tag.getInt("VisibleListingIndex") : -1;
        barterStorageSame = tag.contains("BarterStorageSame") ? tag.getBoolean("BarterStorageSame") : true;
        maxListings = tag.contains("MaxListings") ? tag.getInt("MaxListings") : DEFAULT_MAX_LISTINGS;
        displayYOffset = clamp(tag.contains("DisplayYOffset") ? tag.getFloat("DisplayYOffset") : 0.0F,
                DISPLAY_Y_OFFSET_MIN, DISPLAY_Y_OFFSET_MAX);
        displayScale = clamp(tag.contains("DisplayScale") ? tag.getFloat("DisplayScale") : 1.0F,
                DISPLAY_SCALE_MIN, DISPLAY_SCALE_MAX);
        nameplateHidden = tag.getBoolean("NameplateHidden");
        placedByCreative = tag.getBoolean("PlacedByCreative");
        adminShopMode = placedByCreative && tag.getBoolean("AdminShopMode");
        listings.clear();
        if (tag.contains("Listings", Tag.TAG_LIST)) {
            ListTag listingTags = tag.getList("Listings", Tag.TAG_COMPOUND);
            for (Tag listingTag : listingTags) {
                listings.add(Listing.load((CompoundTag) listingTag, registries));
            }
        } else {
            String listedItemId = tag.getString("ListedItemId");
            if (!listedItemId.isBlank()) {
                Listing listing = new Listing(listedItemId);
                String modeName = tag.getString("TradeMode");
                try {
                    listing.setTradeMode(TradeMode.valueOf(modeName.isBlank() ? TradeMode.MONEY.name() : modeName));
                } catch (IllegalArgumentException ignored) {
                    listing.setTradeMode(TradeMode.MONEY);
                }
                listing.setMoneyPriceMinor(Math.max(1L, tag.getLong("MoneyPriceMinor")));
                listing.setBarterItemId(tag.getString("BarterItemId"));
                listing.setBarterItemCount(Math.max(1, tag.getInt("BarterItemCount")));
                listings.add(listing);
            }
        }
        linkedStoragePos = tag.contains("LinkedStoragePos") ? BlockPos.of(tag.getLong("LinkedStoragePos")) : null;
        barterStoragePos = tag.contains("BarterStoragePos") ? BlockPos.of(tag.getLong("BarterStoragePos")) : null;
    }

    /** Per-listing trade direction: does the shop sell, buy, or both? */
    public enum Direction { SELL, BUY, BOTH }

    public static final class Listing {
        private String itemId = "";
        private TradeMode tradeMode = TradeMode.MONEY;
        private long moneyPriceMinor = 100L;
        private String barterItemId = "";
        private int barterItemCount = 1;
        private String department = ""; // Custom department classification
        private String listingDescription = ""; // Per-listing description (set via /desc)
        private int baseQuantity = 0; // Item 32 fix: Default 0 prevents sales during listing setup
        private boolean nbtAware = false;
        private DataComponentPatch nbtPatch = DataComponentPatch.EMPTY;
        // Buyback / sell-to-shop fields (default to SELL for legacy compat).
        private Direction direction = Direction.SELL;
        private long buybackPriceMinor = 0L;
        private int buybackCap = 0;
        private int buybackBought = 0;
        // NBT-strict barter payment: when the owner registers a barter item with a non-null
        // tag (e.g. an empty tank, a specific enchanted chestplate), the listing captures
        // that tag and the buy/barter pipeline enforces strict equality against the buyer's
        // inventory. Without this, a player could pay with a partially-full tank or an
        // enchanted chestplate and the server would accept it as if it were the plain variant.
        private boolean barterNbtAware = false;
        private DataComponentPatch barterNbtPatch = DataComponentPatch.EMPTY;
        private final Promo promo = new Promo();
        private final List<BundleEntry> bundleOutputs = new ArrayList<>(); // Item 11

        public Listing() {}

        public Listing(String itemId) {
            this.itemId = itemId == null ? "" : itemId;
        }

        public String itemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId == null ? "" : itemId; }
        public TradeMode tradeMode() { return tradeMode; }
        public void setTradeMode(TradeMode tradeMode) { this.tradeMode = tradeMode == null ? TradeMode.MONEY : tradeMode; }
        public long moneyPriceMinor() { return moneyPriceMinor; }
        public void setMoneyPriceMinor(long moneyPriceMinor) { this.moneyPriceMinor = Math.max(1L, moneyPriceMinor); }
        public String barterItemId() { return barterItemId; }
        public void setBarterItemId(String barterItemId) { this.barterItemId = barterItemId == null ? "" : barterItemId; }
        public int barterItemCount() { return barterItemCount; }
        public void setBarterItemCount(int barterItemCount) { this.barterItemCount = Math.max(1, barterItemCount); }
        public boolean barterNbtAware() { return barterNbtAware; }
        public void setBarterNbtAware(boolean barterNbtAware) { this.barterNbtAware = barterNbtAware; }
        public DataComponentPatch barterNbtPatch() { return barterNbtPatch; }
        public void setBarterNbtPatch(DataComponentPatch p) { this.barterNbtPatch = p == null ? DataComponentPatch.EMPTY : p; }
        public String department() { return department; }
        public void setDepartment(String department) { this.department = department == null ? "" : department.trim(); }
        public String listingDescription() { return listingDescription; }
        public void setListingDescription(String desc) { this.listingDescription = desc == null ? "" : desc.trim(); }

        /** Item 32: Number of items delivered per purchase unit. 0 = not configured (cannot purchase). */
        public int baseQuantity() { return baseQuantity; }
        public void setBaseQuantity(int baseQuantity) { this.baseQuantity = Math.max(0, baseQuantity); }

        /**
         * Item 24: Returns the effective barter cost per unit after promo discount.
         * For PERCENTAGE promos, reduces barter cost. Rounds UP so buyer never pays less than 1.
         * E.g., 50% off 64 flint = 32 flint. 50% off 3 = ceil(1.5) = 2.
         */
        public int effectiveBarterItemCount() {
            if (!promo.active()) return barterItemCount;
            if ("PERCENTAGE".equals(promo.promoType()) || "FLASH".equals(promo.promoType())) {
                double discount = Math.min(100.0D, promo.promoValue()) / 100.0D;
                // LGB#8: Allow 0 for 100% discount (free barter)
                return Math.max(0, (int) Math.ceil(barterItemCount * (1.0D - discount)));
            }
            if ("FLAT".equals(promo.promoType())) {
                // Flat discount: reduce barter count by promoValue; LGB#8: allow 0
                return Math.max(0, barterItemCount - (int) promo.promoValue());
            }
            return barterItemCount;
        }

        /**
         * Item 24: Calculates total barter items needed for a quantity, applying promo.
         */
        public int effectiveBarterTotal(int quantity) {
            try {
                return Math.multiplyExact(effectiveBarterItemCount(), quantity);
            } catch (ArithmeticException exception) {
                return -1;
            }
        }

        public Promo promo() { return promo; }
        public boolean nbtAware() { return nbtAware; }
        public void setNbtAware(boolean nbtAware) { this.nbtAware = nbtAware; }
        public DataComponentPatch nbtPatch() { return nbtPatch; }
        public void setNbtPatch(DataComponentPatch p) { this.nbtPatch = p == null ? DataComponentPatch.EMPTY : p; }

        /** Item 11: Bundle output entries (multiple items per listing). */
        public List<BundleEntry> bundleOutputs() {
            return Collections.unmodifiableList(bundleOutputs);
        }

        public void addBundleOutput(String itemId, int count,  DataComponentPatch nbtPatch) {
            if (bundleOutputs.size() >= MAX_BUNDLE_OUTPUTS) return;
            bundleOutputs.add(new BundleEntry(itemId, Math.max(1, count), nbtPatch));
        }

        public boolean removeBundleOutput(int index) {
            if (index < 0 || index >= bundleOutputs.size()) return false;
            bundleOutputs.remove(index);
            return true;
        }

        public long effectiveUnitPriceMinor() {
            return promo.active() ? promo.applyUnitPrice(moneyPriceMinor) : moneyPriceMinor;
        }

        public long calculatePrice(int quantity) {
            if (quantity <= 0) return 0L;
            if (promo.active()) return promo.calculateTotal(moneyPriceMinor, quantity);
            try {
                return Math.multiplyExact(moneyPriceMinor, quantity);
            } catch (ArithmeticException exception) {
                return -1L;
            }
        }

        // ── Buyback / direction accessors ───────────────────────────────────
        public Direction direction() { return direction == null ? Direction.SELL : direction; }
        public void setDirection(Direction direction) { this.direction = direction == null ? Direction.SELL : direction; }
        public long buybackPriceMinor() { return buybackPriceMinor; }
        public void setBuybackPriceMinor(long buybackPriceMinor) { this.buybackPriceMinor = Math.max(0L, buybackPriceMinor); }
        public int buybackCap() { return buybackCap; }
        public void setBuybackCap(int buybackCap) { this.buybackCap = Math.max(0, buybackCap); }
        public int buybackBought() { return buybackBought; }
        public void setBuybackBought(int buybackBought) { this.buybackBought = Math.max(0, buybackBought); }

        public boolean allowsSell() { return direction() == Direction.SELL || direction() == Direction.BOTH; }
        public boolean allowsBuy()  { return direction() == Direction.BUY  || direction() == Direction.BOTH; }
        public int buybackRemaining() {
            return buybackCap == 0 ? Integer.MAX_VALUE : Math.max(0, buybackCap - buybackBought);
        }
        public long effectiveBuybackUnitPriceMinor() { return buybackPriceMinor; }
        public long calculateBuybackTotal(int qty) {
            try {
                return Math.multiplyExact(effectiveBuybackUnitPriceMinor(), Math.max(0, qty));
            } catch (ArithmeticException exception) {
                return -1L;
            }
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("ItemId", itemId);
            tag.putString("TradeMode", tradeMode.name());
            tag.putLong("MoneyPriceMinor", moneyPriceMinor);
            tag.putString("BarterItemId", barterItemId);
            tag.putInt("BarterItemCount", barterItemCount);
            tag.putString("Department", department);
            tag.putString("ListingDescription", listingDescription);
            tag.putInt("BaseQuantity", baseQuantity);
            tag.putBoolean("NbtAware", nbtAware);
            if (nbtPatch != null && !nbtPatch.isEmpty()) tag.put("NbtPatch", NbtMatchUtil.patchToTag(registries, nbtPatch));
            tag.putBoolean("BarterNbtAware", barterNbtAware);
            if (barterNbtPatch != null && !barterNbtPatch.isEmpty()) tag.put("BarterNbtPatch", NbtMatchUtil.patchToTag(registries, barterNbtPatch));
            tag.put("Promo", promo.save());
            // Buyback / direction
            tag.putString("Direction", direction().name());
            tag.putLong("BuybackPriceMinor", buybackPriceMinor);
            tag.putInt("BuybackCap", buybackCap);
            tag.putInt("BuybackBought", buybackBought);
            // Item 11: Save bundle outputs
            if (!bundleOutputs.isEmpty()) {
                ListTag bundleTag = new ListTag();
                for (BundleEntry entry : bundleOutputs) {
                    bundleTag.add(entry.save(registries));
                }
                tag.put("BundleOutputs", bundleTag);
            }
            return tag;
        }

        private static Listing load(CompoundTag tag, HolderLookup.Provider registries) {
            Listing listing = new Listing(tag.getString("ItemId"));
            try {
                listing.setTradeMode(TradeMode.valueOf(tag.getString("TradeMode")));
            } catch (IllegalArgumentException ignored) {
                listing.setTradeMode(TradeMode.MONEY);
            }
            listing.setMoneyPriceMinor(tag.getLong("MoneyPriceMinor"));
            listing.setBarterItemId(tag.getString("BarterItemId"));
            listing.setBarterItemCount(tag.getInt("BarterItemCount"));
            listing.setDepartment(tag.getString("Department"));
            listing.setListingDescription(tag.getString("ListingDescription"));
            listing.setBaseQuantity(tag.contains("BaseQuantity") ? tag.getInt("BaseQuantity") : 0);
            listing.setNbtAware(tag.getBoolean("NbtAware"));
            if (tag.contains("NbtPatch", Tag.TAG_COMPOUND)) listing.setNbtPatch(NbtMatchUtil.tagToPatch(registries, tag.getCompound("NbtPatch")));
            else if (tag.contains("NbtTag", Tag.TAG_COMPOUND)) listing.setNbtPatch(NbtMatchUtil.legacyTagToPatch(registries, ResourceLocation.parse(tag.getString("ItemId")), tag.getCompound("NbtTag")));
            listing.setBarterNbtAware(tag.getBoolean("BarterNbtAware"));
            if (tag.contains("BarterNbtPatch", Tag.TAG_COMPOUND)) listing.setBarterNbtPatch(NbtMatchUtil.tagToPatch(registries, tag.getCompound("BarterNbtPatch")));
            else if (tag.contains("BarterNbtTag", Tag.TAG_COMPOUND)) listing.setBarterNbtPatch(NbtMatchUtil.legacyTagToPatch(registries, ResourceLocation.parse(tag.getString("BarterItemId")), tag.getCompound("BarterNbtTag")));
            if (tag.contains("Promo", Tag.TAG_COMPOUND)) {
                listing.promo.load(tag.getCompound("Promo"));
            }
            // Buyback / direction — legacy listings will default to SELL/0/0/0.
            if (tag.contains("Direction")) {
                try { listing.setDirection(Direction.valueOf(tag.getString("Direction"))); }
                catch (IllegalArgumentException ignored) { listing.setDirection(Direction.SELL); }
            }
            if (tag.contains("BuybackPriceMinor")) listing.setBuybackPriceMinor(tag.getLong("BuybackPriceMinor"));
            if (tag.contains("BuybackCap")) listing.setBuybackCap(tag.getInt("BuybackCap"));
            if (tag.contains("BuybackBought")) listing.setBuybackBought(tag.getInt("BuybackBought"));
            // Item 11: Load bundle outputs
            if (tag.contains("BundleOutputs", Tag.TAG_LIST)) {
                ListTag bundleTag = tag.getList("BundleOutputs", Tag.TAG_COMPOUND);
                for (Tag bt : bundleTag) {
                    listing.bundleOutputs.add(BundleEntry.load((CompoundTag) bt, registries));
                }
            }
            return listing;
        }
    }

    public static final class Promo {
        private String promoType = "";
        private double promoValue = 0.0D;
        private int buyX = 0;
        private int buyY = 0;
        private long startEpochSeconds = 0L;
        private long endEpochSeconds = 0L;
        private boolean flash = false;

        public String promoType() { return promoType; }
        public double promoValue() { return promoValue; }
        public int buyX() { return buyX; }
        public int buyY() { return buyY; }
        public long startEpochSeconds() { return startEpochSeconds; }
        public long endEpochSeconds() { return endEpochSeconds; }
        public boolean flash() { return flash; }
        public boolean configured() { return promoType != null && !promoType.isBlank(); }
        public boolean active() {
            if (!configured()) return false;
            long now = System.currentTimeMillis() / 1000L;
            return now >= startEpochSeconds && (endEpochSeconds <= 0L || now <= endEpochSeconds);
        }
        public long applyUnitPrice(long basePrice) {
            return switch (promoType) {
                // LGB#8: Allow 0 for 100% discount; cap at 100%
                case "PERCENTAGE", "FLASH" -> Math.max(0L, Math.round(basePrice * (1.0D - Math.min(100.0D, promoValue) / 100.0D)));
                case "FLAT" -> {
                    // promoValue is in major units (e.g. 10.0 = $10.00); convert to minor
                    long flatMinor = Math.round(promoValue * Math.pow(10, com.enviouse.futureshopsp.Config.economyCurrencyDecimals));
                    yield Math.max(0L, basePrice - flatMinor);
                }
                default -> basePrice;
            };
        }
        public long calculateTotal(long basePrice, int quantity) {
            if (quantity <= 0) return 0L;
            if ("BUY_X_GET_Y".equals(promoType) && buyX > 0 && buyY > 0) {
                int groupSize = buyX + buyY;
                int fullGroups = quantity / groupSize;
                int remainder = quantity % groupSize;
                int payable = fullGroups * buyX + Math.min(remainder, buyX);
                try {
                    return Math.multiplyExact(basePrice, payable);
                } catch (ArithmeticException exception) {
                    return -1L;
                }
            }
            try {
                return Math.multiplyExact(applyUnitPrice(basePrice), quantity);
            } catch (ArithmeticException exception) {
                return -1L;
            }
        }
        public void configure(String promoType, double promoValue, int buyX, int buyY, long startEpochSeconds, long endEpochSeconds, boolean flash) {
            this.promoType = promoType == null ? "" : promoType;
            // LGB#8: Cap percentage at 100%
            double cappedValue = Double.isFinite(promoValue)
                    ? Math.max(0.0D, promoValue) : 0.0D;
            if ("PERCENTAGE".equals(this.promoType) || "FLASH".equals(this.promoType)) {
                cappedValue = Math.min(100.0D, cappedValue);
            }
            this.promoValue = cappedValue;
            this.buyX = Math.max(0, buyX);
            this.buyY = Math.max(0, buyY);
            this.startEpochSeconds = Math.max(0L, startEpochSeconds);
            this.endEpochSeconds = Math.max(0L, endEpochSeconds);
            this.flash = flash;
        }
        public void clear() { configure("", 0.0D, 0, 0, 0L, 0L, false); }
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", promoType);
            tag.putDouble("Value", promoValue);
            tag.putInt("BuyX", buyX);
            tag.putInt("BuyY", buyY);
            tag.putLong("Start", startEpochSeconds);
            tag.putLong("End", endEpochSeconds);
            tag.putBoolean("Flash", flash);
            return tag;
        }
        private void load(CompoundTag tag) {
            configure(tag.getString("Type"), tag.getDouble("Value"), tag.getInt("BuyX"), tag.getInt("BuyY"),
                    tag.getLong("Start"), tag.getLong("End"), tag.getBoolean("Flash"));
        }
    }

    // ---- GeckoLib (animated block rendering) -------------------------------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, this::idleController));
    }

    private <E extends ShopBlockEntity> PlayState idleController(AnimationState<E> state) {
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // ---- Client sync for the top-of-block spinning listing preview ---------
    // The renderer only needs the ordered list of listing item ids, not the
    // rest of the shop state; we ship a compact tag (just "TopItems": [ids])
    // instead of the full saveAdditional payload which would needlessly
    // expose prices/barter/promos on the network every chunk load.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();
        for (Listing l : listings) {
            if (l != null && l.itemId() != null && !l.itemId().isBlank()) {
                CompoundTag e = new CompoundTag();
                e.putString("Id", l.itemId());
                items.add(e);
            }
        }
        tag.put("TopItems", items);
        tag.putString("ShopName", shopName);
        tag.putString("OwnerName", ownerName);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        tag.putFloat("DisplayYOffset", displayYOffset);
        tag.putFloat("DisplayScale", displayScale);
        tag.putBoolean("NameplateHidden", nameplateHidden);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientTopItemIds.clear();
        if (tag.contains("TopItems", Tag.TAG_LIST)) {
            ListTag items = tag.getList("TopItems", Tag.TAG_COMPOUND);
            for (Tag t : items) {
                if (t instanceof CompoundTag ct) {
                    String id = ct.getString("Id");
                    if (!id.isBlank()) clientTopItemIds.add(id);
                }
            }
        }
        if (tag.contains("ShopName")) {
            this.shopName = tag.getString("ShopName");
        }
        if (tag.contains("OwnerName")) {
            this.ownerName = tag.getString("OwnerName");
        }
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUuid = tag.getUUID("OwnerUUID");
        }
        if (tag.contains("DisplayYOffset")) {
            this.displayYOffset = clamp(tag.getFloat("DisplayYOffset"),
                    DISPLAY_Y_OFFSET_MIN, DISPLAY_Y_OFFSET_MAX);
        }
        if (tag.contains("DisplayScale")) {
            this.displayScale = clamp(tag.getFloat("DisplayScale"),
                    DISPLAY_SCALE_MIN, DISPLAY_SCALE_MAX);
        }
        if (tag.contains("NameplateHidden")) {
            this.nameplateHidden = tag.getBoolean("NameplateHidden");
        }
    }

    /**
     * Client-side display label for the block-top nameplate. Falls back to the
     * owner username if the shop was never custom-named.
     */
    public String getDisplayLabel() {
        if (shopName != null && !shopName.isBlank()) {
            return shopName;
        }
        if (ownerName != null && !ownerName.isBlank()) {
            return ownerName;
        }
        return "";
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag(), registries);
    }

    /** Client-visible list of listing item ids, in order. Empty on servers. */
    public List<String> getClientTopItemIds() {
        return Collections.unmodifiableList(clientTopItemIds);
    }

    /** Call on the server whenever listings change to push a fresh update packet to nearby clients. */
    public void syncTopItemsToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private final List<String> clientTopItemIds = new ArrayList<>();
}
