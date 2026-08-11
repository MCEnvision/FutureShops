package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockLimits;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CatalogStockSeedSnapshot(
        List<CatalogStockSeedEntry> entries,
        String fingerprint
) {
    private static final byte[] FINGERPRINT_DOMAIN =
            "futureshops.catalog.stock.seed.v1"
                    .getBytes(StandardCharsets.UTF_8);

    public CatalogStockSeedSnapshot {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        validateEntries(entries);
        if (!fingerprint.matches("[0-9a-f]{64}")
                || !fingerprint.equals(computeFingerprint(entries))) {
            throw new IllegalArgumentException(
                    "Catalog stock seed fingerprint is invalid");
        }
    }

    public static CatalogStockSeedSnapshot capture(
            Collection<CatalogStockSeedEntry> entries
    ) {
        List<CatalogStockSeedEntry> sorted = Objects.requireNonNull(
                entries, "entries").stream()
                .map(value -> Objects.requireNonNull(
                        value, "catalog stock seed entry"))
                .sorted().toList();
        return new CatalogStockSeedSnapshot(
                sorted, computeFingerprint(sorted));
    }

    public List<StockDefinition> definitions() {
        return entries.stream().map(CatalogStockSeedEntry::definition).toList();
    }

    public long finiteAvailableQuantity() {
        long total = 0L;
        for (CatalogStockSeedEntry entry : entries) {
            if (!entry.unlimited()) {
                total = Math.addExact(total, entry.availableQuantity());
            }
        }
        return total;
    }

    public long finiteCapacityQuantity() {
        long total = 0L;
        for (CatalogStockSeedEntry entry : entries) {
            if (!entry.unlimited()) {
                total = Math.addExact(total, entry.durableCapacity());
            }
        }
        return total;
    }

    public int unlimitedListings() {
        return (int) entries.stream()
                .filter(CatalogStockSeedEntry::unlimited).count();
    }

    static String computeFingerprint(List<CatalogStockSeedEntry> entries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_DOMAIN);
            digest.update(intBytes(entries.size()));
            for (CatalogStockSeedEntry entry : entries) {
                updateString(digest, entry.key().shopId());
                updateString(digest, entry.key().listingId());
                digest.update((byte) (entry.unlimited() ? 1 : 0));
                digest.update(longBytes(entry.configuredQuantity()));
                digest.update(longBytes(entry.availableQuantity()));
                updateString(digest, entry.configFingerprint());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void validateEntries(List<CatalogStockSeedEntry> entries) {
        if (entries.size() > StockLimits.MAX_BATCH_LINES) {
            throw new IllegalArgumentException(
                    "Catalog stock seed exceeds the reconciliation limit");
        }
        Set<StockKey> keys = new HashSet<>();
        CatalogStockSeedEntry previous = null;
        for (CatalogStockSeedEntry entry : entries) {
            Objects.requireNonNull(entry, "catalog stock seed entry");
            if (!keys.add(entry.key())) {
                throw new IllegalArgumentException(
                        "Catalog stock seed repeats a listing");
            }
            if (previous != null && previous.compareTo(entry) >= 0) {
                throw new IllegalArgumentException(
                        "Catalog stock seed order is invalid");
            }
            previous = entry;
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update(intBytes(encoded.length));
        digest.update(encoded);
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
