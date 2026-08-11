package com.enviouse.futureshops.server.market.control;

import java.util.Objects;

public record MarketControlMutation(
        String previousStateFingerprint,
        String nextStateFingerprint,
        MarketModuleControl previousModule,
        MarketModuleControl nextModule,
        MarketControlAuditEntry auditEntry
) {
    public MarketControlMutation {
        previousStateFingerprint = MarketControlText.requireFingerprint(
                previousStateFingerprint,
                "previous state fingerprint");
        nextStateFingerprint = MarketControlText.requireFingerprint(
                nextStateFingerprint, "next state fingerprint");
        previousModule = Objects.requireNonNull(
                previousModule, "previousModule");
        nextModule = Objects.requireNonNull(nextModule, "nextModule");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        if (previousStateFingerprint.equals(nextStateFingerprint)
                || previousModule.module() != nextModule.module()
                || previousModule.module() != auditEntry.module()
                || previousModule.status() != auditEntry.previousStatus()
                || nextModule.status() != auditEntry.nextStatus()
                || nextModule.revision() != auditEntry.moduleRevision()
                || nextModule.revision() <= 0L
                || previousModule.revision()
                != nextModule.revision() - 1L
                || !nextModule.actor().equals(auditEntry.actor())
                || !nextModule.reason().equals(auditEntry.reason())
                || nextModule.changedAtMillis()
                != auditEntry.appliedAtMillis()
                || !nextModule.cancellationBatchId().equals(
                auditEntry.cancellationBatchId())) {
            throw new IllegalArgumentException(
                    "Market control mutation is inconsistent");
        }
    }
}
