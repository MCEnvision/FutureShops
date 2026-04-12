package com.enviouse.futureshops.block;

import com.enviouse.futureshops.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ShopBlockEntity extends BlockEntity {
    public static final int MAX_LISTINGS = 12;

    public enum TradeMode {
        MONEY,
        BARTER,
        BOTH
    }

    private UUID ownerUuid;
    private String shopId = "default";
    private String shopName = "";
    private boolean singleItemMode = false;
    private boolean barterStorageSame = true;
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

    public BlockPos getBarterStoragePos() {
        return barterStoragePos;
    }

    public void setBarterStoragePos(BlockPos barterStoragePos) {
        this.barterStoragePos = barterStoragePos;
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
        if (listings.size() >= MAX_LISTINGS) {
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
        tag.putBoolean("SingleItemMode", singleItemMode);
        tag.putBoolean("BarterStorageSame", barterStorageSame);
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
        singleItemMode = tag.getBoolean("SingleItemMode");
        barterStorageSame = tag.contains("BarterStorageSame") ? tag.getBoolean("BarterStorageSame") : true;
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
        private final Promo promo = new Promo();

        public Listing() {
        }

        public Listing(String itemId) {
            this.itemId = itemId == null ? "" : itemId;
        }

        public String itemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId == null ? "" : itemId;
        }

        public TradeMode tradeMode() {
            return tradeMode;
        }

        public void setTradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode == null ? TradeMode.MONEY : tradeMode;
        }

        public long moneyPriceMinor() {
            return moneyPriceMinor;
        }

        public void setMoneyPriceMinor(long moneyPriceMinor) {
            this.moneyPriceMinor = Math.max(1L, moneyPriceMinor);
        }

        public String barterItemId() {
            return barterItemId;
        }

        public void setBarterItemId(String barterItemId) {
            this.barterItemId = barterItemId == null ? "" : barterItemId;
        }

        public int barterItemCount() {
            return barterItemCount;
        }

        public void setBarterItemCount(int barterItemCount) {
            this.barterItemCount = Math.max(1, barterItemCount);
        }

        public Promo promo() {
            return promo;
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
            tag.put("Promo", promo.save());
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
            if (tag.contains("Promo", Tag.TAG_COMPOUND)) {
                listing.promo.load(tag.getCompound("Promo"));
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

        public String promoType() {
            return promoType;
        }

        public double promoValue() {
            return promoValue;
        }

        public int buyX() {
            return buyX;
        }

        public int buyY() {
            return buyY;
        }

        public boolean flash() {
            return flash;
        }

        public boolean configured() {
            return promoType != null && !promoType.isBlank();
        }

        public boolean active() {
            if (!configured()) {
                return false;
            }
            long now = System.currentTimeMillis() / 1000L;
            return now >= startEpochSeconds && (endEpochSeconds <= 0L || now <= endEpochSeconds);
        }

        public long applyUnitPrice(long basePrice) {
            return switch (promoType) {
                case "PERCENTAGE", "FLASH" -> Math.max(1L, Math.round(basePrice * (1.0D - promoValue / 100.0D)));
                case "FLAT" -> Math.max(1L, basePrice - (long) promoValue);
                default -> basePrice;
            };
        }

        public long calculateTotal(long basePrice, int quantity) {
            if (quantity <= 0) {
                return 0L;
            }
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
            this.promoValue = Math.max(0.0D, promoValue);
            this.buyX = Math.max(0, buyX);
            this.buyY = Math.max(0, buyY);
            this.startEpochSeconds = Math.max(0L, startEpochSeconds);
            this.endEpochSeconds = Math.max(0L, endEpochSeconds);
            this.flash = flash;
        }

        public void clear() {
            configure("", 0.0D, 0, 0, 0L, 0L, false);
        }

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
            configure(
                    tag.getString("Type"),
                    tag.getDouble("Value"),
                    tag.getInt("BuyX"),
                    tag.getInt("BuyY"),
                    tag.getLong("Start"),
                    tag.getLong("End"),
                    tag.getBoolean("Flash"));
        }
    }
}

