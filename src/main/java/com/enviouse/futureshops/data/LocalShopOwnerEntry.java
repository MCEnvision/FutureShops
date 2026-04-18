package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/**
 * DTO for one player/franchise in the local shops aggregation view.
 * Sent from server to client as part of S2CLocalShopsPacket.
 */
public record LocalShopOwnerEntry(
        UUID ownerUuid,
        String displayName,
        String franchiseName,
        int shopBlockCount,
        int totalListings,
        int totalStock,
        double closestDistance,
        List<LocalDepartment> departments) {

    public record LocalDepartment(String name, List<LocalListing> listings) {
        public static void encode(FriendlyByteBuf buffer, LocalDepartment dept) {
            buffer.writeUtf(dept.name);
            buffer.writeCollection(dept.listings, LocalListing::encode);
        }

        public static LocalDepartment decode(FriendlyByteBuf buffer) {
            return new LocalDepartment(buffer.readUtf(), buffer.readList(LocalListing::decode));
        }
    }

    public record LocalListing(
            String itemId,
            String displayName,
            int stock,
            long priceMinor,
            String tradeMode,
            String barterItemId,
            int barterItemCount,
            boolean nbtAware,
            String nbtJson,
            String shopName,
            long shopPosLong,
            int listingIndex,
            int baseQuantity,
            boolean hasPromo,
            long promoPriceMinor) {

        public static void encode(FriendlyByteBuf buffer, LocalListing listing) {
            buffer.writeUtf(listing.itemId);
            buffer.writeUtf(listing.displayName);
            buffer.writeVarInt(listing.stock);
            buffer.writeLong(listing.priceMinor);
            buffer.writeUtf(listing.tradeMode);
            buffer.writeUtf(listing.barterItemId);
            buffer.writeVarInt(listing.barterItemCount);
            buffer.writeBoolean(listing.nbtAware);
            buffer.writeUtf(listing.nbtJson);
            buffer.writeUtf(listing.shopName);
            buffer.writeLong(listing.shopPosLong);
            buffer.writeVarInt(listing.listingIndex);
            buffer.writeVarInt(listing.baseQuantity);
            buffer.writeBoolean(listing.hasPromo);
            buffer.writeLong(listing.promoPriceMinor);
        }

        public static LocalListing decode(FriendlyByteBuf buffer) {
            return new LocalListing(
                    buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                    buffer.readLong(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readLong(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readLong());
        }
    }

    public static void encode(FriendlyByteBuf buffer, LocalShopOwnerEntry entry) {
        buffer.writeUUID(entry.ownerUuid);
        buffer.writeUtf(entry.displayName);
        buffer.writeUtf(entry.franchiseName);
        buffer.writeVarInt(entry.shopBlockCount);
        buffer.writeVarInt(entry.totalListings);
        buffer.writeVarInt(entry.totalStock);
        buffer.writeDouble(entry.closestDistance);
        buffer.writeCollection(entry.departments, LocalDepartment::encode);
    }

    public static LocalShopOwnerEntry decode(FriendlyByteBuf buffer) {
        return new LocalShopOwnerEntry(
                buffer.readUUID(), buffer.readUtf(), buffer.readUtf(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readDouble(), buffer.readList(LocalDepartment::decode));
    }
}

