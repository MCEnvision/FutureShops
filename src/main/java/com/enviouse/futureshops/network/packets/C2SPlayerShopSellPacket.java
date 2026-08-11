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

public record C2SPlayerShopSellPacket(
        BlockPos shopPos,
        int listingIndex,
        int quantity,
        UUID requestId,
        int responseToken
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SPlayerShopSellPacket {
        shopPos = Objects.requireNonNull(shopPos, "shopPos");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (!ShopTransactionUtil.isValidPlayerShopListingIndex(
                listingIndex)) {
            throw new IllegalArgumentException(
                    "Player shop listing index is invalid");
        }
        if (quantity < 1
                || quantity > ShopTransactionUtil.MAX_SELL_QUANTITY) {
            throw new IllegalArgumentException(
                    "Player shop sell quantity is invalid");
        }
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

    public C2SPlayerShopSellPacket(
            BlockPos shopPos,
            int listingIndex,
            int quantity
    ) {
        this(shopPos, listingIndex, quantity, UUID.randomUUID(), 0);
    }

    public static void encode(
            C2SPlayerShopSellPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeVarInt(packet.quantity());
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.responseToken());
    }

    public static C2SPlayerShopSellPacket decode(FriendlyByteBuf buffer) {
        try {
            BlockPos shopPos = buffer.readBlockPos();
            int listingIndex = buffer.readVarInt();
            int quantity = buffer.readVarInt();
            UUID requestId = buffer.readUUID();
            int responseToken = buffer.readVarInt();
            return new C2SPlayerShopSellPacket(shopPos, listingIndex,
                    quantity, requestId, responseToken);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Malformed player shop sell packet", exception);
        }
    }

    public static void handle(
            C2SPlayerShopSellPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.handleSell(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
