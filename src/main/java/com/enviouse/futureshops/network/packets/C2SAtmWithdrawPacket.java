package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.server.economy.AtmService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public record C2SAtmWithdrawPacket(
        UUID requestId,
        String currencySignature,
        List<Integer> denominationCounts
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

    public C2SAtmWithdrawPacket {
        Objects.requireNonNull(requestId, "requestId");
        if (requestId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException("ATM request ID is invalid");
        }
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        if (!SIGNATURE.matcher(currencySignature).matches()) {
            throw new IllegalArgumentException(
                    "ATM currency signature is invalid");
        }
        denominationCounts = List.copyOf(Objects.requireNonNull(
                denominationCounts, "denominationCounts"));
        if (denominationCounts.isEmpty()
                || denominationCounts.size()
                > CurrencyWithdrawalService.MAX_DENOMINATIONS) {
            throw new IllegalArgumentException(
                    "ATM denomination count is invalid");
        }
        int selected = 0;
        for (Integer count : denominationCounts) {
            if (count == null || count < 0
                    || count > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "ATM denomination selection is invalid");
            }
            selected = Math.addExact(selected, count);
            if (selected > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                throw new IllegalArgumentException(
                        "ATM denomination selection exceeds its limit");
            }
        }
        if (selected == 0) {
            throw new IllegalArgumentException(
                    "ATM denomination selection is empty");
        }
    }

    public static void encode(C2SAtmWithdrawPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.currencySignature(), 64);
        buffer.writeVarInt(packet.denominationCounts().size());
        for (int count : packet.denominationCounts()) {
            buffer.writeVarInt(count);
        }
    }

    public static C2SAtmWithdrawPacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            String signature = buffer.readUtf(64);
            int size = buffer.readVarInt();
            if (size <= 0
                    || size > CurrencyWithdrawalService.MAX_DENOMINATIONS) {
                throw new DecoderException(
                        "ATM denomination count is invalid");
            }
            List<Integer> counts = new ArrayList<>(size);
            int selected = 0;
            for (int index = 0; index < size; index++) {
                int count = buffer.readVarInt();
                if (count < 0
                        || count > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                    throw new DecoderException(
                            "ATM denomination selection is invalid");
                }
                selected = Math.addExact(selected, count);
                if (selected > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
                    throw new DecoderException(
                            "ATM denomination selection exceeds its limit");
                }
                counts.add(count);
            }
            return new C2SAtmWithdrawPacket(requestId, signature, counts);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("ATM withdrawal packet is invalid",
                    exception);
        }
    }

    public static void handle(C2SAtmWithdrawPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AtmService.withdraw(player, packet.requestId(),
                        packet.currencySignature(),
                        packet.denominationCounts());
            }
        });
        context.setPacketHandled(true);
    }
}
