package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;
import com.enviouse.futureshops.data.NearbyShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C packet sent after a successful {@link C2SOpenShopPacket}.
 * Carries the full shop catalog (categories, items, promos, barter recipes) together with
 * the player's current balance, display-currency metadata, nearby player shops, and admin toggle.
 *
 * <p>Protocol version 13 — bumped for nearby shop + admin toggle fields.
 */
public record S2CShopDataPacket(
        String shopId,
        long balanceMinorUnits,
        String currencyName,
        int currencyDecimals,
        List<CatalogCategory> categories,
        List<CatalogItem> items,
        List<CatalogPromo> promos,
        List<CatalogBarterRecipe> barterRecipes,
        boolean adminShopEnabled,
        List<NearbyShopEntry> nearbyShops) {

    /** Backward-compat constructor without nearby shops / admin toggle. */
    public S2CShopDataPacket(String shopId, long balanceMinorUnits, String currencyName, int currencyDecimals,
                             List<CatalogCategory> categories, List<CatalogItem> items,
                             List<CatalogPromo> promos, List<CatalogBarterRecipe> barterRecipes) {
        this(shopId, balanceMinorUnits, currencyName, currencyDecimals, categories, items, promos, barterRecipes, true, List.of());
    }

    public static void encode(S2CShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeLong(packet.balanceMinorUnits);
        buffer.writeUtf(packet.currencyName);
        buffer.writeVarInt(packet.currencyDecimals);
        buffer.writeCollection(packet.categories, CatalogCategory::encode);
        buffer.writeCollection(packet.items, CatalogItem::encode);
        buffer.writeCollection(packet.promos, CatalogPromo::encode);
        buffer.writeCollection(packet.barterRecipes, CatalogBarterRecipe::encode);
        buffer.writeBoolean(packet.adminShopEnabled);
        buffer.writeVarInt(packet.nearbyShops.size());
        for (NearbyShopEntry entry : packet.nearbyShops) {
            buffer.writeBlockPos(entry.pos());
            buffer.writeUUID(entry.ownerUuid());
            buffer.writeUtf(entry.ownerName());
            buffer.writeUtf(entry.shopName());
            buffer.writeVarInt(entry.listingCount());
            buffer.writeVarInt(entry.totalStock());
            buffer.writeDouble(entry.distance());
        }
    }

    public static S2CShopDataPacket decode(FriendlyByteBuf buffer) {
        String shopId = buffer.readUtf();
        long balance = buffer.readLong();
        String currencyName = buffer.readUtf();
        int decimals = buffer.readVarInt();
        List<CatalogCategory> categories = buffer.readList(CatalogCategory::decode);
        List<CatalogItem> items = buffer.readList(CatalogItem::decode);
        List<CatalogPromo> promos = buffer.readList(CatalogPromo::decode);
        List<CatalogBarterRecipe> barterRecipes = buffer.readList(CatalogBarterRecipe::decode);
        boolean adminShopEnabled = buffer.readBoolean();
        int nearbyCount = buffer.readVarInt();
        List<NearbyShopEntry> nearbyShops = new java.util.ArrayList<>();
        for (int i = 0; i < nearbyCount; i++) {
            BlockPos pos = buffer.readBlockPos();
            UUID ownerUuid = buffer.readUUID();
            String ownerName = buffer.readUtf();
            String shopName = buffer.readUtf();
            int listingCount = buffer.readVarInt();
            int totalStock = buffer.readVarInt();
            double distance = buffer.readDouble();
            nearbyShops.add(new NearbyShopEntry(pos, ownerUuid, ownerName, shopName, listingCount, totalStock, distance));
        }
        return new S2CShopDataPacket(shopId, balance, currencyName, decimals, categories, items, promos, barterRecipes, adminShopEnabled, nearbyShops);
    }

    public static void handle(S2CShopDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler.handleShopData(packet)));
        context.setPacketHandled(true);
    }
}
