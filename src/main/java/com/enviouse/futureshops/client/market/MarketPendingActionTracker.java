package com.enviouse.futureshops.client.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-side ledger of in-flight market mutations (plan §12: every mutation carries a request
 * UUID). Each pressed action registers here so its button renders a busy state and ignores
 * repeat clicks until the matching {@code S2CMarketActionResponsePacket} arrives, and — after
 * the timeout — so the surface offers an explicit retry of the SAME request instead of minting
 * a new one. A timed-out entry is deliberately NOT forgotten: the original request may still be
 * executing server-side, and a fresh request UUID for the same intent would be a second,
 * economically distinct mutation (server replay protection is per-request and cannot collapse
 * two different UUIDs). Only {@link #retry} (resend, same UUID — the server replays the stored
 * result) or an explicit {@link #abandon} (give up + refresh) resolves it besides the response.
 */
public final class MarketPendingActionTracker {
    public static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;
    private static final int MAXIMUM_PENDING = 16;

    /** Lifecycle of a tracked mutation. */
    public enum State {
        /** Sent (or resent) and awaiting its response. */
        IN_FLIGHT,
        /** No response within the timeout — retry (same UUID) or abandon explicitly. */
        TIMED_OUT
    }

    /**
     * One tracked mutation. {@code actionKey} is the client action family (for status
     * localization, e.g. {@code auction_bid}); {@code subjectId} the acted-on entity
     * (listing/order/product identity, or empty for create); {@code resend} replays the
     * ORIGINAL packet — same request UUID — for the timed-out retry path.
     */
    public record PendingAction(
            UUID requestId,
            String actionKey,
            String subjectId,
            long sentAtMillis,
            State state,
            Runnable resend
    ) {
        public PendingAction {
            Objects.requireNonNull(requestId, "requestId");
            actionKey = Objects.requireNonNull(actionKey, "actionKey");
            subjectId = Objects.requireNonNull(subjectId, "subjectId");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(resend, "resend");
            if (actionKey.isEmpty()) {
                throw new IllegalArgumentException(
                        "Pending market action key is required");
            }
            if (sentAtMillis < 0L) {
                throw new IllegalArgumentException(
                        "Pending market action timestamp is invalid");
            }
        }

        private PendingAction with(State nextState, long atMillis) {
            return new PendingAction(requestId, actionKey, subjectId,
                    atMillis, nextState, resend);
        }
    }

    private final Map<UUID, PendingAction> pending = new LinkedHashMap<>();

    /**
     * Registers a freshly sent request. Returns false (and registers nothing) when the
     * in-flight budget is exhausted — the caller should refuse to send rather than lose
     * track of a request.
     */
    public synchronized boolean begin(
            UUID requestId,
            String actionKey,
            String subjectId,
            long nowMillis,
            Runnable resend
    ) {
        if (pending.size() >= MAXIMUM_PENDING
                || pending.containsKey(
                Objects.requireNonNull(requestId, "requestId"))) {
            return false;
        }
        pending.put(requestId, new PendingAction(requestId, actionKey,
                subjectId, nowMillis, State.IN_FLIGHT, resend));
        return true;
    }

    /**
     * Clears and returns the pending entry for a response, if this tracker knows it. A
     * response resolves TIMED_OUT entries too — the retried (or slow original) request
     * finally answered.
     */
    public synchronized Optional<PendingAction> complete(UUID requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.remove(requestId));
    }

    /**
     * True when an action of this family against this subject is already tracked — in flight
     * OR timed out. A timed-out entry still blocks fresh sends: a new request UUID for the
     * same intent could double-execute against the still-running original.
     */
    public synchronized boolean busy(String actionKey, String subjectId) {
        for (PendingAction action : pending.values()) {
            if (action.actionKey().equals(actionKey)
                    && action.subjectId().equals(subjectId)) {
                return true;
            }
        }
        return false;
    }

    /** The timed-out entry for this family+subject, if one is awaiting retry/abandon. */
    public synchronized Optional<PendingAction> timedOut(
            String actionKey,
            String subjectId
    ) {
        for (PendingAction action : pending.values()) {
            if (action.state() == State.TIMED_OUT
                    && action.actionKey().equals(actionKey)
                    && action.subjectId().equals(subjectId)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean anyPending() {
        return !pending.isEmpty();
    }

    /**
     * Transitions every IN_FLIGHT request older than {@code timeoutMillis} to TIMED_OUT and
     * returns the newly transitioned entries. Nothing is removed here — the entry keeps
     * blocking fresh sends until the response arrives, {@link #retry} succeeds+answers, or
     * the user explicitly {@link #abandon}s it.
     */
    public synchronized List<PendingAction> expire(
            long nowMillis,
            long timeoutMillis
    ) {
        List<PendingAction> expired = new ArrayList<>();
        for (Map.Entry<UUID, PendingAction> entry : pending.entrySet()) {
            PendingAction action = entry.getValue();
            if (action.state() == State.IN_FLIGHT
                    && nowMillis - action.sentAtMillis()
                    >= timeoutMillis) {
                PendingAction timedOut = action.with(State.TIMED_OUT,
                        action.sentAtMillis());
                entry.setValue(timedOut);
                expired.add(timedOut);
            }
        }
        return expired;
    }

    /**
     * Re-arms a TIMED_OUT entry as IN_FLIGHT (fresh timeout window) and hands back its
     * resend runnable — the SAME request UUID goes over the wire again, so the server either
     * processes it once or replays the stored result. Empty when the request is unknown or
     * not timed out (e.g. the response landed between render and click).
     */
    public synchronized Optional<Runnable> retry(
            UUID requestId,
            long nowMillis
    ) {
        PendingAction action = requestId == null ? null
                : pending.get(requestId);
        if (action == null || action.state() != State.TIMED_OUT) {
            return Optional.empty();
        }
        pending.put(requestId, action.with(State.IN_FLIGHT, nowMillis));
        return Optional.of(action.resend());
    }

    /**
     * Explicit give-up: forgets the entry so a fresh request becomes possible again. Callers
     * must pair this with a page refresh — the abandoned request may have been applied
     * server-side, and only fresh state tells the user what actually happened.
     */
    public synchronized boolean abandon(UUID requestId) {
        return requestId != null && pending.remove(requestId) != null;
    }

    public synchronized void clear() {
        pending.clear();
    }
}
