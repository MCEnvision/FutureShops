package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.economy.AtmService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SAtmDepositRecoveryPacket(
        UUID requestId,
        UUID transactionId
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SAtmDepositRecoveryPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(transactionId, "transactionId");
        if (requestId.equals(ZERO_UUID) || transactionId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(
                    "ATM deposit recovery identity is invalid");
        }
    }

    public static void encode(
            C2SAtmDepositRecoveryPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.transactionId());
    }

    public static C2SAtmDepositRecoveryPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new C2SAtmDepositRecoveryPacket(
                    buffer.readUUID(), buffer.readUUID());
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "ATM deposit recovery packet is invalid", exception);
        }
    }

    public static void handle(
            C2SAtmDepositRecoveryPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AtmService.checkDepositRecovery(
                        player, packet.requestId(), packet.transactionId());
            }
        });
        context.setPacketHandled(true);
    }
}
