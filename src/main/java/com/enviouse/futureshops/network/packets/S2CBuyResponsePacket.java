package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.UUID;

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
        long totalMinorUnits,
        UUID requestId,
        long snapshotRevision,
        String responseReason) {

    private static final UUID UNCORRELATED_REQUEST_ID = new UUID(0L, 0L);
    private static final int MAX_RESPONSE_REASON_LENGTH = 32;

    public S2CBuyResponsePacket {
        requestId = requestId == null ? UNCORRELATED_REQUEST_ID : requestId;
        if (snapshotRevision < 0L) {
            throw new IllegalArgumentException("snapshotRevision is invalid");
        }
        responseReason = responseReason == null ? "" : responseReason;
        if (responseReason.length() > MAX_RESPONSE_REASON_LENGTH
                || !responseReason.matches("[a-z_]*")) {
            throw new IllegalArgumentException("responseReason is invalid");
        }
    }

    public S2CBuyResponsePacket(
            boolean success,
            boolean cartCheckout,
            String shopId,
            ShopResultCode errorCode,
            long resultingBalanceMinorUnits,
            int totalQuantity,
            long totalMinorUnits
    ) {
        this(success, cartCheckout, shopId, errorCode, resultingBalanceMinorUnits,
                totalQuantity, totalMinorUnits, UNCORRELATED_REQUEST_ID);
    }

    public S2CBuyResponsePacket(
            boolean success,
            boolean cartCheckout,
            String shopId,
            ShopResultCode errorCode,
            long resultingBalanceMinorUnits,
            int totalQuantity,
            long totalMinorUnits,
            UUID requestId
    ) {
        this(success, cartCheckout, shopId, errorCode, resultingBalanceMinorUnits,
                totalQuantity, totalMinorUnits, requestId, 0L, "");
    }

    public static void encode(S2CBuyResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeBoolean(packet.cartCheckout);
        buffer.writeUtf(packet.shopId);
        // Serialize by name for enum-reorder tolerance.
        buffer.writeUtf(packet.errorCode.name());
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeVarInt(packet.totalQuantity);
        buffer.writeLong(packet.totalMinorUnits);
        buffer.writeUUID(packet.requestId);
        buffer.writeLong(packet.snapshotRevision);
        buffer.writeUtf(packet.responseReason);
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
        return new S2CBuyResponsePacket(
                success, cartCheckout, shopId, code, bal, totalQty, totalMu,
                buffer.readUUID(), buffer.readLong(),
                buffer.readUtf(MAX_RESPONSE_REASON_LENGTH));
    }

    public static void handle(S2CBuyResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBuyResponse(packet)));
        context.setPacketHandled(true);
    }
}
