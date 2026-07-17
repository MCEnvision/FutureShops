package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.money.CurrencyWithdrawalService;
import com.enviouse.futureshops.server.escrow.runtime.AtmWithdrawalOutcome;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Protocol 38 appends bounded withdrawal retry timing.
 */
public record S2CAtmResultPacket(
        UUID requestId,
        String status,
        boolean retryable,
        boolean replayed,
        boolean balanceKnown,
        long balanceMinor,
        long amountMinor,
        int deliveredBillCount,
        int claimedBillCount,
        String currencySignature,
        long retryAfterMillis
) {
    public static final long MAX_RETRY_AFTER_MILLIS =
            AtmWithdrawalOutcome.MAX_RETRY_AFTER_MILLIS;

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> STATUSES = Set.of(
            "DELIVERED", "CLAIMED", "PARTIALLY_DELIVERED",
            "INVALID_AMOUNT", "INVALID_PLAN", "CURRENCY_CHANGED",
            "INSUFFICIENT_FUNDS", "CANCELLED", "CONFLICT",
            "RATE_LIMITED",
            "MIGRATION_PENDING", "ESCROW_UNAVAILABLE",
            "RECOVERY_PENDING", "MANUAL_REVIEW", "SERVER_ERROR");
    private static final Set<String> RETRYABLE_STATUSES = Set.of(
            "RATE_LIMITED",
            "MIGRATION_PENDING", "ESCROW_UNAVAILABLE",
            "RECOVERY_PENDING", "SERVER_ERROR");
    private static final Set<String> SUCCESS = Set.of(
            "DELIVERED", "CLAIMED", "PARTIALLY_DELIVERED");

    public S2CAtmResultPacket {
        Objects.requireNonNull(requestId, "requestId");
        if (requestId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException("ATM request ID is invalid");
        }
        status = Objects.requireNonNull(status, "status");
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        if (!STATUSES.contains(status)
                || !SIGNATURE.matcher(currencySignature).matches()) {
            throw new IllegalArgumentException("ATM result text is invalid");
        }
        if (!balanceKnown && balanceMinor != 0L
                || amountMinor < 0L
                || deliveredBillCount < 0
                || claimedBillCount < 0
                || retryAfterMillis < 0L
                || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                || deliveredBillCount > CurrencyWithdrawalService.MAX_SELECTED_ITEMS
                || claimedBillCount > CurrencyWithdrawalService.MAX_SELECTED_ITEMS
                || Math.addExact(deliveredBillCount, claimedBillCount)
                > CurrencyWithdrawalService.MAX_SELECTED_ITEMS) {
            throw new IllegalArgumentException("ATM result values are invalid");
        }
        boolean success = SUCCESS.contains(status);
        boolean rateLimited = status.equals("RATE_LIMITED");
        if (success && retryable
                || retryable && !RETRYABLE_STATUSES.contains(status)
                || retryAfterMillis > 0L && !retryable
                || rateLimited != (retryAfterMillis > 0L)
                || rateLimited && (!retryable || replayed
                || balanceKnown || balanceMinor != 0L
                || amountMinor != 0L || deliveredBillCount != 0
                || claimedBillCount != 0)
                || success && amountMinor <= 0L
                || status.equals("DELIVERED")
                && (deliveredBillCount <= 0 || claimedBillCount != 0)
                || status.equals("CLAIMED")
                && (deliveredBillCount != 0 || claimedBillCount <= 0)
                || status.equals("PARTIALLY_DELIVERED")
                && (deliveredBillCount <= 0 || claimedBillCount <= 0)) {
            throw new IllegalArgumentException("ATM result status is invalid");
        }
    }

    public S2CAtmResultPacket(
            UUID requestId,
            String status,
            boolean retryable,
            boolean replayed,
            boolean balanceKnown,
            long balanceMinor,
            long amountMinor,
            int deliveredBillCount,
            int claimedBillCount,
            String currencySignature
    ) {
        this(requestId, status, retryable, replayed, balanceKnown,
                balanceMinor, amountMinor, deliveredBillCount,
                claimedBillCount, currencySignature, 0L);
    }

    public boolean success() {
        return SUCCESS.contains(status);
    }

    public String code() {
        return status;
    }

    public String deduplicationKey() {
        return status + "," + retryable + "," + replayed + ","
                + balanceKnown + "," + balanceMinor + "," + amountMinor
                + "," + deliveredBillCount + "," + claimedBillCount + ","
                + currencySignature + "," + retryAfterMillis;
    }

    public static void encode(S2CAtmResultPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.status(), 64);
        buffer.writeBoolean(packet.retryable());
        buffer.writeBoolean(packet.replayed());
        buffer.writeBoolean(packet.balanceKnown());
        buffer.writeLong(packet.balanceMinor());
        buffer.writeLong(packet.amountMinor());
        buffer.writeVarInt(packet.deliveredBillCount());
        buffer.writeVarInt(packet.claimedBillCount());
        buffer.writeUtf(packet.currencySignature(), 64);
        buffer.writeVarLong(packet.retryAfterMillis());
    }

    public static S2CAtmResultPacket decode(FriendlyByteBuf buffer) {
        try {
            return new S2CAtmResultPacket(
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(64),
                    buffer.readVarLong());
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("ATM result packet is invalid",
                    exception);
        }
    }

    public static void handle(S2CAtmResultPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleAtmResult(packet)));
        context.setPacketHandled(true);
    }
}
