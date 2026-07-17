package com.enviouse.futureshops.server.escrow.custody;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class CustodyNbtCodec {
    private CustodyNbtCodec() {
    }

    static CompoundTag writeLot(CustodyLot lot) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("lot", lot.lotId());
        tag.putUUID("transaction", lot.transactionId());
        tag.putString("request", lot.reserveRequestKey());
        tag.putString("assetType", lot.assetType().name());
        tag.putString("protectionTier", lot.protectionTier().name());
        tag.putString("sourceCapability", lot.sourceCapability().name());
        tag.putString("state", lot.state().name());
        tag.putLong("units", lot.units());
        tag.putString("currencyProvider", lot.currencyProvider());
        tag.put("snapshots", writeSnapshots(lot.itemSnapshots()));
        tag.put("provenance", writeProvenance(lot.protectedProvenance()));
        tag.putByteArray("assetFingerprint", lot.assetFingerprint());
        tag.put("holdEvidence", writeTransferEvidence(lot.holdEvidence()));
        writeInstant(tag, "created", lot.createdAt());
        writeInstant(tag, "updated", lot.updatedAt());
        tag.putLong("revision", lot.revision());
        return tag;
    }

    static CustodyLot readLot(CompoundTag tag) {
        requireUuid(tag, "lot");
        requireUuid(tag, "transaction");
        return new CustodyLot(
                tag.getUUID("lot"),
                tag.getUUID("transaction"),
                tag.getString("request"),
                enumValue(CustodyAssetType.class, tag.getString("assetType"), "custody asset type"),
                enumValue(CustodyProtectionTier.class, tag.getString("protectionTier"),
                        "custody protection tier"),
                enumValue(CustodyAdapterCapability.class, tag.getString("sourceCapability"),
                        "custody source capability"),
                enumValue(CustodyLotState.class, tag.getString("state"), "custody lot state"),
                tag.getLong("units"),
                tag.getString("currencyProvider"),
                readSnapshots(requireCompoundList(tag, "snapshots")),
                readProvenance(requireCompoundList(tag, "provenance")),
                tag.getByteArray("assetFingerprint"),
                readTransferEvidence(requireCompound(tag, "holdEvidence")),
                readInstant(tag, "created"),
                readInstant(tag, "updated"),
                tag.getLong("revision"));
    }

    static CompoundTag writeReceipt(CustodyOperationReceipt receipt) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("receipt", receipt.receiptId());
        tag.putUUID("lot", receipt.lotId());
        tag.putUUID("transaction", receipt.transactionId());
        tag.putString("operation", receipt.operation().name());
        tag.putString("request", receipt.requestKey());
        receipt.previousState().ifPresent(value -> tag.putString("previousState", value.name()));
        tag.putString("resultingState", receipt.resultingState().name());
        tag.putLong("units", receipt.units());
        tag.putByteArray("assetFingerprint", receipt.assetFingerprint());
        tag.put("evidence", writeTransferEvidence(receipt.evidence()));
        writeInstant(tag, "created", receipt.createdAt());
        return tag;
    }

    static CustodyOperationReceipt readReceipt(CompoundTag tag) {
        requireUuid(tag, "receipt");
        requireUuid(tag, "lot");
        requireUuid(tag, "transaction");
        Optional<CustodyLotState> previous = tag.contains("previousState", Tag.TAG_STRING)
                ? Optional.of(enumValue(CustodyLotState.class, tag.getString("previousState"),
                "custody receipt previous state"))
                : Optional.empty();
        return new CustodyOperationReceipt(
                tag.getUUID("receipt"),
                tag.getUUID("lot"),
                tag.getUUID("transaction"),
                enumValue(CustodyOperation.class, tag.getString("operation"), "custody operation"),
                tag.getString("request"),
                previous,
                enumValue(CustodyLotState.class, tag.getString("resultingState"),
                        "custody receipt resulting state"),
                tag.getLong("units"),
                tag.getByteArray("assetFingerprint"),
                readTransferEvidence(requireCompound(tag, "evidence")),
                readInstant(tag, "created"));
    }

    static CompoundTag writeTransferEvidence(CustodyTransferEvidence evidence) {
        CompoundTag tag = new CompoundTag();
        tag.put("source", writeEndpointEvidence(evidence.source()));
        tag.put("destination", writeEndpointEvidence(evidence.destination()));
        return tag;
    }

    static CustodyTransferEvidence readTransferEvidence(CompoundTag tag) {
        return new CustodyTransferEvidence(
                readEndpointEvidence(requireCompound(tag, "source")),
                readEndpointEvidence(requireCompound(tag, "destination")));
    }

    private static CompoundTag writeEndpointEvidence(CustodyEndpointEvidence evidence) {
        CompoundTag tag = new CompoundTag();
        tag.putString("adapter", evidence.adapterId());
        tag.putString("capability", evidence.capability().name());
        tag.putString("owner", evidence.ownerKey());
        tag.putString("location", evidence.locationKey());
        tag.putByteArray("before", evidence.beforeStateHash());
        tag.putByteArray("after", evidence.afterStateHash());
        tag.putString("token", evidence.mutationToken());
        return tag;
    }

    private static CustodyEndpointEvidence readEndpointEvidence(CompoundTag tag) {
        return new CustodyEndpointEvidence(
                tag.getString("adapter"),
                enumValue(CustodyAdapterCapability.class, tag.getString("capability"),
                        "custody endpoint capability"),
                tag.getString("owner"),
                tag.getString("location"),
                tag.getByteArray("before"),
                tag.getByteArray("after"),
                tag.getString("token"));
    }

    private static ListTag writeSnapshots(List<CustodyItemSnapshot> snapshots) {
        ListTag values = new ListTag();
        for (CustodyItemSnapshot snapshot : snapshots) {
            CompoundTag tag = new CompoundTag();
            tag.putString("registry", snapshot.registryId());
            tag.putInt("count", snapshot.count());
            tag.putByteArray("nbt", snapshot.serializedNbt());
            tag.putByteArray("hash", snapshot.contentHash());
            values.add(tag);
        }
        return values;
    }

    private static List<CustodyItemSnapshot> readSnapshots(ListTag values) {
        if (values.size() > CustodyLot.MAX_SNAPSHOTS) {
            throw new IllegalStateException("Custody snapshot count exceeds bounds");
        }
        List<CustodyItemSnapshot> snapshots = new ArrayList<>(values.size());
        for (Tag value : values) {
            CompoundTag tag = (CompoundTag) value;
            snapshots.add(new CustodyItemSnapshot(tag.getString("registry"), tag.getInt("count"),
                    tag.getByteArray("nbt"), tag.getByteArray("hash")));
        }
        return List.copyOf(snapshots);
    }

    private static ListTag writeProvenance(List<ProtectedCurrencyProvenance> provenance) {
        ListTag values = new ListTag();
        for (ProtectedCurrencyProvenance entry : provenance) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("mint", entry.mintId());
            tag.putLong("denomination", entry.denominationMinorUnits());
            tag.putInt("authorizedCount", entry.authorizedCount());
            tag.putInt("billCount", entry.billCount());
            tag.putString("serverIdentityEvidence", entry.serverIdentityEvidence());
            tag.putString("checksumEvidence", entry.checksumEvidence());
            values.add(tag);
        }
        return values;
    }

    private static List<ProtectedCurrencyProvenance> readProvenance(ListTag values) {
        if (values.size() > CustodyLot.MAX_SNAPSHOTS) {
            throw new IllegalStateException("Custody provenance count exceeds bounds");
        }
        List<ProtectedCurrencyProvenance> provenance = new ArrayList<>(values.size());
        for (Tag value : values) {
            CompoundTag tag = (CompoundTag) value;
            requireUuid(tag, "mint");
            if (!tag.contains("denomination", Tag.TAG_LONG)
                    || !tag.contains("authorizedCount", Tag.TAG_INT)
                    || !tag.contains("billCount", Tag.TAG_INT)
                    || !tag.contains("serverIdentityEvidence", Tag.TAG_STRING)
                    || !tag.contains("checksumEvidence", Tag.TAG_STRING)) {
                throw new IllegalStateException("Protected currency provenance format is unsupported");
            }
            provenance.add(new ProtectedCurrencyProvenance(tag.getUUID("mint"),
                    tag.getLong("denomination"), tag.getInt("authorizedCount"),
                    tag.getInt("billCount"), tag.getString("serverIdentityEvidence"),
                    tag.getString("checksumEvidence")));
        }
        return List.copyOf(provenance);
    }

    private static void writeInstant(CompoundTag tag, String key, Instant value) {
        tag.putLong(key + "Second", value.getEpochSecond());
        tag.putInt(key + "Nano", value.getNano());
    }

    private static Instant readInstant(CompoundTag tag, String key) {
        try {
            if (!tag.contains(key + "Second", Tag.TAG_LONG)
                    || !tag.contains(key + "Nano", Tag.TAG_INT)) {
                throw new IllegalStateException("Custody timestamp is missing");
            }
            int nanos = tag.getInt(key + "Nano");
            if (nanos < 0 || nanos > 999_999_999) {
                throw new IllegalStateException("Custody timestamp nanoseconds are invalid");
            }
            return Instant.ofEpochSecond(tag.getLong(key + "Second"), nanos);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid custody timestamp", exception);
        }
    }

    private static void requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalStateException("Custody data is missing UUID " + key);
        }
    }

    private static CompoundTag requireCompound(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof CompoundTag compound)) {
            throw new IllegalStateException("Custody data is missing compound " + key);
        }
        return compound;
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)) {
            throw new IllegalStateException("Custody data is missing list " + key);
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException("Custody data list has the wrong element type " + key);
        }
        return list;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, String label) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown " + label, exception);
        }
    }
}
