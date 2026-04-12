package com.enviouse.futureshops.block;

import com.enviouse.futureshops.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class ShopBlockEntity extends BlockEntity {
    public enum TradeMode {
        MONEY,
        BARTER
    }

    private UUID ownerUuid;
    private String shopId = "default";

    private String listedItemId = "";
    private TradeMode tradeMode = TradeMode.MONEY;
    private long moneyPriceMinor = 100L;
    private String barterItemId = "";
    private int barterItemCount = 1;
    private BlockPos linkedStoragePos;

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

    public String getListedItemId() {
        return listedItemId;
    }

    public void setListedItemId(String listedItemId) {
        this.listedItemId = listedItemId == null ? "" : listedItemId;
        setChanged();
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public void setTradeMode(TradeMode tradeMode) {
        this.tradeMode = tradeMode == null ? TradeMode.MONEY : tradeMode;
        setChanged();
    }

    public long getMoneyPriceMinor() {
        return moneyPriceMinor;
    }

    public void setMoneyPriceMinor(long moneyPriceMinor) {
        this.moneyPriceMinor = Math.max(1L, moneyPriceMinor);
        setChanged();
    }

    public String getBarterItemId() {
        return barterItemId;
    }

    public void setBarterItemId(String barterItemId) {
        this.barterItemId = barterItemId == null ? "" : barterItemId;
        setChanged();
    }

    public int getBarterItemCount() {
        return barterItemCount;
    }

    public void setBarterItemCount(int barterItemCount) {
        this.barterItemCount = Math.max(1, barterItemCount);
        setChanged();
    }

    public BlockPos getLinkedStoragePos() {
        return linkedStoragePos;
    }

    public void setLinkedStoragePos(BlockPos linkedStoragePos) {
        this.linkedStoragePos = linkedStoragePos;
        setChanged();
    }

    public void clearListing() {
        listedItemId = "";
        barterItemId = "";
        barterItemCount = 1;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        tag.putString("ShopId", shopId);
        tag.putString("ListedItemId", listedItemId);
        tag.putString("TradeMode", tradeMode.name());
        tag.putLong("MoneyPriceMinor", moneyPriceMinor);
        tag.putString("BarterItemId", barterItemId);
        tag.putInt("BarterItemCount", barterItemCount);
        if (linkedStoragePos != null) {
            tag.putLong("LinkedStoragePos", linkedStoragePos.asLong());
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
        listedItemId = tag.getString("ListedItemId");
        String modeName = tag.getString("TradeMode");
        try {
            tradeMode = TradeMode.valueOf(modeName.isBlank() ? TradeMode.MONEY.name() : modeName);
        } catch (IllegalArgumentException ignored) {
            tradeMode = TradeMode.MONEY;
        }
        moneyPriceMinor = Math.max(1L, tag.getLong("MoneyPriceMinor"));
        barterItemId = tag.getString("BarterItemId");
        barterItemCount = Math.max(1, tag.getInt("BarterItemCount"));
        linkedStoragePos = tag.contains("LinkedStoragePos") ? BlockPos.of(tag.getLong("LinkedStoragePos")) : null;
    }
}

