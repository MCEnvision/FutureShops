package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.AdminShopEditService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: one in-GUI admin shop edit action. A generic action envelope so the
 * whole editor rides one packet shape; unused slots are blank / 0 for a given action.
 *
 * <p>Actions (unknown strings ack {@code INVALID}):
 * <ul>
 *   <li>{@code SAVE_LISTING}    targetId=listingId, stringA=displayName (blank → clear),
 *       stringB=categoryId ("" = all), longA=buyMinor, longB=sellMinor, longC=stock (-1 unlimited)</li>
 *   <li>{@code REMOVE_LISTING}  targetId=listingId</li>
 *   <li>{@code BARTER_OFF}      targetId=listingId</li>
 *   <li>{@code ADD_HELD_ITEM}   stringB=categoryId, longA=buyMinor, longB=sellMinor, longC=stock —
 *       the server captures the sender's main-hand item (registry id + full NBT + hover name)</li>
 *   <li>{@code ADD_CATEGORY}    stringA=displayName</li>
 *   <li>{@code RENAME_CATEGORY} targetId=categoryId, stringA=new displayName</li>
 *   <li>{@code MOVE_CATEGORY}   targetId=categoryId, longA=-1 (up) or +1 (down)</li>
 *   <li>{@code REMOVE_CATEGORY} targetId=categoryId</li>
 * </ul>
 *
 * <p>The GUI's edit button is convenience only — {@link AdminShopEditService} re-validates
 * permission level 2 and every input server-side.
 */
public record C2SAdminShopEditPacket(
        String action,
        String targetId,
        String stringA,
        String stringB,
        long longA,
        long longB,
        long longC) {

    public static void encode(C2SAdminShopEditPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.targetId);
        buffer.writeUtf(packet.stringA);
        buffer.writeUtf(packet.stringB);
        buffer.writeLong(packet.longA);
        buffer.writeLong(packet.longB);
        buffer.writeLong(packet.longC);
    }

    public static C2SAdminShopEditPacket decode(FriendlyByteBuf buffer) {
        return new C2SAdminShopEditPacket(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong());
    }

    public static void handle(C2SAdminShopEditPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            AdminShopEditService.handleEdit(player, packet);
        });
        context.setPacketHandled(true);
    }
}
