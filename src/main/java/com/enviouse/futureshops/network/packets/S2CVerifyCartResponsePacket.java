package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client: Cart verification result.
 * Contains a list of warnings about cart entries that have changed since they were added.
 */
public record S2CVerifyCartResponsePacket(boolean allOk, List<CartWarning> warnings) {

    /**
     * Separator packing dynamic args into {@link CartWarning#detail} — the server sends a stable
     * {@code warningCode} plus args here (never English text) so the client localizes each warning
     * in the viewer's own language. Shared by the server verification services and the client
     * renderer (ShopUiUtil.cartWarningComponent).
     */
    public static final String WARNING_ARG_SEP = "\u001f";

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

    public static void handle(S2CVerifyCartResponsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleCartVerification(packet)));
        ctx.get().setPacketHandled(true);
    }
}

