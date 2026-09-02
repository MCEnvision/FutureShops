package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.AdminBulkListingPlanner;
import com.enviouse.futureshops.catalog.AdminBulkListingService;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Server result for a bulk preview or commit. The packet contains no raw catalog content. */
public record S2CAdminBulkResultPacket(
        UUID requestId,
        boolean preview,
        AdminBulkListingService.Status status,
        String message,
        String registryFingerprint,
        String catalogFingerprint,
        String previewFingerprint,
        long priceMinor,
        int stock,
        List<Row> rows) {
    private static final int MAX_TEXT = 512;
    private static final int MAX_ROWS = AdminBulkListingPlanner.MAX_SELECTIONS;

    public S2CAdminBulkResultPacket {
        if (requestId == null || requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("bulk result request id is required");
        }
        status = java.util.Objects.requireNonNull(status, "status");
        message = bounded(message, MAX_TEXT);
        registryFingerprint = bounded(registryFingerprint, 128);
        catalogFingerprint = bounded(catalogFingerprint, 128);
        previewFingerprint = bounded(previewFingerprint, 128);
        rows = List.copyOf(rows == null ? List.of() : rows);
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("bulk result has too many rows");
        }
    }

    public record Row(int ordinal, String itemId, String nbt, String canonicalNbt,
                      String identityDigest, String listingId, String displayName,
                      AdminBulkListingPlanner.Action action, String reason,
                      boolean replaceEligible) {
        public Row {
            itemId = bounded(itemId, C2SAdminBulkPreviewPacket.MAX_TEXT);
            nbt = bounded(nbt, com.enviouse.futureshops.catalog.AdminBulkListingIdentity.MAX_NBT_BYTES);
            canonicalNbt = bounded(canonicalNbt, com.enviouse.futureshops.catalog.AdminBulkListingIdentity.MAX_NBT_BYTES);
            identityDigest = bounded(identityDigest, 128);
            listingId = bounded(listingId, C2SAdminBulkPreviewPacket.MAX_TEXT);
            displayName = bounded(displayName, C2SAdminBulkPreviewPacket.MAX_TEXT);
            action = java.util.Objects.requireNonNull(action, "action");
            reason = bounded(reason, MAX_TEXT);
        }

        static Row from(AdminBulkListingPlanner.Row row) {
            return new Row(row.ordinal(), row.itemId(), row.nbt(), row.canonicalNbt(),
                    row.identityDigest(), row.listingId(), row.displayName(), row.action(),
                    row.reason(), row.replaceEligible());
        }
    }

    public static S2CAdminBulkResultPacket preview(UUID requestId,
                                                     AdminBulkListingService.PreviewResult result) {
        AdminBulkListingPlanner.Preview value = result.preview();
        return from(requestId, true, result.status(), result.message(), value);
    }

    public static S2CAdminBulkResultPacket commit(UUID requestId,
                                                    AdminBulkListingService.CommitResult result) {
        AdminBulkListingPlanner.Preview value = result.preview();
        return from(requestId, false, result.status(), result.message(), value);
    }

    private static S2CAdminBulkResultPacket from(UUID requestId, boolean preview,
                                                 AdminBulkListingService.Status status,
                                                 String message,
                                                 AdminBulkListingPlanner.Preview value) {
        return new S2CAdminBulkResultPacket(requestId, preview, status, message,
                value == null ? "" : value.registryFingerprint(),
                value == null ? "" : value.catalogFingerprint(),
                value == null ? "" : value.fingerprint(),
                value == null ? 0L : value.priceMinor(),
                value == null ? 0 : value.stock(),
                value == null ? List.of() : value.rows().stream().map(Row::from).toList());
    }

    public static void encode(S2CAdminBulkResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeBoolean(packet.preview());
        buffer.writeUtf(packet.status().name(), 32);
        buffer.writeUtf(packet.message(), MAX_TEXT);
        buffer.writeUtf(packet.registryFingerprint(), 128);
        buffer.writeUtf(packet.catalogFingerprint(), 128);
        buffer.writeUtf(packet.previewFingerprint(), 128);
        buffer.writeLong(packet.priceMinor());
        buffer.writeVarInt(packet.stock());
        buffer.writeVarInt(packet.rows().size());
        for (Row row : packet.rows()) {
            buffer.writeVarInt(row.ordinal());
            buffer.writeUtf(row.itemId(), C2SAdminBulkPreviewPacket.MAX_TEXT);
            buffer.writeUtf(row.nbt(), com.enviouse.futureshops.catalog.AdminBulkListingIdentity.MAX_NBT_BYTES);
            buffer.writeUtf(row.canonicalNbt(), com.enviouse.futureshops.catalog.AdminBulkListingIdentity.MAX_NBT_BYTES);
            buffer.writeUtf(row.identityDigest(), 128);
            buffer.writeUtf(row.listingId(), C2SAdminBulkPreviewPacket.MAX_TEXT);
            buffer.writeUtf(row.displayName(), C2SAdminBulkPreviewPacket.MAX_TEXT);
            buffer.writeUtf(row.action().name(), 16);
            buffer.writeUtf(row.reason(), MAX_TEXT);
            buffer.writeBoolean(row.replaceEligible());
        }
    }

    public static S2CAdminBulkResultPacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            boolean preview = buffer.readBoolean();
            AdminBulkListingService.Status status = AdminBulkListingService.Status.valueOf(
                    buffer.readUtf(32).toUpperCase(Locale.ROOT));
            String message = buffer.readUtf(MAX_TEXT);
            String registryFingerprint = buffer.readUtf(128);
            String catalogFingerprint = buffer.readUtf(128);
            String previewFingerprint = buffer.readUtf(128);
            long priceMinor = buffer.readLong();
            int stock = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ROWS) {
                throw new DecoderException("bulk result row count is invalid");
            }
            List<Row> rows = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                rows.add(new Row(buffer.readVarInt(),
                        buffer.readUtf(C2SAdminBulkPreviewPacket.MAX_TEXT),
                        buffer.readUtf(com.enviouse.futureshops.catalog.AdminBulkListingIdentity.MAX_NBT_BYTES),
                        buffer.readUtf(com.enviouse.futureshops.catalog.AdminBulkListingIdentity.MAX_NBT_BYTES),
                        buffer.readUtf(128),
                        buffer.readUtf(C2SAdminBulkPreviewPacket.MAX_TEXT),
                        buffer.readUtf(C2SAdminBulkPreviewPacket.MAX_TEXT),
                        AdminBulkListingPlanner.Action.valueOf(buffer.readUtf(16).toUpperCase(Locale.ROOT)),
                        buffer.readUtf(MAX_TEXT), buffer.readBoolean()));
            }
            return new S2CAdminBulkResultPacket(requestId, preview, status, message,
                    registryFingerprint, catalogFingerprint, previewFingerprint,
                    priceMinor, stock, rows);
        } catch (RuntimeException exception) {
            throw new DecoderException("bulk result is malformed", exception);
        }
    }

    public static void handle(S2CAdminBulkResultPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleAdminBulkResult(packet)));
        context.setPacketHandled(true);
    }

    private static String bounded(String value, int maximum) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maximum) {
            throw new IllegalArgumentException("bulk result text is too long");
        }
        return result;
    }
}
