package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.DepartmentClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client: Department search results for the department picker.
 */
public class S2CDepartmentListPacket {
    private final List<String> departments;

    public S2CDepartmentListPacket(List<String> departments) {
        this.departments = departments;
    }

    public static void encode(S2CDepartmentListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.departments.size());
        for (String dept : packet.departments) {
            buffer.writeUtf(dept);
        }
    }

    public static S2CDepartmentListPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<String> departments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            departments.add(buffer.readUtf());
        }
        return new S2CDepartmentListPacket(departments);
    }

    public static void handle(S2CDepartmentListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DepartmentClientState.setSearchResults(packet.departments);
        });
        ctx.get().setPacketHandled(true);
    }
}

