package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SPlayerShopSettlementClaimPacket(
        BlockPos shopPos,
        UUID requestId,
        int responseToken
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SPlayerShopSettlementClaimPacket {
        shopPos = Objects.requireNonNull(shopPos, "shopPos");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (ZERO.equals(requestId)) {
            throw new IllegalArgumentException(
                    "Player shop request identity must be nonzero");
        }
        if (!ShopTransactionUtil.isValidPlayerShopResponseToken(
                responseToken)) {
            throw new IllegalArgumentException(
                    "Player shop response token is invalid");
        }
    }

    public static void encode(
            C2SPlayerShopSettlementClaimPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.responseToken());
    }

    public static C2SPlayerShopSettlementClaimPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new C2SPlayerShopSettlementClaimPacket(
                    buffer.readBlockPos(), buffer.readUUID(),
                    buffer.readVarInt());
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Malformed player shop settlement packet", exception);
        }
    }

    public static void handle(
            C2SPlayerShopSettlementClaimPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.claimSettlement(player,
                        packet.shopPos(), packet.requestId(),
                        packet.responseToken());
            }
        });
        context.setPacketHandled(true);
    }
}
