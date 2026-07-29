package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CAdminOfferSaveResultPacket(
        UUID requestId,
        AdminShopOfferConfigWriter.Status status,
        boolean success,
        long revision,
        Optional<ServerShopOfferListing> snapshot,
        List<OfferValidationIssue> issues
) {
    private static final int MAX_ISSUES = 128;
    private static final int MAX_PATH = 512;
    private static final int MAX_CODE = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public S2CAdminOfferSaveResultPacket {
        requestId = java.util.Objects.requireNonNull(
                requestId, "requestId");
        status = java.util.Objects.requireNonNull(status, "status");
        snapshot = java.util.Objects.requireNonNull(snapshot, "snapshot");
        issues = List.copyOf(issues);
        if (requestId.equals(ZERO_UUID)
                || revision < 0L
                || issues.size() > MAX_ISSUES
                || success != (status
                == AdminShopOfferConfigWriter.Status.SUCCESS)) {
            throw new IllegalArgumentException(
                    "Admin offer save result is invalid");
        }
    }

    public static S2CAdminOfferSaveResultPacket from(
            UUID requestId,
            AdminShopOfferConfigWriter.SaveResult result
    ) {
        return new S2CAdminOfferSaveResultPacket(
                requestId, result.status(), result.success(),
                result.revision(), Optional.ofNullable(
                result.snapshot()), result.issues());
    }

    public static S2CAdminOfferSaveResultPacket denied(UUID requestId) {
        return new S2CAdminOfferSaveResultPacket(
                requestId,
                AdminShopOfferConfigWriter.Status.CONFLICT,
                false, 0L, Optional.empty(), List.of(
                new OfferValidationIssue(
                        OfferValidationIssue.Severity.ERROR,
                        "permission", "offer.save.denied")));
    }

    public static S2CAdminOfferSaveResultPacket conflict(UUID requestId) {
        return new S2CAdminOfferSaveResultPacket(
                requestId,
                AdminShopOfferConfigWriter.Status.CONFLICT,
                false, 0L, Optional.empty(), List.of(
                new OfferValidationIssue(
                        OfferValidationIssue.Severity.ERROR,
                        "requestId", "offer.save.request_conflict")));
    }

    public static S2CAdminOfferSaveResultPacket unavailable(
            UUID requestId
    ) {
        return new S2CAdminOfferSaveResultPacket(
                requestId,
                AdminShopOfferConfigWriter.Status.UNAVAILABLE,
                false, 0L, Optional.empty(), List.of(
                new OfferValidationIssue(
                        OfferValidationIssue.Severity.ERROR,
                        "requestId", "offer.save.rate_limited")));
    }

    public static void encode(
            S2CAdminOfferSaveResultPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId);
        buffer.writeUtf(packet.status.name(), 32);
        buffer.writeBoolean(packet.success);
        buffer.writeVarLong(packet.revision);
        buffer.writeBoolean(packet.snapshot.isPresent());
        packet.snapshot.ifPresent(value ->
                ServerShopOfferNetworkCodec.encodeListing(
                        buffer, value));
        buffer.writeVarInt(packet.issues.size());
        for (OfferValidationIssue issue : packet.issues) {
            buffer.writeUtf(issue.severity().name(), 16);
            buffer.writeUtf(issue.path(), MAX_PATH);
            buffer.writeUtf(issue.code(), MAX_CODE);
        }
    }

    public static S2CAdminOfferSaveResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            AdminShopOfferConfigWriter.Status status =
                    AdminShopOfferConfigWriter.Status.valueOf(
                            buffer.readUtf(32)
                                    .toUpperCase(Locale.ROOT));
            boolean success = buffer.readBoolean();
            long revision = buffer.readVarLong();
            Optional<ServerShopOfferListing> snapshot =
                    buffer.readBoolean()
                            ? Optional.of(ServerShopOfferNetworkCodec
                            .decodeListing(buffer))
                            : Optional.empty();
            int issueCount = buffer.readVarInt();
            if (issueCount < 0 || issueCount > MAX_ISSUES) {
                throw new DecoderException(
                        "Admin offer issue count is invalid");
            }
            List<OfferValidationIssue> issues =
                    new ArrayList<>(issueCount);
            for (int index = 0; index < issueCount; index++) {
                issues.add(new OfferValidationIssue(
                        OfferValidationIssue.Severity.valueOf(
                                buffer.readUtf(16)
                                        .toUpperCase(Locale.ROOT)),
                        buffer.readUtf(MAX_PATH),
                        buffer.readUtf(MAX_CODE)));
            }
            return new S2CAdminOfferSaveResultPacket(
                    requestId, status, success, revision,
                    snapshot, issues);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Admin offer save result is malformed", exception);
        }
    }

    public static void handle(
            S2CAdminOfferSaveResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler
                                .handleAdminOfferSaveResult(packet)));
        context.setPacketHandled(true);
    }
}
