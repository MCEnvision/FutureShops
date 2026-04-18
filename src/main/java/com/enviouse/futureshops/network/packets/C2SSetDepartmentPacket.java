package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.DepartmentSavedData;
import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: Set the department on a player shop listing.
 */
public class C2SSetDepartmentPacket {
    private final BlockPos shopPos;
    private final int listingIndex;
    private final String department;

    public C2SSetDepartmentPacket(BlockPos shopPos, int listingIndex, String department) {
        this.shopPos = shopPos;
        this.listingIndex = listingIndex;
        this.department = department;
    }

    public static void encode(C2SSetDepartmentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos);
        buffer.writeVarInt(packet.listingIndex);
        buffer.writeUtf(packet.department);
    }

    public static C2SSetDepartmentPacket decode(FriendlyByteBuf buffer) {
        return new C2SSetDepartmentPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf());
    }

    public static void handle(C2SSetDepartmentPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                PlayerShopBlockService.setDepartment(player, packet.shopPos, packet.listingIndex, packet.department);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

