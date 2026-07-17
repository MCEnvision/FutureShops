package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.economy.AtmService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public record C2SAtmDepositPacket(
        UUID requestId,
        String currencySignature,
        Source source,
        OptionalLong requestedMinorUnits
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> SOURCES = Set.of(
            "INVENTORY", "MAIN_HAND", "OFF_HAND");

    public C2SAtmDepositPacket {
        Objects.requireNonNull(requestId, "requestId");
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        Objects.requireNonNull(source, "source");
        requestedMinorUnits = Objects.requireNonNull(
                requestedMinorUnits, "requestedMinorUnits");
        if (requestId.equals(ZERO_UUID)
                || !SIGNATURE.matcher(currencySignature).matches()
                || requestedMinorUnits.isPresent()
                && requestedMinorUnits.getAsLong() <= 0L) {
            throw new IllegalArgumentException(
                    "ATM deposit request is invalid");
        }
    }

    public static void encode(
            C2SAtmDepositPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.currencySignature(), 64);
        buffer.writeUtf(packet.source().name(), 16);
        buffer.writeBoolean(packet.requestedMinorUnits().isPresent());
        if (packet.requestedMinorUnits().isPresent()) {
            buffer.writeLong(packet.requestedMinorUnits().getAsLong());
        }
    }

    public static C2SAtmDepositPacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            String signature = buffer.readUtf(64);
            String sourceName = buffer.readUtf(16);
            if (!SOURCES.contains(sourceName)) {
                throw new DecoderException(
                        "ATM deposit source is invalid");
            }
            OptionalLong amount = buffer.readBoolean()
                    ? OptionalLong.of(buffer.readLong())
                    : OptionalLong.empty();
            return new C2SAtmDepositPacket(
                    requestId, signature, Source.valueOf(sourceName), amount);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "ATM deposit packet is invalid", exception);
        }
    }

    public static void handle(
            C2SAtmDepositPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AtmService.deposit(player, packet.requestId(),
                        packet.currencySignature(), packet.source(),
                        packet.requestedMinorUnits());
            }
        });
        context.setPacketHandled(true);
    }

    public enum Source {
        INVENTORY,
        MAIN_HAND,
        OFF_HAND
    }
}
