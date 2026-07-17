package com.enviouse.futureshops.server.escrow.stock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PersistentStockRepository {
    public static final String EMPTY_CATALOG_FINGERPRINT = "0".repeat(64);

    private final int maximumListings;
    private final int maximumReservations;
    private final int maximumRequests;
    private final Map<StockKey, CatalogStockState> listings = new LinkedHashMap<>();
    private final Map<StockReservationId, StockReservation> reservations =
            new LinkedHashMap<>();
    private final Map<UUID, StockMutationReceipt> receipts = new LinkedHashMap<>();
    private final Map<StockKey, Set<StockReservationId>> reservationsByKey = new HashMap<>();
    private long storeRevision;
    private String catalogFingerprint;

    public PersistentStockRepository() {
        this(StockLimits.MAX_LISTINGS, StockLimits.MAX_RESERVATIONS,
                StockLimits.MAX_REQUESTS, EMPTY_CATALOG_FINGERPRINT);
    }

    public PersistentStockRepository(String catalogFingerprint) {
        this(StockLimits.MAX_LISTINGS, StockLimits.MAX_RESERVATIONS,
                StockLimits.MAX_REQUESTS, catalogFingerprint);
    }

    PersistentStockRepository(int maximumListings, int maximumReservations,
                              int maximumRequests, String catalogFingerprint) {
        if (maximumListings <= 0 || maximumListings > StockLimits.MAX_LISTINGS
                || maximumReservations <= 0
                || maximumReservations > StockLimits.MAX_RESERVATIONS
                || maximumRequests <= 0 || maximumRequests > StockLimits.MAX_REQUESTS) {
            throw new IllegalArgumentException("Invalid stock repository limits");
        }
        this.maximumListings = maximumListings;
        this.maximumReservations = maximumReservations;
        this.maximumRequests = maximumRequests;
        this.catalogFingerprint = StockLimits.requireFingerprint(catalogFingerprint,
                "stock catalog fingerprint");
    }

    public synchronized StockApplyResult seed(UUID requestId, StockDefinition definition,
                                              Instant now) {
        Objects.requireNonNull(definition, "definition");
        requireTime(now);
        String fingerprint = StockRequestFingerprints.definition(StockMutationType.SEED,
                definition, -1L);
        StockApplyResult replay = replay(requestId, StockMutationType.SEED, fingerprint);
        if (replay != null) {
            return replay;
        }
        requireRequestCapacity();
        CatalogStockState current = listings.get(definition.key());
        StockMutationOutcome outcome;
        CatalogStockState resulting;
        if (current == null) {
            requireListingCapacity();
            resulting = CatalogStockState.seed(definition, now);
            outcome = StockMutationOutcome.APPLIED;
        } else if (matchesActiveDefinition(current, definition)) {
            resulting = current;
            outcome = StockMutationOutcome.UNCHANGED;
        } else {
            throw new StockConflictException("Stock listing was seeded with different config");
        }
        StockMutationReceipt receipt = newReceipt(requestId, StockMutationType.SEED,
                fingerprint, Optional.of(definition.key()), Optional.empty(), outcome,
                resulting.revision(), -1L, now);
        if (current == null) {
            listings.put(definition.key(), resulting);
        }
        persistReceipt(receipt);
        return new StockApplyResult(receipt, false);
    }

    public synchronized StockApplyResult reserve(UUID requestId, UUID transactionId,
                                                 StockKey key, long quantity,
                                                 long expectedListingRevision, Instant now) {
        StockLimits.requireNonzeroUuid(transactionId, "stock transaction identifier");
        Objects.requireNonNull(key, "key");
        StockLimits.requireQuantity(quantity, false, "stock reserve quantity");
        StockLimits.requireRevision(expectedListingRevision, false,
                "expected stock listing revision");
        requireTime(now);
        String fingerprint = StockRequestFingerprints.reserve(transactionId, key, quantity,
                expectedListingRevision);
        StockApplyResult replay = replay(requestId, StockMutationType.RESERVE, fingerprint);
        if (replay != null) {
            return replay;
        }
        requireRequestCapacity();
        CatalogStockState current = requireListing(key);
        requireExpectedRevision(current.revision(), expectedListingRevision,
                "Stock listing changed before reservation");
        requireActive(current);
        requireMonotonic(now, current.updatedAt());
        StockReservation reservation = StockReservation.held(transactionId, key, quantity,
                !current.unlimited(), now);
        if (reservations.containsKey(reservation.reservationId())) {
            throw new StockConflictException("Stock transaction already has a reservation for listing");
        }
        if (!current.unlimited() && current.availableQuantity() < quantity) {
            StockMutationReceipt receipt = newReservationReceipt(
                    requestId, StockMutationType.RESERVE, transactionId,
                    fingerprint, Optional.of(key), Optional.of(reservation.reservationId()),
                    StockMutationOutcome.INSUFFICIENT_STOCK, current.revision(), -1L, now);
            persistReceipt(receipt);
            return new StockApplyResult(receipt, false);
        }
        requireReservationCapacity();
        CatalogStockState updated = current.unlimited() ? current
                : replaceListing(current, current.policy(), current.status(),
                current.availableQuantity() - quantity, current.configFingerprint(), now);
        StockMutationReceipt receipt = newReservationReceipt(
                requestId, StockMutationType.RESERVE, transactionId,
                fingerprint, Optional.of(key), Optional.of(reservation.reservationId()),
                StockMutationOutcome.APPLIED, updated.revision(), reservation.revision(), now);
        if (updated != current) {
            listings.put(key, updated);
        }
        putReservation(reservation);
        persistReceipt(receipt);
        return new StockApplyResult(receipt, false);
    }

    public synchronized StockBatchApplyResult reserveBatch(
            UUID requestId,
            UUID transactionId,
            Collection<StockReservationRequest> requests,
            Instant now
    ) {
        StockLimits.requireNonzeroUuid(transactionId,
                "stock transaction identifier");
        requireTime(now);
        List<StockReservationRequest> lines = normalizeBatchRequests(requests);
        String fingerprint = StockRequestFingerprints.reserveBatch(
                transactionId, lines);
        StockMutationReceipt replay = replayReceipt(requestId,
                StockMutationType.RESERVE_BATCH, fingerprint);
        if (replay != null) {
            return batchResult(replay, true);
        }
        requireRequestCapacity();
        Map<StockKey, CatalogStockState> listingUpdates =
                new LinkedHashMap<>();
        List<StockReservation> plannedReservations = new ArrayList<>();
        boolean insufficient = false;
        for (StockReservationRequest line : lines) {
            CatalogStockState current = requireListing(line.stockKey());
            requireExpectedRevision(current.revision(),
                    line.expectedListingRevision(),
                    "Stock listing changed before batch reservation");
            requireActive(current);
            requireMonotonic(now, current.updatedAt());
            StockReservation reservation = StockReservation.held(
                    transactionId, line.stockKey(), line.direction(),
                    line.quantity(), !current.unlimited(), now);
            if (reservations.containsKey(reservation.reservationId())) {
                throw new StockConflictException(
                        "Stock transaction already has a reservation for listing and direction");
            }
            if (!current.unlimited()) {
                if (line.direction() == StockReservationDirection.OUTBOUND) {
                    if (current.availableQuantity() < line.quantity()) {
                        insufficient = true;
                    } else {
                        listingUpdates.put(line.stockKey(), replaceListing(
                                current, current.policy(), current.status(),
                                current.availableQuantity() - line.quantity(),
                                current.configFingerprint(), now));
                    }
                } else if (inboundCapacity(current) < line.quantity()) {
                    insufficient = true;
                }
            }
            plannedReservations.add(reservation);
        }
        if (insufficient) {
            StockMutationReceipt receipt = newBatchReceipt(requestId,
                    StockMutationType.RESERVE_BATCH, transactionId,
                    fingerprint, List.of(),
                    StockMutationOutcome.INSUFFICIENT_STOCK, now);
            persistReceipt(receipt);
            return new StockBatchApplyResult(receipt, List.of(), false);
        }
        if (reservations.size() > maximumReservations - lines.size()) {
            throw new StockConflictException(
                    "Stock reservation capacity is exhausted");
        }
        List<StockReservationId> reservationIds = plannedReservations.stream()
                .map(StockReservation::reservationId).sorted().toList();
        StockMutationReceipt receipt = newBatchReceipt(requestId,
                StockMutationType.RESERVE_BATCH, transactionId,
                fingerprint, reservationIds, StockMutationOutcome.APPLIED,
                now);
        listings.putAll(listingUpdates);
        for (StockReservation reservation : plannedReservations) {
            putReservation(reservation);
        }
        persistReceipt(receipt);
        return batchResult(receipt, false);
    }

    public synchronized StockApplyResult commit(UUID requestId, UUID transactionId,
                                                StockReservationId reservationId,
                                                long expectedReservationRevision, Instant now) {
        return resolve(requestId, StockMutationType.COMMIT, transactionId, reservationId,
                expectedReservationRevision, now);
    }

    public synchronized StockApplyResult release(UUID requestId, UUID transactionId,
                                                 StockReservationId reservationId,
                                                 long expectedReservationRevision, Instant now) {
        return resolve(requestId, StockMutationType.RELEASE, transactionId, reservationId,
                expectedReservationRevision, now);
    }

    public synchronized StockBatchApplyResult commitBatch(
            UUID requestId,
            UUID transactionId,
            Collection<StockReservationResolution> resolutions,
            Instant now
    ) {
        return resolveBatch(requestId, StockMutationType.COMMIT_BATCH,
                transactionId, resolutions, now);
    }

    public synchronized StockBatchApplyResult releaseBatch(
            UUID requestId,
            UUID transactionId,
            Collection<StockReservationResolution> resolutions,
            Instant now
    ) {
        return resolveBatch(requestId, StockMutationType.RELEASE_BATCH,
                transactionId, resolutions, now);
    }

    public synchronized StockApplyResult refresh(UUID requestId, StockDefinition definition,
                                                 long expectedListingRevision, Instant now) {
        return replaceFromDefinition(requestId, StockMutationType.REFRESH, definition,
                expectedListingRevision, now);
    }

    public synchronized StockApplyResult adminReset(UUID requestId, StockDefinition definition,
                                                    long expectedListingRevision, Instant now) {
        return replaceFromDefinition(requestId, StockMutationType.ADMIN_RESET, definition,
                expectedListingRevision, now);
    }

    public synchronized StockApplyResult reconcileReload(UUID requestId,
                                                         Collection<StockDefinition> definitions,
                                                         String newCatalogFingerprint,
                                                         Instant now) {
        Objects.requireNonNull(definitions, "definitions");
        newCatalogFingerprint = StockLimits.requireFingerprint(newCatalogFingerprint,
                "stock catalog fingerprint");
        requireTime(now);
        Map<StockKey, StockDefinition> incoming = indexDefinitions(definitions);
        String requestFingerprint = StockRequestFingerprints.reconcile(incoming.values(),
                newCatalogFingerprint);
        StockApplyResult replay = replay(requestId, StockMutationType.RELOAD_RECONCILE,
                requestFingerprint);
        if (replay != null) {
            return replay;
        }
        requireRequestCapacity();
        if (incoming.size() > maximumListings) {
            throw new StockConflictException("Reloaded stock catalog exceeds listing capacity");
        }
        Map<StockKey, CatalogStockState> rebuilt = new LinkedHashMap<>(listings);
        boolean changed = !catalogFingerprint.equals(newCatalogFingerprint);
        for (Map.Entry<StockKey, CatalogStockState> entry : listings.entrySet()) {
            StockDefinition definition = incoming.get(entry.getKey());
            CatalogStockState current = entry.getValue();
            if (definition == null) {
                if (current.status() == CatalogStockStatus.ACTIVE) {
                    requireMonotonic(now, current.updatedAt());
                    rebuilt.put(entry.getKey(), replaceListing(current, current.policy(),
                            CatalogStockStatus.RETIRED, 0L, current.configFingerprint(), now));
                    changed = true;
                }
            } else if (!matchesActiveDefinition(current, definition)) {
                requireMonotonic(now, current.updatedAt());
                rebuilt.put(entry.getKey(), refreshedState(current, definition, now));
                changed = true;
            }
        }
        for (StockDefinition definition : incoming.values()) {
            if (!rebuilt.containsKey(definition.key())) {
                if (rebuilt.size() >= maximumListings) {
                    throw new StockConflictException(
                            "Reloaded stock catalog and retained listings exceed capacity");
                }
                rebuilt.put(definition.key(), CatalogStockState.seed(definition, now));
                changed = true;
            }
        }
        StockMutationReceipt receipt = newReceipt(requestId,
                StockMutationType.RELOAD_RECONCILE, requestFingerprint, Optional.empty(),
                Optional.empty(), changed ? StockMutationOutcome.APPLIED
                        : StockMutationOutcome.UNCHANGED, -1L, -1L, now);
        listings.clear();
        listings.putAll(rebuilt);
        catalogFingerprint = newCatalogFingerprint;
        persistReceipt(receipt);
        return new StockApplyResult(receipt, false);
    }

    public synchronized CatalogStockState listing(StockKey key) {
        return listings.get(Objects.requireNonNull(key, "key"));
    }

    public synchronized StockReservation reservation(StockReservationId reservationId) {
        return reservations.get(Objects.requireNonNull(reservationId, "reservationId"));
    }

    public synchronized StockReservation reservationForTransaction(UUID transactionId,
                                                                    StockKey key) {
        return reservations.get(StockReservationId.forTransaction(transactionId, key));
    }

    public synchronized StockReservation reservationForTransaction(
            UUID transactionId,
            StockKey key,
            StockReservationDirection direction
    ) {
        return reservations.get(StockReservationId.forTransaction(
                transactionId, key, direction));
    }

    public synchronized StockMutationReceipt receipt(UUID requestId) {
        return receipts.get(Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized StockCommandResult resultForRequest(
            UUID requestId,
            boolean replayed
    ) {
        StockMutationReceipt receipt = receipts.get(
                Objects.requireNonNull(requestId, "requestId"));
        if (receipt == null) {
            throw new StockConflictException(
                    "Stock request receipt does not exist");
        }
        List<StockReservationId> ids = receipt.reservationIds().isEmpty()
                ? receipt.reservationId().stream().toList()
                : receipt.reservationIds();
        List<StockReservation> values = ids.stream()
                .map(reservations::get)
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparing(
                        StockReservation::reservationId)).toList();
        return new StockCommandResult(receipt, values, replayed);
    }

    public synchronized List<StockReservation> reservationsFor(StockKey key) {
        Set<StockReservationId> ids = reservationsByKey.getOrDefault(
                Objects.requireNonNull(key, "key"), Set.of());
        return ids.stream().map(reservations::get)
                .sorted(java.util.Comparator.comparing(StockReservation::reservationId))
                .toList();
    }

    public synchronized long backedHeldQuantity(StockKey key) {
        return heldQuantity(key, true);
    }

    public synchronized List<StockReservation> reservationsForTransaction(
            UUID transactionId
    ) {
        StockLimits.requireNonzeroUuid(transactionId,
                "stock transaction identifier");
        return reservations.values().stream()
                .filter(value -> value.transactionId().equals(transactionId))
                .sorted(java.util.Comparator.comparing(
                        StockReservation::reservationId)).toList();
    }

    public synchronized StockStoreSnapshot snapshot() {
        return new StockStoreSnapshot(storeRevision, catalogFingerprint,
                listings, reservations, receipts);
    }

    public synchronized void rebuild(StockStoreSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateSnapshot(snapshot);
        if (snapshot.listings().size() > maximumListings
                || snapshot.reservations().size() > maximumReservations
                || snapshot.receipts().size() > maximumRequests) {
            throw new StockConflictException("Stock snapshot exceeds repository capacity");
        }
        listings.clear();
        reservations.clear();
        receipts.clear();
        reservationsByKey.clear();
        listings.putAll(snapshot.listings());
        for (StockReservation reservation : snapshot.reservations().values()) {
            putReservation(reservation);
        }
        receipts.putAll(snapshot.receipts());
        storeRevision = snapshot.storeRevision();
        catalogFingerprint = snapshot.catalogFingerprint();
    }

    public synchronized StockConservationReport conservation() {
        return conservation(snapshot());
    }

    public synchronized StockCommandResult preflightCommitted(
            StockMutationCommand command
    ) {
        Objects.requireNonNull(command, "command");
        PersistentStockRepository copy = new PersistentStockRepository(
                maximumListings, maximumReservations, maximumRequests,
                catalogFingerprint);
        copy.rebuild(snapshot());
        return copy.applyCommitted(command);
    }

    public synchronized StockCommandResult applyCommitted(
            StockMutationCommand command
    ) {
        Objects.requireNonNull(command, "command");
        StockCommandResult result;
        if (command instanceof StockMutationCommand.Seed value) {
            result = commandResult(seed(value.requestId(), value.definition(),
                    value.appliedAt()));
        } else if (command instanceof StockMutationCommand.Reserve value) {
            result = commandResult(reserve(value.requestId(),
                    value.transactionId(), value.stockKey(), value.quantity(),
                    value.expectedListingRevision(), value.appliedAt()));
        } else if (command instanceof StockMutationCommand.Resolve value) {
            StockApplyResult applied = value.operation()
                    == StockMutationType.COMMIT
                    ? commit(value.requestId(), value.transactionId(),
                    value.reservationId(),
                    value.expectedReservationRevision(), value.appliedAt())
                    : release(value.requestId(), value.transactionId(),
                    value.reservationId(),
                    value.expectedReservationRevision(), value.appliedAt());
            result = commandResult(applied);
        } else if (command instanceof StockMutationCommand.DefinitionChange value) {
            StockApplyResult applied = value.operation()
                    == StockMutationType.REFRESH
                    ? refresh(value.requestId(), value.definition(),
                    value.expectedListingRevision(), value.appliedAt())
                    : adminReset(value.requestId(), value.definition(),
                    value.expectedListingRevision(), value.appliedAt());
            result = commandResult(applied);
        } else if (command instanceof StockMutationCommand.Reconcile value) {
            result = commandResult(reconcileReload(value.requestId(),
                    value.definitions(), value.catalogFingerprint(),
                    value.appliedAt()));
        } else if (command instanceof StockMutationCommand.ReserveBatch value) {
            result = commandResult(reserveBatch(value.requestId(),
                    value.transactionId(), value.reservations(),
                    value.appliedAt()));
        } else if (command instanceof StockMutationCommand.ResolveBatch value) {
            StockBatchApplyResult applied = value.operation()
                    == StockMutationType.COMMIT_BATCH
                    ? commitBatch(value.requestId(), value.transactionId(),
                    value.reservations(), value.appliedAt())
                    : releaseBatch(value.requestId(), value.transactionId(),
                    value.reservations(), value.appliedAt());
            result = commandResult(applied);
        } else {
            throw new IllegalArgumentException(
                    "Unknown stock mutation command");
        }
        return result;
    }

    public static void validateSnapshot(StockStoreSnapshot snapshot) {
        StockConservationReport report = conservation(Objects.requireNonNull(snapshot,
                "snapshot"));
        if (!report.conserved()) {
            throw new StockConflictException("Stock snapshot failed conservation checks. "
                    + String.join(", ", report.violations()));
        }
    }

    private StockCommandResult commandResult(StockApplyResult result) {
        List<StockReservation> values = result.receipt().reservationId()
                .map(reservations::get).stream()
                .filter(Objects::nonNull).toList();
        return new StockCommandResult(result.receipt(), values,
                result.replayed());
    }

    private static StockCommandResult commandResult(
            StockBatchApplyResult result
    ) {
        return new StockCommandResult(result.receipt(),
                result.reservations(), result.replayed());
    }

    private StockApplyResult resolve(UUID requestId, StockMutationType operation,
                                     UUID transactionId, StockReservationId reservationId,
                                     long expectedReservationRevision, Instant now) {
        if (operation != StockMutationType.COMMIT && operation != StockMutationType.RELEASE) {
            throw new IllegalArgumentException("Invalid stock reservation resolution");
        }
        StockLimits.requireNonzeroUuid(transactionId, "stock transaction identifier");
        Objects.requireNonNull(reservationId, "reservationId");
        StockLimits.requireRevision(expectedReservationRevision, false,
                "expected stock reservation revision");
        requireTime(now);
        String fingerprint = StockRequestFingerprints.resolution(operation, transactionId,
                reservationId, expectedReservationRevision);
        StockApplyResult replay = replay(requestId, operation, fingerprint);
        if (replay != null) {
            return replay;
        }
        requireRequestCapacity();
        StockReservation current = reservations.get(reservationId);
        if (current == null || !current.transactionId().equals(transactionId)
                || !current.reservationId().equals(
                StockReservationId.forTransaction(transactionId,
                        current.stockKey(), current.direction()))) {
            throw new StockConflictException("Stock reservation was not found for transaction");
        }
        requireExpectedRevision(current.revision(), expectedReservationRevision,
                "Stock reservation changed before resolution");
        requireMonotonic(now, current.updatedAt());
        StockReservationState target = operation == StockMutationType.COMMIT
                ? StockReservationState.COMMITTED : StockReservationState.RELEASED;
        if (current.state() == target) {
            StockMutationReceipt receipt = newReservationReceipt(
                    requestId, operation, transactionId, fingerprint,
                    Optional.of(current.stockKey()), Optional.of(reservationId),
                    StockMutationOutcome.UNCHANGED,
                    requireListing(current.stockKey()).revision(), current.revision(), now);
            persistReceipt(receipt);
            return new StockApplyResult(receipt, false);
        }
        if (current.state() != StockReservationState.HELD) {
            throw new StockConflictException("Stock reservation has an opposing terminal state");
        }
        CatalogStockState listing = requireListing(current.stockKey());
        requireMonotonic(now, listing.updatedAt());
        StockReservation resolved = current.resolve(target, now);
        long available = listing.availableQuantity();
        boolean returnsToAvailability = current.inventoryBacked()
                && !listing.unlimited()
                && (target == StockReservationState.RELEASED
                && current.direction() == StockReservationDirection.OUTBOUND
                || target == StockReservationState.COMMITTED
                && current.direction() == StockReservationDirection.INBOUND);
        if (returnsToAvailability) {
            try {
                available = Math.addExact(available, current.quantity());
            } catch (ArithmeticException exception) {
                throw new StockConflictException("Released stock quantity overflowed", exception);
            }
            if (available > StockLimits.MAX_QUANTITY) {
                throw new StockConflictException("Released stock quantity exceeds its bound");
            }
            long heldBeforeRelease = heldQuantity(current.stockKey(), true);
            long heldAfterRelease = Math.subtractExact(
                    heldBeforeRelease, current.quantity());
            long maximumAvailable = Math.max(0L,
                    listing.policy().configuredQuantity() - heldAfterRelease);
            if (available > maximumAvailable
                    && current.direction()
                    == StockReservationDirection.INBOUND) {
                throw new StockConflictException(
                        "Inbound stock reservation exceeds preserved capacity");
            }
            available = Math.min(available, maximumAvailable);
        }
        CatalogStockState updated = replaceListing(listing, listing.policy(), listing.status(),
                available, listing.configFingerprint(), now);
        StockMutationReceipt receipt = newReservationReceipt(
                requestId, operation, transactionId, fingerprint,
                Optional.of(current.stockKey()), Optional.of(reservationId),
                StockMutationOutcome.APPLIED, updated.revision(), resolved.revision(), now);
        listings.put(updated.key(), updated);
        reservations.put(reservationId, resolved);
        persistReceipt(receipt);
        return new StockApplyResult(receipt, false);
    }

    private StockBatchApplyResult resolveBatch(
            UUID requestId,
            StockMutationType operation,
            UUID transactionId,
            Collection<StockReservationResolution> resolutions,
            Instant now
    ) {
        if (operation != StockMutationType.COMMIT_BATCH
                && operation != StockMutationType.RELEASE_BATCH) {
            throw new IllegalArgumentException(
                    "Invalid stock batch reservation resolution");
        }
        StockLimits.requireNonzeroUuid(transactionId,
                "stock transaction identifier");
        requireTime(now);
        List<StockReservationResolution> lines =
                normalizeBatchResolutions(resolutions);
        String fingerprint = StockRequestFingerprints.resolveBatch(
                operation, transactionId, lines);
        StockMutationReceipt replay = replayReceipt(requestId, operation,
                fingerprint);
        if (replay != null) {
            return batchResult(replay, true);
        }
        requireRequestCapacity();
        StockReservationState target = operation
                == StockMutationType.COMMIT_BATCH
                ? StockReservationState.COMMITTED
                : StockReservationState.RELEASED;
        Map<StockReservationId, StockReservation> currentReservations =
                new LinkedHashMap<>();
        boolean changed = false;
        for (StockReservationResolution line : lines) {
            StockReservation current = reservations.get(line.reservationId());
            if (current == null
                    || !current.transactionId().equals(transactionId)) {
                throw new StockConflictException(
                        "Stock batch reservation was not found for transaction");
            }
            requireExpectedRevision(current.revision(),
                    line.expectedReservationRevision(),
                    "Stock reservation changed before batch resolution");
            requireMonotonic(now, current.updatedAt());
            if (current.state() != StockReservationState.HELD
                    && current.state() != target) {
                throw new StockConflictException(
                        "Stock batch reservation has an opposing terminal state");
            }
            changed |= current.state() == StockReservationState.HELD;
            currentReservations.put(current.reservationId(), current);
        }
        Map<StockReservationId, StockReservation> resolvedReservations =
                new LinkedHashMap<>();
        Map<StockKey, Long> availabilityCredits = new HashMap<>();
        Map<StockKey, Long> resolvedHeld = new HashMap<>();
        Set<StockKey> touchedListings = new HashSet<>();
        Set<StockKey> inboundCommits = new HashSet<>();
        for (StockReservation current : currentReservations.values()) {
            if (current.state() != StockReservationState.HELD) {
                resolvedReservations.put(current.reservationId(), current);
                continue;
            }
            StockReservation resolved = current.resolve(target, now);
            resolvedReservations.put(current.reservationId(), resolved);
            touchedListings.add(current.stockKey());
            if (current.inventoryBacked()) {
                resolvedHeld.merge(current.stockKey(), current.quantity(),
                        Math::addExact);
                boolean credit = target == StockReservationState.RELEASED
                        && current.direction()
                        == StockReservationDirection.OUTBOUND
                        || target == StockReservationState.COMMITTED
                        && current.direction()
                        == StockReservationDirection.INBOUND;
                if (credit) {
                    availabilityCredits.merge(current.stockKey(),
                            current.quantity(), Math::addExact);
                }
                if (target == StockReservationState.COMMITTED
                        && current.direction()
                        == StockReservationDirection.INBOUND) {
                    inboundCommits.add(current.stockKey());
                }
            }
        }
        Map<StockKey, CatalogStockState> listingUpdates =
                new LinkedHashMap<>();
        for (StockKey key : touchedListings.stream().sorted().toList()) {
            CatalogStockState listing = requireListing(key);
            requireMonotonic(now, listing.updatedAt());
            long available = listing.availableQuantity();
            if (!listing.unlimited()) {
                available = Math.addExact(available,
                        availabilityCredits.getOrDefault(key, 0L));
                long heldAfter = Math.subtractExact(heldQuantity(key, true),
                        resolvedHeld.getOrDefault(key, 0L));
                long maximumAvailable = Math.max(0L,
                        listing.policy().configuredQuantity() - heldAfter);
                if (available > maximumAvailable
                        && inboundCommits.contains(key)) {
                    throw new StockConflictException(
                            "Inbound stock batch exceeds preserved capacity");
                }
                available = Math.min(available, maximumAvailable);
            }
            listingUpdates.put(key, replaceListing(listing,
                    listing.policy(), listing.status(), available,
                    listing.configFingerprint(), now));
        }
        List<StockReservationId> reservationIds = lines.stream()
                .map(StockReservationResolution::reservationId).sorted().toList();
        StockMutationReceipt receipt = newBatchReceipt(requestId, operation,
                transactionId, fingerprint, reservationIds,
                changed ? StockMutationOutcome.APPLIED
                        : StockMutationOutcome.UNCHANGED, now);
        listings.putAll(listingUpdates);
        reservations.putAll(resolvedReservations);
        persistReceipt(receipt);
        return batchResult(receipt, false);
    }

    private StockApplyResult replaceFromDefinition(UUID requestId, StockMutationType operation,
                                                   StockDefinition definition,
                                                   long expectedListingRevision, Instant now) {
        Objects.requireNonNull(definition, "definition");
        StockLimits.requireRevision(expectedListingRevision, false,
                "expected stock listing revision");
        requireTime(now);
        String fingerprint = StockRequestFingerprints.definition(operation, definition,
                expectedListingRevision);
        StockApplyResult replay = replay(requestId, operation, fingerprint);
        if (replay != null) {
            return replay;
        }
        requireRequestCapacity();
        CatalogStockState current = requireListing(definition.key());
        requireExpectedRevision(current.revision(), expectedListingRevision,
                "Stock listing changed before reset");
        requireMonotonic(now, current.updatedAt());
        CatalogStockState updated = refreshedState(current, definition, now);
        StockMutationReceipt receipt = newReceipt(requestId, operation, fingerprint,
                Optional.of(definition.key()), Optional.empty(), StockMutationOutcome.APPLIED,
                updated.revision(), -1L, now);
        listings.put(definition.key(), updated);
        persistReceipt(receipt);
        return new StockApplyResult(receipt, false);
    }

    private CatalogStockState refreshedState(CatalogStockState current,
                                             StockDefinition definition, Instant now) {
        long held = heldQuantity(definition.key(), true);
        long available = definition.policy().unlimited() ? 0L
                : Math.max(0L, definition.policy().configuredQuantity() - held);
        return replaceListing(current, definition.policy(), CatalogStockStatus.ACTIVE,
                available, definition.configFingerprint(), now);
    }

    private long heldQuantity(StockKey key, boolean inventoryBacked) {
        long held = 0L;
        for (StockReservationId id : reservationsByKey.getOrDefault(key, Set.of())) {
            StockReservation reservation = reservations.get(id);
            if (reservation.state() == StockReservationState.HELD
                    && reservation.inventoryBacked() == inventoryBacked) {
                held = Math.addExact(held, reservation.quantity());
                if (held > StockLimits.MAX_QUANTITY) {
                    throw new StockConflictException("Held stock quantity exceeds its bound");
                }
            }
        }
        return held;
    }

    private long inboundCapacity(CatalogStockState listing) {
        if (listing.unlimited()) {
            return StockLimits.MAX_QUANTITY;
        }
        long exposure;
        try {
            exposure = Math.addExact(listing.availableQuantity(),
                    heldQuantity(listing.key(), true));
        } catch (ArithmeticException exception) {
            throw new StockConflictException(
                    "Stock capacity exposure overflowed", exception);
        }
        if (exposure >= listing.policy().configuredQuantity()) {
            return 0L;
        }
        return listing.policy().configuredQuantity() - exposure;
    }

    private static List<StockReservationRequest> normalizeBatchRequests(
            Collection<StockReservationRequest> requests
    ) {
        Objects.requireNonNull(requests, "requests");
        if (requests.isEmpty()
                || requests.size() > StockLimits.MAX_BATCH_LINES) {
            throw new IllegalArgumentException(
                    "Invalid stock reservation batch size");
        }
        List<StockReservationRequest> sorted = requests.stream()
                .map(value -> Objects.requireNonNull(value,
                        "stock reservation request"))
                .sorted().toList();
        Set<StockKey> keys = new HashSet<>();
        for (StockReservationRequest request : sorted) {
            if (!keys.add(request.stockKey())) {
                throw new StockConflictException(
                        "Stock reservation batch repeats a listing");
            }
        }
        return sorted;
    }

    private static List<StockReservationResolution>
    normalizeBatchResolutions(
            Collection<StockReservationResolution> resolutions
    ) {
        Objects.requireNonNull(resolutions, "resolutions");
        if (resolutions.isEmpty()
                || resolutions.size() > StockLimits.MAX_BATCH_LINES) {
            throw new IllegalArgumentException(
                    "Invalid stock resolution batch size");
        }
        List<StockReservationResolution> sorted = resolutions.stream()
                .map(value -> Objects.requireNonNull(value,
                        "stock reservation resolution"))
                .sorted().toList();
        Set<StockReservationId> ids = new HashSet<>();
        for (StockReservationResolution resolution : sorted) {
            if (!ids.add(resolution.reservationId())) {
                throw new StockConflictException(
                        "Stock resolution batch repeats a reservation");
            }
        }
        return sorted;
    }

    private StockApplyResult replay(UUID requestId, StockMutationType operation,
                                    String fingerprint) {
        StockMutationReceipt receipt = replayReceipt(requestId, operation,
                fingerprint);
        if (receipt == null) {
            return null;
        }
        return new StockApplyResult(receipt, true);
    }

    private StockMutationReceipt replayReceipt(
            UUID requestId,
            StockMutationType operation,
            String fingerprint
    ) {
        StockLimits.requireNonzeroUuid(requestId, "stock request identifier");
        StockMutationReceipt receipt = receipts.get(requestId);
        if (receipt == null) {
            return null;
        }
        if (receipt.operation() != operation
                || !receipt.requestFingerprint().equals(fingerprint)) {
            throw new StockConflictException("Stock request identifier was reused");
        }
        return receipt;
    }

    private StockMutationReceipt newReceipt(UUID requestId, StockMutationType operation,
                                            String requestFingerprint,
                                            Optional<StockKey> key,
                                            Optional<StockReservationId> reservationId,
                                            StockMutationOutcome outcome, long listingRevision,
                                            long reservationRevision, Instant now) {
        long revision = StockLimits.nextRevision(storeRevision, "Stock store revision");
        return new StockMutationReceipt(requestId, operation, requestFingerprint, revision,
                Optional.empty(), key, reservationId, List.of(), outcome,
                listingRevision, reservationRevision, now);
    }

    private StockMutationReceipt newReservationReceipt(
            UUID requestId,
            StockMutationType operation,
            UUID transactionId,
            String requestFingerprint,
            Optional<StockKey> key,
            Optional<StockReservationId> reservationId,
            StockMutationOutcome outcome,
            long listingRevision,
            long reservationRevision,
            Instant now
    ) {
        long revision = StockLimits.nextRevision(storeRevision,
                "Stock store revision");
        return new StockMutationReceipt(requestId, operation,
                requestFingerprint, revision, Optional.of(transactionId), key,
                reservationId, List.of(), outcome, listingRevision,
                reservationRevision, now);
    }

    private StockMutationReceipt newBatchReceipt(
            UUID requestId,
            StockMutationType operation,
            UUID transactionId,
            String requestFingerprint,
            List<StockReservationId> reservationIds,
            StockMutationOutcome outcome,
            Instant now
    ) {
        long revision = StockLimits.nextRevision(storeRevision,
                "Stock store revision");
        return new StockMutationReceipt(requestId, operation,
                requestFingerprint, revision, Optional.of(transactionId),
                Optional.empty(), Optional.empty(), reservationIds, outcome,
                -1L, -1L, now);
    }

    private StockBatchApplyResult batchResult(
            StockMutationReceipt receipt,
            boolean replayed
    ) {
        List<StockReservation> values = receipt.reservationIds().stream()
                .map(reservations::get)
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparing(
                        StockReservation::reservationId)).toList();
        return new StockBatchApplyResult(receipt, values, replayed);
    }

    private void persistReceipt(StockMutationReceipt receipt) {
        if (receipts.put(receipt.requestId(), receipt) != null) {
            throw new StockConflictException("Duplicate stock request receipt");
        }
        storeRevision = receipt.storeRevision();
    }

    private static CatalogStockState replaceListing(CatalogStockState current,
                                                    StockPolicy policy,
                                                    CatalogStockStatus status,
                                                    long available,
                                                    String configFingerprint,
                                                    Instant now) {
        requireMonotonic(now, current.updatedAt());
        return new CatalogStockState(current.key(), policy, status, available,
                configFingerprint,
                StockLimits.nextRevision(current.revision(), "Stock listing revision"), now);
    }

    private static Map<StockKey, StockDefinition> indexDefinitions(
            Collection<StockDefinition> definitions) {
        if (definitions.size() > StockLimits.MAX_LISTINGS) {
            throw new IllegalArgumentException("Stock definition count exceeds its bound");
        }
        Map<StockKey, StockDefinition> indexed = new HashMap<>();
        for (StockDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            if (indexed.put(definition.key(), definition) != null) {
                throw new StockConflictException("Reloaded stock catalog contains duplicate listing");
            }
        }
        return indexed;
    }

    private void putReservation(StockReservation reservation) {
        if (reservations.put(reservation.reservationId(), reservation) != null) {
            throw new StockConflictException("Duplicate stock reservation");
        }
        reservationsByKey.computeIfAbsent(reservation.stockKey(), ignored -> new HashSet<>())
                .add(reservation.reservationId());
    }

    private CatalogStockState requireListing(StockKey key) {
        CatalogStockState listing = listings.get(key);
        if (listing == null) {
            throw new StockConflictException("Stock listing does not exist");
        }
        return listing;
    }

    private static void requireActive(CatalogStockState listing) {
        if (listing.status() != CatalogStockStatus.ACTIVE) {
            throw new StockConflictException("Stock listing is retired");
        }
    }

    private static boolean matchesActiveDefinition(CatalogStockState current,
                                                   StockDefinition definition) {
        return current.status() == CatalogStockStatus.ACTIVE
                && current.policy().equals(definition.policy())
                && current.configFingerprint().equals(definition.configFingerprint());
    }

    private static void requireExpectedRevision(long current, long expected, String message) {
        if (current != expected) {
            throw new StockConflictException(message);
        }
    }

    private static void requireTime(Instant now) {
        StockLimits.requireInstant(now, "stock mutation time");
    }

    private static void requireMonotonic(Instant now, Instant previous) {
        if (now.isBefore(previous)) {
            throw new StockConflictException("Stock mutation time moved backwards");
        }
    }

    private void requireListingCapacity() {
        if (listings.size() >= maximumListings) {
            throw new StockConflictException("Stock listing capacity is exhausted");
        }
    }

    private void requireReservationCapacity() {
        if (reservations.size() >= maximumReservations) {
            throw new StockConflictException("Stock reservation capacity is exhausted");
        }
    }

    private void requireRequestCapacity() {
        if (receipts.size() >= maximumRequests) {
            throw new StockConflictException("Stock request capacity is exhausted");
        }
    }

    private static StockConservationReport conservation(StockStoreSnapshot snapshot) {
        List<String> violations = new ArrayList<>();
        long available = 0L;
        long backedHeld = 0L;
        long unlimitedHeld = 0L;
        long committed = 0L;
        long released = 0L;
        Map<StockKey, Long> heldByKey = new HashMap<>();
        for (Map.Entry<StockKey, CatalogStockState> entry : snapshot.listings().entrySet()) {
            if (!entry.getKey().equals(entry.getValue().key())) {
                violations.add("Listing map key does not match listing identity");
            }
            if (!entry.getValue().unlimited()) {
                available = safeTotal(available, entry.getValue().availableQuantity(),
                        violations);
            }
        }
        for (Map.Entry<StockReservationId, StockReservation> entry
                : snapshot.reservations().entrySet()) {
            StockReservation reservation = entry.getValue();
            if (!entry.getKey().equals(reservation.reservationId())) {
                violations.add("Reservation map key does not match reservation identity");
            }
            if (!snapshot.listings().containsKey(reservation.stockKey())) {
                violations.add("Reservation references a missing listing");
            }
            if (!reservation.reservationId().equals(StockReservationId.forTransaction(
                    reservation.transactionId(), reservation.stockKey(),
                    reservation.direction()))) {
                violations.add("Reservation identity is not transaction bound");
            }
            if (reservation.state() == StockReservationState.HELD) {
                if (reservation.inventoryBacked()) {
                    backedHeld = safeTotal(backedHeld, reservation.quantity(), violations);
                    heldByKey.merge(reservation.stockKey(), reservation.quantity(), (left, right) -> {
                        try {
                            return Math.addExact(left, right);
                        } catch (ArithmeticException exception) {
                            return Long.MAX_VALUE;
                        }
                    });
                } else {
                    unlimitedHeld = safeTotal(unlimitedHeld, reservation.quantity(), violations);
                }
            } else if (reservation.state() == StockReservationState.COMMITTED) {
                committed = safeTotal(committed, reservation.quantity(), violations);
            } else {
                released = safeTotal(released, reservation.quantity(), violations);
            }
        }
        for (Map.Entry<StockKey, Long> entry : heldByKey.entrySet()) {
            if (entry.getValue() > StockLimits.MAX_QUANTITY) {
                violations.add("Backed held listing quantity exceeds its bound");
            }
        }
        for (CatalogStockState listing : snapshot.listings().values()) {
            if (!listing.unlimited()) {
                long held = heldByKey.getOrDefault(listing.key(), 0L);
                long total = safeTotal(listing.availableQuantity(), held, violations);
                if (total > StockLimits.MAX_QUANTITY) {
                    violations.add("Finite listing quantity exceeds its bound");
                }
                long maximumAvailable = Math.max(0L,
                        listing.policy().configuredQuantity() - held);
                if (listing.availableQuantity() > maximumAvailable) {
                    violations.add("Finite listing availability exceeds its configured target");
                }
            }
        }
        Set<Long> requestRevisions = new HashSet<>();
        Map<StockReservationId, Integer> appliedReserveReceipts = new HashMap<>();
        Map<StockReservationId, Integer> appliedTerminalReceipts = new HashMap<>();
        for (Map.Entry<UUID, StockMutationReceipt> entry : snapshot.receipts().entrySet()) {
            StockMutationReceipt receipt = entry.getValue();
            if (!entry.getKey().equals(receipt.requestId())) {
                violations.add("Receipt map key does not match request identity");
            }
            if (!requestRevisions.add(receipt.storeRevision())) {
                violations.add("Stock request revisions are duplicated");
            }
            if (receipt.storeRevision() <= 0L
                    || receipt.storeRevision() > snapshot.storeRevision()) {
                violations.add("Stock request revision is outside the snapshot");
            }
            if (receipt.stockKey().isPresent()) {
                CatalogStockState listing = snapshot.listings().get(
                        receipt.stockKey().orElseThrow());
                if (listing == null) {
                    violations.add("Stock receipt references a missing listing");
                } else if (receipt.listingRevision() > listing.revision()) {
                    violations.add("Stock receipt listing revision is ahead of state");
                }
            }
            if (receipt.reservationId().isPresent()
                    && receipt.transactionId().isEmpty()) {
                violations.add(
                        "Stock reservation receipt lacks transaction identity");
            }
            if (receipt.reservationId().isPresent()) {
                StockReservation reservation = snapshot.reservations().get(
                        receipt.reservationId().orElseThrow());
                if (reservation == null) {
                    if (receipt.outcome() != StockMutationOutcome.INSUFFICIENT_STOCK) {
                        violations.add("Stock receipt references a missing reservation");
                    }
                } else {
                    if (receipt.transactionId().isPresent()
                            && !receipt.transactionId().orElseThrow().equals(
                            reservation.transactionId())) {
                        violations.add(
                                "Stock receipt and reservation transaction differ");
                    }
                    if (receipt.stockKey().isPresent()
                            && !receipt.stockKey().orElseThrow().equals(
                            reservation.stockKey())) {
                        violations.add("Stock receipt and reservation listing differ");
                    }
                    if (receipt.reservationRevision() > reservation.revision()) {
                        violations.add("Stock receipt reservation revision is ahead of state");
                    }
                    if (receipt.operation() == StockMutationType.RESERVE
                            && receipt.outcome() == StockMutationOutcome.APPLIED) {
                        appliedReserveReceipts.merge(reservation.reservationId(), 1,
                                Integer::sum);
                        if (!receipt.appliedAt().equals(reservation.createdAt())) {
                            violations.add("Stock reserve receipt time differs from reservation");
                        }
                    }
                    if (receipt.operation() == StockMutationType.COMMIT
                            || receipt.operation() == StockMutationType.RELEASE) {
                        StockReservationState expectedState = receipt.operation()
                                == StockMutationType.COMMIT
                                ? StockReservationState.COMMITTED
                                : StockReservationState.RELEASED;
                        if (reservation.state() != expectedState) {
                            violations.add("Stock terminal receipt conflicts with reservation state");
                        }
                        if (receipt.outcome() == StockMutationOutcome.APPLIED) {
                            appliedTerminalReceipts.merge(reservation.reservationId(), 1,
                                    Integer::sum);
                            if (!receipt.appliedAt().equals(reservation.updatedAt())) {
                                violations.add("Stock terminal receipt time differs from reservation");
                            }
                        }
                    }
                }
            }
            if (receipt.operation().batchOperation()) {
                auditBatchReceipt(snapshot, receipt, appliedReserveReceipts,
                        appliedTerminalReceipts, violations);
            }
            if (receipt.outcome() == StockMutationOutcome.INSUFFICIENT_STOCK
                    && receipt.operation() != StockMutationType.RESERVE
                    && receipt.operation()
                    != StockMutationType.RESERVE_BATCH) {
                violations.add("Insufficient stock outcome belongs to another operation");
            }
        }
        for (StockReservation reservation : snapshot.reservations().values()) {
            if (appliedReserveReceipts.getOrDefault(reservation.reservationId(), 0) != 1) {
                violations.add("Stock reservation lacks one applied reserve receipt");
            }
            int terminalReceipts = appliedTerminalReceipts.getOrDefault(
                    reservation.reservationId(), 0);
            if (reservation.state() == StockReservationState.HELD
                    && terminalReceipts != 0) {
                violations.add("Held stock reservation has a terminal receipt");
            }
            if (reservation.state() != StockReservationState.HELD
                    && terminalReceipts != 1) {
                violations.add("Resolved stock reservation lacks one terminal receipt");
            }
        }
        if (snapshot.storeRevision() != snapshot.receipts().size()
                || requestRevisions.size() != snapshot.receipts().size()) {
            violations.add("Stock request revision sequence is incomplete");
        } else {
            for (long revision = 1L; revision <= snapshot.storeRevision(); revision++) {
                if (!requestRevisions.contains(revision)) {
                    violations.add("Stock request revision sequence has a gap");
                    break;
                }
            }
        }
        return new StockConservationReport(violations.isEmpty(), available, backedHeld,
                unlimitedHeld, committed, released, violations);
    }

    private static void auditBatchReceipt(
            StockStoreSnapshot snapshot,
            StockMutationReceipt receipt,
            Map<StockReservationId, Integer> appliedReserveReceipts,
            Map<StockReservationId, Integer> appliedTerminalReceipts,
            List<String> violations
    ) {
        if (receipt.transactionId().isEmpty()) {
            violations.add("Stock batch receipt lacks transaction identity");
            return;
        }
        if (receipt.operation() == StockMutationType.RESERVE_BATCH
                && receipt.outcome()
                == StockMutationOutcome.INSUFFICIENT_STOCK) {
            return;
        }
        StockReservationState expectedState = receipt.operation()
                == StockMutationType.COMMIT_BATCH
                ? StockReservationState.COMMITTED
                : receipt.operation() == StockMutationType.RELEASE_BATCH
                ? StockReservationState.RELEASED : null;
        for (StockReservationId reservationId : receipt.reservationIds()) {
            StockReservation reservation = snapshot.reservations().get(
                    reservationId);
            if (reservation == null) {
                violations.add(
                        "Stock batch receipt references a missing reservation");
                continue;
            }
            if (!reservation.transactionId().equals(
                    receipt.transactionId().orElseThrow())) {
                violations.add(
                        "Stock batch receipt and reservation transaction differ");
            }
            if (receipt.operation() == StockMutationType.RESERVE_BATCH) {
                appliedReserveReceipts.merge(reservationId, 1, Integer::sum);
                if (!receipt.appliedAt().equals(reservation.createdAt())) {
                    violations.add(
                            "Stock batch reserve time differs from reservation");
                }
            } else {
                if (reservation.state() != expectedState) {
                    violations.add(
                            "Stock batch terminal receipt conflicts with reservation state");
                }
                if (receipt.outcome() == StockMutationOutcome.APPLIED
                        && receipt.appliedAt().equals(
                        reservation.updatedAt())) {
                    appliedTerminalReceipts.merge(reservationId, 1,
                            Integer::sum);
                }
            }
        }
    }

    private static long safeTotal(long current, long amount, List<String> violations) {
        try {
            return Math.addExact(current, amount);
        } catch (ArithmeticException exception) {
            violations.add("Stock conservation total overflowed");
            return Long.MAX_VALUE;
        }
    }
}
