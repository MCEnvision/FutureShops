package com.enviouse.futureshops.server.escrow.admin;

import java.util.Arrays;
import java.util.Objects;

public final class MaintenanceStateFingerprint {
    public static final int BYTE_LENGTH = 32;

    private final byte[] bytes;

    private MaintenanceStateFingerprint(byte[] bytes) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        if (this.bytes.length != BYTE_LENGTH || allZero(this.bytes)) {
            throw new IllegalArgumentException("Invalid maintenance state fingerprint");
        }
    }

    public static MaintenanceStateFingerprint of(byte[] bytes) {
        return new MaintenanceStateFingerprint(bytes);
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public boolean equals(Object value) {
        return this == value || value instanceof MaintenanceStateFingerprint other
                && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    private static boolean allZero(byte[] value) {
        int combined = 0;
        for (byte element : value) {
            combined |= element;
        }
        return combined == 0;
    }
}
