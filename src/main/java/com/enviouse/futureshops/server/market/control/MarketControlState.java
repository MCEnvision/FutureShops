package com.enviouse.futureshops.server.market.control;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

public record MarketControlState(
        long globalRevision,
        Map<MarketControlModule, MarketModuleControl> modules,
        Map<UUID, MarketControlRequestReceipt> requestReceipts,
        List<MarketControlAuditEntry> auditEntries
) {
    public static final int MAX_AUDIT_ENTRIES = 50_000;
    public static final int MAX_REQUEST_RECEIPTS = 50_000;

    public MarketControlState {
        if (globalRevision < 0L) {
            throw new IllegalArgumentException(
                    "Market control global revision is invalid");
        }
        EnumMap<MarketControlModule, MarketModuleControl> moduleCopy =
                new EnumMap<>(MarketControlModule.class);
        moduleCopy.putAll(Objects.requireNonNull(modules, "modules"));
        modules = Map.copyOf(moduleCopy);
        requestReceipts = Map.copyOf(new HashMap<>(
                Objects.requireNonNull(requestReceipts,
                        "requestReceipts")));
        auditEntries = List.copyOf(new ArrayList<>(
                Objects.requireNonNull(auditEntries,
                        "auditEntries")));
        if (modules.size() != MarketControlModule.values().length
                || requestReceipts.size() > MAX_REQUEST_RECEIPTS
                || auditEntries.size() > MAX_AUDIT_ENTRIES) {
            throw new IllegalArgumentException(
                    "Market control state size is invalid");
        }
        for (MarketControlModule module : MarketControlModule.values()) {
            MarketModuleControl control = modules.get(module);
            if (control == null || control.module() != module) {
                throw new IllegalArgumentException(
                        "Market control module index is invalid");
            }
        }
        if (globalRevision != auditEntries.size()
                || requestReceipts.size() != auditEntries.size()) {
            throw new IllegalArgumentException(
                    "Market control history length is invalid");
        }
        EnumMap<MarketControlModule, Long> moduleRevisions =
                new EnumMap<>(MarketControlModule.class);
        EnumMap<MarketControlModule, MarketModuleStatus> moduleStatuses =
                new EnumMap<>(MarketControlModule.class);
        EnumMap<MarketControlModule, Long> accumulatedPauses =
                new EnumMap<>(MarketControlModule.class);
        EnumMap<MarketControlModule, OptionalLong> openPauses =
                new EnumMap<>(MarketControlModule.class);
        EnumMap<MarketControlModule, Optional<UUID>> cancellationBatches =
                new EnumMap<>(MarketControlModule.class);
        EnumMap<MarketControlModule, Optional<MarketPauseTimingEvidence>>
                lastPauseEvidence = new EnumMap<>(
                MarketControlModule.class);
        EnumMap<MarketControlModule, MarketControlAuditEntry> lastAudits =
                new EnumMap<>(MarketControlModule.class);
        EnumMap<MarketControlModule, Long> lastAppliedTimes =
                new EnumMap<>(MarketControlModule.class);
        for (MarketControlModule module : MarketControlModule.values()) {
            moduleRevisions.put(module, 0L);
            moduleStatuses.put(module, MarketModuleStatus.ENABLED);
            accumulatedPauses.put(module, 0L);
            openPauses.put(module, OptionalLong.empty());
            cancellationBatches.put(module, Optional.empty());
            lastPauseEvidence.put(module, Optional.empty());
            lastAppliedTimes.put(module, -1L);
        }
        Set<UUID> seenRequests = new HashSet<>();
        Set<UUID> seenCancellationBatches = new HashSet<>();
        Set<UUID> seenSafetyEvidence = new HashSet<>();
        long expectedGlobalRevision = 1L;
        for (MarketControlAuditEntry entry : auditEntries) {
            if (entry.globalRevision() != expectedGlobalRevision
                    || !seenRequests.add(entry.requestId())) {
                throw new IllegalArgumentException(
                        "Market control audit sequence is invalid");
            }
            long expectedModuleRevision = Math.addExact(
                    moduleRevisions.get(entry.module()), 1L);
            if (entry.moduleRevision() != expectedModuleRevision) {
                throw new IllegalArgumentException(
                        "Market control module audit sequence is invalid");
            }
            MarketControlModule module = entry.module();
            if (entry.previousStatus() != moduleStatuses.get(module)
                    || entry.appliedAtMillis()
                    < lastAppliedTimes.get(module)) {
                throw new IllegalArgumentException(
                        "Market control audit transition is invalid");
            }
            long accumulated = accumulatedPauses.get(module);
            OptionalLong openPause = openPauses.get(module);
            Optional<MarketPauseTimingEvidence> timing =
                    entry.pauseTimingEvidence();
            if (!entry.previousStatus().timersPaused()
                    && entry.nextStatus().timersPaused()) {
                MarketPauseTimingEvidence evidence =
                        timing.orElseThrow();
                if (!evidence.open()
                        || evidence.pausedAtMillis()
                        != entry.appliedAtMillis()
                        || evidence.accumulatedPausedMillisBefore()
                        != accumulated) {
                    throw new IllegalArgumentException(
                            "Market control pause opening is invalid");
                }
                openPause = OptionalLong.of(evidence.pausedAtMillis());
                lastPauseEvidence.put(module, timing);
            } else if (entry.previousStatus().timersPaused()
                    && !entry.nextStatus().timersPaused()) {
                MarketPauseTimingEvidence evidence =
                        timing.orElseThrow();
                if (evidence.open() || openPause.isEmpty()
                        || evidence.pausedAtMillis()
                        != openPause.getAsLong()
                        || evidence.resumedAtMillis().orElseThrow()
                        != entry.appliedAtMillis()
                        || evidence.accumulatedPausedMillisBefore()
                        != accumulated) {
                    throw new IllegalArgumentException(
                            "Market control pause closing is invalid");
                }
                accumulated = evidence.accumulatedPausedMillisAfter();
                openPause = OptionalLong.empty();
                lastPauseEvidence.put(module, timing);
            }
            Optional<UUID> cancellationBatch =
                    cancellationBatches.get(module);
            if (entry.nextStatus().requiresCancellationAndRefund()) {
                if (!seenCancellationBatches.add(
                        entry.cancellationBatchId().orElseThrow())) {
                    throw new IllegalArgumentException(
                            "Market control cancellation batch is reused");
                }
                cancellationBatch = entry.cancellationBatchId();
            } else if (entry.previousStatus()
                    .requiresCancellationAndRefund()) {
                MarketControlSafetyEvidence evidence =
                        entry.safetyEvidence().orElseThrow();
                if (!cancellationBatch.equals(Optional.of(
                        evidence.cancellationBatchId()))
                        || !seenSafetyEvidence.add(
                        evidence.evidenceId())
                        || evidence.observedAtMillis()
                        < lastAppliedTimes.get(module)
                        || evidence.observedAtMillis()
                        > entry.appliedAtMillis()) {
                    throw new IllegalArgumentException(
                            "Market control resume batch is invalid");
                }
                cancellationBatch = Optional.empty();
            }
            moduleStatuses.put(module, entry.nextStatus());
            accumulatedPauses.put(module, accumulated);
            openPauses.put(module, openPause);
            cancellationBatches.put(module, cancellationBatch);
            lastAudits.put(module, entry);
            lastAppliedTimes.put(module, entry.appliedAtMillis());
            moduleRevisions.put(entry.module(), expectedModuleRevision);
            MarketControlRequestReceipt receipt =
                    requestReceipts.get(entry.requestId());
            if (receipt == null
                    || !receipt.auditEntry().equals(entry)) {
                throw new IllegalArgumentException(
                        "Market control request receipt is invalid");
            }
            expectedGlobalRevision = Math.addExact(
                    expectedGlobalRevision, 1L);
        }
        for (MarketControlModule module : MarketControlModule.values()) {
            MarketModuleControl control = modules.get(module);
            MarketControlAuditEntry lastAudit = lastAudits.get(module);
            if (control.revision() != moduleRevisions.get(module)
                    || control.status() != moduleStatuses.get(module)
                    || control.accumulatedPausedMillis()
                    != accumulatedPauses.get(module)
                    || !control.pauseStartedAtMillis().equals(
                    openPauses.get(module))
                    || !control.cancellationBatchId().equals(
                    cancellationBatches.get(module))
                    || !control.lastPauseTimingEvidence().equals(
                    lastPauseEvidence.get(module))) {
                throw new IllegalArgumentException(
                        "Market control module revision is invalid");
            }
            if (lastAudit == null) {
                if (control.status() != MarketModuleStatus.ENABLED
                        || !control.actor().equals(
                        MarketControlActor.system())
                        || !control.reason().equals("Initial state")) {
                    throw new IllegalArgumentException(
                            "Initial market control module is invalid");
                }
            } else if (!control.actor().equals(lastAudit.actor())
                    || !control.reason().equals(lastAudit.reason())
                    || control.changedAtMillis()
                    != lastAudit.appliedAtMillis()) {
                throw new IllegalArgumentException(
                        "Current market control module is invalid");
            }
        }
    }

    public static MarketControlState initial(long initializedAtMillis) {
        if (initializedAtMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market control initialization time is invalid");
        }
        EnumMap<MarketControlModule, MarketModuleControl> modules =
                new EnumMap<>(MarketControlModule.class);
        for (MarketControlModule module : MarketControlModule.values()) {
            modules.put(module, MarketModuleControl.initial(
                    module, initializedAtMillis));
        }
        return new MarketControlState(0L, modules, Map.of(), List.of());
    }

    public MarketModuleControl module(MarketControlModule module) {
        return modules.get(Objects.requireNonNull(module, "module"));
    }

    public boolean hasMaterializedHistory() {
        return globalRevision > 0L;
    }
}
