package com.enviouse.futureshops.server.escrow.mint;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class ProtectedMintIds {
    private ProtectedMintIds() {
    }

    public static UUID batchId(UUID transactionId, String requestKey) {
        if (transactionId == null) {
            throw new NullPointerException("transactionId");
        }
        return deterministicUuid("batch", transactionId.toString(),
                ProtectedMintText.requestKey(requestKey));
    }

    public static UUID receiptId(String requestKey) {
        return deterministicUuid("receipt", ProtectedMintText.requestKey(requestKey));
    }

    static byte[] hash(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static UUID deterministicUuid(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("futureshops.protected.mint".getBytes(StandardCharsets.UTF_8));
            for (String part : parts) {
                digest.update((byte) 0);
                digest.update(part.getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer bytes = ByteBuffer.wrap(hash);
            return new UUID(bytes.getLong(), bytes.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
