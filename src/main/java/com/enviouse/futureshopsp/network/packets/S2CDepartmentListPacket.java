package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.DepartmentClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: Department search results for the department picker.
 */
public class S2CDepartmentListPacket implements CustomPacketPayload {
    public static final Type<S2CDepartmentListPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cdepartmentlistpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDepartmentListPacket> STREAM_CODEC = StreamCodec.ofMember(S2CDepartmentListPacket::encode, S2CDepartmentListPacket::decode);

    @Override
    public Type<S2CDepartmentListPacket> type() {
        return TYPE;
    }

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

    public static void handle(S2CDepartmentListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DepartmentClientState.setSearchResults(packet.departments);
        });
    }
}

