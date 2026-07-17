package com.enviouse.futureshops.server.escrow.checkpoint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

final class EscrowCheckpointComponentCodec {
    static final int FORMAT_VERSION = 1;
    static final int FIXED_BYTES = Integer.BYTES * 4 + 32;

    private static final int MAGIC = 0x46534353;

    private EscrowCheckpointComponentCodec() {
    }

    static byte[] encode(EscrowCheckpointStore store, CompoundTag tag,
                         int maximumStoreBytes) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(tag, "tag");
        requireMaximum(maximumStoreBytes);
        int maximumPayloadBytes = maximumStoreBytes - FIXED_BYTES;
        try {
            BoundedOutputStream bounded = new BoundedOutputStream(maximumPayloadBytes);
            DataOutputStream nbtOutput = new DataOutputStream(bounded);
            NbtIo.write(tag, nbtOutput);
            nbtOutput.flush();
            byte[] payload = bounded.toByteArray();
            if (payload.length == 0) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component is empty");
            }
            ByteArrayOutputStream encoded = new ByteArrayOutputStream(
                    Math.addExact(FIXED_BYTES, payload.length));
            DataOutputStream output = new DataOutputStream(encoded);
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(store.wireId());
            output.writeInt(payload.length);
            output.write(payload);
            output.write(sha256(payload));
            output.flush();
            byte[] result = encoded.toByteArray();
            if (result.length > maximumStoreBytes) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component exceeds its size limit");
            }
            return result;
        } catch (IOException | ArithmeticException exception) {
            throw new EscrowCheckpointSnapshotException(
                    "Unable to encode escrow checkpoint component", exception);
        }
    }

    static CompoundTag decode(EscrowCheckpointStore expectedStore, byte[] encoded,
                              int maximumStoreBytes) {
        Objects.requireNonNull(expectedStore, "expectedStore");
        Objects.requireNonNull(encoded, "encoded");
        requireMaximum(maximumStoreBytes);
        if (encoded.length <= FIXED_BYTES || encoded.length > maximumStoreBytes) {
            throw new EscrowCheckpointSnapshotException(
                    "Escrow checkpoint component size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component magic is invalid");
            }
            int format = input.readInt();
            if (format != FORMAT_VERSION) {
                throw new EscrowCheckpointSnapshotException(format > FORMAT_VERSION
                        ? "Escrow checkpoint component schema is newer than this build"
                        : "Escrow checkpoint component schema is unsupported");
            }
            EscrowCheckpointStore actualStore;
            try {
                actualStore = EscrowCheckpointStore.fromWireId(input.readInt());
            } catch (IllegalArgumentException exception) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component store is invalid", exception);
            }
            if (actualStore != expectedStore) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component store does not match");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0
                    || payloadLength > maximumStoreBytes - FIXED_BYTES
                    || payloadLength != encoded.length - FIXED_BYTES) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component payload size is invalid");
            }
            byte[] payload = input.readNBytes(payloadLength);
            byte[] expectedHash = input.readNBytes(32);
            if (payload.length != payloadLength || expectedHash.length != 32
                    || input.read() != -1) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component is truncated");
            }
            if (!MessageDigest.isEqual(expectedHash, sha256(payload))) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component checksum is invalid");
            }
            ByteArrayInputStream nbtBytes = new ByteArrayInputStream(payload);
            CompoundTag tag;
            try (DataInputStream nbtInput = new DataInputStream(nbtBytes)) {
                long accountedLimit = Math.multiplyExact((long) maximumStoreBytes, 4L);
                tag = NbtIo.read(nbtInput, new NbtAccounter(accountedLimit));
            }
            if (tag == null || nbtBytes.available() != 0) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint component NBT is invalid");
            }
            return tag;
        } catch (EscrowCheckpointSnapshotException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new EscrowCheckpointSnapshotException(
                    "Escrow checkpoint component is malformed", exception);
        }
    }

    private static void requireMaximum(int maximumStoreBytes) {
        if (maximumStoreBytes <= FIXED_BYTES
                || maximumStoreBytes > EscrowCheckpoint.MAX_STORE_BYTES) {
            throw new IllegalArgumentException(
                    "Escrow checkpoint component size limit is invalid");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final int maximumBytes;
        private final ByteArrayOutputStream bytes;

        private BoundedOutputStream(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.bytes = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            bytes.write(value);
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, value.length);
            requireCapacity(length);
            bytes.write(value, offset, length);
        }

        private byte[] toByteArray() {
            return bytes.toByteArray();
        }

        private void requireCapacity(int additional) throws IOException {
            if (additional < 0 || bytes.size() > maximumBytes - additional) {
                throw new IOException("Escrow checkpoint component exceeds its size limit");
            }
        }
    }
}
