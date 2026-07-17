package com.enviouse.futureshops.server.escrow.playershop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerShopReplayRegistry {
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public synchronized ApplyResult applyIntent(PlayerShopEscrowIntent intent) {
        Objects.requireNonNull(intent, "intent");
        Entry existing = entries.get(intent.requestId());
        if (existing == null) {
            entries.put(intent.requestId(), new Entry(intent, null));
            return new ApplyResult(ApplyStatus.ADDED,
                    entries.get(intent.requestId()));
        }
        if (!existing.intent().intentFingerprint().equals(
                intent.intentFingerprint())) {
            return new ApplyResult(ApplyStatus.CONFLICT, existing);
        }
        if (existing.commit() != null) {
            return new ApplyResult(ApplyStatus.TERMINAL_REPLAY, existing);
        }
        if (existing.intent().status() == intent.status()
                && existing.intent().revision() == intent.revision()) {
            return new ApplyResult(ApplyStatus.IDEMPOTENT_REPLAY, existing);
        }
        if (existing.intent().status()
                == PlayerShopEscrowIntent.Status.PREPARED
                && intent.status() != PlayerShopEscrowIntent.Status.PREPARED) {
            Entry updated = new Entry(intent, null);
            entries.put(intent.requestId(), updated);
            return new ApplyResult(ApplyStatus.UPDATED, updated);
        }
        return new ApplyResult(ApplyStatus.TERMINAL_REPLAY, existing);
    }

    public synchronized ApplyResult applyCommit(PlayerShopAtomicCommit commit) {
        Objects.requireNonNull(commit, "commit");
        Entry existing = entries.get(commit.commitId());
        if (existing == null) {
            return new ApplyResult(ApplyStatus.MISSING_INTENT, null);
        }
        if (!existing.intent().intentFingerprint().equals(
                commit.committedIntent().intentFingerprint())) {
            return new ApplyResult(ApplyStatus.CONFLICT, existing);
        }
        if (existing.commit() != null) {
            return new ApplyResult(existing.commit().commitFingerprint().equals(
                    commit.commitFingerprint())
                    ? ApplyStatus.IDEMPOTENT_REPLAY
                    : ApplyStatus.CONFLICT, existing);
        }
        if (existing.intent().status()
                != PlayerShopEscrowIntent.Status.PREPARED
                && existing.intent().status()
                != PlayerShopEscrowIntent.Status.COMMITTED) {
            return new ApplyResult(ApplyStatus.TERMINAL_REPLAY, existing);
        }
        Entry updated = new Entry(commit.committedIntent(), commit);
        entries.put(commit.commitId(), updated);
        return new ApplyResult(ApplyStatus.UPDATED, updated);
    }

    public synchronized Optional<Entry> find(UUID requestId) {
        return Optional.ofNullable(entries.get(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized Map<UUID, Entry> snapshot() {
        return Map.copyOf(entries);
    }

    public record Entry(
            PlayerShopEscrowIntent intent,
            PlayerShopAtomicCommit commit
    ) {
        public Entry {
            intent = Objects.requireNonNull(intent, "intent");
            if (commit != null && (!commit.commitId().equals(intent.requestId())
                    || !commit.committedIntent().equals(intent))) {
                throw new IllegalArgumentException("Player shop replay entry is invalid");
            }
        }

        public Optional<PlayerShopAtomicCommit> committedValue() {
            return Optional.ofNullable(commit);
        }
    }

    public record ApplyResult(ApplyStatus status, Entry stored) {
        public ApplyResult {
            status = Objects.requireNonNull(status, "status");
            if (stored == null && status != ApplyStatus.MISSING_INTENT) {
                throw new IllegalArgumentException("Player shop replay result is invalid");
            }
        }

        public Optional<Entry> storedValue() {
            return Optional.ofNullable(stored);
        }
    }

    public enum ApplyStatus {
        ADDED,
        UPDATED,
        IDEMPOTENT_REPLAY,
        TERMINAL_REPLAY,
        CONFLICT,
        MISSING_INTENT
    }
}
