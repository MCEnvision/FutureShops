package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Server → client response for a sell request. */
public record S2CSellResponsePacket(
        boolean success,
        String shopId,
        String itemId,
        ShopResultCode errorCode,
        long resultingBalanceMinorUnits,
        int quantity,
        long totalMinorUnits,
        UUID requestId) {

    public S2CSellResponsePacket {
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "Sell response identity is invalid");
        }
    }

    public static void encode(S2CSellResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.itemId);
        // Serialize by name for enum-reorder tolerance.
        buffer.writeUtf(packet.errorCode.name());
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeVarInt(packet.quantity);
        buffer.writeLong(packet.totalMinorUnits);
        buffer.writeUUID(packet.requestId);
    }

    public static S2CSellResponsePacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String shopId = buffer.readUtf();
        String itemId = buffer.readUtf();
        String rawCode = buffer.readUtf();
        ShopResultCode code;
        try {
            code = ShopResultCode.valueOf(rawCode);
        } catch (IllegalArgumentException ex) {
            code = ShopResultCode.SERVER_ERROR;
        }
        long bal = buffer.readLong();
        int qty = buffer.readVarInt();
        long totalMu = buffer.readLong();
        return new S2CSellResponsePacket(success, shopId, itemId, code,
                bal, qty, totalMu, buffer.readUUID());
    }

    public static void handle(S2CSellResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleSellResponse(packet)));
        context.setPacketHandled(true);
    }
}
