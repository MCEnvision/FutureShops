package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Immutable account identity projection retained across a provider operation. */
public record BindingV1(
        String providerId,
        int apiVersion,
        String adapterId,
        String protocol,
        String backendClassFingerprint,
        String artifactFingerprint,
        String lineage,
        UUID accountUuid,
        String currencyId,
        int precision,
        String durableManagerId,
        long generation,
        int receiptProtocol) {
    public static final int SCHEMA_VERSION = 1;

    public BindingV1 {
        if (!EconomyApi.isValidProviderId(providerId)) {
            throw new IllegalArgumentException("providerId is invalid");
        }
        requireToken(adapterId, "adapterId", 128);
        requireToken(protocol, "protocol", 64);
        requireFingerprint(backendClassFingerprint, "backendClassFingerprint");
        requireFingerprint(artifactFingerprint, "artifactFingerprint");
        requireToken(lineage, "lineage", 512);
        Objects.requireNonNull(accountUuid, "accountUuid");
        requireToken(currencyId, "currencyId", 128);
        if (apiVersion < 1 || apiVersion > 32 || precision < 0 || precision > 18
                || generation < 0L || receiptProtocol < 1 || receiptProtocol > 32) {
            throw new IllegalArgumentException("binding version or numeric field is outside bounds");
        }
        requireToken(durableManagerId, "durableManagerId", 512);
    }

    private static void requireToken(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxLength
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be a bounded single line");
        }
    }

    private static void requireFingerprint(String value, String field) {
        requireToken(value, field, 128);
        if ((value.length() != 64 && value.length() != 128)
                || !value.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException(field + " must be a hexadecimal fingerprint");
        }
    }
}
