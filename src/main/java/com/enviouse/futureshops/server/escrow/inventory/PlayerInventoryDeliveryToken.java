package com.enviouse.futureshops.server.escrow.inventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public record PlayerInventoryDeliveryToken(
        int version,
        UUID playerId,
        UUID claimId,
        UUID transactionId,
        UUID batchId,
        UUID lotId,
        UUID receiptId,
        byte[] requestKeyHash,
        byte[] assetFingerprint,
        byte[] beforeInventoryHash,
        byte[] afterInventoryHash,
        byte[] digest
) {
    private static final int MAGIC = 0x50494454;
    static final int LEGACY_VERSION = 1;
    static final int CURRENT_VERSION = 2;
    private static final int MAX_ENCODED_BYTES = 512;

    public PlayerInventoryDeliveryToken {
        if (version < LEGACY_VERSION || version > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Player inventory delivery token version is invalid");
        }
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(receiptId, "receiptId");
        requestKeyHash = cloneHash(requestKeyHash, "requestKeyHash");
        assetFingerprint = cloneHash(assetFingerprint, "assetFingerprint");
        beforeInventoryHash = cloneHash(
                beforeInventoryHash, "beforeInventoryHash");
        afterInventoryHash = cloneHash(
                afterInventoryHash, "afterInventoryHash");
        digest = cloneHash(digest, "digest");
        byte[] expected = digest(version, playerId, claimId,
                transactionId, batchId,
                lotId, receiptId, requestKeyHash, assetFingerprint,
                beforeInventoryHash, afterInventoryHash);
        if (!PlayerInventoryHashes.equal(expected, digest)) {
            throw new IllegalArgumentException(
                    "Player inventory delivery token digest is invalid");
        }
    }

    public static PlayerInventoryDeliveryToken create(
            UUID playerId,
            UUID claimId,
            UUID transactionId,
            UUID batchId,
            UUID lotId,
            String requestKey,
            byte[] assetFingerprint,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash
    ) {
        return create(CURRENT_VERSION, playerId, claimId,
                transactionId, batchId, lotId, requestKey,
                assetFingerprint, beforeInventoryHash,
                afterInventoryHash);
    }

    static PlayerInventoryDeliveryToken createLegacy(
            UUID playerId,
            UUID claimId,
            UUID transactionId,
            UUID batchId,
            UUID lotId,
            String requestKey,
            byte[] assetFingerprint,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash
    ) {
        return create(LEGACY_VERSION, playerId, claimId,
                transactionId, batchId, lotId, requestKey,
                assetFingerprint, beforeInventoryHash,
                afterInventoryHash);
    }

    private static PlayerInventoryDeliveryToken create(
            int version,
            UUID playerId,
            UUID claimId,
            UUID transactionId,
            UUID batchId,
            UUID lotId,
            String requestKey,
            byte[] assetFingerprint,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash
    ) {
        Objects.requireNonNull(requestKey, "requestKey");
        UUID receiptId = UUID.nameUUIDFromBytes((
                "futureshops player inventory receipt " + batchId)
                .getBytes(StandardCharsets.UTF_8));
        byte[] requestHash = PlayerInventoryHashes.hashText(requestKey);
        byte[] tokenDigest = digest(version, playerId, claimId,
                transactionId, batchId,
                lotId, receiptId, requestHash, assetFingerprint,
                beforeInventoryHash, afterInventoryHash);
        return new PlayerInventoryDeliveryToken(version,
                playerId, claimId,
                transactionId, batchId, lotId, receiptId, requestHash,
                assetFingerprint, beforeInventoryHash, afterInventoryHash,
                tokenDigest);
    }

    public String encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(version);
                writeUuid(output, playerId);
                writeUuid(output, claimId);
                writeUuid(output, transactionId);
                writeUuid(output, batchId);
                writeUuid(output, lotId);
                writeUuid(output, receiptId);
                output.write(requestKeyHash);
                output.write(assetFingerprint);
                output.write(beforeInventoryHash);
                output.write(afterInventoryHash);
                output.write(digest);
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalStateException(
                        "Player inventory delivery token exceeds its limit");
            }
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(encoded);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode player inventory delivery token",
                    exception);
        }
    }

    public static PlayerInventoryDeliveryToken decode(String token) {
        Objects.requireNonNull(token, "token");
        byte[] encoded;
        try {
            encoded = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Player inventory delivery token is malformed", exception);
        }
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Player inventory delivery token size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Player inventory delivery token version is invalid");
            }
            int version = input.readInt();
            if (version < LEGACY_VERSION || version > CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Player inventory delivery token version is invalid");
            }
            PlayerInventoryDeliveryToken result =
                    new PlayerInventoryDeliveryToken(
                            version,
                            readUuid(input), readUuid(input), readUuid(input),
                            readUuid(input), readUuid(input), readUuid(input),
                            input.readNBytes(PlayerInventoryHashes.HASH_BYTES),
                            input.readNBytes(PlayerInventoryHashes.HASH_BYTES),
                            input.readNBytes(PlayerInventoryHashes.HASH_BYTES),
                            input.readNBytes(PlayerInventoryHashes.HASH_BYTES),
                            input.readNBytes(PlayerInventoryHashes.HASH_BYTES));
            if (input.available() != 0) {
                throw new IllegalArgumentException(
                        "Player inventory delivery token has trailing data");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Player inventory delivery token is truncated", exception);
        }
    }

    public boolean matches(String requestKey, byte[] fingerprint) {
        return PlayerInventoryHashes.equal(
                requestKeyHash, PlayerInventoryHashes.hashText(requestKey))
                && PlayerInventoryHashes.equal(
                assetFingerprint, fingerprint);
    }

    @Override
    public byte[] requestKeyHash() {
        return requestKeyHash.clone();
    }

    @Override
    public byte[] assetFingerprint() {
        return assetFingerprint.clone();
    }

    @Override
    public byte[] beforeInventoryHash() {
        return beforeInventoryHash.clone();
    }

    @Override
    public byte[] afterInventoryHash() {
        return afterInventoryHash.clone();
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof PlayerInventoryDeliveryToken other)) {
            return false;
        }
        return version == other.version
                && playerId.equals(other.playerId)
                && claimId.equals(other.claimId)
                && transactionId.equals(other.transactionId)
                && batchId.equals(other.batchId)
                && lotId.equals(other.lotId)
                && receiptId.equals(other.receiptId)
                && Arrays.equals(requestKeyHash, other.requestKeyHash)
                && Arrays.equals(assetFingerprint, other.assetFingerprint)
                && Arrays.equals(beforeInventoryHash,
                other.beforeInventoryHash)
                && Arrays.equals(afterInventoryHash,
                other.afterInventoryHash)
                && Arrays.equals(digest, other.digest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, playerId, claimId,
                transactionId, batchId,
                lotId, receiptId);
        result = 31 * result + Arrays.hashCode(requestKeyHash);
        result = 31 * result + Arrays.hashCode(assetFingerprint);
        result = 31 * result + Arrays.hashCode(beforeInventoryHash);
        result = 31 * result + Arrays.hashCode(afterInventoryHash);
        return 31 * result + Arrays.hashCode(digest);
    }

    private static byte[] digest(
            int version,
            UUID playerId,
            UUID claimId,
            UUID transactionId,
            UUID batchId,
            UUID lotId,
            UUID receiptId,
            byte[] requestKeyHash,
            byte[] assetFingerprint,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(version);
                writeUuid(output, playerId);
                writeUuid(output, claimId);
                writeUuid(output, transactionId);
                writeUuid(output, batchId);
                writeUuid(output, lotId);
                writeUuid(output, receiptId);
                output.write(requestKeyHash);
                output.write(assetFingerprint);
                output.write(beforeInventoryHash);
                output.write(afterInventoryHash);
            }
            return PlayerInventoryHashes.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to hash player inventory delivery token",
                    exception);
        }
    }

    private static byte[] cloneHash(byte[] value, String label) {
        byte[] copy = Objects.requireNonNull(value, label).clone();
        PlayerInventoryHashes.requireHash(copy, label);
        return copy;
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
