package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryTerminalTombstone {
    private final UUID compactionCommandId;
    private final UUID checkpointId;
    private final ItemInventoryMutationToken token;
    private final ItemInventoryJournalStatus status;
    private final byte[] terminalDigest;
    private final Instant terminalAt;

    public ItemInventoryTerminalTombstone(
            UUID compactionCommandId,
            UUID checkpointId,
            ItemInventoryMutationToken token,
            ItemInventoryJournalStatus status,
            byte[] terminalDigest,
            Instant terminalAt
    ) {
        this.compactionCommandId = requireUuid(
                compactionCommandId, "compactionCommandId");
        this.checkpointId = requireUuid(checkpointId, "checkpointId");
        this.token = Objects.requireNonNull(token, "token");
        this.status = Objects.requireNonNull(status, "status");
        this.terminalDigest = Objects.requireNonNull(
                terminalDigest, "terminalDigest").clone();
        this.terminalAt = Objects.requireNonNull(terminalAt, "terminalAt");
        if (this.terminalDigest.length != 32
                || status != ItemInventoryJournalStatus.COMMITTED
                && status != ItemInventoryJournalStatus.ABORTED) {
            throw new IllegalArgumentException(
                    "Item inventory terminal tombstone is invalid");
        }
    }

    public static ItemInventoryTerminalTombstone fromEntry(
            ItemInventoryJournalEntry entry,
            UUID compactionCommandId,
            UUID checkpointId
    ) {
        ItemInventoryJournalEntry value = Objects.requireNonNull(
                entry, "entry");
        return switch (value.status()) {
            case COMMITTED -> new ItemInventoryTerminalTombstone(
                    compactionCommandId, checkpointId,
                    value.intent().token(), value.status(),
                    value.committedReceipt().orElseThrow().digest(),
                    value.committedReceipt().orElseThrow().appliedAt());
            case ABORTED -> new ItemInventoryTerminalTombstone(
                    compactionCommandId, checkpointId,
                    value.intent().token(), value.status(),
                    sha256(ItemInventoryMutationAbortCodec.encode(
                            value.abort().orElseThrow())),
                    value.abort().orElseThrow().abortedAt());
            default -> throw new IllegalArgumentException(
                    "Item inventory entry is not compactable");
        };
    }

    public boolean matchesEntry(ItemInventoryJournalEntry entry) {
        if (entry == null || entry.status() != status
                || !entry.intent().token().equals(token)) {
            return false;
        }
        return switch (status) {
            case COMMITTED -> terminalAt.equals(entry.committedReceipt()
                    .orElseThrow().appliedAt())
                    && MessageDigest.isEqual(terminalDigest,
                    entry.committedReceipt().orElseThrow().digest());
            case ABORTED -> terminalAt.equals(entry.abort().orElseThrow()
                    .abortedAt())
                    && MessageDigest.isEqual(terminalDigest,
                    sha256(ItemInventoryMutationAbortCodec.encode(
                            entry.abort().orElseThrow())));
            default -> false;
        };
    }

    public UUID compactionCommandId() {
        return compactionCommandId;
    }

    public UUID checkpointId() {
        return checkpointId;
    }

    public ItemInventoryMutationToken token() {
        return token;
    }

    public UUID requestId() {
        return token.requestId();
    }

    public ItemInventoryJournalStatus status() {
        return status;
    }

    public byte[] terminalDigest() {
        return terminalDigest.clone();
    }

    public Instant terminalAt() {
        return terminalAt;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryTerminalTombstone other
                && compactionCommandId.equals(other.compactionCommandId)
                && checkpointId.equals(other.checkpointId)
                && token.equals(other.token)
                && status == other.status
                && Arrays.equals(terminalDigest, other.terminalDigest)
                && terminalAt.equals(other.terminalAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(compactionCommandId, checkpointId, token,
                status, terminalAt);
        return 31 * result + Arrays.hashCode(terminalDigest);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be zero");
        }
        return result;
    }
}
