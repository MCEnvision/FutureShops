package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.market.MarketModuleService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SOpenMarketModulePacket(
        UUID requestId,
        String moduleId,
        String view
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SOpenMarketModulePacket {
        requestId = Objects.requireNonNull(requestId, "requestId");
        moduleId = requireText(moduleId, 32);
        view = requireText(view, 32);
        if (requestId.equals(ZERO)) {
            throw new IllegalArgumentException("Market open request identity is invalid");
        }
    }

    public static void encode(C2SOpenMarketModulePacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.moduleId(), 32);
        buffer.writeUtf(packet.view(), 32);
    }

    public static C2SOpenMarketModulePacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SOpenMarketModulePacket(buffer.readUUID(),
                    buffer.readUtf(32), buffer.readUtf(32));
        } catch (RuntimeException exception) {
            throw new DecoderException("Market open request is invalid", exception);
        }
    }

    public static void handle(C2SOpenMarketModulePacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MarketModuleService.open(player, packet.moduleId(),
                        packet.view(), packet.requestId());
            }
        });
        context.setPacketHandled(true);
    }

    private static String requireText(String value, int maximum) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException("Market open text is invalid");
        }
        return normalized;
    }
}
