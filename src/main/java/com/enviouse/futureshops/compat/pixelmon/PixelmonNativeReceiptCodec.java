package com.enviouse.futureshops.compat.pixelmon;

import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Bounded receipt projection stored beside the native Pixelmon balance image. */
public final class PixelmonNativeReceiptCodec {
    private static final String KEY = "futureshopsReceipt";
    private static final int SCHEMA = 1;

    private PixelmonNativeReceiptCodec() {
    }

    public static void write(CompoundTag accountTag, PixelmonNativeRequestContext.Request request) {
        request.resultingBalance().ifPresent(balance -> {
            CompoundTag receipt = new CompoundTag();
            receipt.putInt("schema", SCHEMA);
            receipt.putUUID("root", request.rootId().value());
            receipt.putUUID("leg", request.legId().value());
            receipt.putString("operation", request.operation());
            receipt.putLong("amount", request.amountMinorUnits());
            receipt.putBoolean("debit", request.debit());
            receipt.putString("payload", request.payloadFingerprint());
            receipt.putString("balance", balance.toPlainString());
            accountTag.put(KEY, receipt);
        });
    }

    public static void preserve(CompoundTag accountTag, CompoundTag sourceTag) {
        read(sourceTag).ifPresent(ignored -> accountTag.put(KEY, sourceTag.getCompound(KEY).copy()));
    }

    public static Optional<Receipt> read(CompoundTag accountTag) {
        if (!accountTag.contains(KEY, 10)) {
            return Optional.empty();
        }
        CompoundTag receipt = accountTag.getCompound(KEY);
        if (receipt.getInt("schema") != SCHEMA || !receipt.hasUUID("root")
                || !receipt.hasUUID("leg") || !receipt.contains("operation", 8)
                || !receipt.contains("amount", 4) || !receipt.contains("debit", 1)
                || !receipt.contains("payload", 8) || !receipt.contains("balance", 8)) {
            throw new IllegalArgumentException("Pixelmon receipt is malformed");
        }
        long amount = receipt.getLong("amount");
        String operation = receipt.getString("operation");
        String payload = receipt.getString("payload");
        String balance = receipt.getString("balance");
        if (amount <= 0L || operation.isBlank() || operation.length() > 64
                || payload.length() != 64 || !payload.matches("[0-9a-fA-F]+")
                || balance.length() > 128) {
            throw new IllegalArgumentException("Pixelmon receipt is outside bounds");
        }
        try {
            return Optional.of(new Receipt(receipt.getUUID("root"), receipt.getUUID("leg"),
                    operation, amount, receipt.getBoolean("debit"), payload,
                    new BigDecimal(balance)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Pixelmon receipt balance is invalid", exception);
        }
    }

    public record Receipt(UUID rootId, UUID legId, String operation, long amountMinorUnits,
                          boolean debit, String payloadFingerprint, BigDecimal resultingBalance) {
    }
}
