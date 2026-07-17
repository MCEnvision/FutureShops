package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record PlayerShopListingData(
        String itemId,
        String tradeMode,
        long moneyPriceMinor,
        long effectiveUnitPriceMinor,
        String barterItemId,
        int barterItemCount,
        int stock,
        PlayerShopPromoData promo,
        boolean nbtAware,
        String nbtJson,
        boolean visible,
        List<BundleOutputData> bundleOutputs,
        String department,
        int baseQuantity,
        int baseBarterItemCount,
        String listingDescription,
        boolean barterNbtAware,
        String barterNbtJson,
        // Buyback / sell-to-shop additions
        String direction,
        long buybackPriceMinor,
        int buybackCap,
        int buybackRemaining,
        // Redesign: per-listing visibility flags (protocol 27). hidden = not shown/sold to
        // visitors; showcase = shown as display-only "visit in person" (not auto-sold).
        boolean hidden,
        boolean showcase) {

    /** Item 11: Network DTO for a bundle output entry. */
    public record BundleOutputData(String itemId, int count, String nbtJson) {
        public static void encode(FriendlyByteBuf buffer, BundleOutputData data) {
            buffer.writeUtf(data.itemId());
            buffer.writeVarInt(data.count());
            buffer.writeUtf(data.nbtJson());
        }

        public static BundleOutputData decode(FriendlyByteBuf buffer) {
            return new BundleOutputData(buffer.readUtf(), buffer.readVarInt(), buffer.readUtf());
        }
    }

    public static void encode(FriendlyByteBuf buffer, PlayerShopListingData data) {
        buffer.writeUtf(data.itemId());
        buffer.writeUtf(data.tradeMode());
        buffer.writeLong(data.moneyPriceMinor());
        buffer.writeLong(data.effectiveUnitPriceMinor());
        buffer.writeUtf(data.barterItemId());
        buffer.writeVarInt(data.barterItemCount());
        buffer.writeVarInt(data.stock());
        PlayerShopPromoData.encode(buffer, data.promo());
        buffer.writeBoolean(data.nbtAware());
        buffer.writeUtf(data.nbtJson());
        buffer.writeBoolean(data.visible());
        buffer.writeVarInt(data.bundleOutputs().size());
        for (BundleOutputData entry : data.bundleOutputs()) {
            BundleOutputData.encode(buffer, entry);
        }
        buffer.writeUtf(data.department());
        buffer.writeVarInt(data.baseQuantity());
        buffer.writeVarInt(data.baseBarterItemCount());
        buffer.writeUtf(data.listingDescription());
        buffer.writeBoolean(data.barterNbtAware());
        buffer.writeUtf(data.barterNbtJson());
        buffer.writeUtf(data.direction());
        buffer.writeLong(data.buybackPriceMinor());
        buffer.writeVarInt(data.buybackCap());
        buffer.writeVarInt(data.buybackRemaining());
        buffer.writeBoolean(data.hidden());
        buffer.writeBoolean(data.showcase());
    }

    public static PlayerShopListingData decode(FriendlyByteBuf buffer) {
        String itemId = buffer.readUtf();
        String tradeMode = buffer.readUtf();
        long moneyPriceMinor = buffer.readLong();
        long effectiveUnitPriceMinor = buffer.readLong();
        String barterItemId = buffer.readUtf();
        int barterItemCount = buffer.readVarInt();
        int stock = buffer.readVarInt();
        PlayerShopPromoData promo = PlayerShopPromoData.decode(buffer);
        boolean nbtAware = buffer.readBoolean();
        String nbtJson = buffer.readUtf();
        boolean visible = buffer.readBoolean();
        int bundleCount = buffer.readVarInt();
        List<BundleOutputData> bundleOutputs = new ArrayList<>(bundleCount);
        for (int i = 0; i < bundleCount; i++) {
            bundleOutputs.add(BundleOutputData.decode(buffer));
        }
        String department = buffer.readUtf();
        int baseQuantity = buffer.readVarInt();
        int baseBarterItemCount = buffer.readVarInt();
        String listingDescription = buffer.readUtf();
        boolean barterNbtAware = buffer.readBoolean();
        String barterNbtJson = buffer.readUtf();
        String direction = buffer.readUtf();
        long buybackPriceMinor = buffer.readLong();
        int buybackCap = buffer.readVarInt();
        int buybackRemaining = buffer.readVarInt();
        boolean hidden = buffer.readBoolean();
        boolean showcase = buffer.readBoolean();
        return new PlayerShopListingData(itemId, tradeMode, moneyPriceMinor, effectiveUnitPriceMinor,
                barterItemId, barterItemCount, stock, promo, nbtAware, nbtJson, visible, bundleOutputs, department, baseQuantity, baseBarterItemCount, listingDescription,
                barterNbtAware, barterNbtJson, direction, buybackPriceMinor, buybackCap, buybackRemaining, hidden, showcase);
    }
}
