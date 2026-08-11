package com.enviouse.futureshops.server.escrow.claim;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ClaimRepository {
    public static final int DEFAULT_MAXIMUM_CLAIMS = 1_000_000;
    public static final int DEFAULT_MAXIMUM_ATTEMPTS = 1_000_000;
    public static final long DEFAULT_MAXIMUM_PAYLOAD_BYTES = 268_435_456L;

    private final Clock clock;
    private final int maximumClaims;
    private final int maximumAttempts;
    private final long maximumPayloadBytes;
    private final Map<UUID, EscrowClaim> claims = new LinkedHashMap<>();
    private final Map<String, UUID> claimsBySource = new HashMap<>();
    private final Map<UUID, String> claimFingerprints = new HashMap<>();
    private final Map<String, ClaimAttemptResult> attempts = new HashMap<>();
    private long claimPayloadBytes;

    public ClaimRepository() {
        this(Clock.systemUTC(), DEFAULT_MAXIMUM_CLAIMS, DEFAULT_MAXIMUM_ATTEMPTS,
                DEFAULT_MAXIMUM_PAYLOAD_BYTES);
    }

    public ClaimRepository(Clock clock) {
        this(clock, DEFAULT_MAXIMUM_CLAIMS, DEFAULT_MAXIMUM_ATTEMPTS,
                DEFAULT_MAXIMUM_PAYLOAD_BYTES);
    }

    ClaimRepository(Clock clock, int maximumClaims, int maximumAttempts,
                    long maximumPayloadBytes) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumClaims <= 0 || maximumAttempts <= 0 || maximumPayloadBytes <= 0L) {
            throw new IllegalArgumentException("Claim repository limits must be positive");
        }
        this.maximumClaims = maximumClaims;
        this.maximumAttempts = maximumAttempts;
        this.maximumPayloadBytes = maximumPayloadBytes;
    }

    public synchronized EscrowClaim create(EscrowClaim claim) {
        return evaluateCreate(claim, true);
    }

    public synchronized EscrowClaim preflightCreate(EscrowClaim claim) {
        return evaluateCreate(claim, false);
    }

    public synchronized void preflightCreateBatch(List<EscrowClaim> values) {
        Objects.requireNonNull(values, "values");
        java.util.Set<UUID> newClaimIds = new java.util.HashSet<>();
        java.util.Set<String> newSourceKeys = new java.util.HashSet<>();
        int additionalClaims = 0;
        long additionalPayloadBytes = 0L;
        for (EscrowClaim claim : values) {
            boolean existing = claims.containsKey(claim.claimId());
            preflightCreate(claim);
            if (existing) {
                continue;
            }
            if (!newClaimIds.add(claim.claimId())
                    || !newSourceKeys.add(claim.sourceKey())) {
                throw new ClaimConflictException(
                        "Claim creation batch contains duplicate identity");
            }
            additionalClaims = Math.addExact(additionalClaims, 1);
            additionalPayloadBytes = Math.addExact(
                    additionalPayloadBytes, claim.payload().length);
        }
        if (Math.addExact(claims.size(), additionalClaims) > maximumClaims
                || Math.addExact(claimPayloadBytes, additionalPayloadBytes)
                > maximumPayloadBytes) {
            throw new ClaimConflictException("Claim creation batch exceeds storage limits");
        }
    }

    private EscrowClaim evaluateCreate(EscrowClaim claim, boolean commit) {
        Objects.requireNonNull(claim, "claim");
        String fingerprint = fingerprint(claim);
        EscrowClaim existing = claims.get(claim.claimId());
        if (existing != null) {
            if (!Objects.equals(claimFingerprints.get(claim.claimId()), fingerprint)) {
                throw new ClaimConflictException("Claim ID reused with different contents");
            }
            return existing;
        }
        UUID sourceClaimId = claimsBySource.get(claim.sourceKey());
        if (sourceClaimId != null) {
            throw new ClaimConflictException("Claim source already has a claim");
        }
        if (claim.status() != ClaimStatus.PENDING
                || claim.remainingUnits() != claim.originalUnits()) {
            throw new ClaimConflictException("New claim must begin pending");
        }
        if (claims.size() >= maximumClaims
                || Math.addExact(claimPayloadBytes, claim.payload().length) > maximumPayloadBytes) {
            throw new ClaimConflictException("Claim storage limit is exceeded");
        }
        if (commit) {
            claims.put(claim.claimId(), claim);
            claimsBySource.put(claim.sourceKey(), claim.claimId());
            claimFingerprints.put(claim.claimId(), fingerprint);
            claimPayloadBytes = Math.addExact(claimPayloadBytes, claim.payload().length);
        }
        return claim;
    }

    public synchronized ClaimAttemptResult deliver(UUID ownerId, UUID claimId, String requestKey,
                                                   long requestedUnits, Instant deliveredAt,
                                                   ClaimDelivery delivery) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimId, "claimId");
        String safeRequestKey = requireRequestKey(requestKey);
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        ClaimAttemptResult prior = attempts.get(safeRequestKey);
        if (prior != null) {
            if (!prior.claimId().equals(claimId)
                    || !prior.deliveredAt().equals(deliveredAt)) {
                throw new ClaimConflictException("Claim request key reused");
            }
            return prior.asReplay();
        }
        EscrowClaim claim = requireOwnedPending(ownerId, claimId);
        requireAttemptCapacity();
        long requested = requestedUnits <= 0L
                ? claim.remainingUnits()
                : Math.min(requestedUnits, claim.remainingUnits());
        long delivered = delivery.deliver(claim, requested);
        if (delivered < 0L || delivered > requested) {
            throw new ClaimConflictException("Delivery returned invalid units");
        }
        EscrowClaim updated = claim;
        if (delivered > 0L) {
            updated = claim.deliver(delivered, deliveredAt);
            claims.put(claimId, updated);
        }
        ClaimAttemptResult result = new ClaimAttemptResult(claimId, safeRequestKey, delivered,
                updated.remainingUnits(), updated.status(), deliveredAt, false);
        attempts.put(safeRequestKey, result);
        return result;
    }

    public synchronized ClaimAttemptResult preflightDeliver(UUID ownerId, UUID claimId,
                                                            String requestKey,
                                                            long requestedUnits,
                                                            Instant deliveredAt) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimId, "claimId");
        String safeRequestKey = requireRequestKey(requestKey);
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        ClaimAttemptResult prior = attempts.get(safeRequestKey);
        if (prior != null) {
            if (!prior.claimId().equals(claimId)
                    || !prior.deliveredAt().equals(deliveredAt)) {
                throw new ClaimConflictException("Claim request key reused");
            }
            return prior.asReplay();
        }
        EscrowClaim claim = requireOwnedPending(ownerId, claimId);
        requireAttemptCapacity();
        long requested = requestedUnits <= 0L
                ? claim.remainingUnits()
                : Math.min(requestedUnits, claim.remainingUnits());
        EscrowClaim updated = claim.deliver(requested, deliveredAt);
        return new ClaimAttemptResult(claimId, safeRequestKey, requested,
                updated.remainingUnits(), updated.status(), deliveredAt, false);
    }

    public synchronized EscrowClaim quarantine(UUID claimId) {
        EscrowClaim claim = Objects.requireNonNull(claims.get(claimId), "claim");
        return quarantine(claim.ownerId(), claimId, clock.instant());
    }

    public synchronized EscrowClaim quarantine(UUID ownerId, UUID claimId, Instant now) {
        return evaluateQuarantine(ownerId, claimId, now, true);
    }

    public synchronized EscrowClaim preflightQuarantine(UUID ownerId, UUID claimId, Instant now) {
        return evaluateQuarantine(ownerId, claimId, now, false);
    }

    private EscrowClaim evaluateQuarantine(UUID ownerId, UUID claimId, Instant now,
                                           boolean commit) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(now, "now");
        EscrowClaim claim = claims.get(claimId);
        if (claim == null || !claim.ownerId().equals(ownerId)) {
            throw new ClaimConflictException("Claim not found for owner");
        }
        if (claim.status() == ClaimStatus.QUARANTINED) {
            if (!claim.updatedAt().equals(now)) {
                throw new ClaimConflictException("Claim was quarantined by another request");
            }
            return claim;
        }
        EscrowClaim updated = claim.quarantine(now);
        if (commit) {
            claims.put(claimId, updated);
        }
        return updated;
    }

    public synchronized EscrowClaim get(UUID claimId) {
        return claims.get(claimId);
    }

    public synchronized ClaimAttemptResult getAttempt(String requestKey) {
        return attempts.get(requireRequestKey(requestKey));
    }

    public synchronized ClaimMaintenanceApplyResult preflightMaintenanceReplace(
            EscrowClaim replacement
    ) {
        return evaluateMaintenanceReplace(replacement, false);
    }

    public synchronized ClaimMaintenanceApplyResult applyMaintenanceReplace(
            EscrowClaim replacement
    ) {
        return evaluateMaintenanceReplace(replacement, true);
    }

    public synchronized boolean maintenanceStateWasApplied(EscrowClaim state) {
        Objects.requireNonNull(state, "state");
        EscrowClaim current = claims.get(state.claimId());
        if (current == null) {
            return false;
        }
        if (current.equals(state)) {
            return true;
        }
        if (!sameMaintenanceIdentity(state, current)
                || current.remainingUnits() > state.remainingUnits()
                || current.updatedAt().isBefore(state.updatedAt())) {
            return false;
        }
        return state.status() == ClaimStatus.QUARANTINED
                || state.status() == ClaimStatus.PENDING
                || state.status() == ClaimStatus.PARTIALLY_DELIVERED;
    }

    private ClaimMaintenanceApplyResult evaluateMaintenanceReplace(
            EscrowClaim replacement,
            boolean commit
    ) {
        Objects.requireNonNull(replacement, "replacement");
        EscrowClaim current = claims.get(replacement.claimId());
        if (current == null) {
            throw new ClaimConflictException("Maintenance claim does not exist");
        }
        if (current.equals(replacement)) {
            return new ClaimMaintenanceApplyResult(current, true);
        }
        requireMaintenanceIdentity(current, replacement);
        requireMaintenanceTransition(current, replacement);
        if (commit) {
            claims.put(replacement.claimId(), replacement);
            claimFingerprints.put(replacement.claimId(), fingerprint(replacement));
        }
        return new ClaimMaintenanceApplyResult(replacement, false);
    }

    public synchronized List<EscrowClaim> pendingFor(UUID ownerId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 256));
        return claims.values().stream()
                .filter(claim -> claim.ownerId().equals(ownerId))
                .filter(claim -> claim.kind().publiclyVisible())
                .filter(claim -> claim.status() == ClaimStatus.PENDING
                        || claim.status() == ClaimStatus.PARTIALLY_DELIVERED)
                .sorted(Comparator.comparing(EscrowClaim::createdAt))
                .limit(safeLimit)
                .toList();
    }

    public synchronized OpenClaimPage openPageFor(
            UUID ownerId,
            String sourcePrefix,
            int pageIndex,
            int pageSize
    ) {
        UUID owner = Objects.requireNonNull(ownerId, "ownerId");
        String prefix = OpenClaimPage.requireSourcePrefix(sourcePrefix);
        if (pageIndex < 0 || pageSize <= 0
                || pageSize > OpenClaimPage.MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Open claim page request is invalid");
        }
        List<EscrowClaim> matching = claims.values().stream()
                .filter(claim -> claim.ownerId().equals(owner))
                .filter(claim -> claim.sourceKey().startsWith(prefix))
                .filter(claim -> claim.kind().publiclyVisible())
                .filter(claim -> OpenClaimPage.open(claim.status()))
                .sorted(OpenClaimPage.ORDER)
                .toList();
        int totalResults = matching.size();
        int pageCount = OpenClaimPage.pageCount(
                totalResults, pageSize);
        long start = (long) pageIndex * pageSize;
        List<EscrowClaim> page = start >= totalResults
                ? List.of()
                : matching.subList((int) start,
                (int) Math.min((long) totalResults, start + pageSize));
        return new OpenClaimPage(owner, prefix, pageIndex, pageSize,
                totalResults, pageCount, page);
    }

    public synchronized OpenClaimSourceCounts openSourceCountsFor(
            UUID ownerId,
            List<String> sourcePrefixes
    ) {
        UUID owner = Objects.requireNonNull(ownerId, "ownerId");
        List<String> requested = List.copyOf(Objects.requireNonNull(
                sourcePrefixes, "sourcePrefixes"));
        if (requested.size() > OpenClaimSourceCounts.MAXIMUM_PREFIXES) {
            throw new IllegalArgumentException(
                    "Open claim prefix count exceeds its limit");
        }
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (String value : requested) {
            String prefix = OpenClaimSourceCounts.requirePrefix(value);
            if (counts.put(prefix, 0L) != null) {
                throw new IllegalArgumentException(
                        "Open claim source prefix is duplicated");
            }
        }
        long total = 0L;
        for (EscrowClaim claim : claims.values()) {
            if (!claim.ownerId().equals(owner)
                    || !claim.kind().publiclyVisible()
                    || !OpenClaimPage.open(claim.status())) {
                continue;
            }
            total = Math.addExact(total, 1L);
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                if (claim.sourceKey().startsWith(entry.getKey())) {
                    entry.setValue(Math.addExact(entry.getValue(), 1L));
                }
            }
        }
        return new OpenClaimSourceCounts(total, counts);
    }

    public synchronized List<EscrowClaim> pendingCashFor(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return claims.values().stream()
                .filter(claim -> claim.ownerId().equals(ownerId))
                .filter(claim -> claim.status() == ClaimStatus.PENDING)
                .filter(claim -> claim.kind() == ClaimKind.PROTECTED_CASH
                        || claim.kind() == ClaimKind.FOREIGN_CASH)
                .sorted(Comparator.comparing(EscrowClaim::createdAt)
                        .thenComparing(value -> value.claimId().toString()))
                .toList();
    }

    public synchronized List<EscrowClaim> forTransaction(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        return claims.values().stream()
                .filter(claim -> claim.transactionId().equals(transactionId))
                .sorted(Comparator.comparing(value -> value.claimId().toString()))
                .toList();
    }

    public synchronized Map<UUID, EscrowClaim> snapshotClaims() {
        return Map.copyOf(claims);
    }

    public synchronized Map<String, ClaimAttemptResult> snapshotAttempts() {
        return Map.copyOf(attempts);
    }

    public synchronized boolean hasMaterializedState() {
        return !claims.isEmpty() || !attempts.isEmpty();
    }

    private static void requireMaintenanceIdentity(EscrowClaim current,
                                                   EscrowClaim replacement) {
        if (!sameMaintenanceIdentity(current, replacement)
                || current.remainingUnits() != replacement.remainingUnits()) {
            throw new ClaimConflictException(
                    "Maintenance claim replacement changes conserved data");
        }
        if (replacement.updatedAt().isBefore(current.updatedAt())) {
            throw new ClaimConflictException(
                    "Maintenance claim replacement moves time backward");
        }
    }

    private static boolean sameMaintenanceIdentity(EscrowClaim first,
                                                   EscrowClaim second) {
        return first.claimId().equals(second.claimId())
                && first.transactionId().equals(second.transactionId())
                && first.ownerId().equals(second.ownerId())
                && first.sourceKey().equals(second.sourceKey())
                && first.kind() == second.kind()
                && first.originalUnits() == second.originalUnits()
                && java.util.Arrays.equals(first.payload(), second.payload())
                && first.label().equals(second.label())
                && first.createdAt().equals(second.createdAt());
    }

    private static void requireMaintenanceTransition(EscrowClaim current,
                                                     EscrowClaim replacement) {
        boolean quarantine = current.status() != ClaimStatus.COMPLETED
                && current.status() != ClaimStatus.QUARANTINED
                && replacement.status() == ClaimStatus.QUARANTINED;
        ClaimStatus reopenedStatus = current.remainingUnits() == current.originalUnits()
                ? ClaimStatus.PENDING : ClaimStatus.PARTIALLY_DELIVERED;
        boolean reopen = current.status() == ClaimStatus.QUARANTINED
                && replacement.status() == reopenedStatus;
        if (!quarantine && !reopen) {
            throw new ClaimConflictException(
                    "Maintenance claim replacement is not conservative");
        }
    }

    public synchronized void restore(Map<UUID, EscrowClaim> restoredClaims,
                                     Map<String, ClaimAttemptResult> restoredAttempts) {
        Objects.requireNonNull(restoredClaims, "restoredClaims");
        Objects.requireNonNull(restoredAttempts, "restoredAttempts");
        claims.clear();
        claimsBySource.clear();
        claimFingerprints.clear();
        attempts.clear();
        claimPayloadBytes = 0L;
        if (restoredClaims.size() > maximumClaims || restoredAttempts.size() > maximumAttempts) {
            throw new IllegalArgumentException("Restored claims exceed repository limits");
        }
        for (Map.Entry<UUID, EscrowClaim> entry : restoredClaims.entrySet()) {
            EscrowClaim claim = Objects.requireNonNull(entry.getValue(), "restored claim");
            if (!entry.getKey().equals(claim.claimId())
                    || claims.put(claim.claimId(), claim) != null) {
                throw new IllegalArgumentException("Restored claim index is invalid");
            }
            if (claimsBySource.put(claim.sourceKey(), claim.claimId()) != null) {
                throw new IllegalArgumentException("Restored claim source is duplicated");
            }
            claimFingerprints.put(claim.claimId(), fingerprint(claim));
            claimPayloadBytes = Math.addExact(claimPayloadBytes, claim.payload().length);
            if (claimPayloadBytes > maximumPayloadBytes) {
                throw new IllegalArgumentException("Restored claim payloads exceed repository limits");
            }
        }
        for (Map.Entry<String, ClaimAttemptResult> entry : restoredAttempts.entrySet()) {
            ClaimAttemptResult attempt = Objects.requireNonNull(entry.getValue(), "restored claim attempt");
            if (!entry.getKey().equals(attempt.requestKey())) {
                throw new IllegalArgumentException("Claim attempt index does not match its request key");
            }
            EscrowClaim claim = claims.get(attempt.claimId());
            if (claim == null) {
                throw new IllegalArgumentException("Claim attempt references missing claim");
            }
            long accounted;
            try {
                accounted = Math.addExact(attempt.deliveredUnits(), attempt.remainingUnits());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Claim attempt units overflow", exception);
            }
            if (accounted > claim.originalUnits()
                    || attempt.remainingUnits() < claim.remainingUnits()
                    || attempt.deliveredAt().isBefore(claim.createdAt())
                    || attempt.status() == ClaimStatus.COMPLETED
                    && claim.status() != ClaimStatus.COMPLETED) {
                throw new IllegalArgumentException("Claim attempt does not match its claim");
            }
            attempts.put(entry.getKey(), attempt);
        }
        validateAttemptChains();
    }

    private EscrowClaim requireOwnedPending(UUID ownerId, UUID claimId) {
        EscrowClaim claim = claims.get(claimId);
        if (claim == null || !claim.ownerId().equals(ownerId)) {
            throw new ClaimConflictException("Claim not found for owner");
        }
        if (claim.status() == ClaimStatus.COMPLETED || claim.status() == ClaimStatus.QUARANTINED) {
            throw new ClaimConflictException("Claim is not deliverable");
        }
        return claim;
    }

    private static String requireRequestKey(String requestKey) {
        String safeRequestKey = Objects.requireNonNull(requestKey, "requestKey").trim();
        if (safeRequestKey.isEmpty() || safeRequestKey.length() > 192) {
            throw new IllegalArgumentException("Invalid request key");
        }
        return safeRequestKey;
    }

    private void requireAttemptCapacity() {
        if (attempts.size() >= maximumAttempts) {
            throw new ClaimConflictException("Claim attempt storage limit is exceeded");
        }
    }

    private void validateAttemptChains() {
        Map<UUID, List<ClaimAttemptResult>> byClaim = new HashMap<>();
        for (ClaimAttemptResult attempt : attempts.values()) {
            byClaim.computeIfAbsent(attempt.claimId(), ignored -> new ArrayList<>()).add(attempt);
        }
        for (Map.Entry<UUID, List<ClaimAttemptResult>> entry : byClaim.entrySet()) {
            EscrowClaim claim = claims.get(entry.getKey());
            long remaining = claim.originalUnits();
            List<ClaimAttemptResult> ordered = entry.getValue().stream()
                    .sorted(Comparator.comparing(ClaimAttemptResult::deliveredAt)
                            .thenComparing(Comparator.comparingLong(
                                    ClaimAttemptResult::remainingUnits).reversed())
                            .thenComparing(ClaimAttemptResult::requestKey))
                    .toList();
            for (ClaimAttemptResult attempt : ordered) {
                if (attempt.deliveredUnits() == 0L) {
                    if (attempt.remainingUnits() != remaining) {
                        throw new IllegalArgumentException("Claim attempt chain is invalid");
                    }
                    continue;
                }
                long expected = Math.subtractExact(remaining, attempt.deliveredUnits());
                if (expected != attempt.remainingUnits()) {
                    throw new IllegalArgumentException("Claim attempt chain is invalid");
                }
                remaining = expected;
            }
            if (remaining != claim.remainingUnits()) {
                throw new IllegalArgumentException("Claim attempt chain does not reach claim state");
            }
        }
        for (EscrowClaim claim : claims.values()) {
            if (claim.remainingUnits() != claim.originalUnits()
                    && !byClaim.containsKey(claim.claimId())) {
                throw new IllegalArgumentException("Delivered claim has no attempt history");
            }
        }
    }

    private static String fingerprint(EscrowClaim claim) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUuid(output, claim.claimId());
            writeUuid(output, claim.transactionId());
            writeUuid(output, claim.ownerId());
            output.writeUTF(claim.sourceKey());
            output.writeUTF(claim.kind().name());
            output.writeLong(claim.originalUnits());
            output.writeUTF(claim.label());
            output.writeLong(claim.createdAt().getEpochSecond());
            output.writeInt(claim.createdAt().getNano());
            byte[] payload = claim.payload();
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("Unable to fingerprint claim", ex);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
}
