package com.enviouse.futureshops.server.escrow.stock;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;

final class StockRequestFingerprints {
    private StockRequestFingerprints() {
    }

    static String definition(StockMutationType operation, StockDefinition definition,
                             long expectedRevision) {
        return digest(output -> {
            output.writeInt(operation.wireId());
            StockRecordCodec.writeDefinition(output, definition);
            output.writeLong(expectedRevision);
        });
    }

    static String reserve(UUID transactionId, StockKey key, long quantity,
                          long expectedRevision) {
        return digest(output -> {
            output.writeInt(StockMutationType.RESERVE.wireId());
            StockBinaryIo.writeUuid(output, transactionId);
            StockRecordCodec.writeKey(output, key);
            output.writeInt(StockReservationDirection.OUTBOUND.wireId());
            output.writeLong(quantity);
            output.writeLong(expectedRevision);
        });
    }

    static String resolution(StockMutationType operation, UUID transactionId,
                             StockReservationId reservationId,
                             long expectedReservationRevision) {
        return digest(output -> {
            output.writeInt(operation.wireId());
            StockBinaryIo.writeUuid(output, transactionId);
            StockBinaryIo.writeUuid(output, reservationId.value());
            output.writeLong(expectedReservationRevision);
        });
    }

    static String reconcile(Collection<StockDefinition> definitions,
                            String catalogFingerprint) {
        return digest(output -> {
            output.writeInt(StockMutationType.RELOAD_RECONCILE.wireId());
            StockBinaryIo.writeString(output, catalogFingerprint);
            java.util.List<StockDefinition> sorted = definitions.stream()
                    .sorted(java.util.Comparator.comparing(StockDefinition::key)).toList();
            output.writeInt(sorted.size());
            for (StockDefinition definition : sorted) {
                StockRecordCodec.writeDefinition(output, definition);
            }
        });
    }

    static String reserveBatch(UUID transactionId,
                               Collection<StockReservationRequest> requests) {
        return digest(output -> {
            output.writeInt(StockMutationType.RESERVE_BATCH.wireId());
            StockBinaryIo.writeUuid(output, transactionId);
            java.util.List<StockReservationRequest> sorted = requests.stream()
                    .sorted().toList();
            output.writeInt(sorted.size());
            for (StockReservationRequest request : sorted) {
                StockRecordCodec.writeKey(output, request.stockKey());
                output.writeInt(request.direction().wireId());
                output.writeLong(request.quantity());
                output.writeLong(request.expectedListingRevision());
            }
        });
    }

    static String resolveBatch(StockMutationType operation, UUID transactionId,
                               Collection<StockReservationResolution> resolutions) {
        return digest(output -> {
            output.writeInt(operation.wireId());
            StockBinaryIo.writeUuid(output, transactionId);
            java.util.List<StockReservationResolution> sorted = resolutions.stream()
                    .sorted().toList();
            output.writeInt(sorted.size());
            for (StockReservationResolution resolution : sorted) {
                StockBinaryIo.writeUuid(output,
                        resolution.reservationId().value());
                output.writeLong(resolution.expectedReservationRevision());
            }
        });
    }

    private static String digest(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            StockBinaryIo.writeString(output, "futureshops.stock.request.1");
            writer.write(output);
            output.flush();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint stock request", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}
