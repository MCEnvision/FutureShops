package com.enviouse.futureshops.server.escrow.stock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class StockLimits {
    public static final int MAX_IDENTIFIER_LENGTH = 160;
    public static final int FINGERPRINT_LENGTH = 64;
    public static final long MAX_QUANTITY = 1_000_000_000L;
    public static final long MAX_REVISION = 1_000_000_000_000L;
    public static final int MAX_LISTINGS = 1_000_000;
    public static final int MAX_RESERVATIONS = 2_000_000;
    public static final int MAX_REQUESTS = 4_000_000;
    public static final int MAX_BATCH_LINES = 4_096;
    public static final int MAX_ENCODED_BYTES = 268_435_456;
    public static final int MAX_SNAPSHOT_BYTES = 50_331_648;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:/-]+");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private StockLimits() {
    }

    static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.length() > MAX_IDENTIFIER_LENGTH
                || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    static String requireFingerprint(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    static long requireQuantity(long value, boolean allowZero, String name) {
        if (value < (allowZero ? 0L : 1L) || value > MAX_QUANTITY) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    static long requireRevision(long value, boolean allowAbsent, String name) {
        if (value < (allowAbsent ? -1L : 0L) || value > MAX_REVISION) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    static UUID requireNonzeroUuid(UUID value, String name) {
        Objects.requireNonNull(value, name);
        if (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    static Instant requireInstant(Instant value, String name) {
        return Objects.requireNonNull(value, name);
    }

    static long nextRevision(long revision, String name) {
        requireRevision(revision, false, name);
        if (revision == MAX_REVISION) {
            throw new StockConflictException(name + " is exhausted");
        }
        return revision + 1L;
    }
}
