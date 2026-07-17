package com.enviouse.futureshops.server.escrow.custody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CustodyPreparedBatchCodec {
    public static final int MAX_ENCODED_BYTES = CustodyMutationCodec.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x43504254;
    private static final int VERSION = 1;

    private CustodyPreparedBatchCodec() {
    }

    public static byte[] encode(CustodyPreparedBatch batch) {
        Objects.requireNonNull(batch, "batch");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            CustodyMutationCodec.writeUuid(output, batch.batchId());
            CustodyMutationCodec.writeUuid(output, batch.transactionId());
            CustodyMutationCodec.writeString(output, batch.requestKey());
            output.writeInt(batch.operations().size());
            for (CustodyPreparedOperation operation : batch.operations()) {
                byte[] encodedOperation = CustodyPreparedOperationCodec.encode(operation);
                output.writeInt(encodedOperation.length);
                output.write(encodedOperation);
            }
            CustodyMutationCodec.writeString(output, batch.status().name());
            CustodyMutationCodec.writeInstant(output, batch.preparedAt());
            CustodyMutationCodec.writeInstant(output, batch.updatedAt());
            output.writeLong(batch.revision());
            CustodyMutationCodec.writeString(output, batch.detail());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Prepared custody batch exceeds journal bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode prepared custody batch", exception);
        }
    }

    public static CustodyPreparedBatch decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid prepared custody batch size");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Prepared custody batch magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported prepared custody batch version");
            }
            UUID batchId = CustodyMutationCodec.readUuid(input);
            UUID transactionId = CustodyMutationCodec.readUuid(input);
            String requestKey = CustodyMutationCodec.readString(input,
                    CustodyLot.MAX_REQUEST_KEY_LENGTH * 4);
            int count = input.readInt();
            if (count <= 0 || count > CustodyBatchPlan.MAX_BATCH_LOTS) {
                throw new IllegalArgumentException("Invalid prepared custody batch member count");
            }
            List<CustodyPreparedOperation> operations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int operationBytes = input.readInt();
                if (operationBytes <= 0
                        || operationBytes > CustodyPreparedOperationCodec.MAX_ENCODED_BYTES
                        || operationBytes > input.available()) {
                    throw new IllegalArgumentException("Invalid prepared custody batch member size");
                }
                operations.add(CustodyPreparedOperationCodec.decode(
                        input.readNBytes(operationBytes)));
            }
            CustodyBatchStatus status = CustodyMutationCodec.readEnum(input,
                    CustodyBatchStatus.class, "prepared custody batch status");
            Instant preparedAt = CustodyMutationCodec.readInstant(input);
            Instant updatedAt = CustodyMutationCodec.readInstant(input);
            long revision = input.readLong();
            String detail = CustodyMutationCodec.readString(input,
                    CustodyPreparedBatch.MAX_DETAIL_LENGTH * 4);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Prepared custody batch has trailing bytes");
            }
            return new CustodyPreparedBatch(batchId, transactionId, requestKey, operations,
                    status, preparedAt, updatedAt, revision, detail);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode prepared custody batch", exception);
        }
    }
}
