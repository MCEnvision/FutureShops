package com.enviouse.futureshops.block;

import com.enviouse.futureshops.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ShopBlockEntity extends BlockEntity {
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
    public record BundleEntry(String itemId, int count, @Nullable CompoundTag nbtTag) {
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
            int count = Math.max(1, tag.getInt("Count"));
            CompoundTag nbt = tag.contains("NbtTag", Tag.TAG_COMPOUND) ? tag.getCompound("NbtTag") : null;
            return new BundleEntry(id, count, nbt);
        }
    }

    private UUID ownerUuid;
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

    public ShopBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SHOP_BLOCK_ENTITY.get(), pos, blockState);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        setChanged();
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
        if (itemId == null || itemId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < listings.size(); i++) {
            if (itemId.equals(listings.get(i).itemId())) {
                return i;
            }
        }
        if (listings.size() >= effectiveMaxListings()) {
            return -1;
        }
        listings.add(new Listing(itemId));
        setChanged();
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        tag.putString("ShopId", shopId);
        tag.putString("ShopName", shopName);
        tag.putString("Description", description);
        tag.putBoolean("SingleItemMode", singleItemMode);
        tag.putInt("VisibleListingIndex", visibleListingIndex);
        tag.putBoolean("BarterStorageSame", barterStorageSame);
        tag.putInt("MaxListings", maxListings);
        ListTag listingTags = new ListTag();
        for (Listing listing : listings) {
            listingTags.add(listing.save());
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
    public void load(CompoundTag tag) {
        super.load(tag);
        ownerUuid = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
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
        listings.clear();
        if (tag.contains("Listings", Tag.TAG_LIST)) {
            ListTag listingTags = tag.getList("Listings", Tag.TAG_COMPOUND);
            for (Tag listingTag : listingTags) {
                listings.add(Listing.load((CompoundTag) listingTag));
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
        private CompoundTag nbtTag = null;
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
            return effectiveBarterItemCount() * quantity;
        }

        public Promo promo() { return promo; }
        public boolean nbtAware() { return nbtAware; }
        public void setNbtAware(boolean nbtAware) { this.nbtAware = nbtAware; }
        public CompoundTag nbtTag() { return nbtTag; }
        public void setNbtTag(CompoundTag nbtTag) { this.nbtTag = nbtTag; }

        /** Item 11: Bundle output entries (multiple items per listing). */
        public List<BundleEntry> bundleOutputs() {
            return Collections.unmodifiableList(bundleOutputs);
        }

        public void addBundleOutput(String itemId, int count, @Nullable CompoundTag nbtTag) {
            if (bundleOutputs.size() >= MAX_BUNDLE_OUTPUTS) return;
            bundleOutputs.add(new BundleEntry(itemId, Math.max(1, count), nbtTag));
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
            return promo.active() ? promo.calculateTotal(moneyPriceMinor, quantity) : moneyPriceMinor * quantity;
        }

        private CompoundTag save() {
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
            if (nbtTag != null) {
                tag.put("NbtTag", nbtTag.copy());
            }
            tag.put("Promo", promo.save());
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

        private static Listing load(CompoundTag tag) {
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
            if (tag.contains("NbtTag", Tag.TAG_COMPOUND)) {
                listing.setNbtTag(tag.getCompound("NbtTag"));
            }
            if (tag.contains("Promo", Tag.TAG_COMPOUND)) {
                listing.promo.load(tag.getCompound("Promo"));
            }
            // Item 11: Load bundle outputs
            if (tag.contains("BundleOutputs", Tag.TAG_LIST)) {
                ListTag bundleTag = tag.getList("BundleOutputs", Tag.TAG_COMPOUND);
                for (Tag bt : bundleTag) {
                    listing.bundleOutputs.add(BundleEntry.load((CompoundTag) bt));
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
            double cappedValue = Math.max(0.0D, promoValue);
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
}

