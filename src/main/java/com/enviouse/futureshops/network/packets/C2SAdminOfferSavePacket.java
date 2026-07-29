package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.shop.AdminShopOfferSaveSavedData;
import com.enviouse.futureshops.server.security.ServerRequestAction;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

public record C2SAdminOfferSavePacket(
        UUID requestId,
        String shopId,
        String listingId,
        long expectedRevision,
        AdminShopOfferConfigWriter.Operation operation,
        Optional<ServerShopOfferListing> candidate
) {
    private static final int MAX_IDENTIFIER = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SAdminOfferSavePacket {
        requestId = java.util.Objects.requireNonNull(
                requestId, "requestId");
        operation = java.util.Objects.requireNonNull(
                operation, "operation");
        shopId = identifier(shopId);
        listingId = operation
                == AdminShopOfferConfigWriter.Operation.CREATE
                ? java.util.Objects.requireNonNullElse(
                listingId, "").strip() : identifier(listingId);
        candidate = java.util.Objects.requireNonNull(
                candidate, "candidate");
        if (requestId.equals(ZERO_UUID)
                || expectedRevision < 0L
                || expectedRevision
                > com.enviouse.futureshops.server.escrow.runtime
                .ServerShopOfferCommit.MAX_REVISION
                || operation == AdminShopOfferConfigWriter.Operation.REMOVE
                != candidate.isEmpty()) {
            throw new IllegalArgumentException(
                    "Admin offer save request is invalid");
        }
    }

    public static void encode(
            C2SAdminOfferSavePacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId);
        buffer.writeUtf(packet.shopId, MAX_IDENTIFIER);
        buffer.writeUtf(packet.listingId, MAX_IDENTIFIER);
        buffer.writeVarLong(packet.expectedRevision);
        buffer.writeUtf(packet.operation.name(), 32);
        buffer.writeBoolean(packet.candidate.isPresent());
        packet.candidate.ifPresent(value ->
                ServerShopOfferNetworkCodec.encodeListing(
                        buffer, value));
    }

    public static C2SAdminOfferSavePacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            String shopId = buffer.readUtf(MAX_IDENTIFIER);
            String listingId = buffer.readUtf(MAX_IDENTIFIER);
            long revision = buffer.readVarLong();
            AdminShopOfferConfigWriter.Operation operation =
                    AdminShopOfferConfigWriter.Operation.valueOf(
                            buffer.readUtf(32)
                                    .toUpperCase(Locale.ROOT));
            Optional<ServerShopOfferListing> candidate =
                    buffer.readBoolean()
                            ? Optional.of(ServerShopOfferNetworkCodec
                            .decodeListing(buffer))
                            : Optional.empty();
            return new C2SAdminOfferSavePacket(
                    requestId, shopId, listingId, revision,
                    operation, candidate);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Admin offer save request is malformed", exception);
        }
    }

    public static void handle(
            C2SAdminOfferSavePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                if (player != null) {
                    ShopPackets.sendToPlayer(player,
                            S2CAdminOfferSaveResultPacket.denied(
                                    packet.requestId));
                }
                return;
            }
            String fingerprint = packet.fingerprint();
            Optional<AdminShopOfferSaveSavedData.Receipt> prior =
                    AdminShopOfferSaveSavedData.get(
                            player.getServer()).find(packet.requestId);
            if (prior.isPresent()) {
                AdminShopOfferSaveSavedData.Receipt receipt =
                        prior.orElseThrow();
                if (receipt.playerId().equals(player.getUUID())
                        && receipt.requestFingerprint()
                        .equals(fingerprint)) {
                    ShopPackets.sendToPlayer(player, receipt.packet());
                } else {
                    ShopPackets.sendToPlayer(player,
                            S2CAdminOfferSaveResultPacket.conflict(
                                    packet.requestId));
                }
                return;
            }
            Optional<AdminShopOfferConfigWriter.DurableReceipt>
                    durable;
            try {
                durable = AdminShopOfferConfigWriter
                        .resolveDurableReceipt(
                                player.getServer(),
                                packet.requestId);
            } catch (RuntimeException exception) {
                ShopPackets.sendToPlayer(player,
                        S2CAdminOfferSaveResultPacket.unavailable(
                                packet.requestId));
                return;
            }
            if (durable.isPresent()) {
                AdminShopOfferConfigWriter.DurableReceipt receipt =
                        durable.orElseThrow();
                if (!receipt.playerId().equals(player.getUUID())
                        || !receipt.requestFingerprint()
                        .equals(fingerprint)) {
                    ShopPackets.sendToPlayer(player,
                            S2CAdminOfferSaveResultPacket.conflict(
                                    packet.requestId));
                    return;
                }
                S2CAdminOfferSaveResultPacket response =
                        S2CAdminOfferSaveResultPacket.from(
                                packet.requestId, receipt.result());
                if (receipt.status()
                        != AdminShopOfferConfigWriter.Status.UNAVAILABLE) {
                    AdminShopOfferSaveSavedData.get(
                            player.getServer()).record(
                            AdminShopOfferSaveSavedData.Receipt.capture(
                                    player.getUUID(), fingerprint,
                                    response));
                }
                ShopPackets.sendToPlayer(player, response);
                return;
            }
            if (!ServerRequestSecurityManager.tryAcquire(
                    player, ServerRequestAction.SERVER_SHOP_OFFER_ADMIN)
                    .allowed()) {
                ShopPackets.sendToPlayer(player,
                        S2CAdminOfferSaveResultPacket.unavailable(
                                packet.requestId));
                return;
            }
            AdminShopOfferConfigWriter.SaveResult result =
                    AdminShopOfferConfigWriter.save(
                            player.getServer(), packet.operation,
                            packet.shopId,
                            packet.listingId, packet.expectedRevision,
                            packet.candidate.orElse(null),
                            new AdminShopOfferConfigWriter.MutationIdentity(
                                    packet.requestId, player.getUUID(),
                                    fingerprint));
            S2CAdminOfferSaveResultPacket response =
                    S2CAdminOfferSaveResultPacket.from(
                            packet.requestId, result);
            if (result.status()
                    != AdminShopOfferConfigWriter.Status.UNAVAILABLE
                    && result.status()
                    != AdminShopOfferConfigWriter.Status.IO_ERROR) {
                AdminShopOfferSaveSavedData.get(
                        player.getServer()).record(
                        AdminShopOfferSaveSavedData.Receipt.capture(
                                player.getUUID(), fingerprint,
                                response));
            }
            ShopPackets.sendToPlayer(player, response);
            if (result.success()) {
                ShopDataService.resendActiveSessions(
                        player.getServer(), false);
            }
        });
        context.setPacketHandled(true);
    }

    private static String identifier(String value) {
        String candidate = java.util.Objects.requireNonNull(
                value, "identifier").strip();
        if (candidate.isEmpty() || candidate.length() > MAX_IDENTIFIER
                || !candidate.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(
                    "Admin offer identifier is invalid");
        }
        return candidate;
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops admin offer save v1");
            output.writeUTF(shopId);
            output.writeUTF(listingId);
            output.writeLong(expectedRevision);
            output.writeByte(operation.ordinal());
            output.writeBoolean(candidate.isPresent());
            if (candidate.isPresent()) {
                byte[] listing = ServerShopOfferNetworkCodec
                        .encodeListingBytes(candidate.orElseThrow());
                output.writeInt(listing.length);
                output.write(listing);
            }
            output.flush();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(bytes.toByteArray()));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint admin offer save request",
                    exception);
        }
    }
}
