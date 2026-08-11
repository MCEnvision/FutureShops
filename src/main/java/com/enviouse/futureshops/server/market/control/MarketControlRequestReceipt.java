package com.enviouse.futureshops.server.market.control;

import java.util.Objects;

public record MarketControlRequestReceipt(
        String requestFingerprint,
        MarketControlAuditEntry auditEntry
) {
    public MarketControlRequestReceipt {
        requestFingerprint = MarketControlText.requireFingerprint(
                requestFingerprint, "request fingerprint");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        if (!requestFingerprint.equals(
                auditEntry.requestFingerprint())) {
            throw new IllegalArgumentException(
                    "Market control receipt fingerprint is invalid");
        }
    }
}
