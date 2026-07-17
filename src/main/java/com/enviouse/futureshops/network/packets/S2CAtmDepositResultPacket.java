package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCashDepositService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CAtmDepositResultPacket(
        UUID requestId,
        String status,
        boolean retryable,
        boolean replayed,
        Optional<UUID> transactionId,
        long depositedMinorUnits,
        int itemsConsumed,
        long walletCreditMinorUnits,
        long overflowClaimMinorUnits,
        boolean balanceKnown,
        long resultingBalanceMinorUnits,
        boolean cleanupPending,
        Optional<LegacyMigrationSummary> legacyMigration,
        long retryAfterMillis
) {
    public static final int MAX_ITEMS_CONSUMED =
            EscrowCashDepositService.MAX_ITEMS_CONSUMED;
    public static final int MAX_LEGACY_BILLS = 4_096;
    public static final int MAX_LEGACY_ENTRIES = 256;
    public static final long MAX_RETRY_AFTER_MILLIS =
            EscrowCashDepositService.MAX_RETRY_AFTER_MILLIS;

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Set<String> STATUSES = Set.of(
            "SUCCESS", "NO_CURRENCY", "INVALID_AMOUNT",
            "NOT_ENOUGH_CURRENCY", "INVALID_DENOMINATION",
            "TOO_MANY_ITEMS",
            "WRONG_PROVIDER", "CREATIVE_BLOCKED",
            "LEGACY_MIGRATION_REQUIRED", "INVALID_CURRENCY",
            "CONFIG_CHANGED", "CANCELLED", "ESCROW_UNAVAILABLE",
            "RECOVERY_REQUIRED", "REQUEST_CONFLICT",
            "RATE_LIMITED", "SERVER_ERROR");
    private static final Set<String> RETRYABLE_STATUSES = Set.of(
            "ESCROW_UNAVAILABLE", "RECOVERY_REQUIRED",
            "RATE_LIMITED", "SERVER_ERROR");

    public S2CAtmDepositResultPacket {
        Objects.requireNonNull(requestId, "requestId");
        status = Objects.requireNonNull(status, "status");
        transactionId = Objects.requireNonNull(
                transactionId, "transactionId");
        legacyMigration = Objects.requireNonNull(
                legacyMigration, "legacyMigration");
        transactionId.ifPresent(value -> {
            if (value.equals(ZERO_UUID)) {
                throw new IllegalArgumentException(
                        "ATM deposit transaction ID is invalid");
            }
        });
        boolean success = status.equals("SUCCESS");
        boolean rateLimited = status.equals("RATE_LIMITED");
        boolean requiresTransaction = success
                || status.equals("RECOVERY_REQUIRED")
                || status.equals("CANCELLED")
                || status.equals("REQUEST_CONFLICT");
        boolean permitsTransaction = requiresTransaction
                || status.equals("CONFIG_CHANGED");
        if (requestId.equals(ZERO_UUID)
                || !STATUSES.contains(status)
                || retryable != RETRYABLE_STATUSES.contains(status)
                || depositedMinorUnits < 0L
                || itemsConsumed < 0
                || itemsConsumed > MAX_ITEMS_CONSUMED
                || walletCreditMinorUnits < 0L
                || overflowClaimMinorUnits < 0L
                || !balanceKnown && resultingBalanceMinorUnits != 0L
                || retryAfterMillis < 0L
                || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                || rateLimited != (retryAfterMillis > 0L)
                || replayed && !success
                || rateLimited && (replayed
                || transactionId.isPresent()
                || depositedMinorUnits != 0L || itemsConsumed != 0
                || walletCreditMinorUnits != 0L
                || overflowClaimMinorUnits != 0L || balanceKnown
                || resultingBalanceMinorUnits != 0L || cleanupPending
                || legacyMigration.isPresent())
                || requiresTransaction && transactionId.isEmpty()
                || !permitsTransaction && transactionId.isPresent()
                || success && (retryable || depositedMinorUnits <= 0L
                || itemsConsumed <= 0
                || !balanceKnown
                || Math.addExact(walletCreditMinorUnits,
                overflowClaimMinorUnits) != depositedMinorUnits)
                || !success && (depositedMinorUnits != 0L
                || itemsConsumed != 0 || walletCreditMinorUnits != 0L
                || overflowClaimMinorUnits != 0L || balanceKnown
                || resultingBalanceMinorUnits != 0L || cleanupPending)
                || legacyMigration.isPresent()
                != status.equals("LEGACY_MIGRATION_REQUIRED")) {
            throw new IllegalArgumentException(
                    "ATM deposit result is invalid");
        }
    }

    public boolean success() {
        return status.equals("SUCCESS");
    }

    public String deduplicationKey() {
        return status + "," + retryable + "," + replayed + ","
                + transactionId + "," + depositedMinorUnits + ","
                + itemsConsumed + "," + walletCreditMinorUnits + ","
                + overflowClaimMinorUnits + ","
                + balanceKnown + "," + resultingBalanceMinorUnits + ","
                + cleanupPending + ","
                + legacyMigration + "," + retryAfterMillis;
    }

    public static void encode(
            S2CAtmDepositResultPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.status(), 32);
        buffer.writeBoolean(packet.retryable());
        buffer.writeBoolean(packet.replayed());
        buffer.writeBoolean(packet.transactionId().isPresent());
        packet.transactionId().ifPresent(buffer::writeUUID);
        buffer.writeLong(packet.depositedMinorUnits());
        buffer.writeVarInt(packet.itemsConsumed());
        buffer.writeLong(packet.walletCreditMinorUnits());
        buffer.writeLong(packet.overflowClaimMinorUnits());
        buffer.writeBoolean(packet.balanceKnown());
        buffer.writeLong(packet.resultingBalanceMinorUnits());
        buffer.writeBoolean(packet.cleanupPending());
        buffer.writeBoolean(packet.legacyMigration().isPresent());
        if (packet.legacyMigration().isPresent()) {
            LegacyMigrationSummary legacy =
                    packet.legacyMigration().orElseThrow();
            buffer.writeLong(legacy.availableMinorUnits());
            buffer.writeVarInt(legacy.billCount());
            buffer.writeVarInt(legacy.entryCount());
        }
        buffer.writeVarLong(packet.retryAfterMillis());
    }

    public static S2CAtmDepositResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            String status = buffer.readUtf(32);
            boolean retryable = buffer.readBoolean();
            boolean replayed = buffer.readBoolean();
            Optional<UUID> transactionId = buffer.readBoolean()
                    ? Optional.of(buffer.readUUID()) : Optional.empty();
            long deposited = buffer.readLong();
            int items = buffer.readVarInt();
            long walletCredit = buffer.readLong();
            long overflowClaim = buffer.readLong();
            boolean balanceKnown = buffer.readBoolean();
            long balance = buffer.readLong();
            boolean cleanupPending = buffer.readBoolean();
            Optional<LegacyMigrationSummary> legacy = buffer.readBoolean()
                    ? Optional.of(new LegacyMigrationSummary(
                    buffer.readLong(), buffer.readVarInt(),
                    buffer.readVarInt()))
                    : Optional.empty();
            long retryAfterMillis = buffer.readVarLong();
            return new S2CAtmDepositResultPacket(
                    requestId, status, retryable, replayed,
                    transactionId, deposited, items, walletCredit,
                    overflowClaim, balanceKnown, balance, cleanupPending,
                    legacy,
                    retryAfterMillis);
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "ATM deposit result packet is invalid", exception);
        }
    }

    public static void handle(
            S2CAtmDepositResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler
                        .handleAtmDepositResult(packet)));
        context.setPacketHandled(true);
    }

    public record LegacyMigrationSummary(
            long availableMinorUnits,
            int billCount,
            int entryCount
    ) {
        public LegacyMigrationSummary {
            if (availableMinorUnits <= 0L
                    || billCount <= 0 || billCount > MAX_LEGACY_BILLS
                    || entryCount <= 0
                    || entryCount > MAX_LEGACY_ENTRIES
                    || entryCount > billCount) {
                throw new IllegalArgumentException(
                        "ATM legacy migration summary is invalid");
            }
        }
    }
}
