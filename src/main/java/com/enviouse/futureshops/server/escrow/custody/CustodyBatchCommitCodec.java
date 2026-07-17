package com.enviouse.futureshops.server.escrow.custody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CustodyBatchCommitCodec {
    public static final int MAX_ENCODED_BYTES = CustodyMutationCodec.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x4342434d;
    private static final int VERSION = 1;

    private CustodyBatchCommitCodec() {
    }

    public static byte[] encode(CustodyBatchCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            byte[] batch = CustodyPreparedBatchCodec.encode(commit.batch());
            output.writeInt(batch.length);
            output.write(batch);
            output.writeInt(commit.mutations().size());
            for (CustodyMutation mutation : commit.mutations()) {
                byte[] encodedMutation = CustodyMutationCodec.encode(mutation);
                output.writeInt(encodedMutation.length);
                output.write(encodedMutation);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Custody batch commit exceeds journal bounds");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode custody batch commit", exception);
        }
    }

    public static CustodyBatchCommit decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid custody batch commit size");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC || input.readUnsignedShort() != VERSION) {
                throw new IllegalArgumentException("Unsupported custody batch commit header");
            }
            int batchBytes = input.readInt();
            if (batchBytes <= 0 || batchBytes > CustodyPreparedBatchCodec.MAX_ENCODED_BYTES
                    || batchBytes > input.available()) {
                throw new IllegalArgumentException("Invalid custody batch commit batch size");
            }
            CustodyPreparedBatch batch = CustodyPreparedBatchCodec.decode(
                    input.readNBytes(batchBytes));
            int count = input.readInt();
            if (count < 0 || count > CustodyBatchPlan.MAX_BATCH_LOTS) {
                throw new IllegalArgumentException("Invalid custody batch commit mutation count");
            }
            List<CustodyMutation> mutations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int mutationBytes = input.readInt();
                if (mutationBytes <= 0 || mutationBytes > CustodyMutationCodec.MAX_ENCODED_BYTES
                        || mutationBytes > input.available()) {
                    throw new IllegalArgumentException("Invalid custody batch commit mutation size");
                }
                mutations.add(CustodyMutationCodec.decode(input.readNBytes(mutationBytes)));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Custody batch commit has trailing bytes");
            }
            return new CustodyBatchCommit(batch, mutations);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode custody batch commit", exception);
        }
    }
}
