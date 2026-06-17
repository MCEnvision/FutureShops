package com.enviouse.futureshopsp.money;

import com.enviouse.futureshopsp.Config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MoneyChecksumService {
    private MoneyChecksumService() {
    }

    /**
     * Creates the anti-dupe checksum for a batch-minted coin stack.
     * <p>The {@code authorizedCount} field binds the mint record's batch size into
     * the hash so tampering with the NBT (e.g. cloning a stack and editing the
     * count) invalidates the checksum.</p>
     */
    public static String createChecksum(long denominationMinorUnits, String mintId, long mintTimestamp,
                                        String mintPlayer, String mintServer, int authorizedCount) {
        String payload = denominationMinorUnits + "|" + mintId + "|" + mintTimestamp + "|" + mintPlayer
                + "|" + mintServer + "|" + authorizedCount + "|" + Config.moneyChecksumSalt;
        return sha256(payload);
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
