package com.enviouse.futureshops.coin;

import com.enviouse.futureshops.Config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CoinChecksumService {
    private CoinChecksumService() {
    }

    public static String createChecksum(long denominationMinorUnits, String mintId, long mintTimestamp, String mintPlayer, String mintServer) {
        String payload = denominationMinorUnits + "|" + mintId + "|" + mintTimestamp + "|" + mintPlayer + "|" + mintServer + "|" + Config.coinChecksumSalt;
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

