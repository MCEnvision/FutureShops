package com.enviouse.futureshops.server.debug;

import java.util.Objects;
import java.util.UUID;

/**
 * The stable, privacy bounded diagnostic projection shared by server routes.
 * Correlation references are capture scoped pseudonyms, never raw identities.
 */
public record DiagnosticEventV2(
        UUID captureId,
        String sourceCommit,
        String artifactHash,
        String side,
        long sequence,
        DebugModule module,
        String operation,
        String rootRef,
        String legRef,
        String actorRef,
        String provider,
        String lifecycle,
        String desiredState,
        String actualState,
        String reason,
        String capabilities,
        String journalState,
        String receiptState,
        String custodyState,
        String claimState,
        String nextAction
) {
    public DiagnosticEventV2 {
        Objects.requireNonNull(captureId, "captureId");
        Objects.requireNonNull(module, "module");
        sourceCommit = DebugDiagnostics.sanitize(sourceCommit);
        artifactHash = DebugDiagnostics.sanitize(artifactHash);
        side = DebugDiagnostics.sanitize(side);
        operation = DebugDiagnostics.sanitize(operation);
        rootRef = DebugDiagnostics.sanitize(rootRef);
        legRef = DebugDiagnostics.sanitize(legRef);
        actorRef = DebugDiagnostics.sanitize(actorRef);
        provider = DebugDiagnostics.sanitize(provider);
        lifecycle = DebugDiagnostics.sanitize(lifecycle);
        desiredState = DebugDiagnostics.sanitize(desiredState);
        actualState = DebugDiagnostics.sanitize(actualState);
        reason = DebugDiagnostics.sanitizeReason(reason);
        capabilities = DebugDiagnostics.sanitize(capabilities);
        journalState = DebugDiagnostics.sanitize(journalState);
        receiptState = DebugDiagnostics.sanitize(receiptState);
        custodyState = DebugDiagnostics.sanitize(custodyState);
        claimState = DebugDiagnostics.sanitize(claimState);
        nextAction = DebugDiagnostics.sanitize(nextAction);
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
    }
}
