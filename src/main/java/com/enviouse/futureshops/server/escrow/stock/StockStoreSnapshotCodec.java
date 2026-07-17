package com.enviouse.futureshops.server.escrow.stock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StockStoreSnapshotCodec {
    private static final int MAGIC = 0x53545350;
    private static final int VERSION = 2;
    private static final int DIGEST_BYTES = 32;

    private StockStoreSnapshotCodec() {
    }

    public static byte[] encode(StockStoreSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeLong(snapshot.storeRevision());
            StockBinaryIo.writeString(output, snapshot.catalogFingerprint());

            List<CatalogStockState> listings = snapshot.listings().values().stream()
                    .sorted(java.util.Comparator.comparing(CatalogStockState::key)).toList();
            output.writeInt(listings.size());
            for (CatalogStockState listing : listings) {
                writeRecord(output, StockRecordCodec.encodeListing(listing));
            }

            List<StockReservation> reservations = snapshot.reservations().values().stream()
                    .sorted(java.util.Comparator.comparing(StockReservation::reservationId))
                    .toList();
            output.writeInt(reservations.size());
            for (StockReservation reservation : reservations) {
                writeRecord(output, StockRecordCodec.encodeReservation(reservation));
            }

            List<StockMutationReceipt> receipts = snapshot.receipts().values().stream()
                    .sorted(java.util.Comparator.comparing(value -> value.requestId().toString()))
                    .toList();
            output.writeInt(receipts.size());
            for (StockMutationReceipt receipt : receipts) {
                writeRecord(output, StockRecordCodec.encodeReceipt(receipt));
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            byte[] digest = digest(payload);
            byte[] encoded = java.util.Arrays.copyOf(payload,
                    Math.addExact(payload.length, digest.length));
            System.arraycopy(digest, 0, encoded, payload.length, digest.length);
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode stock snapshot", exception);
        }
    }

    public static StockStoreSnapshot decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        requireSize(encoded);
        int payloadLength = encoded.length - DIGEST_BYTES;
        byte[] payload = java.util.Arrays.copyOf(encoded, payloadLength);
        byte[] storedDigest = java.util.Arrays.copyOfRange(encoded, payloadLength,
                encoded.length);
        if (!MessageDigest.isEqual(digest(payload), storedDigest)) {
            throw new IllegalArgumentException("Stock snapshot digest does not match");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Stock snapshot magic does not match");
            }
            if (input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException("Unsupported stock snapshot version");
            }
            long storeRevision = input.readLong();
            String catalogFingerprint = StockBinaryIo.readString(input,
                    StockLimits.FINGERPRINT_LENGTH);
            Map<StockKey, CatalogStockState> listings = new HashMap<>();
            for (byte[] value : readRecords(input, StockLimits.MAX_LISTINGS,
                    "stock listing")) {
                CatalogStockState listing = StockRecordCodec.decodeListing(value);
                if (listings.put(listing.key(), listing) != null) {
                    throw new IllegalArgumentException("Duplicate stock listing");
                }
            }
            Map<StockReservationId, StockReservation> reservations = new HashMap<>();
            for (byte[] value : readRecords(input, StockLimits.MAX_RESERVATIONS,
                    "stock reservation")) {
                StockReservation reservation = StockRecordCodec.decodeReservation(value);
                if (reservations.put(reservation.reservationId(), reservation) != null) {
                    throw new IllegalArgumentException("Duplicate stock reservation");
                }
            }
            Map<UUID, StockMutationReceipt> receipts = new HashMap<>();
            for (byte[] value : readRecords(input, StockLimits.MAX_REQUESTS,
                    "stock receipt")) {
                StockMutationReceipt receipt = StockRecordCodec.decodeReceipt(value);
                if (receipts.put(receipt.requestId(), receipt) != null) {
                    throw new IllegalArgumentException("Duplicate stock receipt");
                }
            }
            StockBinaryIo.requireFinished(input, "Stock snapshot");
            StockStoreSnapshot snapshot = new StockStoreSnapshot(storeRevision,
                    catalogFingerprint, listings, reservations, receipts);
            PersistentStockRepository.validateSnapshot(snapshot);
            return snapshot;
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalArgumentException("Unable to decode stock snapshot", exception);
        }
    }

    private static void writeRecord(DataOutputStream output, byte[] encoded) throws IOException {
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static List<byte[]> readRecords(DataInputStream input, int maximumCount, String name)
            throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximumCount) {
            throw new IllegalArgumentException("Invalid " + name + " count");
        }
        List<byte[]> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int length = input.readInt();
            if (length <= 0 || length > StockLimits.MAX_ENCODED_BYTES
                    || length > input.available()) {
                throw new IllegalArgumentException("Invalid " + name + " record length");
            }
            values.add(input.readNBytes(length));
        }
        return values;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length <= DIGEST_BYTES
                || encoded.length > StockLimits.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Invalid stock snapshot size");
        }
    }

    private static byte[] digest(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
