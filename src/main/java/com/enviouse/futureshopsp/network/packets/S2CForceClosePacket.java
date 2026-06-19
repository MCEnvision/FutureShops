package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;


/**
 * S2C packet that instructs the client to close any open shop screen immediately.
 *
 * <p>Sent by the server when:
 * <ul>
 *   <li>{@code "DEATH"} — player died while the shop was open.</li>
 *   <li>{@code "DAMAGE"} — player took damage and {@code session.close_on_damage = true}.</li>
 *   <li>{@code "DISTANCE"} — player walked too far from the shop block.</li>
 *   <li>{@code "SERVER_STOPPING"} — server is shutting down.</li>
 *   <li>{@code "ADMIN"} — an operator used {@code /shopadmin closeall}.</li>
 *   <li>{@code "SHOP_REMOVED"} — the shop block was broken or shop config was removed.</li>
 * </ul>
 */
public record S2CForceClosePacket(String reason) implements CustomPacketPayload {
    public static final Type<S2CForceClosePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cforceclosepacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CForceClosePacket> STREAM_CODEC = StreamCodec.ofMember(S2CForceClosePacket::encode, S2CForceClosePacket::decode);

    @Override
    public Type<S2CForceClosePacket> type() {
        return TYPE;
    }


    public static void encode(S2CForceClosePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.reason);
    }

    public static S2CForceClosePacket decode(FriendlyByteBuf buffer) {
        return new S2CForceClosePacket(buffer.readUtf());
    }

    public static void handle(S2CForceClosePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                ShopClientPacketHandler.handleForceClose(packet));
    }
}

