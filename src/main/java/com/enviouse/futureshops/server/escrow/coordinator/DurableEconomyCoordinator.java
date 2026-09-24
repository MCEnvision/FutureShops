package com.enviouse.futureshops.server.escrow.coordinator;

import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.LegId;
import com.enviouse.futureshops.api.economy.RootId;
import com.enviouse.futureshops.server.debug.DebugDiagnostics;
import com.enviouse.futureshops.server.debug.DebugModule;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.journal.JournalReplayBatch;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The single durable boundary for account bound monetary roots and legs.
 *
 * <p>This class deliberately extends the existing FutureShops write ahead
 * journal. It owns intent, receipt facts, custody, and claims, but never
 * mirrors an external spendable balance.</p>
 */
public final class DurableEconomyCoordinator implements AutoCloseable {
    private static final int PAYLOAD_MAGIC = 0x46534350;
    private static final short PAYLOAD_VERSION = 1;
    private static final int MAX_LEGS = 64;
    private static final int MAX_CLAIMS = 10_000;
    private static final int MAX_STRING_BYTES = 4_096;
    private static final int MAX_REPLAY_RECORDS = WriteAheadJournal.MAX_REPLAY_BATCH_RECORDS;
    private static final long MAX_REPLAY_BYTES = WriteAheadJournal.MAX_REPLAY_BATCH_BYTES;

    private final WriteAheadJournal journal;
    private final DurableEconomyEffect effect;
    private final Map<UUID, RootRecord> roots = new LinkedHashMap<>();
    private final Map<UUID, ClaimRecord> claims = new LinkedHashMap<>();
    private final ThreadLocal<Set<UUID>> activeRoots = ThreadLocal.withInitial(
            () -> java.util.Collections.newSetFromMap(new java.util.HashMap<>()));
    private CoordinatorLifecycle lifecycle;
    private boolean closed;

    private DurableEconomyCoordinator(WriteAheadJournal journal, DurableEconomyEffect effect) {
        this.journal = journal;
        this.effect = effect;
        this.lifecycle = CoordinatorLifecycle.READY;
    }

    /** Returns the canonical receipt audit path under a world data directory. */
    public static Path receiptAuditPath(Path worldRoot) {
        return ReceiptAuditPath.forWorld(worldRoot);
    }

    /** Opens and replays the existing Forge WAL extension. */
    public static DurableEconomyCoordinator open(Path journalPath, DurableEconomyEffect effect)
            throws IOException, CoordinatorException {
        Objects.requireNonNull(journalPath, "journalPath");
        Objects.requireNonNull(effect, "effect");
        WriteAheadJournal journal = WriteAheadJournal.open(journalPath);
        DurableEconomyCoordinator coordinator = new DurableEconomyCoordinator(journal, effect);
        try {
            coordinator.replay();
            if (journal.recovery().truncatedTail()) {
                coordinator.lifecycle = CoordinatorLifecycle.RECOVERING;
            }
            coordinator.recomputeLifecycle();
            return coordinator;
        } catch (IOException | RuntimeException exception) {
            try {
                journal.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            if (exception instanceof CoordinatorException coordinatorException) {
                throw coordinatorException;
            }
            if (exception instanceof IOException ioException) {
                throw new CoordinatorException(CoordinatorError.STORAGE_CORRUPT,
                        "durable coordinator replay failed", ioException);
            }
            throw new CoordinatorException(CoordinatorError.STORAGE_CORRUPT,
                    "durable coordinator record is invalid", exception);
        }
    }

    public synchronized CoordinatorLifecycle lifecycle() {
        return lifecycle;
    }

    /** Stops new admissions before server shutdown while preserving claims. */
    public synchronized void beginDraining() throws CoordinatorException {
        ensureOpen();
        if (lifecycle == CoordinatorLifecycle.DRAINING) {
            return;
        }
        if (!lifecycle.canTransitionTo(CoordinatorLifecycle.DRAINING)) {
            throw failure(CoordinatorError.LIFECYCLE_UNAVAILABLE,
                    "coordinator cannot drain from " + lifecycle.name().toLowerCase());
        }
        persistLifecycle(CoordinatorLifecycle.DRAINING);
    }

    /** Reopens admissions only after a clean drain with no frozen root. */
    public synchronized void resumeReady() throws CoordinatorException {
        ensureOpen();
        if (lifecycle == CoordinatorLifecycle.READY) {
            return;
        }
        if (lifecycle != CoordinatorLifecycle.DRAINING
                || roots.values().stream().anyMatch(root -> root.state == OperationState.FROZEN
                || root.state == OperationState.UNKNOWN)) {
            throw failure(CoordinatorError.LIFECYCLE_UNAVAILABLE,
                    "coordinator cannot resume while unresolved work exists");
        }
        persistLifecycle(CoordinatorLifecycle.READY);
    }

    /** Requests a clean shutdown marker. */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        if (!journal.failed()) {
            try {
                append(EventType.CLEAN_SHUTDOWN, new UUID(0L, 1L), new UUID(0L, 2L), out -> {
                    // The marker has no mutable payload beyond the version header.
                });
            } catch (CoordinatorException | RuntimeException ignored) {
                // The journal is still closed below. A failed marker is treated as unclean.
            }
        }
        closed = true;
        journal.close();
    }

