package com.enviouse.futureshops.server.escrow.custody;

import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CustodyMutationCodec {
    public static final int MAX_ENCODED_BYTES = WriteAheadJournal.MAX_PAYLOAD_BYTES - 32;

    private static final int MAGIC = 0x43555354;
    private static final int VERSION = 2;
    private static final int MAX_STRING_BYTES = 8192;

    private CustodyMutationCodec() {
    }

    public static byte[] encode(CustodyMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            writeLot(output, mutation.resultingLot());
            writeReceipt(output, mutation.receipt());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Custody mutation exceeds journal payload bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode custody mutation", exception);
        }
    }

    public static CustodyMutation decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid custody mutation size");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Custody mutation magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported custody mutation version");
            }
            CustodyLot lot = readLot(input);
            CustodyOperationReceipt receipt = readReceipt(input);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Custody mutation has trailing bytes");
            }
            return new CustodyMutation(lot, receipt);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode custody mutation", exception);
        }
    }

    static void writeLot(DataOutputStream output, CustodyLot lot) throws IOException {
        writeUuid(output, lot.lotId());
        writeUuid(output, lot.transactionId());
        writeString(output, lot.reserveRequestKey());
        writeString(output, lot.assetType().name());
        writeString(output, lot.protectionTier().name());
        writeString(output, lot.sourceCapability().name());
        writeString(output, lot.state().name());
        output.writeLong(lot.units());
        writeString(output, lot.currencyProvider());
        output.writeInt(lot.itemSnapshots().size());
        for (CustodyItemSnapshot snapshot : lot.itemSnapshots()) {
            writeString(output, snapshot.registryId());
            output.writeInt(snapshot.count());
            writeBytes(output, snapshot.serializedNbt());
            writeBytes(output, snapshot.contentHash());
        }
        output.writeInt(lot.protectedProvenance().size());
        for (ProtectedCurrencyProvenance provenance : lot.protectedProvenance()) {
            writeUuid(output, provenance.mintId());
            output.writeLong(provenance.denominationMinorUnits());
            output.writeInt(provenance.authorizedCount());
            output.writeInt(provenance.billCount());
            writeString(output, provenance.serverIdentityEvidence());
            writeString(output, provenance.checksumEvidence());
        }
        writeBytes(output, lot.assetFingerprint());
        writeEvidence(output, lot.holdEvidence());
        writeInstant(output, lot.createdAt());
        writeInstant(output, lot.updatedAt());
        output.writeLong(lot.revision());
    }

    static CustodyLot readLot(DataInputStream input) throws IOException {
        UUID lotId = readUuid(input);
        UUID transactionId = readUuid(input);
        String requestKey = readString(input, CustodyLot.MAX_REQUEST_KEY_LENGTH * 4);
        CustodyAssetType type = readEnum(input, CustodyAssetType.class, "custody asset type");
        CustodyProtectionTier tier = readEnum(input, CustodyProtectionTier.class,
                "custody protection tier");
        CustodyAdapterCapability capability = readEnum(input, CustodyAdapterCapability.class,
                "custody adapter capability");
        CustodyLotState state = readEnum(input, CustodyLotState.class, "custody lot state");
        long units = input.readLong();
        String provider = readString(input, CustodyLot.MAX_PROVIDER_LENGTH * 4);
        int snapshotCount = readCount(input, CustodyLot.MAX_SNAPSHOTS, "custody snapshot count");
        List<CustodyItemSnapshot> snapshots = new ArrayList<>(snapshotCount);
        long totalNbtBytes = 0L;
        for (int index = 0; index < snapshotCount; index++) {
            String registryId = readString(input, CustodyItemSnapshot.MAX_REGISTRY_ID_LENGTH * 4);
            int count = input.readInt();
            byte[] nbt = readBytes(input, CustodyItemSnapshot.MAX_NBT_BYTES, "custody item NBT");
            totalNbtBytes = Math.addExact(totalNbtBytes, nbt.length);
            if (totalNbtBytes > CustodyLot.MAX_TOTAL_NBT_BYTES) {
                throw new IllegalArgumentException("Custody item NBT total exceeds bounds");
            }
            byte[] hash = readBytes(input, CustodyHashes.HASH_BYTES, "custody item hash");
            snapshots.add(new CustodyItemSnapshot(registryId, count, nbt, hash));
        }
        int provenanceCount = readCount(input, CustodyLot.MAX_SNAPSHOTS,
                "custody provenance count");
        List<ProtectedCurrencyProvenance> provenance = new ArrayList<>(provenanceCount);
        for (int index = 0; index < provenanceCount; index++) {
            provenance.add(new ProtectedCurrencyProvenance(readUuid(input), input.readLong(),
                    input.readInt(), input.readInt(),
                    readString(input,
                            ProtectedCurrencyProvenance.MAX_SERVER_EVIDENCE_LENGTH * 4),
                    readString(input,
                            ProtectedCurrencyProvenance.MAX_CHECKSUM_EVIDENCE_LENGTH * 4)));
        }
        byte[] fingerprint = readBytes(input, CustodyHashes.HASH_BYTES,
                "custody asset fingerprint");
        CustodyTransferEvidence evidence = readEvidence(input);
        Instant created = readInstant(input);
        Instant updated = readInstant(input);
        long revision = input.readLong();
        return new CustodyLot(lotId, transactionId, requestKey, type, tier, capability, state,
                units, provider, snapshots, provenance, fingerprint, evidence,
                created, updated, revision);
    }

    private static void writeReceipt(DataOutputStream output,
                                     CustodyOperationReceipt receipt) throws IOException {
        writeUuid(output, receipt.receiptId());
        writeUuid(output, receipt.lotId());
        writeUuid(output, receipt.transactionId());
        writeString(output, receipt.operation().name());
        writeString(output, receipt.requestKey());
        writeStrictBoolean(output, receipt.previousState().isPresent());
        if (receipt.previousState().isPresent()) {
            writeString(output, receipt.previousState().orElseThrow().name());
        }
        writeString(output, receipt.resultingState().name());
        output.writeLong(receipt.units());
        writeBytes(output, receipt.assetFingerprint());
        writeEvidence(output, receipt.evidence());
        writeInstant(output, receipt.createdAt());
    }

    private static CustodyOperationReceipt readReceipt(DataInputStream input) throws IOException {
        UUID receiptId = readUuid(input);
        UUID lotId = readUuid(input);
        UUID transactionId = readUuid(input);
        CustodyOperation operation = readEnum(input, CustodyOperation.class, "custody operation");
        String requestKey = readString(input, CustodyLot.MAX_REQUEST_KEY_LENGTH * 4);
        Optional<CustodyLotState> previous = readStrictBoolean(input)
                ? Optional.of(readEnum(input, CustodyLotState.class, "custody previous state"))
                : Optional.empty();
        CustodyLotState resulting = readEnum(input, CustodyLotState.class,
                "custody resulting state");
        long units = input.readLong();
        byte[] fingerprint = readBytes(input, CustodyHashes.HASH_BYTES,
                "custody receipt fingerprint");
        CustodyTransferEvidence evidence = readEvidence(input);
        Instant created = readInstant(input);
        return new CustodyOperationReceipt(receiptId, lotId, transactionId, operation,
                requestKey, previous, resulting, units, fingerprint, evidence, created);
    }

    static void writeEvidence(DataOutputStream output,
                              CustodyTransferEvidence evidence) throws IOException {
        writeEndpoint(output, evidence.source());
        writeEndpoint(output, evidence.destination());
    }

    static CustodyTransferEvidence readEvidence(DataInputStream input) throws IOException {
        return new CustodyTransferEvidence(readEndpoint(input), readEndpoint(input));
    }

    private static void writeEndpoint(DataOutputStream output,
                                      CustodyEndpointEvidence endpoint) throws IOException {
        writeString(output, endpoint.adapterId());
        writeString(output, endpoint.capability().name());
        writeString(output, endpoint.ownerKey());
        writeString(output, endpoint.locationKey());
        writeBytes(output, endpoint.beforeStateHash());
        writeBytes(output, endpoint.afterStateHash());
        writeString(output, endpoint.mutationToken());
    }

    private static CustodyEndpointEvidence readEndpoint(DataInputStream input) throws IOException {
        return new CustodyEndpointEvidence(
                readString(input, MAX_STRING_BYTES),
                readEnum(input, CustodyAdapterCapability.class, "custody endpoint capability"),
                readString(input, MAX_STRING_BYTES),
                readString(input, MAX_STRING_BYTES),
                readBytes(input, CustodyHashes.HASH_BYTES, "custody before state hash"),
                readBytes(input, CustodyHashes.HASH_BYTES, "custody after state hash"),
                readString(input, MAX_STRING_BYTES));
    }

    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        writeBytes(output, CustodyHashes.strictUtf8(value));
    }

    static String readString(DataInputStream input, int maximumBytes) throws IOException {
        byte[] encoded = readBytes(input, Math.min(MAX_STRING_BYTES, maximumBytes),
                "custody string");
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Custody string is not valid UTF-8", exception);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input,
                                    int maximumBytes,
                                    String label) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > maximumBytes || size > input.available()) {
            throw new IllegalArgumentException("Invalid " + label + " size");
        }
        byte[] value = input.readNBytes(size);
        if (value.length != size) {
            throw new IllegalArgumentException(label + " is truncated");
        }
        return value;
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return count;
    }

    static <E extends Enum<E>> E readEnum(DataInputStream input,
                                          Class<E> enumType,
                                          String label) throws IOException {
        String name = readString(input, 128);
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + label, exception);
        }
    }

    static void writeInstant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Invalid custody instant nanoseconds");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid custody instant", exception);
        }
    }

    static void writeStrictBoolean(DataOutputStream output, boolean value) throws IOException {
        output.writeByte(value ? 1 : 0);
    }

    static boolean readStrictBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("Invalid custody boolean value");
        }
        return value == 1;
    }
}
