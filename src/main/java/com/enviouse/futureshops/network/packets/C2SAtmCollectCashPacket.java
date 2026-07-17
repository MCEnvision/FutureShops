package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.economy.AtmService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SAtmCollectCashPacket(
        UUID requestId,
        List<UUID> claimIds
) {
    public static final int MAX_CLAIMS = 4;
    private static final byte[] REQUEST_ID_DOMAIN =
            "futureshops.atm.cash.collection.v1"
                    .getBytes(StandardCharsets.UTF_8);

    public C2SAtmCollectCashPacket {
        Objects.requireNonNull(requestId, "requestId");
        claimIds = requireClaimIds(claimIds);
        if (requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "ATM cash collection request is invalid");
        }
    }

    public static UUID deriveRequestId(
            UUID playerId,
            List<UUID> claimIds
    ) {
        UUID owner = Objects.requireNonNull(playerId, "playerId");
        if (owner.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "ATM cash collection player is invalid");
        }
        List<UUID> exactClaimIds = requireClaimIds(claimIds);
        ByteBuffer identity = ByteBuffer.allocate(
                Integer.BYTES + REQUEST_ID_DOMAIN.length
                        + Long.BYTES * 2 + Integer.BYTES
                        + exactClaimIds.size() * Long.BYTES * 2);
        identity.putInt(REQUEST_ID_DOMAIN.length);
        identity.put(REQUEST_ID_DOMAIN);
        identity.putLong(owner.getMostSignificantBits());
        identity.putLong(owner.getLeastSignificantBits());
        identity.putInt(exactClaimIds.size());
        for (UUID claimId : exactClaimIds) {
            identity.putLong(claimId.getMostSignificantBits());
            identity.putLong(claimId.getLeastSignificantBits());
        }
        try {
            ByteBuffer digest = ByteBuffer.wrap(
                    MessageDigest.getInstance("SHA-256")
                            .digest(identity.array()));
            long most = digest.getLong();
            long least = digest.getLong();
            most = (most & 0xffffffffffff0fffL)
                    | 0x0000000000005000L;
            least = (least & 0x3fffffffffffffffL)
                    | 0x8000000000000000L;
            return new UUID(most, least);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    public static boolean matchesRequestId(
            UUID requestId,
            UUID playerId,
            List<UUID> claimIds
    ) {
        return Objects.requireNonNull(requestId, "requestId").equals(
                deriveRequestId(playerId, claimIds));
    }

    public static void encode(C2SAtmCollectCashPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeVarInt(packet.claimIds().size());
        packet.claimIds().forEach(buffer::writeUUID);
    }

    public static C2SAtmCollectCashPacket decode(FriendlyByteBuf buffer) {
        try {
            UUID requestId = buffer.readUUID();
            int count = buffer.readVarInt();
            if (count <= 0 || count > MAX_CLAIMS) {
                throw new DecoderException(
                        "ATM cash collection claim count is invalid");
            }
            List<UUID> claimIds = new ArrayList<>(count);
            Set<UUID> unique = new HashSet<>();
            for (int index = 0; index < count; index++) {
                UUID claimId = buffer.readUUID();
                if (!unique.add(claimId)) {
                    throw new DecoderException(
                            "ATM cash collection claim is duplicated");
                }
                claimIds.add(claimId);
            }
            return new C2SAtmCollectCashPacket(requestId, claimIds);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "ATM cash collection packet is invalid", exception);
        }
    }

    public static void handle(C2SAtmCollectCashPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AtmService.collectCash(player, packet.requestId(),
                        packet.claimIds());
            }
        });
        context.setPacketHandled(true);
    }

    private static List<UUID> requireClaimIds(List<UUID> values) {
        List<UUID> claimIds = List.copyOf(Objects.requireNonNull(
                values, "claimIds"));
        if (claimIds.isEmpty() || claimIds.size() > MAX_CLAIMS
                || claimIds.stream().anyMatch(Objects::isNull)
                || claimIds.stream().anyMatch(value -> value.equals(
                new UUID(0L, 0L)))
                || new HashSet<>(claimIds).size() != claimIds.size()) {
            throw new IllegalArgumentException(
                    "ATM cash collection request is invalid");
        }
        return claimIds;
    }
}
