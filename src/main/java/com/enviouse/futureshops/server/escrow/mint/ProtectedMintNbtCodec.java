package com.enviouse.futureshops.server.escrow.mint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ProtectedMintNbtCodec {
    private ProtectedMintNbtCodec() {
    }

    static CompoundTag writeBatch(ProtectedMintBatch batch) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("batch", batch.batchId());
        tag.putUUID("transaction", batch.transactionId());
        tag.putString("request", batch.authorizeRequestKey());
        tag.putLong("denomination", batch.denominationMinorUnits());
        tag.putInt("authorizedCount", batch.authorizedCount());
        tag.putInt("authorized", batch.authorizedQuantity());
        tag.putInt("available", batch.availableQuantity());
        tag.put("reserved", writeQuantities(batch.reservedQuantities()));
        tag.put("spent", writeQuantities(batch.spentQuantities()));
        tag.putInt("refunded", batch.refundedQuantity());
        tag.putInt("quarantined", batch.quarantinedQuantity());
        batch.replacementForBatchId().ifPresent(value -> tag.putUUID("replacementFor", value));
        tag.putString("serverEvidence", batch.serverIdentityEvidence());
        tag.putString("checksumEvidence", batch.checksumEvidence());
        writeInstant(tag, "authorizedAt", batch.authorizedAt());
        writeInstant(tag, "updatedAt", batch.updatedAt());
        tag.putLong("revision", batch.revision());
        return tag;
    }

    static ProtectedMintBatch readBatch(CompoundTag tag) {
        return new ProtectedMintBatch(requireUuid(tag, "batch"),
                requireUuid(tag, "transaction"), requireString(tag, "request"),
                requireLong(tag, "denomination"), requireInt(tag, "authorizedCount"),
                requireInt(tag, "authorized"), requireInt(tag, "available"),
                readQuantities(tag, "reserved"), readQuantities(tag, "spent"),
                requireInt(tag, "refunded"), requireInt(tag, "quarantined"),
                optionalUuid(tag, "replacementFor"), requireString(tag, "serverEvidence"),
                requireString(tag, "checksumEvidence"), readInstant(tag, "authorizedAt"),
                readInstant(tag, "updatedAt"), requireLong(tag, "revision"));
    }

    static CompoundTag writeReceipt(ProtectedMintReceipt receipt) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("receipt", receipt.receiptId());
        tag.putString("request", receipt.requestKey());
        tag.putString("operation", receipt.operation().name());
        tag.putUUID("transaction", receipt.transactionId());
        receipt.sourceBatchId().ifPresent(value -> tag.putUUID("sourceBatch", value));
        receipt.resultingBatchId().ifPresent(value -> tag.putUUID("resultingBatch", value));
        tag.putInt("quantity", receipt.quantity());
        receipt.sourceState().ifPresent(value -> tag.putString("sourceState", value.name()));
        tag.putByteArray("mutationHash", receipt.mutationHash());
        writeInstant(tag, "occurredAt", receipt.occurredAt());
        return tag;
    }

    static ProtectedMintReceipt readReceipt(CompoundTag tag) {
        byte[] mutationHash = requireBytes(tag, "mutationHash",
                ProtectedMintReceipt.HASH_BYTES);
        if (mutationHash.length != ProtectedMintReceipt.HASH_BYTES) {
            throw new IllegalStateException("Protected mint receipt hash length is invalid");
        }
        Optional<ProtectedMintState> sourceState = tag.contains("sourceState")
                ? Optional.of(enumValue(ProtectedMintState.class,
                requireString(tag, "sourceState"), "source state")) : Optional.empty();
        return new ProtectedMintReceipt(requireUuid(tag, "receipt"),
                requireString(tag, "request"), enumValue(ProtectedMintOperation.class,
                requireString(tag, "operation"), "operation"),
                requireUuid(tag, "transaction"), optionalUuid(tag, "sourceBatch"),
                optionalUuid(tag, "resultingBatch"), requireInt(tag, "quantity"),
                sourceState, mutationHash, readInstant(tag, "occurredAt"));
    }

    private static ListTag writeQuantities(Map<UUID, Integer> quantities) {
        ListTag tags = new ListTag();
        quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("transaction", entry.getKey());
                    tag.putInt("quantity", entry.getValue());
                    tags.add(tag);
                });
        return tags;
    }

    private static Map<UUID, Integer> readQuantities(CompoundTag tag, String key) {
        ListTag tags = requireCompoundList(tag, key);
        if (tags.size() > ProtectedMintBatch.MAX_RESERVATION_ENTRIES) {
            throw new IllegalStateException("Protected mint quantity map exceeds its limit");
        }
        Map<UUID, Integer> quantities = new HashMap<>();
        for (Tag raw : tags) {
            CompoundTag entry = (CompoundTag) raw;
            UUID transactionId = requireUuid(entry, "transaction");
            int quantity = requireInt(entry, "quantity");
            if (quantity <= 0 || quantities.put(transactionId, quantity) != null) {
                throw new IllegalStateException("Protected mint quantity map is invalid");
            }
        }
        return Map.copyOf(quantities);
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            throw new IllegalStateException("Protected mint list is missing");
        }
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Protected mint list has the wrong type");
        }
        return list;
    }

    private static UUID requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalStateException("Protected mint UUID is missing");
        }
        return tag.getUUID(key);
    }

    private static Optional<UUID> optionalUuid(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(requireUuid(tag, key));
    }

    private static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalStateException("Protected mint text is missing");
        }
        String value = tag.getString(key);
        if (value.isEmpty()) {
            throw new IllegalStateException("Protected mint text is empty");
        }
        return value;
    }

    private static long requireLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalStateException("Protected mint long value is missing");
        }
        return tag.getLong(key);
    }

    private static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalStateException("Protected mint integer value is missing");
        }
        return tag.getInt(key);
    }

    private static byte[] requireBytes(CompoundTag tag, String key, int maximum) {
        if (!tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException("Protected mint byte data is missing");
        }
        byte[] value = tag.getByteArray(key);
        if (value.length > maximum) {
            throw new IllegalStateException("Protected mint byte data exceeds its limit");
        }
        return value;
    }

    private static void writeInstant(CompoundTag tag, String key, Instant value) {
        tag.putLong(key + "Seconds", value.getEpochSecond());
        tag.putInt(key + "Nanos", value.getNano());
    }

    private static Instant readInstant(CompoundTag tag, String key) {
        long seconds = requireLong(tag, key + "Seconds");
        int nanos = requireInt(tag, key + "Nanos");
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalStateException("Protected mint timestamp nanoseconds are invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalStateException("Protected mint timestamp is invalid", exception);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value,
                                                    String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Protected mint " + label + " is invalid", exception);
        }
    }
}
