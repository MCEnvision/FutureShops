package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.shop.BulkSellService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SBulkSellCommitPacket(
        UUID quoteId,
        List<String> selectedLineIds
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SBulkSellCommitPacket {
        quoteId = Objects.requireNonNull(quoteId, "quoteId");
        selectedLineIds = List.copyOf(Objects.requireNonNull(
                selectedLineIds, "selectedLineIds"));
        if (ZERO_UUID.equals(quoteId)
                || selectedLineIds.isEmpty()
                || selectedLineIds.size() > BulkSellQuote.MAX_LINES
                || new LinkedHashSet<>(selectedLineIds).size()
                != selectedLineIds.size()
                || selectedLineIds.stream().anyMatch(value ->
                value == null || value.isBlank()
                        || value.length()
                        > BulkSellQuote.MAX_TEXT_LENGTH)) {
            throw new IllegalArgumentException(
                    "Bulk sell commit request is invalid");
        }
    }

    public static void encode(
            C2SBulkSellCommitPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.quoteId);
        buffer.writeVarInt(packet.selectedLineIds.size());
        for (String lineId : packet.selectedLineIds) {
            buffer.writeUtf(lineId,
                    BulkSellQuote.MAX_TEXT_LENGTH);
        }
    }

    public static C2SBulkSellCommitPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID quoteId = buffer.readUUID();
            int count = BulkSellPacketCodec.boundedCount(
                    buffer.readVarInt(), BulkSellQuote.MAX_LINES,
                    "bulk sell selected lines");
            List<String> selected = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                selected.add(buffer.readUtf(
                        BulkSellQuote.MAX_TEXT_LENGTH));
            }
            C2SBulkSellCommitPacket packet =
                    new C2SBulkSellCommitPacket(quoteId, selected);
            BulkSellPacketCodec.requireComplete(
                    buffer, "Bulk sell commit request");
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bulk sell commit request is malformed",
                    exception);
        }
    }

    public static void handle(
            C2SBulkSellCommitPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            BulkSellService.CommitResult result =
                    BulkSellService.commit(
                            player, packet.quoteId,
                            packet.selectedLineIds);
            ShopPackets.sendToPlayer(player,
                    S2CBulkSellResultPacket.from(
                            result,
                            BalanceManagerBridge.currencyName(),
                            BalanceManagerBridge.currencyDecimals()));
        });
        context.setPacketHandled(true);
    }

    private static final class BalanceManagerBridge {
        private static String currencyName() {
            return com.enviouse.futureshops.server.economy
                    .BalanceManager.getProvider().getCurrencyName();
        }

        private static int currencyDecimals() {
            return com.enviouse.futureshops.server.economy
                    .BalanceManager.getProvider().getDecimalPlaces();
        }
    }
}
