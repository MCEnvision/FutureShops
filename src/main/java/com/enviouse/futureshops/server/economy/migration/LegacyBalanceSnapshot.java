package com.enviouse.futureshops.server.economy.migration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record LegacyBalanceSnapshot(List<LegacyBalanceEntry> entries,
                                    String fingerprint) {
    public static final int MAXIMUM_ENTRIES = 1_000_000;
    private static final byte[] FINGERPRINT_DOMAIN =
            "futureshops.legacy.balance.snapshot.v1".getBytes(StandardCharsets.UTF_8);
    private static final Comparator<LegacyBalanceEntry> UUID_ORDER =
            Comparator.comparing(entry -> entry.playerId().toString());

    public LegacyBalanceSnapshot {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        validateEntries(entries);
        if (!fingerprint.matches("[0-9a-f]{64}")
                || !fingerprint.equals(computeFingerprint(entries))) {
            throw new IllegalArgumentException("Legacy balance snapshot fingerprint is invalid");
        }
    }

    public static LegacyBalanceSnapshot capture(Map<UUID, Long> balances) {
        Objects.requireNonNull(balances, "balances");
        if (balances.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("Legacy balance snapshot exceeds its entry limit");
        }
        List<LegacyBalanceEntry> entries = new ArrayList<>(balances.size());
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            UUID playerId = Objects.requireNonNull(entry.getKey(), "legacy player id");
            Long balance = Objects.requireNonNull(entry.getValue(), "legacy balance");
            entries.add(new LegacyBalanceEntry(playerId, balance));
        }
        entries.sort(UUID_ORDER);
        List<LegacyBalanceEntry> immutableEntries = List.copyOf(entries);
        return new LegacyBalanceSnapshot(
                immutableEntries, computeFingerprint(immutableEntries));
    }

    public boolean matches(Map<UUID, Long> balances) {
        try {
            return equals(capture(balances));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    static String computeFingerprint(List<LegacyBalanceEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_DOMAIN);
            digest.update(intBytes(entries.size()));
            for (LegacyBalanceEntry entry : entries) {
                digest.update(entry.playerId().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(longBytes(entry.balanceMinorUnits()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void validateEntries(List<LegacyBalanceEntry> entries) {
        if (entries.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("Legacy balance snapshot exceeds its entry limit");
        }
        Set<UUID> players = new HashSet<>();
        LegacyBalanceEntry previous = null;
        for (LegacyBalanceEntry entry : entries) {
            Objects.requireNonNull(entry, "legacy balance entry");
            if (!players.add(entry.playerId())) {
                throw new IllegalArgumentException("Legacy balance snapshot has duplicate players");
            }
            if (previous != null && UUID_ORDER.compare(previous, entry) >= 0) {
                throw new IllegalArgumentException("Legacy balance snapshot order is invalid");
            }
            previous = entry;
        }
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
