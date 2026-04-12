package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * S2C packet sent after a successful {@link C2SOpenShopPacket}.
 * Carries the full shop catalog (categories, items, promos, barter recipes) together with
 * the player's current balance and display-currency metadata.
 *
 * <p>Protocol version 5 — any change to the field layout must bump the
 * network channel protocol version in {@code ShopPackets}.
 */
public record S2CShopDataPacket(
        String shopId,
        long balanceMinorUnits,
        String currencyName,
        int currencyDecimals,
        List<CatalogCategory> categories,
        List<CatalogItem> items,
        List<CatalogPromo> promos,
        List<CatalogBarterRecipe> barterRecipes) {

    public static void encode(S2CShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeLong(packet.balanceMinorUnits);
        buffer.writeUtf(packet.currencyName);
        buffer.writeVarInt(packet.currencyDecimals);
        buffer.writeCollection(packet.categories, CatalogCategory::encode);
        buffer.writeCollection(packet.items, CatalogItem::encode);
        buffer.writeCollection(packet.promos, CatalogPromo::encode);
        buffer.writeCollection(packet.barterRecipes, CatalogBarterRecipe::encode);
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
        return new S2CShopDataPacket(shopId, balance, currencyName, decimals, categories, items, promos, barterRecipes);
    }

    public static void handle(S2CShopDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler.handleShopData(packet)));
        context.setPacketHandled(true);
    }
}
