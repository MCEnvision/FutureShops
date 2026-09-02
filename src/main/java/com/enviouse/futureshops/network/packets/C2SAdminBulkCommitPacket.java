package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.AdminBulkListingPlanner;
import com.enviouse.futureshops.catalog.AdminBulkListingService;
import com.enviouse.futureshops.network.ShopPackets;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Client confirmation for one previously previewed bulk catalog mutation. */
public record C2SAdminBulkCommitPacket(
        UUID requestId,
        String previewFingerprint,
        String catalogFingerprint,
        String registryFingerprint,
        Set<String> replaceListingIds) {
    private static final int MAX_TEXT = 256;
    private static final int MAX_REPLACEMENTS = AdminBulkListingPlanner.MAX_SELECTIONS;

    public C2SAdminBulkCommitPacket {
        if (requestId == null || requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("bulk commit request id is required");
        }
        previewFingerprint = bounded(previewFingerprint, 128);
        catalogFingerprint = bounded(catalogFingerprint, 128);
        registryFingerprint = bounded(registryFingerprint, 128);
        replaceListingIds = Set.copyOf(replaceListingIds == null ? Set.of() : replaceListingIds);
        if (replaceListingIds.size() > MAX_REPLACEMENTS) {
            throw new IllegalArgumentException("too many bulk replacements");
        }
    }

    public static void encode(C2SAdminBulkCommitPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.previewFingerprint(), 128);
        buffer.writeUtf(packet.catalogFingerprint(), 128);
        buffer.writeUtf(packet.registryFingerprint(), 128);
        buffer.writeVarInt(packet.replaceListingIds().size());
        packet.replaceListingIds().forEach(value -> buffer.writeUtf(value, MAX_TEXT));
    }

    public static C2SAdminBulkCommitPacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            String previewFingerprint = buffer.readUtf(128);
            String catalogFingerprint = buffer.readUtf(128);
            String registryFingerprint = buffer.readUtf(128);
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_REPLACEMENTS) {
                throw new DecoderException("bulk commit replacement count is invalid");
            }
            Set<String> replacements = new LinkedHashSet<>();
            for (int index = 0; index < count; index++) {
                replacements.add(buffer.readUtf(MAX_TEXT));
            }
            return new C2SAdminBulkCommitPacket(requestId, previewFingerprint,
                    catalogFingerprint, registryFingerprint, replacements);
        } catch (RuntimeException exception) {
            throw new DecoderException("bulk commit request is malformed", exception);
        }
    }

    public static void handle(C2SAdminBulkCommitPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            AdminBulkListingService.CommitResult result = AdminBulkListingService.commit(
                    player, new AdminBulkListingService.CommitRequest(
                            packet.requestId(), packet.previewFingerprint(),
                            packet.catalogFingerprint(), packet.registryFingerprint(),
                            packet.replaceListingIds()));
            if (player != null) {
                ShopPackets.sendToPlayer(player, S2CAdminBulkResultPacket.commit(
                        packet.requestId(), result));
            }
        });
        context.setPacketHandled(true);
    }

    private static String bounded(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maximum) {
            throw new IllegalArgumentException("bulk commit fingerprint is too long");
        }
        return result;
    }
}
