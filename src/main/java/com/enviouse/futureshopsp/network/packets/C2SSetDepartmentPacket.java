package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.DepartmentSavedData;
import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


/**
 * Client → Server: Set the department on a player shop listing.
 */
public class C2SSetDepartmentPacket implements CustomPacketPayload {
    public static final Type<C2SSetDepartmentPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2ssetdepartmentpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetDepartmentPacket> STREAM_CODEC = StreamCodec.ofMember(C2SSetDepartmentPacket::encode, C2SSetDepartmentPacket::decode);

    @Override
    public Type<C2SSetDepartmentPacket> type() {
        return TYPE;
    }

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

    public static void handle(C2SSetDepartmentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                PlayerShopBlockService.setDepartment(player, packet.shopPos, packet.listingIndex, packet.department);
            }
        });
    }
}

