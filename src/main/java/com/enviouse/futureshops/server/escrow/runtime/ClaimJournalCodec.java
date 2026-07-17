package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

public final class ClaimJournalCodec {
    private static final int LEGACY_VERSION = 1;
    private static final int NANOSECOND_VERSION = 2;
    private static final int VERSION = 3;

    private ClaimJournalCodec() {
    }

    public static byte[] encodeClaim(EscrowClaim claim) {
        Objects.requireNonNull(claim, "claim");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, claim.claimId());
            BinaryCodecSupport.writeUuid(output, claim.transactionId());
            BinaryCodecSupport.writeUuid(output, claim.ownerId());
            BinaryCodecSupport.writeString(output, claim.sourceKey(), 768);
            BinaryCodecSupport.writeString(output, claim.kind().name(), 128);
            output.writeLong(claim.originalUnits());
            output.writeLong(claim.remainingUnits());
            output.writeInt(claim.payload().length);
            output.write(claim.payload());
            BinaryCodecSupport.writeString(output, claim.status().name(), 128);
            BinaryCodecSupport.writeString(output, claim.label(), 640);
            writeInstant(output, claim.createdAt());
            writeInstant(output, claim.updatedAt());
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > EscrowJournalEventCodec.MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Claim journal body is too large");
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode claim journal body", ex);
        }
    }

    public static EscrowClaim decodeClaim(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            int version = requireVersion(input.readInt());
            java.util.UUID claimId = BinaryCodecSupport.readUuid(input);
            java.util.UUID transactionId = BinaryCodecSupport.readUuid(input);
            java.util.UUID ownerId = BinaryCodecSupport.readUuid(input);
            String sourceKey = version >= VERSION
                    ? BinaryCodecSupport.readString(input, 768)
                    : "legacy.claim." + claimId;
            ClaimKind kind;
            try {
                kind = ClaimKind.valueOf(BinaryCodecSupport.readString(input, 128));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown claim kind", ex);
            }
            long original = input.readLong();
            long remaining = input.readLong();
            int payloadSize = input.readInt();
            if (payloadSize < 0 || payloadSize > EscrowClaim.MAX_PAYLOAD_BYTES || payloadSize > input.available()) {
                throw new IllegalArgumentException("Invalid claim journal payload size");
            }
            byte[] payload = input.readNBytes(payloadSize);
            ClaimStatus status;
            try {
                status = ClaimStatus.valueOf(BinaryCodecSupport.readString(input, 128));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown claim status", ex);
            }
            String label = BinaryCodecSupport.readString(input, 640);
            Instant created = readInstant(input, version);
            Instant updated = readInstant(input, version);
            if (input.read() != -1) {
                throw new IllegalArgumentException("Claim journal body has trailing data");
            }
            return new EscrowClaim(claimId, transactionId, ownerId, sourceKey,
                    kind, original, remaining,
                    payload, status, label, created, updated);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode claim journal body", ex);
        }
    }

    public static byte[] encodeDelivery(ClaimDeliveryCommit delivery) {
        Objects.requireNonNull(delivery, "delivery");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, delivery.ownerId());
            BinaryCodecSupport.writeUuid(output, delivery.claimId());
            BinaryCodecSupport.writeString(output, delivery.requestKey(), 768);
            output.writeLong(delivery.units());
            writeInstant(output, delivery.deliveredAt());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode claim delivery", ex);
        }
    }

    public static ClaimDeliveryCommit decodeDelivery(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            int version = input.readInt();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported claim delivery journal version");
            }
            ClaimDeliveryCommit result = new ClaimDeliveryCommit(
                    BinaryCodecSupport.readUuid(input),
                    BinaryCodecSupport.readUuid(input),
                    BinaryCodecSupport.readString(input, 768),
                    input.readLong(),
                    readInstant(input, version));
            if (input.read() != -1) {
                throw new IllegalArgumentException("Claim delivery body has trailing data");
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode claim delivery", ex);
        }
    }

    public static byte[] encodeQuarantine(ClaimQuarantineCommit quarantine) {
        Objects.requireNonNull(quarantine, "quarantine");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            BinaryCodecSupport.writeUuid(output, quarantine.ownerId());
            BinaryCodecSupport.writeUuid(output, quarantine.claimId());
            BinaryCodecSupport.writeUuid(output, quarantine.transactionId());
            BinaryCodecSupport.writeString(output, quarantine.requestKey(), 768);
            writeInstant(output, quarantine.quarantinedAt());
            BinaryCodecSupport.writeString(output, quarantine.reasonCode(), 640);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode claim quarantine", ex);
        }
    }

    public static ClaimQuarantineCommit decodeQuarantine(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            int version = input.readInt();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported claim quarantine journal version");
            }
            ClaimQuarantineCommit result = new ClaimQuarantineCommit(
                    BinaryCodecSupport.readUuid(input),
                    BinaryCodecSupport.readUuid(input),
                    BinaryCodecSupport.readUuid(input),
                    BinaryCodecSupport.readString(input, 768),
                    readInstant(input, version),
                    BinaryCodecSupport.readString(input, 640));
            if (input.read() != -1) {
                throw new IllegalArgumentException("Claim quarantine body has trailing data");
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to decode claim quarantine", ex);
        }
    }

    private static int requireVersion(int version) {
        if (version != LEGACY_VERSION && version != NANOSECOND_VERSION && version != VERSION) {
            throw new IllegalArgumentException("Unsupported claim journal version");
        }
        return version;
    }

    private static void writeInstant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input, int version) throws IOException {
        if (version == LEGACY_VERSION) {
            return Instant.ofEpochMilli(input.readLong());
        }
        long epochSecond = input.readLong();
        int nano = input.readInt();
        if (nano < 0 || nano > 999_999_999) {
            throw new IllegalArgumentException("Invalid claim journal nanoseconds");
        }
        try {
            return Instant.ofEpochSecond(epochSecond, nano);
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Invalid claim journal time", exception);
        }
    }
}