    /** Closes without writing a clean marker, for crash and restart fixtures. */
    public synchronized void closeUncleanForTest() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        journal.close();
    }

    /** Durable admission. No provider method is called by this method. */
    public synchronized PreparedRoot admit(RouteContext route, RootId rootId,
                                           List<BoundLeg> legs, CustodyPlan custody)
            throws CoordinatorException {
        ensureOpen();
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(rootId, "rootId");
        Objects.requireNonNull(legs, "legs");
        Objects.requireNonNull(custody, "custody");
        if (!activeRoots.get().isEmpty()) {
            throw failure(CoordinatorError.REENTRANT_ADMISSION,
                    "reentrant admission is refused");
        }
        RootRecord existing = roots.get(rootId.value());
        List<BoundLeg> immutableLegs = List.copyOf(legs);
        String fingerprint = fingerprint(route, immutableLegs, custody);
        if (existing != null) {
            if (!existing.fingerprint.equals(fingerprint)) {
                throw failure(CoordinatorError.DUPLICATE_CONFLICT,
                        "root identity is already bound to different payload");
            }
            return existing.prepared(true);
        }
        if (!lifecycle.canAcceptAdmissions()) {
            throw failure(CoordinatorError.LIFECYCLE_UNAVAILABLE,
                    "coordinator lifecycle is " + lifecycle.name().toLowerCase());
        }
        if (!route.authorized()) {
            throw failure(CoordinatorError.UNAUTHORIZED, "route permission was refused");
        }
        if (!route.capabilityAvailable()) {
            throw failure(CoordinatorError.CAPABILITY_UNAVAILABLE,
                    "required provider capability is unavailable");
        }
        long legTotal = validateLegs(rootId, immutableLegs);
        if (custody.minorUnits() != legTotal) {
            throw failure(CoordinatorError.INVALID_AMOUNT,
                    "custody amount must equal the admitted leg total");
        }
        long admittedAt = System.currentTimeMillis();
        append(EventType.ROOT_ADMITTED, rootId.value(), rootId.value(), out -> {
            writeRoot(out, route, rootId, immutableLegs, custody, fingerprint, admittedAt);
        });
        RootRecord record = new RootRecord(rootId, route, immutableLegs, custody, fingerprint,
                admittedAt);
        roots.put(rootId.value(), record);
        recordDiagnostic(route, rootId.value(), null, "admit", "prepared", "prepared",
                "intent_flushed");
        return record.prepared(false);
    }

    /** Executes only durably admitted legs. Unknown effects are never retried. */
    public synchronized SettlementOutcome execute(PreparedRoot prepared) throws CoordinatorException {
        Objects.requireNonNull(prepared, "prepared");
        RootRecord root = roots.get(prepared.rootId().value());
        if (root == null || !root.matches(prepared)) {
            return SettlementOutcome.refused(CoordinatorError.PAYLOAD_CONFLICT,
                    "prepared root does not match durable admission");
        }
        if (root.state == OperationState.RESOLVED || root.state == OperationState.REPLAYED) {
            return SettlementOutcome.replayed(root.state);
        }
        if (root.state == OperationState.UNKNOWN || root.state == OperationState.FROZEN
                || lifecycle == CoordinatorLifecycle.FROZEN
                || lifecycle == CoordinatorLifecycle.RECOVERING) {
            return SettlementOutcome.pending(root.state);
        }
        if (!activeRoots.get().add(root.rootId.value())) {
            freeze(root, CoordinatorError.REENTRANT_ADMISSION, "reentrant execution");
            return SettlementOutcome.pending(OperationState.FROZEN);
        }
        try {
            boolean anyConfirmed = false;
            for (LegRecord leg : root.legs.values()) {
                if (leg.state == OperationState.CONFIRMED || leg.state == OperationState.RESOLVED) {
                    anyConfirmed = true;
                    continue;
                }
                if (leg.state != OperationState.PREPARED) {
                    return SettlementOutcome.pending(root.state);
                }
                transition(root, leg, OperationState.SUBMITTED, null);
                DispatchResult result;
                try {
                    result = effect.dispatch(leg.leg);
                } catch (RuntimeException exception) {
                    result = DispatchResult.unknown();
                }
                if (result.status() == DispatchResult.Status.CONFIRMED) {
                    DispatchReceipt receipt = result.receipt().orElseThrow();
                    if (!receiptMatches(leg.leg, receipt)) {
                        freeze(root, CoordinatorError.RECEIPT_MISMATCH,
                                "provider returned a mismatched receipt");
                        return SettlementOutcome.pending(OperationState.FROZEN);
                    }
                    transition(root, leg, OperationState.CONFIRMED, receipt);
                    anyConfirmed = true;
                } else if (result.status() == DispatchResult.Status.REJECTED) {
                    transition(root, leg, OperationState.REJECTED, null);
                    if (anyConfirmed) {
                        createClaimsForConfirmed(root);
                        freeze(root, CoordinatorError.RECEIPT_MISSING,
                                "partial settlement requires a claim");
                        return SettlementOutcome.partial(root.claimIds);
                    }
                    transition(root, leg, OperationState.RESOLVED, null);
                    resolve(root);
                    return SettlementOutcome.rejected();
                } else {
                    transition(root, leg, OperationState.UNKNOWN, null);
                    if (anyConfirmed) {
                        createClaimsForConfirmed(root);
                    }
                    freeze(root, CoordinatorError.RECEIPT_MISSING,
                            "provider outcome is unknown and requires lookup");
                    return anyConfirmed
                            ? SettlementOutcome.partial(root.claimIds)
                            : SettlementOutcome.pending(OperationState.UNKNOWN);
                }
            }
            for (LegRecord leg : root.legs.values()) {
                if (leg.state == OperationState.CONFIRMED) {
                    transition(root, leg, OperationState.RESOLVED, leg.receipt);
                }
            }
            resolve(root);
            return SettlementOutcome.confirmed();
        } finally {
            activeRoots.get().remove(root.rootId.value());
        }
    }

    /** Looks up authoritative receipts for submitted or unknown legs after restart. */
    public synchronized RecoveryOutcome recover(RootId rootId) {
        Objects.requireNonNull(rootId, "rootId");
        RootRecord root = roots.get(rootId.value());
        if (root == null) {
            return RecoveryOutcome.refused(CoordinatorError.CLAIM_NOT_FOUND,
                    "root is not present in the durable journal");
        }
        lifecycle = CoordinatorLifecycle.RECOVERING;
        try {
            boolean unresolved = false;
            for (LegRecord leg : root.legs.values()) {
                if (leg.state == OperationState.PREPARED) {
                    transition(root, leg, OperationState.REJECTED, null);
                    continue;
                }
                if (leg.state != OperationState.SUBMITTED && leg.state != OperationState.UNKNOWN) {
                    continue;
                }
                Optional<DispatchReceipt> lookedUp;
                try {
                    lookedUp = effect.lookup(leg.leg);
                } catch (RuntimeException exception) {
                    lookedUp = Optional.empty();
                }
                if (lookedUp.isEmpty()) {
                    unresolved = true;
                    continue;
                }
                DispatchReceipt receipt = lookedUp.orElseThrow();
                if (!receiptMatches(leg.leg, receipt)) {
                    freeze(root, CoordinatorError.RECEIPT_MISMATCH,
                            "receipt lookup contradicted the immutable request");
                    return RecoveryOutcome.frozen(CoordinatorError.RECEIPT_MISMATCH);
                }
                transition(root, leg, OperationState.CONFIRMED, receipt);
            }
            if (unresolved) {
                freeze(root, CoordinatorError.RECEIPT_MISSING,
                        "authoritative receipt is still unavailable");
                return RecoveryOutcome.pending();
            }
            for (LegRecord leg : root.legs.values()) {
                if (leg.state == OperationState.CONFIRMED) {
                    transition(root, leg, OperationState.RESOLVED, leg.receipt);
                } else if (leg.state == OperationState.REJECTED) {
                    transition(root, leg, OperationState.RESOLVED, null);
                }
            }
            resolve(root);
            lifecycle = CoordinatorLifecycle.READY;
            return RecoveryOutcome.resolved();
        } catch (CoordinatorException exception) {
            lifecycle = CoordinatorLifecycle.FROZEN;
            return RecoveryOutcome.frozen(exception.error());
        }
    }

    /** Collects a durable partial settlement claim using its original binding only. */
    public synchronized ClaimOutcome collect(ClaimId claimId, BindingV1 originalBinding)
            throws CoordinatorException {
        ensureOpen();
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(originalBinding, "originalBinding");
        ClaimRecord claim = claims.get(claimId.value());
        if (claim == null) {
            throw failure(CoordinatorError.CLAIM_NOT_FOUND, "claim is not present");
        }
        if (!claim.binding.equals(originalBinding)) {
            throw failure(CoordinatorError.ORIGINAL_BINDING_UNAVAILABLE,
                    "claim must be collected through its original provider binding");
        }
        if (claim.state == ClaimStateInternal.COLLECTED) {
            return ClaimOutcome.alreadyCollected(claim.amount);
        }
        if (lifecycle != CoordinatorLifecycle.READY && lifecycle != CoordinatorLifecycle.FROZEN) {
            throw failure(CoordinatorError.LIFECYCLE_UNAVAILABLE,
                    "claim collection is unavailable while coordinator is "
                            + lifecycle.name().toLowerCase());
        }
        append(EventType.CLAIM_COLLECTED, claim.rootId, claim.claimId, out -> {
            writeUuid(out, claim.claimId);
        });
        claim.state = ClaimStateInternal.COLLECTED;
        return ClaimOutcome.collected(claim.amount);
    }

    public synchronized Optional<RootSnapshot> root(RootId rootId) {
        RootRecord root = roots.get(rootId.value());
        return root == null ? Optional.empty() : Optional.of(root.snapshot());
    }

    public synchronized Optional<ClaimSnapshot> claim(ClaimId claimId) {
        ClaimRecord claim = claims.get(claimId.value());
        return claim == null ? Optional.empty() : Optional.of(claim.snapshot());
    }

    public synchronized List<ClaimSnapshot> claims() {
        return claims.values().stream().map(ClaimRecord::snapshot).toList();
    }

    public synchronized int rootCount() {
        return roots.size();
    }

    public synchronized int claimCount() {
        return claims.size();
    }

    public record RootSnapshot(RootId rootId, OperationState state, boolean custodyHeld,
                               long admittedAtEpochMillis, String intendedPhase,
                               List<LegSnapshot> legs, List<ClaimId> claimIds) {
        public RootSnapshot {
            legs = List.copyOf(legs);
            claimIds = List.copyOf(claimIds);
        }
    }

    public record LegSnapshot(BoundLeg leg, OperationState state,
                              Optional<DispatchReceipt> receipt) {
        public LegSnapshot {
            receipt = Objects.requireNonNull(receipt, "receipt");
        }
    }

    public enum ClaimState { PENDING, COLLECTED }

    public record ClaimSnapshot(ClaimId claimId, BindingV1 binding, long amount,
                                ClaimState state) {
    }

    public record SettlementOutcome(Status status, OperationState state,
                                    CoordinatorError error, List<ClaimId> claimIds) {
        public enum Status { CONFIRMED, REJECTED, REPLAYED, PENDING, PARTIAL, REFUSED }

        public SettlementOutcome {
            claimIds = List.copyOf(claimIds);
        }

        static SettlementOutcome confirmed() {
            return new SettlementOutcome(Status.CONFIRMED, OperationState.RESOLVED, null, List.of());
        }

        static SettlementOutcome rejected() {
            return new SettlementOutcome(Status.REJECTED, OperationState.RESOLVED, null, List.of());
        }

        static SettlementOutcome replayed(OperationState state) {
            return new SettlementOutcome(Status.REPLAYED, state, null, List.of());
        }

        static SettlementOutcome pending(OperationState state) {
            return new SettlementOutcome(Status.PENDING, state, CoordinatorError.RECEIPT_MISSING, List.of());
        }

        static SettlementOutcome partial(List<ClaimId> claimIds) {
            return new SettlementOutcome(Status.PARTIAL, OperationState.FROZEN,
                    CoordinatorError.RECEIPT_MISSING, claimIds);
        }

        static SettlementOutcome refused(CoordinatorError error, String ignored) {
            return new SettlementOutcome(Status.REFUSED, OperationState.FROZEN, error, List.of());
        }
    }

    public record RecoveryOutcome(Status status, CoordinatorError error) {
        public enum Status { RESOLVED, PENDING, FROZEN, REFUSED }

        static RecoveryOutcome resolved() {
            return new RecoveryOutcome(Status.RESOLVED, null);
        }

        static RecoveryOutcome pending() {
            return new RecoveryOutcome(Status.PENDING, CoordinatorError.RECEIPT_MISSING);
        }

        static RecoveryOutcome frozen(CoordinatorError error) {
            return new RecoveryOutcome(Status.FROZEN, error);
        }

        static RecoveryOutcome refused(CoordinatorError error, String ignored) {
            return new RecoveryOutcome(Status.REFUSED, error);
        }
    }

    public record ClaimOutcome(Status status, long amount) {
        public enum Status { COLLECTED, ALREADY_COLLECTED }

        static ClaimOutcome collected(long amount) {
            return new ClaimOutcome(Status.COLLECTED, amount);
        }

        static ClaimOutcome alreadyCollected(long amount) {
            return new ClaimOutcome(Status.ALREADY_COLLECTED, amount);
        }
    }

    private enum ClaimStateInternal { PENDING, COLLECTED }

    private enum EventType {
        ROOT_ADMITTED(1), LEG_TRANSITION(2), ROOT_TRANSITION(3), CLAIM_CREATED(4),
        CLAIM_COLLECTED(5), LIFECYCLE(6), CLEAN_SHUTDOWN(7);

        private final int code;

        EventType(int code) {
            this.code = code;
        }

        static EventType fromCode(int code) throws IOException {
            for (EventType value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new IOException("unknown coordinator event type " + code);
        }
    }

    private final class RootRecord {
        private final RootId rootId;
        private final RouteContext route;
        private final CustodyPlan custody;
        private final String fingerprint;
        private final long admittedAtEpochMillis;
        private final Map<UUID, LegRecord> legs = new LinkedHashMap<>();
        private final List<ClaimId> claimIds = new ArrayList<>();
        private OperationState state = OperationState.PREPARED;
        private boolean custodyHeld = true;

        private RootRecord(RootId rootId, RouteContext route, List<BoundLeg> legs,
                           CustodyPlan custody, String fingerprint, long admittedAtEpochMillis) {
            this.rootId = rootId;
            this.route = route;
            this.custody = custody;
            this.fingerprint = fingerprint;
            this.admittedAtEpochMillis = admittedAtEpochMillis;
            for (BoundLeg leg : legs) {
                this.legs.put(leg.legId().value(), new LegRecord(leg));
            }
        }

        private PreparedRoot prepared(boolean replayed) {
            return new PreparedRoot(rootId, route,
                    legs.values().stream().map(value -> value.leg).toList(), custody, replayed);
        }

        private boolean matches(PreparedRoot prepared) {
            return fingerprint.equals(fingerprint(prepared.route(), prepared.legs(), prepared.custody()));
        }

        private RootSnapshot snapshot() {
            return new RootSnapshot(rootId, state, custodyHeld, admittedAtEpochMillis,
                    "phase-003",
                    legs.values().stream().map(LegRecord::snapshot).toList(), claimIds);
        }
    }

    private final class LegRecord {
        private final BoundLeg leg;
        private OperationState state = OperationState.PREPARED;
        private DispatchReceipt receipt;

        private LegRecord(BoundLeg leg) {
            this.leg = leg;
        }

        private LegSnapshot snapshot() {
            return new LegSnapshot(leg, state, Optional.ofNullable(receipt));
        }
    }

    private final class ClaimRecord {
        private final UUID claimId;
        private final UUID rootId;
        private final BindingV1 binding;
        private final long amount;
        private ClaimStateInternal state = ClaimStateInternal.PENDING;

        private ClaimRecord(UUID claimId, UUID rootId, BindingV1 binding, long amount) {
            this.claimId = claimId;
            this.rootId = rootId;
            this.binding = binding;
            this.amount = amount;
        }

        private ClaimSnapshot snapshot() {
            return new ClaimSnapshot(new ClaimId(claimId), binding, amount,
                    state == ClaimStateInternal.COLLECTED ? ClaimState.COLLECTED : ClaimState.PENDING);
        }
    }

    private void replay() throws IOException {
        long offset = 0L;
        long expectedSequence = 1L;
        boolean sawCleanMarker = false;
        while (true) {
            JournalReplayBatch batch = journal.replayBatch(offset, expectedSequence,
                    MAX_REPLAY_RECORDS, MAX_REPLAY_BYTES);
            for (JournalRecord record : batch.records()) {
                sawCleanMarker = decode(record.payload(), record.transactionId());
            }
            offset = batch.nextOffset();
            expectedSequence = batch.nextExpectedSequence();
            if (batch.endOfJournal()) {
                break;
            }
        }
        if (!journal.recovery().empty() && !sawCleanMarker) {
            lifecycle = CoordinatorLifecycle.RECOVERING;
        }
    }

    private boolean decode(byte[] payload, UUID transactionId)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        if (in.readInt() != PAYLOAD_MAGIC || in.readShort() != PAYLOAD_VERSION) {
            throw new IOException("coordinator payload header is invalid");
        }
        EventType type = EventType.fromCode(in.readUnsignedByte());
        switch (type) {
            case ROOT_ADMITTED -> {
                RootDecoded decoded = readRoot(in);
                if (!decoded.root.rootId.value().equals(transactionId)) {
                    throw new IOException("root transaction identity does not match payload");
                }
                if (roots.putIfAbsent(decoded.root.rootId.value(), decoded.root) != null) {
                    throw new IOException("duplicate root admission");
                }
            }
            case LEG_TRANSITION -> {
                UUID rootId = readUuid(in);
                UUID legId = readUuid(in);
                OperationState state = readState(in);
                DispatchReceipt receipt = readOptionalReceipt(in);
                RootRecord root = requireRoot(rootId);
                LegRecord leg = requireLeg(root, legId);
                applyTransition(leg, state, receipt);
            }
            case ROOT_TRANSITION -> {
                UUID rootId = readUuid(in);
                OperationState state = readState(in);
                boolean custodyHeld = in.readBoolean();
                RootRecord root = requireRoot(rootId);
                if (!root.state.canTransitionTo(state) && root.state != state) {
                    throw new IOException("illegal root state transition");
                }
                root.state = state;
                root.custodyHeld = custodyHeld;
            }
            case CLAIM_CREATED -> {
                UUID rootId = readUuid(in);
                UUID claimId = readUuid(in);
                BindingV1 binding = readBinding(in);
                long amount = in.readLong();
                if (amount <= 0L || claims.size() >= MAX_CLAIMS
                        || claims.containsKey(claimId)) {
                    throw new IOException("invalid or duplicate claim");
                }
                RootRecord root = requireRoot(rootId);
                ClaimRecord claim = new ClaimRecord(claimId, rootId, binding, amount);
                claims.put(claimId, claim);
                root.claimIds.add(new ClaimId(claimId));
            }
            case CLAIM_COLLECTED -> {
                UUID claimId = readUuid(in);
                ClaimRecord claim = claims.get(claimId);
                if (claim == null || claim.state == ClaimStateInternal.COLLECTED) {
                    throw new IOException("duplicate or unknown claim collection");
                }
                claim.state = ClaimStateInternal.COLLECTED;
            }
            case LIFECYCLE -> lifecycle = readLifecycle(in);
            case CLEAN_SHUTDOWN -> {
                // The caller tracks the last record to detect an unclean tail.
            }
        }
        if (in.available() != 0) {
            throw new IOException("coordinator payload has trailing bytes");
        }
        return type == EventType.CLEAN_SHUTDOWN;
    }

    private long validateLegs(RootId rootId, List<BoundLeg> legs) throws CoordinatorException {
        if (legs.isEmpty() || legs.size() > MAX_LEGS) {
            throw failure(CoordinatorError.INVALID_AMOUNT, "leg count is outside bounds");
        }
        long total = 0L;
        java.util.HashSet<UUID> seen = new java.util.HashSet<>();
        for (BoundLeg leg : legs) {
            if (!leg.rootId().equals(rootId)) {
                throw failure(CoordinatorError.INVALID_BINDING, "leg root identity does not match");
            }
            if (!seen.add(leg.legId().value())) {
                throw failure(CoordinatorError.DUPLICATE_CONFLICT, "leg identity is duplicated");
            }
            try {
                total = Math.addExact(total, leg.minorUnits());
            } catch (ArithmeticException exception) {
                throw failure(CoordinatorError.INVALID_AMOUNT, "leg total overflow");
            }
        }
        if (total <= 0L) {
            throw failure(CoordinatorError.INVALID_AMOUNT, "leg total must be positive");
        }
        return total;
    }

    private void transition(RootRecord root, LegRecord leg, OperationState state,
                            DispatchReceipt receipt) throws CoordinatorException {
        if (!leg.state.canTransitionTo(state)) {
            throw failure(CoordinatorError.PAYLOAD_CONFLICT,
                    "illegal leg transition from " + leg.state + " to " + state);
        }
        append(EventType.LEG_TRANSITION, root.rootId.value(), leg.leg.legId().value(), out -> {
            writeUuid(out, root.rootId.value());
            writeUuid(out, leg.leg.legId().value());
            writeState(out, state);
            writeOptionalReceipt(out, receipt);
        });
        leg.state = state;
        leg.receipt = receipt;
        recordDiagnostic(root.route, root.rootId.value(), leg.leg.legId().value(),
                "leg_transition", state.name().toLowerCase(), state.name().toLowerCase(),
                receipt == null ? "state_transition" : "receipt_recorded");
    }

    private void resolve(RootRecord root) throws CoordinatorException {
        if (!root.state.canTransitionTo(OperationState.RESOLVED)
                && root.state != OperationState.RESOLVED) {
            if (root.state == OperationState.PREPARED) {
                root.state = OperationState.RESOLVED;
            } else {
                throw failure(CoordinatorError.PAYLOAD_CONFLICT, "root cannot be resolved");
            }
        }
        append(EventType.ROOT_TRANSITION, root.rootId.value(), root.rootId.value(), out -> {
            writeUuid(out, root.rootId.value());
            writeState(out, OperationState.RESOLVED);
            out.writeBoolean(false);
        });
        root.state = OperationState.RESOLVED;
        root.custodyHeld = false;
        lifecycle = CoordinatorLifecycle.READY;
        recordDiagnostic(root.route, root.rootId.value(), null, "root_complete", "resolved",
                "resolved", "completion_confirmed");
    }

    private void freeze(RootRecord root, CoordinatorError error, String message) {
        try {
            if (root.state != OperationState.FROZEN
                    && root.state.canTransitionTo(OperationState.FROZEN)) {
                append(EventType.ROOT_TRANSITION, root.rootId.value(), root.rootId.value(), out -> {
                    writeUuid(out, root.rootId.value());
                    writeState(out, OperationState.FROZEN);
                    out.writeBoolean(root.custodyHeld);
                });
                root.state = OperationState.FROZEN;
            }
            append(EventType.LIFECYCLE, new UUID(0L, 3L), new UUID(0L, 4L),
                    out -> writeLifecycle(out, CoordinatorLifecycle.FROZEN));
            lifecycle = CoordinatorLifecycle.FROZEN;
            recordDiagnostic(root.route, root.rootId.value(), null, "root_frozen", "frozen",
                    "frozen", error.name().toLowerCase());
        } catch (CoordinatorException exception) {
            lifecycle = CoordinatorLifecycle.FROZEN;
        }
    }

    private void createClaimsForConfirmed(RootRecord root) {
        for (LegRecord leg : root.legs.values()) {
            if (leg.state != OperationState.CONFIRMED || leg.receipt == null) {
                continue;
            }
            UUID claimId = UUID.nameUUIDFromBytes(
                    ("futureshops.claim." + root.rootId.value() + "." + leg.leg.legId().value())
                            .getBytes(StandardCharsets.UTF_8));
            if (claims.containsKey(claimId)) {
                continue;
            }
            try {
                append(EventType.CLAIM_CREATED, root.rootId.value(), claimId, out -> {
                    writeUuid(out, root.rootId.value());
                    writeUuid(out, claimId);
                    writeBinding(out, leg.leg.binding());
                    out.writeLong(leg.leg.minorUnits());
                });
                claims.put(claimId, new ClaimRecord(claimId, root.rootId.value(),
                        leg.leg.binding(), leg.leg.minorUnits()));
                root.claimIds.add(new ClaimId(claimId));
            } catch (CoordinatorException ignored) {
                lifecycle = CoordinatorLifecycle.FROZEN;
            }
        }
    }

    private void recomputeLifecycle() {
        if (lifecycle == CoordinatorLifecycle.RECOVERING) {
            return;
        }
        if (roots.values().stream().anyMatch(root -> root.state == OperationState.FROZEN
                || root.state == OperationState.UNKNOWN)) {
            lifecycle = CoordinatorLifecycle.FROZEN;
        } else {
            lifecycle = CoordinatorLifecycle.READY;
        }
    }

    private void persistLifecycle(CoordinatorLifecycle next) throws CoordinatorException {
        if (!lifecycle.canTransitionTo(next)) {
            throw failure(CoordinatorError.LIFECYCLE_UNAVAILABLE,
                    "illegal coordinator lifecycle transition");
        }
        append(EventType.LIFECYCLE, new UUID(0L, 3L), new UUID(0L, 4L),
                out -> writeLifecycle(out, next));
        lifecycle = next;
    }

    private void recordDiagnostic(RouteContext route, UUID rootId, UUID legId,
                                         String operation, String desired, String actual,
                                         String reason) {
        DebugDiagnostics.record(DebugModule.ESCROW, operation,
                rootId.toString(), legId == null ? "none" : legId.toString(), route.actor(),
                route.binding().providerId(), lifecycle.name().toLowerCase(), desired, actual, reason,
                route.capabilityAvailable() ? "required" : "none", actual,
                actual.equals("resolved") ? "confirmed" : "pending",
                actual.equals("resolved") ? "released" : "held",
                "none", actual.equals("resolved") ? "none" : "recover or inspect receipt");
    }

    private void applyTransition(LegRecord leg, OperationState state, DispatchReceipt receipt)
            throws IOException {
        if (!leg.state.canTransitionTo(state) && leg.state != state) {
            throw new IOException("illegal leg state transition");
        }
        if (state == OperationState.CONFIRMED || state == OperationState.RESOLVED) {
            if (receipt == null || !receiptMatches(leg.leg, receipt)) {
                throw new IOException("confirmed leg receipt does not match request");
            }
            leg.receipt = receipt;
        }
        leg.state = state;
    }

    private RootRecord requireRoot(UUID rootId) throws IOException {
        RootRecord root = roots.get(rootId);
        if (root == null) {
            throw new IOException("coordinator event references unknown root");
        }
        return root;
    }

    private LegRecord requireLeg(RootRecord root, UUID legId) throws IOException {
        LegRecord leg = root.legs.get(legId);
        if (leg == null) {
            throw new IOException("coordinator event references unknown leg");
        }
        return leg;
    }

    private void ensureOpen() throws CoordinatorException {
        if (closed) {
            throw failure(CoordinatorError.LIFECYCLE_UNAVAILABLE, "coordinator is closed");
        }
    }

    private CoordinatorException failure(CoordinatorError error, String message) {
        return new CoordinatorException(error, message);
    }

    @FunctionalInterface
    private interface PayloadWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private void append(EventType type, UUID transactionId, UUID stepId, PayloadWriter writer)
            throws CoordinatorException {
        ensureOpen();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(PAYLOAD_MAGIC);
            out.writeShort(PAYLOAD_VERSION);
            out.writeByte(type.code);
            writer.write(out);
            out.flush();
            journal.append(transactionId, stepId, bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            lifecycle = CoordinatorLifecycle.FROZEN;
            throw new CoordinatorException(CoordinatorError.STORAGE_CORRUPT,
                    "durable coordinator append failed", exception);
        }
    }

    private static String fingerprint(RouteContext route, List<BoundLeg> legs,
                                      CustodyPlan custody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, route.routeId());
            updateUuid(digest, route.actor());
            updateBinding(digest, route.binding());
            digest.update((byte) (route.authorized() ? 1 : 0));
            digest.update((byte) (route.capabilityAvailable() ? 1 : 0));
            update(digest, custody.custodyId());
            updateLong(digest, custody.minorUnits());
            for (BoundLeg leg : legs) {
                updateUuid(digest, leg.rootId().value());
                updateUuid(digest, leg.legId().value());
                updateBinding(digest, leg.binding());
                update(digest, leg.operation());
                updateLong(digest, leg.minorUnits());
                update(digest, leg.payloadFingerprint());
                digest.update((byte) (leg.debit() ? 1 : 0));
                update(digest, leg.intendedPhase());
                updateLong(digest, leg.createdAtEpochMillis());
                digest.update((byte) (leg.parentRootId() == null ? 0 : 1));
                if (leg.parentRootId() != null) {
                    updateUuid(digest, leg.parentRootId().value());
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateLong(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateUuid(MessageDigest digest, UUID value) {
        updateLong(digest, value.getMostSignificantBits());
        updateLong(digest, value.getLeastSignificantBits());
    }

    private static void updateBinding(MessageDigest digest, BindingV1 binding) {
        update(digest, binding.providerId());
        updateLong(digest, binding.apiVersion());
        update(digest, binding.adapterId());
        update(digest, binding.protocol());
        update(digest, binding.backendClassFingerprint());
        update(digest, binding.artifactFingerprint());
        update(digest, binding.lineage());
        updateUuid(digest, binding.accountUuid());
        update(digest, binding.currencyId());
        updateLong(digest, binding.precision());
        update(digest, binding.durableManagerId());
        updateLong(digest, binding.generation());
        updateLong(digest, binding.receiptProtocol());
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte byteValue : bytes) {
            value.append(String.format("%02x", byteValue & 0xff));
        }
        return value.toString();
    }

    private static void writeRoot(DataOutputStream out, RouteContext route, RootId rootId,
                                  List<BoundLeg> legs, CustodyPlan custody, String fingerprint,
                                  long admittedAtEpochMillis)
            throws IOException {
        writeUuid(out, rootId.value());
        writeString(out, route.routeId());
        writeUuid(out, route.actor());
        out.writeBoolean(route.authorized());
        out.writeBoolean(route.capabilityAvailable());
        writeBinding(out, route.binding());
        writeString(out, custody.custodyId());
        out.writeLong(custody.minorUnits());
        writeString(out, fingerprint);
        out.writeLong(admittedAtEpochMillis);
        writeString(out, "phase-003");
        out.writeInt(legs.size());
        for (BoundLeg leg : legs) {
            writeUuid(out, leg.rootId().value());
            writeUuid(out, leg.legId().value());
            writeBinding(out, leg.binding());
            writeString(out, leg.operation());
            out.writeLong(leg.minorUnits());
            writeString(out, leg.payloadFingerprint());
            out.writeBoolean(leg.debit());
            writeString(out, leg.intendedPhase());
            out.writeLong(leg.createdAtEpochMillis());
            out.writeBoolean(leg.parentRootId() != null);
            if (leg.parentRootId() != null) {
                writeUuid(out, leg.parentRootId().value());
            }
        }
    }

    private record RootDecoded(RootRecord root) {
    }

    private RootDecoded readRoot(DataInputStream in) throws IOException {
        RootId rootId = new RootId(readUuid(in));
        String routeId = readString(in);
        UUID actor = readUuid(in);
        boolean authorized = in.readBoolean();
        boolean capabilityAvailable = in.readBoolean();
        BindingV1 routeBinding = readBinding(in);
        CustodyPlan custody = new CustodyPlan(readString(in), in.readLong());
        String fingerprint = readString(in);
        long admittedAtEpochMillis = in.readLong();
        String intendedPhase = readString(in);
        if (admittedAtEpochMillis <= 0L || !"phase-003".equals(intendedPhase)) {
            throw new IOException("root audit metadata is invalid");
        }
        int count = in.readInt();
        if (count <= 0 || count > MAX_LEGS) {
            throw new IOException("root leg count is outside bounds");
        }
        List<BoundLeg> legs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            RootId legRootId = new RootId(readUuid(in));
            LegId legId = new LegId(readUuid(in));
            BindingV1 binding = readBinding(in);
            String operation = readString(in);
            long amount = in.readLong();
            String payload = readString(in);
            boolean debit = in.readBoolean();
            String legIntendedPhase = readString(in);
            long createdAt = in.readLong();
            RootId parent = in.readBoolean() ? new RootId(readUuid(in)) : null;
            legs.add(new BoundLeg(legRootId, legId, binding, operation, amount, payload, debit,
                    legIntendedPhase, createdAt, parent));
        }
        RouteContext route = new RouteContext(routeId, actor, routeBinding, authorized,
                capabilityAvailable);
        RootRecord root = new RootRecord(rootId, route, legs, custody, fingerprint,
                admittedAtEpochMillis);
        for (BoundLeg leg : legs) {
            if (!leg.rootId().equals(rootId)) {
                throw new IOException("root admission contains a leg for another root");
            }
        }
        if (!fingerprint.equals(fingerprint(route, legs, custody))) {
            throw new IOException("root admission fingerprint does not match payload");
        }
        return new RootDecoded(root);
    }

    private static void writeBinding(DataOutputStream out, BindingV1 binding) throws IOException {
        writeString(out, binding.providerId());
        out.writeInt(binding.apiVersion());
        writeString(out, binding.adapterId());
        writeString(out, binding.protocol());
        writeString(out, binding.backendClassFingerprint());
        writeString(out, binding.artifactFingerprint());
        writeString(out, binding.lineage());
        writeUuid(out, binding.accountUuid());
        writeString(out, binding.currencyId());
        out.writeInt(binding.precision());
        writeString(out, binding.durableManagerId());
        out.writeLong(binding.generation());
        out.writeInt(binding.receiptProtocol());
    }

    private static BindingV1 readBinding(DataInputStream in) throws IOException {
        try {
            return new BindingV1(readString(in), in.readInt(), readString(in), readString(in),
                    readString(in), readString(in), readString(in), readUuid(in), readString(in),
                    in.readInt(), readString(in), in.readLong(), in.readInt());
        } catch (RuntimeException exception) {
            throw new IOException("binding record is invalid", exception);
        }
    }

    private static void writeOptionalReceipt(DataOutputStream out, DispatchReceipt receipt)
            throws IOException {
        out.writeBoolean(receipt != null);
        if (receipt != null) {
            writeString(out, receipt.externalOperationId());
            out.writeLong(receipt.minorUnits());
            writeString(out, receipt.payloadFingerprint());
        }
    }

    private static DispatchReceipt readOptionalReceipt(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        try {
            return new DispatchReceipt(readString(in), in.readLong(), readString(in));
        } catch (RuntimeException exception) {
            throw new IOException("receipt record is invalid", exception);
        }
    }

    private static boolean receiptMatches(BoundLeg leg, DispatchReceipt receipt) {
        return receipt.minorUnits() == leg.minorUnits()
                && receipt.payloadFingerprint().equalsIgnoreCase(leg.payloadFingerprint());
    }

    private static void writeState(DataOutputStream out, OperationState state) throws IOException {
        out.writeByte(state.ordinal());
    }

    private static OperationState readState(DataInputStream in) throws IOException {
        int ordinal = in.readUnsignedByte();
        OperationState[] values = OperationState.values();
        if (ordinal >= values.length) {
            throw new IOException("unknown operation state");
        }
        return values[ordinal];
    }

    private static void writeLifecycle(DataOutputStream out, CoordinatorLifecycle lifecycle)
            throws IOException {
        out.writeByte(lifecycle.ordinal());
    }

    private static CoordinatorLifecycle readLifecycle(DataInputStream in) throws IOException {
        int ordinal = in.readUnsignedByte();
        CoordinatorLifecycle[] values = CoordinatorLifecycle.values();
        if (ordinal >= values.length) {
            throw new IOException("unknown coordinator lifecycle");
        }
        return values[ordinal];
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        try {
            return new UUID(in.readLong(), in.readLong());
        } catch (EOFException exception) {
            throw new IOException("uuid record is truncated", exception);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("coordinator string exceeds bound");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("coordinator string length is outside bounds");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("coordinator string is truncated");
        }
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IOException("coordinator string contains a line break");
        }
        return value;
    }
}
