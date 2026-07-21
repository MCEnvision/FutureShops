package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.escrow.runtime.BazaarActionService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SBazaarRegisterProductPacket(
        UUID requestId,
        UUID routeNonce,
        String registryItemId
) {
    private static final UUID ZERO = new UUID(0L, 0L);
    private static final int MAXIMUM_ITEM_ID_LENGTH = 256;

    public C2SBazaarRegisterProductPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        registryItemId = Objects.requireNonNull(
                registryItemId, "registryItemId").strip();
        if (ZERO.equals(requestId) || ZERO.equals(routeNonce)) {
            throw new IllegalArgumentException(
                    "Bazaar product registration identifiers are required");
        }
        if (registryItemId.isEmpty()
                || registryItemId.length() > MAXIMUM_ITEM_ID_LENGTH
                || net.minecraft.resources.ResourceLocation.tryParse(
                registryItemId) == null) {
            throw new IllegalArgumentException(
                    "Bazaar product registry item is invalid");
        }
    }

    public static void encode(
            C2SBazaarRegisterProductPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUtf(packet.registryItemId(), MAXIMUM_ITEM_ID_LENGTH);
    }

    public static C2SBazaarRegisterProductPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new C2SBazaarRegisterProductPacket(
                    buffer.readUUID(), buffer.readUUID(),
                    buffer.readUtf(MAXIMUM_ITEM_ID_LENGTH));
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bazaar product registration request is invalid",
                    exception);
        }
    }

    public static void handle(
            C2SBazaarRegisterProductPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BazaarActionService.registerProduct(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
