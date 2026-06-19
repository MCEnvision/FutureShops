package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.DepartmentSavedData;
import com.enviouse.futureshopsp.network.ShopPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Client → Server: Request department search results.
 */
public class C2SFetchDepartmentsPacket implements CustomPacketPayload {
    public static final Type<C2SFetchDepartmentsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sfetchdepartmentspacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SFetchDepartmentsPacket> STREAM_CODEC = StreamCodec.ofMember(C2SFetchDepartmentsPacket::encode, C2SFetchDepartmentsPacket::decode);

    @Override
    public Type<C2SFetchDepartmentsPacket> type() {
        return TYPE;
    }

    private final String searchPrefix;

    public C2SFetchDepartmentsPacket(String searchPrefix) {
        this.searchPrefix = searchPrefix;
    }

    public static void encode(C2SFetchDepartmentsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.searchPrefix);
    }

    public static C2SFetchDepartmentsPacket decode(FriendlyByteBuf buffer) {
        return new C2SFetchDepartmentsPacket(buffer.readUtf());
    }

    public static void handle(C2SFetchDepartmentsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null && player.getServer() != null) {
                List<String> results = DepartmentSavedData.get(player.getServer()).search(packet.searchPrefix, 20);
                ShopPackets.sendToPlayer(player, new S2CDepartmentListPacket(results));
            }
        });
    }
}

