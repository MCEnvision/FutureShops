package com.enviouse.futureshops.server.market.bazaar;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class BazaarIds {
    private BazaarIds() {
    }

    public static UUID fill(UUID requestId, UUID makerOrderId, UUID takerOrderId, int ordinal) {
        return derive("fill", requestId, makerOrderId, takerOrderId, Integer.toString(ordinal));
    }

    public static UUID settlement(UUID fillId) {
        return derive("settlement", fillId);
    }

    public static UUID immediateTerminal(UUID requestId, UUID orderId,
                                         String reason) {
        return derive("immediate_terminal", requestId, orderId, reason);
    }

    public static UUID terminal(UUID requestId, UUID orderId,
                                BazaarOperationType operation) {
        return derive("terminal", requestId, orderId, operation.name());
    }

    public static UUID systemAccount(String purpose) {
        return derive("system", purpose);
    }

    public static UUID derive(String purpose, Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, purpose);
            for (Object part : parts) {
                update(digest, String.valueOf(part));
            }
            byte[] bytes = digest.digest();
            bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x50);
            bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Bazaar identifier algorithm is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
