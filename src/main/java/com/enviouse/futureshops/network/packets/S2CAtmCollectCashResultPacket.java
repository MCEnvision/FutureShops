package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CAtmCollectCashResultPacket(
        UUID requestId,
        String status,
        boolean retryable,
        boolean replayed,
        int deliveredBillCount,
        int remainingPendingClaimCount,
        List<UUID> quarantinedClaimIds,
        long retryAfterMillis
) {
    public static final int MAX_DELIVERED_BILLS = 16_384;
    public static final long MAX_RETRY_AFTER_MILLIS = 3_600_000_000L;
    private static final Set<String> STATUSES = Set.of(
            "DELIVERED", "PARTIALLY_DELIVERED", "MANUAL_REVIEW",
            "RATE_LIMITED", "RETRYABLE", "CONFLICT", "UNAVAILABLE");

    public S2CAtmCollectCashResultPacket {
        Objects.requireNonNull(requestId, "requestId");
        status = Objects.requireNonNull(status, "status");
        quarantinedClaimIds = List.copyOf(Objects.requireNonNull(
                quarantinedClaimIds, "quarantinedClaimIds"));
        boolean rateLimited = status.equals("RATE_LIMITED");
        boolean requiresRetryable = rateLimited
                || status.equals("RETRYABLE")
                || status.equals("UNAVAILABLE")
                || status.equals("PARTIALLY_DELIVERED")
                && quarantinedClaimIds.isEmpty();
        boolean allowsRetryable = requiresRetryable
                || status.equals("MANUAL_REVIEW")
                || status.equals("PARTIALLY_DELIVERED");
        if (requestId.equals(new UUID(0L, 0L))
                || !STATUSES.contains(status)
                || deliveredBillCount < 0
                || deliveredBillCount > MAX_DELIVERED_BILLS
                || remainingPendingClaimCount < 0
                || remainingPendingClaimCount
                > S2CAtmDataPacket.MAX_PENDING_CASH_CLAIMS
                || retryAfterMillis < 0L
                || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                || quarantinedClaimIds.size()
                > C2SAtmCollectCashPacket.MAX_CLAIMS
                || quarantinedClaimIds.stream().anyMatch(Objects::isNull)
                || quarantinedClaimIds.stream().anyMatch(value ->
                value.equals(new UUID(0L, 0L)))
                || new HashSet<>(quarantinedClaimIds).size()
                != quarantinedClaimIds.size()
                || requiresRetryable && !retryable
                || retryable && !allowsRetryable
                || rateLimited != (retryAfterMillis > 0L)
                || rateLimited && (replayed || deliveredBillCount != 0
                || !quarantinedClaimIds.isEmpty())
                || status.equals("DELIVERED") && deliveredBillCount <= 0
                || status.equals("PARTIALLY_DELIVERED")
                && deliveredBillCount <= 0
                || status.equals("MANUAL_REVIEW")
                && (deliveredBillCount != 0
                || quarantinedClaimIds.isEmpty())
                || !status.equals("PARTIALLY_DELIVERED")
                && !status.equals("MANUAL_REVIEW")
                && !quarantinedClaimIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "ATM cash collection result is invalid");
        }
    }

    public S2CAtmCollectCashResultPacket(
            UUID requestId,
            String status,
            boolean retryable,
            boolean replayed,
            int deliveredBillCount,
            int remainingPendingClaimCount,
            List<UUID> quarantinedClaimIds
    ) {
        this(requestId, status, retryable, replayed, deliveredBillCount,
                remainingPendingClaimCount, quarantinedClaimIds, 0L);
    }

    public int quarantinedClaimCount() {
        return quarantinedClaimIds.size();
    }

    public String deduplicationKey() {
        return status + "," + retryable + "," + replayed + ","
                + deliveredBillCount + "," + remainingPendingClaimCount
                + "," + quarantinedClaimIds + "," + retryAfterMillis;
    }

    public static void encode(S2CAtmCollectCashResultPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.status(), 32);
        buffer.writeBoolean(packet.retryable());
        buffer.writeBoolean(packet.replayed());
        buffer.writeVarInt(packet.deliveredBillCount());
        buffer.writeVarInt(packet.remainingPendingClaimCount());
        buffer.writeVarInt(packet.quarantinedClaimIds().size());
        packet.quarantinedClaimIds().forEach(buffer::writeUUID);
        buffer.writeVarLong(packet.retryAfterMillis());
    }

    public static S2CAtmCollectCashResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            String status = buffer.readUtf(32);
            boolean retryable = buffer.readBoolean();
            boolean replayed = buffer.readBoolean();
            int deliveredBills = buffer.readVarInt();
            int pendingClaims = buffer.readVarInt();
            int quarantinedCount = buffer.readVarInt();
            if (quarantinedCount < 0
                    || quarantinedCount
                    > C2SAtmCollectCashPacket.MAX_CLAIMS) {
                throw new DecoderException(
                        "ATM cash collection quarantine count is invalid");
            }
            List<UUID> quarantinedClaimIds = new ArrayList<>(
                    quarantinedCount);
            for (int index = 0; index < quarantinedCount; index++) {
                quarantinedClaimIds.add(buffer.readUUID());
            }
            long retryAfterMillis = buffer.readVarLong();
            return new S2CAtmCollectCashResultPacket(requestId, status,
                    retryable, replayed, deliveredBills, pendingClaims,
                    quarantinedClaimIds, retryAfterMillis);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "ATM cash collection result packet is invalid", exception);
        }
    }

    public static void handle(S2CAtmCollectCashResultPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler
                        .handleAtmCashCollectionResult(packet)));
        context.setPacketHandled(true);
    }
}
