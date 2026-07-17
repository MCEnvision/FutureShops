package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: result of one admin shop edit action ({@link C2SAdminShopEditPacket} /
 * {@link C2SAdminShopAddItemsPacket}). The client localizes {@code code} via
 * {@code gui.futureshops.admin_edit.result.<code lowercase>} and shows it in the status strip.
 *
 * <p>{@code code} is one of: {@code OK}, {@code ADDED} (arg = count), {@code DENIED},
 * {@code NOT_FOUND}, {@code INVALID}, {@code EMPTY_HAND}, {@code DUPLICATE}, {@code IO_ERROR}.
 * This is a string-code system deliberately separate from
 * {@link com.enviouse.futureshops.server.shop.ShopResultCode} (whose switches are pinned exhaustive).
 */
public record S2CAdminEditAckPacket(boolean success, String code, String arg) {

    public static void encode(S2CAdminEditAckPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.code);
        buffer.writeUtf(packet.arg == null ? "" : packet.arg);
    }

    public static S2CAdminEditAckPacket decode(FriendlyByteBuf buffer) {
        return new S2CAdminEditAckPacket(buffer.readBoolean(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(S2CAdminEditAckPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler.handleAdminEditAck(packet)));
        context.setPacketHandled(true);
    }
}
