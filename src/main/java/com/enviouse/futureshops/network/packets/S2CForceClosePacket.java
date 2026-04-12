package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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
public record S2CForceClosePacket(String reason) {

    public static void encode(S2CForceClosePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.reason);
    }

    public static S2CForceClosePacket decode(FriendlyByteBuf buffer) {
        return new S2CForceClosePacket(buffer.readUtf());
    }

    public static void handle(S2CForceClosePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler.handleForceClose(packet)));
        context.setPacketHandled(true);
    }
}

