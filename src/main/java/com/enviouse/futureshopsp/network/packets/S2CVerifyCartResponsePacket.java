package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;

/**
 * Server → Client: Cart verification result.
 * Contains a list of warnings about cart entries that have changed since they were added.
 */
public record S2CVerifyCartResponsePacket(boolean allOk, List<CartWarning> warnings) implements CustomPacketPayload {
    public static final Type<S2CVerifyCartResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cverifycartresponsepacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVerifyCartResponsePacket> STREAM_CODEC = StreamCodec.ofMember(S2CVerifyCartResponsePacket::encode, S2CVerifyCartResponsePacket::decode);

    @Override
    public Type<S2CVerifyCartResponsePacket> type() {
        return TYPE;
    }


    public record CartWarning(int cartLineIndex, String warningCode, String detail) {
        public static void encode(FriendlyByteBuf buffer, CartWarning warning) {
            buffer.writeVarInt(warning.cartLineIndex);
            buffer.writeUtf(warning.warningCode);
            buffer.writeUtf(warning.detail);
        }

        public static CartWarning decode(FriendlyByteBuf buffer) {
            return new CartWarning(buffer.readVarInt(), buffer.readUtf(), buffer.readUtf());
        }
    }

    public static void encode(S2CVerifyCartResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.allOk);
        buffer.writeCollection(packet.warnings, CartWarning::encode);
    }

    public static S2CVerifyCartResponsePacket decode(FriendlyByteBuf buffer) {
        return new S2CVerifyCartResponsePacket(buffer.readBoolean(), buffer.readList(CartWarning::decode));
    }

    public static void handle(S2CVerifyCartResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleCartVerification(packet));
    }
}

