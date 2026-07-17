package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class EscrowJournalEventCodec {
    public static final int MAX_BODY_BYTES = WriteAheadJournal.MAX_PAYLOAD_BYTES - 32;

    private static final int MAGIC = 0x45564E54;
    private static final int VERSION = 1;

    private EscrowJournalEventCodec() {
    }

    public static byte[] encode(EscrowJournalEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(event.body().length + 16);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeByte(event.type().wireId());
            output.writeInt(event.body().length);
            output.write(event.body());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > WriteAheadJournal.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Escrow journal event exceeds payload limit");
            }
            return encoded;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode escrow journal event", ex);
        }
    }

    public static EscrowJournalEvent decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > WriteAheadJournal.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid escrow journal event size");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Escrow journal event magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported escrow journal event version");
            }
            EscrowJournalEventType type = EscrowJournalEventType.fromWireId(input.readUnsignedByte());
            int size = input.readInt();
            if (size <= 0 || size > MAX_BODY_BYTES || size != input.available()) {
                throw new IllegalArgumentException("Invalid escrow journal event body size");
            }
            byte[] body = input.readNBytes(size);
            if (body.length != size || input.read() != -1) {
                throw new IllegalArgumentException("Escrow journal event is truncated");
            }
            return new EscrowJournalEvent(type, body);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode escrow journal event", ex);
        }
    }
}
