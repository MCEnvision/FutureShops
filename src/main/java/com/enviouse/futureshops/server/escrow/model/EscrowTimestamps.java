package com.enviouse.futureshops.server.escrow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EscrowTimestamps(
        Instant createdAt,
        Instant updatedAt,
        Optional<Instant> commitDecidedAt,
        Optional<Instant> terminalAt
) {
    public EscrowTimestamps {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(commitDecidedAt, "commitDecidedAt");
        Objects.requireNonNull(terminalAt, "terminalAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Escrow update time precedes creation time");
        }
        commitDecidedAt.ifPresent(value -> requireBetween(value, createdAt, updatedAt, "commit decision"));
        terminalAt.ifPresent(value -> requireBetween(value, createdAt, updatedAt, "terminal"));
    }

    public static EscrowTimestamps createdAt(Instant createdAt) {
        return new EscrowTimestamps(createdAt, createdAt, Optional.empty(), Optional.empty());
    }

    public EscrowTimestamps advance(EscrowState targetState, Instant at) {
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(at, "at");
        if (at.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Escrow transition time precedes current update time");
        }
        Optional<Instant> nextCommitDecision = commitDecidedAt;
        if (targetState == EscrowState.COMMIT_DECIDED && nextCommitDecision.isEmpty()) {
            nextCommitDecision = Optional.of(at);
        }
        Optional<Instant> nextTerminal = terminalAt;
        if (targetState.isTerminal()) {
            nextTerminal = Optional.of(at);
        }
        return new EscrowTimestamps(createdAt, at, nextCommitDecision, nextTerminal);
    }

    public EscrowTimestamps touch(Instant at) {
        Objects.requireNonNull(at, "at");
        if (at.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Escrow update time precedes current update time");
        }
        return new EscrowTimestamps(createdAt, at, commitDecidedAt, terminalAt);
    }

    private static void requireBetween(Instant value, Instant start, Instant end, String field) {
        if (value.isBefore(start) || value.isAfter(end)) {
            throw new IllegalArgumentException("Escrow " + field + " time is outside its transaction range");
        }
    }
}
