package com.enviouse.futureshops.server.market.control;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class MarketControlRepository {
    private MarketControlRepository() {
    }

    public static MarketControlApplyResult transition(
            MarketControlState state,
            MarketControlTransitionCommand command
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        String requestFingerprint =
                MarketControlRequestFingerprints.fingerprint(command);
        MarketControlRequestReceipt existing =
                state.requestReceipts().get(command.requestId());
        if (existing != null) {
            if (!existing.requestFingerprint().equals(
                    requestFingerprint)) {
                throw conflict(
                        "Market control request id was reused");
            }
            return new MarketControlApplyResult(state,
                    existing.auditEntry(), Optional.empty(), true);
        }
        if (state.auditEntries().size()
                >= MarketControlState.MAX_AUDIT_ENTRIES) {
            throw conflict("Market control audit capacity is exhausted");
        }
        MarketModuleControl current = state.module(command.module());
        if (command.targetStatus()
                == MarketModuleStatus.CANCEL_AND_REFUND) {
            UUID batch = command.cancellationBatchId().orElseThrow();
            boolean reused = state.auditEntries().stream().anyMatch(
                    entry -> entry.cancellationBatchId()
                            .filter(batch::equals).isPresent());
            if (reused) {
                throw conflict(
                        "Market control cancellation batch was reused");
            }
        }
        validateTransition(current, command);
        long moduleRevision = Math.addExact(current.revision(), 1L);
        long globalRevision = Math.addExact(state.globalRevision(), 1L);

        OptionalLong pauseStartedAt = OptionalLong.empty();
        long accumulatedPause = current.accumulatedPausedMillis();
        Optional<MarketPauseTimingEvidence> lastPause =
                current.lastPauseTimingEvidence();
        Optional<MarketPauseTimingEvidence> transitionPause =
                Optional.empty();
        if (!current.status().timersPaused()
                && command.targetStatus().timersPaused()) {
            MarketPauseTimingEvidence evidence =
                    MarketPauseTimingEvidence.opened(
                            command.appliedAtMillis(), accumulatedPause);
            pauseStartedAt = OptionalLong.of(
                    command.appliedAtMillis());
            lastPause = Optional.of(evidence);
            transitionPause = Optional.of(evidence);
        } else if (current.status().timersPaused()
                && !command.targetStatus().timersPaused()) {
            MarketPauseTimingEvidence evidence =
                    MarketPauseTimingEvidence.closed(
                            current.pauseStartedAtMillis().orElseThrow(),
                            command.appliedAtMillis(), accumulatedPause);
            accumulatedPause =
                    evidence.accumulatedPausedMillisAfter();
            lastPause = Optional.of(evidence);
            transitionPause = Optional.of(evidence);
        }

        MarketModuleControl nextModule = new MarketModuleControl(
                current.module(), command.targetStatus(),
                moduleRevision, command.actor(), command.reason(),
                command.appliedAtMillis(), pauseStartedAt,
                accumulatedPause, lastPause,
                command.cancellationBatchId());
        MarketControlAuditEntry audit = new MarketControlAuditEntry(
                command.requestId(), requestFingerprint,
                command.module(), current.status(),
                command.targetStatus(), moduleRevision, globalRevision,
                command.actor(), command.reason(),
                command.requestedAtMillis(), command.appliedAtMillis(),
                command.cancellationBatchId(),
                command.safetyEvidence(), transitionPause);
        MarketControlState nextState = append(state, nextModule, audit);
        MarketControlMutation mutation = new MarketControlMutation(
                MarketControlStateCodec.fingerprint(state),
                MarketControlStateCodec.fingerprint(nextState), current,
                nextModule, audit);
        return new MarketControlApplyResult(nextState, audit,
                Optional.of(mutation), false);
    }

    public static MarketControlApplyResult applyMutation(
            MarketControlState state,
            MarketControlMutation mutation
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(mutation, "mutation");
        MarketControlAuditEntry audit = mutation.auditEntry();
        MarketControlRequestReceipt existing =
                state.requestReceipts().get(audit.requestId());
        if (existing != null) {
            if (!existing.auditEntry().equals(audit)) {
                throw conflict(
                        "Market control mutation request conflicts");
            }
            return new MarketControlApplyResult(state, audit,
                    Optional.empty(), true);
        }
        if (!MarketControlStateCodec.fingerprint(state).equals(
                mutation.previousStateFingerprint())) {
            throw conflict(
                    "Market control mutation previous state conflicts");
        }
        if (!state.module(audit.module()).equals(
                mutation.previousModule())
                || audit.globalRevision()
                != state.globalRevision() + 1L) {
            throw conflict(
                    "Market control mutation sequence conflicts");
        }
        MarketControlState next = append(state,
                mutation.nextModule(), audit);
        if (!MarketControlStateCodec.fingerprint(next).equals(
                mutation.nextStateFingerprint())) {
            throw conflict(
                    "Market control mutation next state conflicts");
        }
        return new MarketControlApplyResult(next, audit,
                Optional.of(mutation), false);
    }

    private static MarketControlState append(
            MarketControlState state,
            MarketModuleControl nextModule,
            MarketControlAuditEntry audit
    ) {
        EnumMap<MarketControlModule, MarketModuleControl> modules =
                new EnumMap<>(MarketControlModule.class);
        modules.putAll(state.modules());
        modules.put(nextModule.module(), nextModule);
        Map<UUID, MarketControlRequestReceipt> receipts =
                new HashMap<>(state.requestReceipts());
        receipts.put(audit.requestId(),
                new MarketControlRequestReceipt(
                        audit.requestFingerprint(), audit));
        List<MarketControlAuditEntry> audits =
                new ArrayList<>(state.auditEntries());
        audits.add(audit);
        return new MarketControlState(audit.globalRevision(), modules,
                receipts, audits);
    }

    private static void validateTransition(
            MarketModuleControl current,
            MarketControlTransitionCommand command
    ) {
        if (current.revision() != command.expectedModuleRevision()) {
            throw conflict(
                    "Market control module revision conflicts");
        }
        if (current.status() == command.targetStatus()) {
            throw conflict("Market control status is unchanged");
        }
        if (command.appliedAtMillis() < current.changedAtMillis()) {
            throw conflict("Market control transition time is stale");
        }
        if (!transitionAllowed(current.status(),
                command.targetStatus())) {
            throw conflict("Market control transition is unsafe");
        }
        if (current.status()
                == MarketModuleStatus.CANCEL_AND_REFUND) {
            MarketControlSafetyEvidence evidence =
                    command.safetyEvidence().orElseThrow(() ->
                            conflict(
                                    "Market control resume evidence is missing"));
            if (!evidence.cancellationBatchId().equals(
                    current.cancellationBatchId().orElseThrow())
                    || evidence.observedAtMillis()
                    < current.changedAtMillis()
                    || evidence.observedAtMillis()
                    > command.appliedAtMillis()
                    || !evidence.provesSafeResume()) {
                throw conflict(
                        "Market control resume evidence is unsafe");
            }
        } else if (command.safetyEvidence().isPresent()) {
            throw conflict(
                    "Market control resume evidence is unexpected");
        }
    }

    private static boolean transitionAllowed(
            MarketModuleStatus previous,
            MarketModuleStatus next
    ) {
        return switch (previous) {
            case ENABLED -> next == MarketModuleStatus.FROZEN
                    || next == MarketModuleStatus.DRAINING
                    || next == MarketModuleStatus.CANCEL_AND_REFUND;
            case FROZEN -> next == MarketModuleStatus.ENABLED
                    || next == MarketModuleStatus.DRAINING
                    || next == MarketModuleStatus.CANCEL_AND_REFUND;
            case DRAINING -> next == MarketModuleStatus.ENABLED
                    || next == MarketModuleStatus.FROZEN
                    || next == MarketModuleStatus.CANCEL_AND_REFUND;
            case CANCEL_AND_REFUND ->
                    next == MarketModuleStatus.ENABLED;
        };
    }

    private static MarketControlConflictException conflict(
            String message
    ) {
        return new MarketControlConflictException(message);
    }
}
