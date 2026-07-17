package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.market.MarketModuleService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SCloseMarketSessionPacket(
        UUID routeNonce,
        boolean allRoutes
) {
    public C2SCloseMarketSessionPacket {
        routeNonce = Objects.requireNonNull(routeNonce, "routeNonce");
        if (routeNonce.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "Market close route is invalid");
        }
    }

    public static void encode(
            C2SCloseMarketSessionPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.routeNonce());
        buffer.writeBoolean(packet.allRoutes());
    }

    public static C2SCloseMarketSessionPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new C2SCloseMarketSessionPacket(buffer.readUUID(),
                    buffer.readBoolean());
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market close request is invalid", exception);
        }
    }

    public static void handle(
            C2SCloseMarketSessionPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                if (packet.allRoutes()) {
                    MarketModuleService.close(player.getUUID());
                } else {
                    MarketModuleService.close(player.getUUID(),
                            packet.routeNonce());
                }
            }
        });
        context.setPacketHandled(true);
    }
}
