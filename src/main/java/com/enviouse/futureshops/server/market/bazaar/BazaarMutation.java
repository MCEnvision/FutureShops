package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarMutation(
        UUID mutationId,
        String previousSnapshotFingerprint,
        String nextSnapshotFingerprint,
        Optional<BazaarRequestReceipt> requestReceipt,
        Optional<BazaarLifecycleCommand> lifecycleCommand
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public BazaarMutation {
        mutationId = Objects.requireNonNull(mutationId, "mutationId");
        previousSnapshotFingerprint = requireFingerprint(
                previousSnapshotFingerprint);
        nextSnapshotFingerprint = requireFingerprint(nextSnapshotFingerprint);
        requestReceipt = Objects.requireNonNull(requestReceipt,
                "requestReceipt");
        lifecycleCommand = Objects.requireNonNull(lifecycleCommand,
                "lifecycleCommand");
        if (ZERO.equals(mutationId)
                || requestReceipt.isPresent()
                == lifecycleCommand.isPresent()
                || requestReceipt.isPresent()
                && !mutationId.equals(requestReceipt.orElseThrow()
                .requestId())
                || lifecycleCommand.isPresent()
                && !mutationId.equals(lifecycleCommand.orElseThrow()
                .mutationId())) {
            throw new IllegalArgumentException(
                    "Bazaar mutation identity is invalid");
        }
    }

    public static BazaarMutation between(
            BazaarOrderBookSnapshot previous,
            BazaarOrderBookSnapshot next,
            UUID requestId
    ) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(requestId, "requestId");
        if (previous.receipts().containsKey(requestId)) {
            throw new IllegalArgumentException(
                    "Bazaar mutation request is already committed");
        }
        BazaarRequestReceipt receipt = next.receipts().get(requestId);
        if (receipt == null) {
            throw new IllegalArgumentException(
                    "Bazaar mutation receipt is missing");
        }
        BazaarMutation mutation = new BazaarMutation(requestId,
                BazaarOrderBookSnapshotCodec.fingerprint(previous),
                BazaarOrderBookSnapshotCodec.fingerprint(next),
                Optional.of(receipt), Optional.empty());
        requireTransition(mutation, previous, next);
        return mutation;
    }

    public static BazaarMutation lifecycle(
            BazaarOrderBookSnapshot previous,
            BazaarLifecycleCommand command
    ) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(command, "command");
        if (previous.lifecycleReceipts().containsKey(command.mutationId())) {
            throw new IllegalArgumentException(
                    "Bazaar lifecycle mutation is already committed");
        }
        BazaarOrderBook book = BazaarOrderBook.restore(previous);
        command.apply(book);
        book.recordLifecycleReceipt(command.mutationId(),
                BazaarOrderBookSnapshotCodec.lifecycleFingerprint(command));
        return between(previous, book.snapshot(), command);
    }

    public static BazaarMutation between(
            BazaarOrderBookSnapshot previous,
            BazaarOrderBookSnapshot next,
            BazaarLifecycleCommand command
    ) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(command, "command");
        UUID mutationId = command.mutationId();
        if (previous.lifecycleReceipts().containsKey(mutationId)) {
            throw new IllegalArgumentException(
                    "Bazaar lifecycle mutation is already committed");
        }
        String commandFingerprint =
                BazaarOrderBookSnapshotCodec.lifecycleFingerprint(command);
        if (!commandFingerprint.equals(
                next.lifecycleReceipts().get(mutationId))) {
            throw new IllegalArgumentException(
                    "Bazaar lifecycle receipt is missing");
        }
        BazaarMutation mutation = new BazaarMutation(mutationId,
                BazaarOrderBookSnapshotCodec.fingerprint(previous),
                BazaarOrderBookSnapshotCodec.fingerprint(next),
                Optional.empty(), Optional.of(command));
        requireTransition(mutation, previous, next);
        return mutation;
    }

    public ApplyResult apply(BazaarOrderBookSnapshot previous) {
        Objects.requireNonNull(previous, "previous");
        if (requestReceipt.isPresent()) {
            BazaarRequestReceipt receipt = requestReceipt.orElseThrow();
            BazaarRequestReceipt existing = previous.receipts().get(
                    mutationId);
            if (existing != null) {
                if (!existing.equals(receipt)) {
                    throw new IllegalArgumentException(
                            "Bazaar mutation replay conflicts");
                }
                return new ApplyResult(previous, true);
            }
        } else {
            BazaarLifecycleCommand command = lifecycleCommand.orElseThrow();
            String commandFingerprint =
                    BazaarOrderBookSnapshotCodec.lifecycleFingerprint(command);
            String existing = previous.lifecycleReceipts().get(mutationId);
            if (existing != null) {
                if (!existing.equals(commandFingerprint)) {
                    throw new IllegalArgumentException(
                            "Bazaar lifecycle replay conflicts");
                }
                return new ApplyResult(previous, true);
            }
        }
        if (!BazaarOrderBookSnapshotCodec.fingerprint(previous).equals(
                previousSnapshotFingerprint)) {
            throw new IllegalArgumentException(
                    "Bazaar mutation snapshot changed");
        }
        BazaarOrderBook book = BazaarOrderBook.restore(previous);
        if (requestReceipt.isPresent()) {
            applyReceipt(book, requestReceipt.orElseThrow());
        } else {
            BazaarLifecycleCommand command = lifecycleCommand.orElseThrow();
            command.apply(book);
            book.recordLifecycleReceipt(mutationId,
                    BazaarOrderBookSnapshotCodec.lifecycleFingerprint(
                            command));
        }
        BazaarOrderBookSnapshot next = book.snapshot();
        if (!BazaarOrderBookSnapshotCodec.fingerprint(next).equals(
                nextSnapshotFingerprint)) {
            throw new IllegalArgumentException(
                    "Bazaar mutation transition changed");
        }
        return new ApplyResult(next, false);
    }

    private static void applyReceipt(BazaarOrderBook book,
                                     BazaarRequestReceipt receipt) {
        BazaarOperationResult result = switch (receipt.result().operation()) {
            case CREATE -> book.create(receipt.createCommand().orElseThrow());
            case CANCEL -> book.cancel(receipt.cancelCommand().orElseThrow());
            case EXPIRE -> book.expire(receipt.expireCommand().orElseThrow());
        };
        if (!result.equals(receipt.result())
                || !book.receipt(receipt.requestId()).filter(
                receipt::equals).isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar mutation command evidence is invalid");
        }
    }

    private static void requireTransition(BazaarMutation mutation,
                                          BazaarOrderBookSnapshot previous,
                                          BazaarOrderBookSnapshot next) {
        ApplyResult applied = mutation.apply(previous);
        if (applied.replayed() || !applied.snapshot().equals(next)) {
            throw new IllegalArgumentException(
                    "Bazaar mutation is not a single canonical transition");
        }
    }

    private static String requireFingerprint(String value) {
        String fingerprint = Objects.requireNonNull(value, "value");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Bazaar mutation fingerprint is invalid");
        }
        return fingerprint;
    }

    public record ApplyResult(BazaarOrderBookSnapshot snapshot,
                              boolean replayed) {
        public ApplyResult {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
