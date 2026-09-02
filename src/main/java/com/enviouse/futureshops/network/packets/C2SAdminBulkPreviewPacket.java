package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.AdminBulkListingIdentity;
import com.enviouse.futureshops.catalog.AdminBulkListingPlanner;
import com.enviouse.futureshops.catalog.AdminBulkListingService;
import com.enviouse.futureshops.network.ShopPackets;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Client proposal for a server-authoritative bulk shop preview. */
public record C2SAdminBulkPreviewPacket(
        UUID requestId,
        List<AdminBulkListingPlanner.Selection> selections,
        String categoryId,
        String priceText,
        String stockText,
        String registryFingerprint) {
    public static final int MAX_TEXT = 256;

    public C2SAdminBulkPreviewPacket {
        if (requestId == null || requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("bulk preview request id is required");
        }
        selections = List.copyOf(selections == null ? List.of() : selections);
        if (selections.size() > AdminBulkListingPlanner.MAX_SELECTIONS) {
            throw new IllegalArgumentException("bulk preview selection is too large");
        }
        categoryId = bounded(categoryId, MAX_TEXT);
        priceText = bounded(priceText, AdminBulkListingPlanner.MAX_PRICE_TEXT);
        stockText = bounded(stockText, MAX_TEXT);
        registryFingerprint = bounded(registryFingerprint, 128);
    }

    public static void encode(C2SAdminBulkPreviewPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.selections().size());
        for (AdminBulkListingPlanner.Selection selection : packet.selections()) {
            buffer.writeUtf(selection.itemId(), MAX_TEXT);
            buffer.writeUtf(selection.nbt(), AdminBulkListingIdentity.MAX_NBT_BYTES);
            buffer.writeUtf(selection.displayName(), MAX_TEXT);
        }
        buffer.writeUtf(packet.categoryId(), MAX_TEXT);
        buffer.writeUtf(packet.priceText(), AdminBulkListingPlanner.MAX_PRICE_TEXT);
        buffer.writeUtf(packet.stockText(), MAX_TEXT);
        buffer.writeUtf(packet.registryFingerprint(), 128);
    }

    public static C2SAdminBulkPreviewPacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            int count = buffer.readVarInt();
            if (count < 0 || count > AdminBulkListingPlanner.MAX_SELECTIONS) {
                throw new DecoderException("bulk preview selection count is invalid");
            }
            List<AdminBulkListingPlanner.Selection> selections = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                selections.add(new AdminBulkListingPlanner.Selection(
                        buffer.readUtf(MAX_TEXT),
                        buffer.readUtf(AdminBulkListingIdentity.MAX_NBT_BYTES),
                        buffer.readUtf(MAX_TEXT)));
            }
            return new C2SAdminBulkPreviewPacket(requestId, selections,
                    buffer.readUtf(MAX_TEXT),
                    buffer.readUtf(AdminBulkListingPlanner.MAX_PRICE_TEXT),
                    buffer.readUtf(MAX_TEXT), buffer.readUtf(128));
        } catch (RuntimeException exception) {
            throw new DecoderException("bulk preview request is malformed", exception);
        }
    }

    public static void handle(C2SAdminBulkPreviewPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            AdminBulkListingService.PreviewResult result = AdminBulkListingService.preview(
                    player, new AdminBulkListingService.PreviewRequest(
                            packet.requestId(), packet.selections(), packet.categoryId(),
                            packet.priceText(), packet.stockText(), packet.registryFingerprint()));
            if (player != null) {
                ShopPackets.sendToPlayer(player, S2CAdminBulkResultPacket.preview(packet.requestId(), result));
            }
        });
        context.setPacketHandled(true);
    }

    private static String bounded(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maximum) {
            throw new IllegalArgumentException("bulk preview text is too long");
        }
        return result;
    }
}
