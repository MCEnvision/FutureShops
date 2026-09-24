package com.enviouse.futureshops.compat.pixelmon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Bounded receipt projection stored beside the native Pixelmon balance image. */
public final class PixelmonNativeReceiptCodec {
    private static final String KEY = "futureshopsReceipt";
    private static final String HISTORY_KEY = "futureshopsReceipts";
    private static final int SCHEMA = 1;
    private static final int MAX_HISTORY = 128;

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
            accountTag.put(KEY, receipt.copy());
            ListTag history = history(accountTag);
            for (int index = history.size() - 1; index >= 0; index--) {
                if (history.getCompound(index).hasUUID("leg")
                        && history.getCompound(index).getUUID("leg").equals(request.legId().value())) {
                    history.remove(index);
                }
            }
            history.add(receipt);
            while (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
            accountTag.put(HISTORY_KEY, history);
        });
    }

    public static void preserve(CompoundTag accountTag, CompoundTag sourceTag) {
        read(sourceTag).ifPresent(ignored -> {
            accountTag.put(KEY, sourceTag.getCompound(KEY).copy());
            if (sourceTag.contains(HISTORY_KEY, Tag.TAG_LIST)) {
                accountTag.put(HISTORY_KEY, sourceTag.getList(HISTORY_KEY, Tag.TAG_COMPOUND).copy());
            }
        });
    }

    public static Optional<Receipt> read(CompoundTag accountTag) {
        Optional<Receipt> current = accountTag.contains(KEY, Tag.TAG_COMPOUND)
                ? Optional.of(parse(accountTag.getCompound(KEY))) : Optional.empty();
        validateHistory(accountTag);
        if (current.isPresent()) {
            return current;
        }
        ListTag history = accountTag.getList(HISTORY_KEY, Tag.TAG_COMPOUND);
        return history.isEmpty() ? Optional.empty()
                : Optional.of(parse(history.getCompound(history.size() - 1)));
    }

    public static Optional<Receipt> read(CompoundTag accountTag, UUID legId) {
        if (legId == null) {
            return Optional.empty();
        }
        validateHistory(accountTag);
        ListTag history = accountTag.getList(HISTORY_KEY, Tag.TAG_COMPOUND);
        for (int index = history.size() - 1; index >= 0; index--) {
            CompoundTag receipt = history.getCompound(index);
            if (receipt.hasUUID("leg") && receipt.getUUID("leg").equals(legId)) {
                return Optional.of(parse(receipt));
            }
        }
        if (accountTag.contains(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag receipt = accountTag.getCompound(KEY);
            if (receipt.hasUUID("leg") && receipt.getUUID("leg").equals(legId)) {
                return Optional.of(parse(receipt));
            }
        }
        return Optional.empty();
    }

    private static Receipt parse(CompoundTag receipt) {
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
            return new Receipt(receipt.getUUID("root"), receipt.getUUID("leg"),
                    operation, amount, receipt.getBoolean("debit"), payload,
                    new BigDecimal(balance));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Pixelmon receipt balance is invalid", exception);
        }
    }

    private static ListTag history(CompoundTag accountTag) {
        validateHistory(accountTag);
        return accountTag.contains(HISTORY_KEY, Tag.TAG_LIST)
                ? accountTag.getList(HISTORY_KEY, Tag.TAG_COMPOUND).copy()
                : new ListTag();
    }

    private static void validateHistory(CompoundTag accountTag) {
        if (!accountTag.contains(HISTORY_KEY)) {
            return;
        }
        if (!accountTag.contains(HISTORY_KEY, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Pixelmon receipt history is malformed");
        }
        ListTag history = accountTag.getList(HISTORY_KEY, Tag.TAG_COMPOUND);
        if (history.size() > MAX_HISTORY) {
            throw new IllegalArgumentException("Pixelmon receipt history is outside bounds");
        }
        for (int index = 0; index < history.size(); index++) {
            if (!(history.get(index) instanceof CompoundTag)) {
                throw new IllegalArgumentException("Pixelmon receipt history is malformed");
            }
            parse(history.getCompound(index));
        }
    }

    public record Receipt(UUID rootId, UUID legId, String operation, long amountMinorUnits,
                          boolean debit, String payloadFingerprint, BigDecimal resultingBalance) {
    }
}
