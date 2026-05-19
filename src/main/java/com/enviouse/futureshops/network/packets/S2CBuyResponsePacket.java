package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client response for a buy request.
 * The authoritative post-transaction catalog refresh still arrives via S2CShopDataPacket.
 */
public record S2CBuyResponsePacket(
        boolean success,
        boolean cartCheckout,
        String shopId,
        ShopResultCode errorCode,
        long resultingBalanceMinorUnits,
        int totalQuantity,
        long totalMinorUnits) {

    public static void encode(S2CBuyResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeBoolean(packet.cartCheckout);
        buffer.writeUtf(packet.shopId);
        // Serialize by name for enum-reorder tolerance.
        buffer.writeUtf(packet.errorCode.name());
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeVarInt(packet.totalQuantity);
        buffer.writeLong(packet.totalMinorUnits);
    }

    public static S2CBuyResponsePacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        boolean cartCheckout = buffer.readBoolean();
        String shopId = buffer.readUtf();
        String rawCode = buffer.readUtf();
        ShopResultCode code;
        try {
            code = ShopResultCode.valueOf(rawCode);
        } catch (IllegalArgumentException ex) {
            code = ShopResultCode.SERVER_ERROR;
        }
        long bal = buffer.readLong();
        int totalQty = buffer.readVarInt();
        long totalMu = buffer.readLong();
        return new S2CBuyResponsePacket(success, cartCheckout, shopId, code, bal, totalQty, totalMu);
    }

    public static void handle(S2CBuyResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBuyResponse(packet)));
        context.setPacketHandled(true);
    }
}


