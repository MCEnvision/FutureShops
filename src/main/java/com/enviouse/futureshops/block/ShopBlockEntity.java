package com.enviouse.futureshops.block;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;
import com.enviouse.futureshops.init.ModBlockEntities;
import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.shop.PlayerShopRegistrySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ShopBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final String REGISTRY_SHOP_ID_KEY = "RegistryShopUUID";
    private static final String REGISTRY_REVISION_KEY = "RegistryIdentityRevision";
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** Default listing cap when maxListings is not overridden (-1 = unlimited). */
    public static final int DEFAULT_MAX_LISTINGS = -1;
    public static final int MAX_PERSISTED_LISTINGS = 4_096;
    public static final int MAX_BUNDLE_OUTPUTS = 36;
    public static final int MAX_ITEM_ID_LENGTH = 256;
    public static final int MAX_LISTING_NBT_CHARACTERS = 262_144;
    /**
     * Hard cap on the number of storage containers a single shop may link for its
     * sale-item stock. Stock is summed across all links and a buy pulls across them
     * (all-or-nothing). Barter storage is separate and always single.
     */
    public static final int MAX_LINKED_STORAGES = 6;

    public enum TradeMode {
        MONEY,
        BARTER,
        BOTH,
        MONEY_AND_BARTER
    }

    /**
     * Item 11: A single entry in a bundle listing — represents one output item and its quantity.
     */
    public record BundleEntry(String itemId, int count, @Nullable CompoundTag nbtTag) {
        public BundleEntry {
            if (itemId == null || itemId.isBlank()
                    || itemId.length() > MAX_ITEM_ID_LENGTH) {
                throw new IllegalArgumentException("Bundle item identifier is invalid");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Bundle item count is invalid");
            }
            validateListingNbt(nbtTag);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("ItemId", itemId);
            tag.putInt("Count", count);
            if (nbtTag != null) {
                tag.put("NbtTag", nbtTag.copy());
            }
            return tag;
        }

        public static BundleEntry load(CompoundTag tag) {
            String id = tag.getString("ItemId");
            int count = tag.getInt("Count");
            if (tag.contains("NbtTag") && !tag.contains("NbtTag", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Bundle item nbt type is invalid");
            }
            CompoundTag nbt = tag.contains("NbtTag", Tag.TAG_COMPOUND)
                    ? tag.getCompound("NbtTag") : null;
            return new BundleEntry(id, count, nbt);
        }
    }

    private UUID ownerUuid;
    private UUID registryShopId;
    private long registryIdentityRevision;
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
    private boolean offerPersistenceMigrationPending;
    /**
     * Ordered, deduped list of linked stock-storage positions (cap
     * {@link #MAX_LINKED_STORAGES}). The composite of these is the shop's stock: counts
     * sum across all, buys pull across all (all-or-nothing), sells spill across in order.
     * Persisted as a long[] under NBT key {@code "LinkedStorages"}; an old single-link
     * {@code "LinkedStoragePos"} is migrated into a 1-element list on load.
     */
    private final List<BlockPos> linkedStoragePositions = new ArrayList<>();
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

    /**
     * How the floating icon on top of the block is chosen (community request / redesign
     * "Floating shop icon modes"). CYCLE keeps the legacy behaviour (rotate through the
     * listing items); OWNER_HEAD shows the owner's player head; CUSTOM_ITEM shows one
     * fixed item ({@link #floatingIconItem}). The mode is resolved into concrete display
     * stacks client-side in {@link #handleUpdateTag}, so the renderer stays mode-agnostic.
     */
    public enum FloatingIconMode {
        CYCLE, OWNER_HEAD, CUSTOM_ITEM;

        public static FloatingIconMode byName(String name) {
            if (name != null) {
                for (FloatingIconMode m : values()) {
                    if (m.name().equals(name)) return m;
                }
            }
            return CYCLE;
        }
    }

    private FloatingIconMode floatingIconMode = FloatingIconMode.CYCLE;
    /** Item id shown when {@link #floatingIconMode} is CUSTOM_ITEM (blank falls back to CYCLE). */
    private String floatingIconItem = "";

    public ShopBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SHOP_BLOCK_ENTITY.get(), pos, blockState);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Nullable
    public UUID getRegistryShopId() {
        return registryShopId;
    }

    public long getRegistryIdentityRevision() {
        return registryIdentityRevision;
    }

    public void setRegistryIdentity(UUID shopId, long revision) {
        validateRegistryIdentity(shopId, revision);
        if (shopId.equals(registryShopId)
                && revision == registryIdentityRevision) {
            return;
        }
        registryShopId = shopId;
        registryIdentityRevision = revision;
        setChanged();
    }

    public void reconcileRegistryIdentity() {
        if (!(level instanceof ServerLevel serverLevel) || ownerUuid == null) {
            return;
        }
        PlayerShopRegistrySavedData.ShopRef identity =
                PlayerShopRegistrySavedData.get(serverLevel.getServer()).reconcile(
                        ownerUuid, Optional.ofNullable(registryShopId),
                        registryIdentityRevision, serverLevel.dimension().location(),
                        worldPosition.asLong());
        setRegistryIdentity(identity.shopId(), identity.revision());
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

    @Override
    public void onLoad() {
        super.onLoad();
        reconcileRegistryIdentity();
        if (offerPersistenceMigrationPending) {
            offerPersistenceMigrationPending = false;
            setChanged();
        }
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

    public FloatingIconMode getFloatingIconMode() {
        return floatingIconMode;
    }

    public void setFloatingIconMode(FloatingIconMode mode) {
        this.floatingIconMode = mode == null ? FloatingIconMode.CYCLE : mode;
        setChanged();
    }

    public String getFloatingIconItem() {
        return floatingIconItem;
    }

    public void setFloatingIconItem(String itemId) {
        this.floatingIconItem = itemId == null ? "" : itemId.trim();
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
        this.maxListings = maxListings < 0
                ? DEFAULT_MAX_LISTINGS
                : Math.min(maxListings, MAX_PERSISTED_LISTINGS);
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
    public int addOrSelectListing(String itemId, @Nullable CompoundTag nbtTag) {
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
            if (nbtTag == null && existing.nbtTag() == null) return i;
            if (nbtTag != null && existing.nbtTag() != null
                    && nbtTag.equals(existing.nbtTag())) {
                return i;
            }
        }
        if (listings.size() >= effectiveMaxListings()) {
            return -1;
        }
        Listing fresh = new Listing(itemId);
        if (nbtTag != null) {
            fresh.setNbtTag(nbtTag.copy());
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

    /** Unmodifiable, ordered view of every linked stock-storage position. */
    public List<BlockPos> getLinkedStoragePositions() {
        return Collections.unmodifiableList(linkedStoragePositions);
    }

    /**
     * Back-compat accessor: the FIRST (primary) linked storage, or {@code null} when
     * none are linked. Kept so callers that only need "is anything linked" or the
     * primary container still compile — prefer {@link #hasLinkedStorage()} for the
     * former and {@link #getLinkedStoragePositions()} for the full set.
     */
    @Nullable
    public BlockPos getLinkedStoragePos() {
        return linkedStoragePositions.isEmpty() ? null : linkedStoragePositions.get(0);
    }

    /** True when the shop has at least one linked stock storage. */
    public boolean hasLinkedStorage() {
        return !linkedStoragePositions.isEmpty();
    }

    /**
     * Adds a stock-storage link. Dedupes (no-op if already linked) and respects the
     * {@link #MAX_LINKED_STORAGES} cap.
     *
     * @return {@code true} if the position was appended, {@code false} if it was null,
     *         already linked, or the cap was reached.
     */
    public boolean addLinkedStorage(BlockPos pos) {
        if (pos == null) return false;
        BlockPos immutable = pos.immutable();
        if (linkedStoragePositions.contains(immutable)) return false;
        if (linkedStoragePositions.size() >= MAX_LINKED_STORAGES) return false;
        linkedStoragePositions.add(immutable);
        setChanged();
        return true;
    }

    /** Removes a single linked storage. @return true if it was present and removed. */
    public boolean removeLinkedStorage(BlockPos pos) {
        if (pos == null) return false;
        boolean removed = linkedStoragePositions.remove(pos.immutable());
        if (removed) setChanged();
        return removed;
    }

    /** Clears every linked stock storage. */
    public void clearLinkedStorages() {
        if (!linkedStoragePositions.isEmpty()) {
            linkedStoragePositions.clear();
            setChanged();
        }
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
        if (listings.size() > MAX_PERSISTED_LISTINGS) {
            throw new IllegalStateException("Shop listing count is invalid");
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("ShopName", shopName);
        tag.putString("Description", description);
        tag.putBoolean("SingleItemMode", singleItemMode);
        tag.putInt("VisibleListingIndex", visibleListingIndex);
        tag.putBoolean("BarterStorageSame", barterStorageSame);
        tag.putInt("MaxListings", maxListings);
        tag.putBoolean("AdminShopMode", adminShopMode);
        tag.putString("FloatingIconMode", floatingIconMode.name());
        tag.putString("FloatingIconItem", floatingIconItem);
        ListTag listingTags = new ListTag();
        for (Listing listing : listings) {
            listingTags.add(listing.save());
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
        if (tag.contains("FloatingIconMode")) floatingIconMode = FloatingIconMode.byName(tag.getString("FloatingIconMode"));
        if (tag.contains("FloatingIconItem")) floatingIconItem = tag.getString("FloatingIconItem");
        if (tag.contains("Listings") && !tag.contains("Listings", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Shop listings have the wrong type");
        }
        if (tag.contains("Listings", Tag.TAG_LIST)) {
            ListTag listingTags = SavedDataMigrations.requireList(
                    tag, "Listings", Tag.TAG_COMPOUND,
                    MAX_PERSISTED_LISTINGS, "Shop listings");
            List<Listing> loadedListings = new ArrayList<>(listingTags.size());
            boolean loadedMigration = false;
            String namespace = listingMigrationNamespace();
            for (int index = 0; index < listingTags.size(); index++) {
                Listing listing = Listing.load(
                        listingTags.getCompound(index),
                        namespace, index);
                loadedMigration |= listing.migratedOfferPersistence();
                loadedListings.add(listing);
            }
            listings.clear();
            listings.addAll(loadedListings);
            offerPersistenceMigrationPending = loadedMigration;
            if (visibleListingIndex >= listings.size()) {
                visibleListingIndex = listings.isEmpty() ? -1 : 0;
            }
        }
        setChanged();
        syncTopItemsToClients();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (listings.size() > MAX_PERSISTED_LISTINGS) {
            throw new IllegalStateException("Shop listing count is invalid");
        }
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        if (registryShopId != null) {
            writeRegistryIdentity(tag, registryShopId, registryIdentityRevision);
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
        tag.putString("FloatingIconMode", floatingIconMode.name());
        tag.putString("FloatingIconItem", floatingIconItem);
        ListTag listingTags = new ListTag();
        for (Listing listing : listings) {
            listingTags.add(listing.save());
        }
        tag.put("Listings", listingTags);
        if (!linkedStoragePositions.isEmpty()) {
            long[] packed = new long[linkedStoragePositions.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = linkedStoragePositions.get(i).asLong();
            }
            tag.putLongArray("LinkedStorages", packed);
        }
        if (barterStoragePos != null) {
            tag.putLong("BarterStoragePos", barterStoragePos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ownerUuid = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        Optional<PersistedRegistryIdentity> registryIdentity = readRegistryIdentity(tag);
        registryShopId = registryIdentity.map(PersistedRegistryIdentity::shopId).orElse(null);
        registryIdentityRevision = registryIdentity
                .map(PersistedRegistryIdentity::revision).orElse(0L);
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
        int persistedMaxListings = tag.contains("MaxListings")
                ? tag.getInt("MaxListings") : DEFAULT_MAX_LISTINGS;
        maxListings = persistedMaxListings < 0
                ? DEFAULT_MAX_LISTINGS
                : Math.min(persistedMaxListings, MAX_PERSISTED_LISTINGS);
        displayYOffset = clamp(tag.contains("DisplayYOffset") ? tag.getFloat("DisplayYOffset") : 0.0F,
                DISPLAY_Y_OFFSET_MIN, DISPLAY_Y_OFFSET_MAX);
        displayScale = clamp(tag.contains("DisplayScale") ? tag.getFloat("DisplayScale") : 1.0F,
                DISPLAY_SCALE_MIN, DISPLAY_SCALE_MAX);
        nameplateHidden = tag.getBoolean("NameplateHidden");
        placedByCreative = tag.getBoolean("PlacedByCreative");
        adminShopMode = placedByCreative && tag.getBoolean("AdminShopMode");
        floatingIconMode = FloatingIconMode.byName(tag.getString("FloatingIconMode"));
        floatingIconItem = tag.getString("FloatingIconItem");
        List<Listing> loadedListings = new ArrayList<>();
        boolean loadedMigration = false;
        if (tag.contains("Listings") && !tag.contains("Listings", Tag.TAG_LIST)) {
            throw new IllegalStateException("Shop listings have the wrong type");
        }
        if (tag.contains("Listings", Tag.TAG_LIST)) {
            ListTag listingTags = SavedDataMigrations.requireList(
                    tag, "Listings", Tag.TAG_COMPOUND,
                    MAX_PERSISTED_LISTINGS, "Shop listings");
            String namespace = listingMigrationNamespace();
            for (int index = 0; index < listingTags.size(); index++) {
                Listing listing = Listing.load(
                        listingTags.getCompound(index),
                        namespace, index);
                loadedMigration |= listing.migratedOfferPersistence();
                loadedListings.add(listing);
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
                listing.listingId = Listing.migratedListingId(
                        listingMigrationNamespace(), 0);
                listing.migrateLegacyOffer();
                loadedMigration = true;
                loadedListings.add(listing);
            }
        }
        listings.clear();
        listings.addAll(loadedListings);
        offerPersistenceMigrationPending = loadedMigration;
        linkedStoragePositions.clear();
        if (tag.contains("LinkedStorages", Tag.TAG_LONG_ARRAY)) {
            for (long packed : tag.getLongArray("LinkedStorages")) {
                BlockPos p = BlockPos.of(packed);
                // Defensive dedupe + cap on load in case the saved data was tampered with.
                if (!linkedStoragePositions.contains(p) && linkedStoragePositions.size() < MAX_LINKED_STORAGES) {
                    linkedStoragePositions.add(p);
                }
            }
        } else if (tag.contains("LinkedStoragePos")) {
            // Legacy single-link migration: existing saved shops keep their one link.
            linkedStoragePositions.add(BlockPos.of(tag.getLong("LinkedStoragePos")));
        }
        barterStoragePos = tag.contains("BarterStoragePos") ? BlockPos.of(tag.getLong("BarterStoragePos")) : null;
    }

    private String listingMigrationNamespace() {
        if (registryShopId != null) {
            return registryShopId.toString();
        }
        String owner = ownerUuid == null
                ? ZERO_UUID.toString() : ownerUuid.toString();
        return owner + "." + worldPosition.asLong() + "." + shopId;
    }

    private static void validateListingNbt(@Nullable CompoundTag nbtTag) {
        if (nbtTag != null
                && nbtTag.toString().length() > MAX_LISTING_NBT_CHARACTERS) {
            throw new IllegalArgumentException("Shop listing nbt exceeds its limit");
        }
    }

    static void writeRegistryIdentity(CompoundTag tag, UUID shopId, long revision) {
        Objects.requireNonNull(tag, "tag");
        validateRegistryIdentity(shopId, revision);
        tag.putUUID(REGISTRY_SHOP_ID_KEY, shopId);
        tag.putLong(REGISTRY_REVISION_KEY, revision);
    }

    static Optional<PersistedRegistryIdentity> readRegistryIdentity(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        boolean hasId = tag.contains(REGISTRY_SHOP_ID_KEY);
        boolean hasRevision = tag.contains(REGISTRY_REVISION_KEY);
        if (!hasId && !hasRevision) {
            return Optional.empty();
        }
        if (!tag.hasUUID(REGISTRY_SHOP_ID_KEY)
                || !tag.contains(REGISTRY_REVISION_KEY, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Player shop block identity is malformed");
        }
        return Optional.of(new PersistedRegistryIdentity(
                tag.getUUID(REGISTRY_SHOP_ID_KEY),
                tag.getLong(REGISTRY_REVISION_KEY)));
    }

    private static void validateRegistryIdentity(UUID shopId, long revision) {
        if (shopId == null || ZERO_UUID.equals(shopId) || revision < 0L) {
            throw new IllegalArgumentException("Player shop block identity is invalid");
        }
    }

    static record PersistedRegistryIdentity(UUID shopId, long revision) {
        PersistedRegistryIdentity {
            validateRegistryIdentity(shopId, revision);
        }
    }

    /** Per-listing trade direction: does the shop sell, buy, or both? */
    public enum Direction { SELL, BUY, BOTH }

    public static final class Listing {
        public static final int CURRENT_OFFER_SCHEMA =
                PlayerShopOfferPersistenceCodec.CURRENT_SCHEMA;
        private static final String OFFER_SCHEMA_KEY =
                "OfferSchemaVersion";
        private static final String OFFER_PAYLOAD_KEY =
                "NormalizedOffer";
        private static final String LEGACY_PROJECTION_KEY =
                "LegacyOfferProjection";

        private String listingId =
                "player_listing_" + UUID.randomUUID();
        private int offerSchemaVersion = CURRENT_OFFER_SCHEMA;
        private ServerShopOfferListing normalizedOffer;
        private boolean legacyOfferProjection = true;
        private boolean offerUnavailable;
        private byte[] preservedOfferPayload;
        private boolean migratedOfferPersistence;
        private String itemId = "";
        private TradeMode tradeMode = TradeMode.MONEY;
        private long moneyPriceMinor = 100L;
        private String barterItemId = "";
        private int barterItemCount = 1;
        private String department = ""; // Custom department classification
        private String listingDescription = ""; // Per-listing description (set via /desc)
        private int baseQuantity = 0; // Item 32 fix: Default 0 prevents sales during listing setup
        private boolean nbtAware = false;
        private CompoundTag nbtTag = null;
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
        private CompoundTag barterNbtTag = null;
        // Redesign "Listings inspector" visibility flags. hidden = not shown to visitors and
        // not purchasable by them (owner still sees/manages it). showcase = shown to visitors
        // as a display-only "visit in person" item that the storefront will not auto-sell.
        private boolean hidden = false;
        private boolean showcase = false;
        private final Promo promo = new Promo();
        private final List<BundleEntry> bundleOutputs = new ArrayList<>(); // Item 11

        public Listing() {
        }

        public Listing(String itemId) {
            this.itemId = itemId == null ? "" : itemId;
        }

        public String listingId() { return listingId; }
        public int offerSchemaVersion() { return offerSchemaVersion; }
        public boolean offerUnavailable() { return offerUnavailable; }
        boolean migratedOfferPersistence() {
            return migratedOfferPersistence;
        }
        public Optional<ServerShopOfferListing> normalizedOffer() {
            return offerUnavailable
                    ? Optional.empty()
                    : Optional.ofNullable(normalizedOffer);
        }

        public void setNormalizedOffer(ServerShopOfferListing offer) {
            Objects.requireNonNull(offer, "offer");
            if (!offer.listingId().equals(listingId)
                    || !ServerShopOfferValidator.validate(offer).valid()) {
                throw new IllegalArgumentException(
                        "Player shop normalized offer is invalid");
            }
            PlayerShopOfferPersistenceCodec.encode(offer);
            normalizedOffer = offer;
            offerSchemaVersion = CURRENT_OFFER_SCHEMA;
            legacyOfferProjection = false;
            offerUnavailable = false;
            preservedOfferPayload = null;
        }

        public String itemId() { return itemId; }
        public void setItemId(String itemId) {
            this.itemId = itemId == null ? "" : itemId;
            refreshLegacyOffer();
        }
        public TradeMode tradeMode() { return tradeMode; }
        public void setTradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode == null
                    ? TradeMode.MONEY : tradeMode;
            refreshLegacyOffer();
        }
        public long moneyPriceMinor() { return moneyPriceMinor; }
        public void setMoneyPriceMinor(long moneyPriceMinor) {
            this.moneyPriceMinor = Math.max(1L, moneyPriceMinor);
            refreshLegacyOffer();
        }
        public String barterItemId() { return barterItemId; }
        public void setBarterItemId(String barterItemId) {
            this.barterItemId = barterItemId == null ? "" : barterItemId;
            refreshLegacyOffer();
        }
        public int barterItemCount() { return barterItemCount; }
        public void setBarterItemCount(int barterItemCount) {
            this.barterItemCount = Math.max(1, barterItemCount);
            refreshLegacyOffer();
        }
        public boolean barterNbtAware() { return barterNbtAware; }
        public void setBarterNbtAware(boolean barterNbtAware) {
            this.barterNbtAware = barterNbtAware;
            refreshLegacyOffer();
        }
        public CompoundTag barterNbtTag() { return barterNbtTag; }
        public void setBarterNbtTag(CompoundTag barterNbtTag) {
            validateListingNbt(barterNbtTag);
            this.barterNbtTag = barterNbtTag;
            refreshLegacyOffer();
        }
        public String department() { return department; }
        public void setDepartment(String department) {
            this.department = department == null
                    ? "" : department.trim();
            refreshLegacyOffer();
        }
        public String listingDescription() { return listingDescription; }
        public void setListingDescription(String desc) {
            this.listingDescription = desc == null ? "" : desc.trim();
            refreshLegacyOffer();
        }

        /** Item 32: Number of items delivered per purchase unit. 0 = not configured (cannot purchase). */
        public int baseQuantity() { return baseQuantity; }
        public void setBaseQuantity(int baseQuantity) {
            this.baseQuantity = Math.max(0, baseQuantity);
            refreshLegacyOffer();
        }

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
            return effectiveBarterItemCount() * quantity;
        }

        public Promo promo() { return promo; }
        public boolean nbtAware() { return nbtAware; }
        public void setNbtAware(boolean nbtAware) {
            this.nbtAware = nbtAware;
            refreshLegacyOffer();
        }
        public boolean hidden() { return hidden; }
        public void setHidden(boolean hidden) {
            this.hidden = hidden;
            refreshLegacyOffer();
        }
        public boolean showcase() { return showcase; }
        public void setShowcase(boolean showcase) {
            this.showcase = showcase;
            refreshLegacyOffer();
        }
        public CompoundTag nbtTag() { return nbtTag; }
        public void setNbtTag(CompoundTag nbtTag) {
            validateListingNbt(nbtTag);
            this.nbtTag = nbtTag;
            refreshLegacyOffer();
        }

        /** Item 11: Bundle output entries (multiple items per listing). */
        public List<BundleEntry> bundleOutputs() {
            return Collections.unmodifiableList(bundleOutputs);
        }

        public void addBundleOutput(String itemId, int count, @Nullable CompoundTag nbtTag) {
            if (bundleOutputs.size() >= MAX_BUNDLE_OUTPUTS
                    || itemId == null || itemId.isBlank()
                    || itemId.length() > MAX_ITEM_ID_LENGTH) return;
            bundleOutputs.add(new BundleEntry(itemId, Math.max(1, count), nbtTag));
            refreshLegacyOffer();
        }

        public boolean removeBundleOutput(int index) {
            if (index < 0 || index >= bundleOutputs.size()) return false;
            bundleOutputs.remove(index);
            refreshLegacyOffer();
            return true;
        }

        public long effectiveUnitPriceMinor() {
            return promo.active() ? promo.applyUnitPrice(moneyPriceMinor) : moneyPriceMinor;
        }

        public long calculatePrice(int quantity) {
            return promo.active() ? promo.calculateTotal(moneyPriceMinor, quantity) : moneyPriceMinor * quantity;
        }

        // ── Buyback / direction accessors ───────────────────────────────────
        public Direction direction() { return direction == null ? Direction.SELL : direction; }
        public void setDirection(Direction direction) {
            this.direction = direction == null
                    ? Direction.SELL : direction;
            refreshLegacyOffer();
        }
        public long buybackPriceMinor() { return buybackPriceMinor; }
        public void setBuybackPriceMinor(long buybackPriceMinor) {
            this.buybackPriceMinor = Math.max(0L, buybackPriceMinor);
            refreshLegacyOffer();
        }
        public int buybackCap() { return buybackCap; }
        public void setBuybackCap(int buybackCap) {
            this.buybackCap = Math.max(0, buybackCap);
            refreshLegacyOffer();
        }
        public int buybackBought() { return buybackBought; }
        public void setBuybackBought(int buybackBought) { this.buybackBought = Math.max(0, buybackBought); }
        public long legacyBuybackConsumedBaseline() {
            return legacyOfferProjection ? buybackBought : 0L;
        }

        public boolean allowsSell() {
            return !offerUnavailable
                    && (direction() == Direction.SELL
                    || direction() == Direction.BOTH);
        }
        public boolean allowsBuy() {
            return !offerUnavailable
                    && (direction() == Direction.BUY
                    || direction() == Direction.BOTH);
        }
        public int buybackRemaining() {
            return buybackCap == 0 ? Integer.MAX_VALUE : Math.max(0, buybackCap - buybackBought);
        }
        public long effectiveBuybackUnitPriceMinor() { return buybackPriceMinor; }
        public long calculateBuybackTotal(int qty) { return effectiveBuybackUnitPriceMinor() * Math.max(0, qty); }

        CompoundTag save() {
            if (listingId == null || listingId.isBlank()
                    || listingId.length() > MAX_ITEM_ID_LENGTH
                    || itemId == null || itemId.isBlank()
                    || itemId.length() > MAX_ITEM_ID_LENGTH) {
                throw new IllegalStateException("Shop listing identity is invalid");
            }
            validateListingNbt(nbtTag);
            validateListingNbt(barterNbtTag);
            CompoundTag tag = new CompoundTag();
            tag.putString("ListingId", listingId);
            tag.putInt(OFFER_SCHEMA_KEY, offerSchemaVersion);
            tag.putBoolean(LEGACY_PROJECTION_KEY,
                    legacyOfferProjection);
            if (offerUnavailable) {
                if (preservedOfferPayload != null) {
                    tag.putByteArray(OFFER_PAYLOAD_KEY,
                            preservedOfferPayload.clone());
                }
            } else if (normalizedOffer != null) {
                tag.putByteArray(OFFER_PAYLOAD_KEY,
                        PlayerShopOfferPersistenceCodec.encode(
                                normalizedOffer));
            }
            tag.putString("ItemId", itemId);
            tag.putString("TradeMode", tradeMode.name());
            tag.putLong("MoneyPriceMinor", moneyPriceMinor);
            tag.putString("BarterItemId", barterItemId);
            tag.putInt("BarterItemCount", barterItemCount);
            tag.putString("Department", department);
            tag.putString("ListingDescription", listingDescription);
            tag.putInt("BaseQuantity", baseQuantity);
            tag.putBoolean("NbtAware", nbtAware);
            if (nbtTag != null) {
                tag.put("NbtTag", nbtTag.copy());
            }
            tag.putBoolean("BarterNbtAware", barterNbtAware);
            if (barterNbtTag != null) {
                tag.put("BarterNbtTag", barterNbtTag.copy());
            }
            tag.putBoolean("Hidden", hidden);
            tag.putBoolean("Showcase", showcase);
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
                    bundleTag.add(entry.save());
                }
                tag.put("BundleOutputs", bundleTag);
            }
            return tag;
        }

        static Listing load(
                CompoundTag tag,
                String migrationNamespace,
                int listingOrdinal
        ) {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(migrationNamespace,
                    "migrationNamespace");
            Listing listing = new Listing();
            listing.legacyOfferProjection = false;
            listing.itemId = tag.getString("ItemId");
            if (listing.itemId.isBlank()
                    || listing.itemId.length() > MAX_ITEM_ID_LENGTH) {
                throw new IllegalStateException("Shop listing item identifier is invalid");
            }
            boolean schemaPresent = tag.contains(OFFER_SCHEMA_KEY);
            boolean payloadPresent = tag.contains(OFFER_PAYLOAD_KEY);
            boolean malformedSchema = schemaPresent
                    && !tag.contains(OFFER_SCHEMA_KEY, Tag.TAG_INT);
            int schema = schemaPresent && !malformedSchema
                    ? tag.getInt(OFFER_SCHEMA_KEY) : 1;
            malformedSchema |= schemaPresent && schema < 1;
            malformedSchema |= payloadPresent
                    && (!schemaPresent
                    || schema < CURRENT_OFFER_SCHEMA);
            boolean persistedListingId = schemaPresent
                    && (malformedSchema
                    || schema >= CURRENT_OFFER_SCHEMA);
            listing.listingId = persistedListingId
                    && tag.contains(
                    "ListingId", Tag.TAG_STRING)
                    && !tag.getString("ListingId").isBlank()
                    ? tag.getString("ListingId")
                    : migratedListingId(migrationNamespace,
                    listingOrdinal);
            try {
                listing.tradeMode = TradeMode.valueOf(
                        tag.getString("TradeMode"));
            } catch (IllegalArgumentException ignored) {
                listing.tradeMode = TradeMode.MONEY;
            }
            listing.moneyPriceMinor = Math.max(1L,
                    tag.getLong("MoneyPriceMinor"));
            listing.barterItemId = tag.getString("BarterItemId");
            listing.barterItemCount = Math.max(1,
                    tag.getInt("BarterItemCount"));
            listing.department = tag.getString("Department").trim();
            listing.listingDescription = tag.getString(
                    "ListingDescription").trim();
            listing.baseQuantity = Math.max(0,
                    tag.contains("BaseQuantity")
                            ? tag.getInt("BaseQuantity") : 0);
            listing.nbtAware = tag.getBoolean("NbtAware");
            if (tag.contains("NbtTag", Tag.TAG_COMPOUND)) {
                listing.nbtTag = tag.getCompound("NbtTag");
                validateListingNbt(listing.nbtTag);
            } else if (tag.contains("NbtTag")) {
                throw new IllegalStateException("Shop listing nbt type is invalid");
            }
            listing.barterNbtAware =
                    tag.getBoolean("BarterNbtAware");
            if (tag.contains("BarterNbtTag", Tag.TAG_COMPOUND)) {
                listing.barterNbtTag =
                        tag.getCompound("BarterNbtTag");
                validateListingNbt(listing.barterNbtTag);
            } else if (tag.contains("BarterNbtTag")) {
                throw new IllegalStateException("Shop barter nbt type is invalid");
            }
            listing.hidden = tag.getBoolean("Hidden");
            listing.showcase = tag.getBoolean("Showcase");
            if (tag.contains("Promo", Tag.TAG_COMPOUND)) {
                listing.promo.load(tag.getCompound("Promo"));
            }
            if (tag.contains("Direction")) {
                try {
                    listing.direction = Direction.valueOf(
                            tag.getString("Direction"));
                } catch (IllegalArgumentException ignored) {
                    listing.direction = Direction.SELL;
                }
            }
            if (tag.contains("BuybackPriceMinor")) {
                listing.buybackPriceMinor = Math.max(0L,
                        tag.getLong("BuybackPriceMinor"));
            }
            if (tag.contains("BuybackCap")) {
                listing.buybackCap = Math.max(0,
                        tag.getInt("BuybackCap"));
            }
            if (tag.contains("BuybackBought")) {
                listing.buybackBought = Math.max(0,
                        tag.getInt("BuybackBought"));
            }
            if (tag.contains("BundleOutputs", Tag.TAG_LIST)) {
                ListTag bundleTag = SavedDataMigrations.requireList(
                        tag, "BundleOutputs", Tag.TAG_COMPOUND,
                        MAX_BUNDLE_OUTPUTS, "Shop bundle outputs");
                for (Tag bt : bundleTag) {
                    listing.bundleOutputs.add(BundleEntry.load((CompoundTag) bt));
                }
            }
            byte[] payload = tag.contains(
                    OFFER_PAYLOAD_KEY, Tag.TAG_BYTE_ARRAY)
                    ? tag.getByteArray(OFFER_PAYLOAD_KEY)
                    : null;
            if (malformedSchema) {
                listing.quarantine(CURRENT_OFFER_SCHEMA, payload);
                return listing;
            }
            if (schema > CURRENT_OFFER_SCHEMA) {
                listing.quarantine(schema, payload);
                return listing;
            }
            if (schema < CURRENT_OFFER_SCHEMA) {
                listing.migrateLegacyOffer();
                return listing;
            }
            listing.offerSchemaVersion = CURRENT_OFFER_SCHEMA;
            listing.legacyOfferProjection =
                    tag.getBoolean(LEGACY_PROJECTION_KEY);
            if (payload == null) {
                if (listing.legacyOfferProjection) {
                    listing.refreshLegacyOffer();
                    listing.migratedOfferPersistence =
                            listing.normalizedOffer != null;
                } else {
                    listing.quarantine(schema, null);
                }
                return listing;
            }
            try {
                ServerShopOfferListing offer =
                        PlayerShopOfferPersistenceCodec.decode(
                                schema, payload);
                if (!offer.listingId().equals(
                        listing.listingId)) {
                    throw new IllegalArgumentException(
                            "Player shop offer identity does not match");
                }
                listing.normalizedOffer = offer;
            } catch (RuntimeException exception) {
                listing.quarantine(schema, payload);
            }
            return listing;
        }

        private void migrateLegacyOffer() {
            offerSchemaVersion = CURRENT_OFFER_SCHEMA;
            legacyOfferProjection = true;
            offerUnavailable = false;
            preservedOfferPayload = null;
            migratedOfferPersistence = true;
            refreshLegacyOffer();
        }

        private void refreshLegacyOffer() {
            if (!legacyOfferProjection || offerUnavailable) {
                return;
            }
            normalizedOffer = PlayerShopLegacyOfferMigration
                    .compile(this).orElse(null);
            offerSchemaVersion = CURRENT_OFFER_SCHEMA;
        }

        private void quarantine(int schema, byte[] payload) {
            offerSchemaVersion = Math.max(1, schema);
            normalizedOffer = null;
            legacyOfferProjection = false;
            offerUnavailable = true;
            preservedOfferPayload = payload == null
                    ? null : payload.clone();
        }

        private static String migratedListingId(
                String migrationNamespace,
                int listingOrdinal
        ) {
            if (listingOrdinal < 0) {
                throw new IllegalArgumentException(
                        "Player shop listing ordinal is invalid");
            }
            String seed = "futureshops.player.shop.offer.v2\u0000"
                    + migrationNamespace + "\u0000" + listingOrdinal;
            return "player_listing_"
                    + UUID.nameUUIDFromBytes(
                    seed.getBytes(StandardCharsets.UTF_8));
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
                    long flatMinor = Math.round(promoValue * Math.pow(10, com.enviouse.futureshops.Config.economyCurrencyDecimals));
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
                return basePrice * payable;
            }
            return applyUnitPrice(basePrice) * quantity;
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
    // The renderer only needs the ordered listing item ids + display tags, not
    // the rest of the shop state; we ship a compact tag (just "TopItems")
    // instead of the full saveAdditional payload which would needlessly
    // expose prices/barter/promos on the network every chunk load.

    /**
     * Budgets for syncing listing tags to the block-top display, measured with
     * Tag#sizeInBytes() — an in-memory footprint ESTIMATE that runs roughly 6x
     * the on-wire NBT size. Per-listing 24576 ≈ 4KB wire (a fully-kitted TacZ
     * gun with 6 attachments estimates ~4.4KB; shulker contents blow past it).
     * The AGGREGATE budget keeps a listing-spammed shop from pushing the BE
     * update / chunk packet toward the 2MB client NBT quota — without it, a
     * few hundred near-cap listings could disconnect every tracking client.
     * Over-budget listings still ship their Id and render as a plain icon.
     */
    private static final long MAX_SYNCED_DISPLAY_TAG_SIZE_ESTIMATE = 24_576L;
    private static final long MAX_SYNCED_DISPLAY_TOTAL_SIZE_ESTIMATE = 393_216L;

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();
        long totalTagSizeEstimate = 0L;
        for (Listing l : listings) {
            if (l != null && l.itemId() != null && !l.itemId().isBlank()) {
                CompoundTag e = new CompoundTag();
                e.putString("Id", l.itemId());
                // Ship the listing tag so tag-dependent models (TacZ guns read
                // GunId off the stack) render correctly on the block top.
                CompoundTag listingNbt = l.nbtTag();
                if (listingNbt != null) {
                    long size = listingNbt.sizeInBytes();
                    if (size <= MAX_SYNCED_DISPLAY_TAG_SIZE_ESTIMATE
                            && totalTagSizeEstimate + size <= MAX_SYNCED_DISPLAY_TOTAL_SIZE_ESTIMATE) {
                        e.put("Tag", listingNbt.copy());
                        totalTagSizeEstimate += size;
                    }
                }
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
        tag.putString("FloatingIconMode", floatingIconMode.name());
        tag.putString("FloatingIconItem", floatingIconItem);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        clientTopStacks.clear();
        if (tag.contains("TopItems", Tag.TAG_LIST)) {
            final ListTag items;
            try {
                items = SavedDataMigrations.requireList(
                        tag, "TopItems", Tag.TAG_COMPOUND,
                        MAX_PERSISTED_LISTINGS, "Shop top items");
            } catch (RuntimeException exception) {
                return;
            }
            for (Tag t : items) {
                if (t instanceof CompoundTag ct) {
                    String id = ct.getString("Id");
                    if (id.isBlank() || id.length() > MAX_ITEM_ID_LENGTH) continue;
                    net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                    net.minecraft.world.item.Item item = rl == null
                            ? null
                            : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                    if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
                    // Build the display stack once per packet (not per frame) so
                    // the renderer never does registry lookups or tag copies.
                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                    if (ct.contains("Tag", Tag.TAG_COMPOUND)) {
                        CompoundTag displayTag = ct.getCompound("Tag");
                        if (displayTag.sizeInBytes() > MAX_LISTING_NBT_CHARACTERS) continue;
                        stack.setTag(displayTag.copy());
                    } else if (ct.contains("Tag")) {
                        continue;
                    }
                    clientTopStacks.add(stack);
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
        // Floating icon mode: CYCLE keeps the TopItems stacks built above; OWNER_HEAD and
        // CUSTOM_ITEM replace them with a single fixed display stack. Owner UUID/name were
        // already read above, so the head stack can be built here.
        this.floatingIconMode = FloatingIconMode.byName(tag.getString("FloatingIconMode"));
        this.floatingIconItem = tag.getString("FloatingIconItem");
        if (floatingIconMode == FloatingIconMode.CUSTOM_ITEM && !floatingIconItem.isBlank()) {
            net.minecraft.world.item.ItemStack custom = resolveDisplayStack(floatingIconItem);
            if (!custom.isEmpty()) {
                clientTopStacks.clear();
                clientTopStacks.add(custom);
            }
        } else if (floatingIconMode == FloatingIconMode.OWNER_HEAD && ownerUuid != null) {
            clientTopStacks.clear();
            clientTopStacks.add(buildOwnerHeadStack(ownerUuid, ownerName));
        }
    }

    /** Resolves an item id to a bare display stack, or EMPTY when the id is invalid. */
    private static net.minecraft.world.item.ItemStack resolveDisplayStack(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        net.minecraft.world.item.Item item = rl == null
                ? null
                : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(item);
    }

    /** Builds a player-head display stack for the owner (profile resolves client-side). */
    private static net.minecraft.world.item.ItemStack buildOwnerHeadStack(UUID uuid, String name) {
        net.minecraft.world.item.ItemStack head =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        CompoundTag owner = new CompoundTag();
        owner.putUUID("Id", uuid);
        if (name != null && !name.isBlank()) {
            owner.putString("Name", name);
        }
        head.getOrCreateTag().put("SkullOwner", owner);
        return head;
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag());
    }

    /**
     * Client-visible display stacks for the block-top preview (item + listing
     * tag when synced), in listing order. Empty on servers. Shared display-only
     * instances — callers must not mutate them.
     */
    public List<net.minecraft.world.item.ItemStack> getClientTopStacks() {
        return Collections.unmodifiableList(clientTopStacks);
    }

    /** Call on the server whenever listings change to push a fresh update packet to nearby clients. */
    public void syncTopItemsToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private final List<net.minecraft.world.item.ItemStack> clientTopStacks = new ArrayList<>();
}
