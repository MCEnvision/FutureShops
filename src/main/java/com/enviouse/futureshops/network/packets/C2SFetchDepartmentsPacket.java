package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.DepartmentSavedData;
import com.enviouse.futureshops.network.ShopPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: Request department search results.
 */
public class C2SFetchDepartmentsPacket {
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

    public static void handle(C2SFetchDepartmentsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                List<String> results = DepartmentSavedData.get(player.getServer()).search(packet.searchPrefix, 20);
                ShopPackets.sendToPlayer(player, new S2CDepartmentListPacket(results));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

