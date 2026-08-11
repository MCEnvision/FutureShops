package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ItemInventoryJournalTransition(
        ItemInventoryJournalTransitionType type,
        Optional<ItemInventoryMutationIntent> intent,
        Optional<ItemInventoryMutationReceipt> receipt,
        Optional<ItemInventoryMutationAbort> abort,
        Optional<ItemInventoryMutationQuarantine> quarantine
) {
    public ItemInventoryJournalTransition {
        Objects.requireNonNull(type, "type");
        intent = Objects.requireNonNull(intent, "intent");
        receipt = Objects.requireNonNull(receipt, "receipt");
        abort = Objects.requireNonNull(abort, "abort");
        quarantine = Objects.requireNonNull(quarantine, "quarantine");
        int present = (intent.isPresent() ? 1 : 0)
                + (receipt.isPresent() ? 1 : 0)
                + (abort.isPresent() ? 1 : 0)
                + (quarantine.isPresent() ? 1 : 0);
        if (present != 1
                || type == ItemInventoryJournalTransitionType.PREPARE
                && intent.isEmpty()
                || type == ItemInventoryJournalTransitionType.COMMIT
                && receipt.isEmpty()
                || type == ItemInventoryJournalTransitionType.ABORT
                && abort.isEmpty()
                || type == ItemInventoryJournalTransitionType.QUARANTINE
                && quarantine.isEmpty()) {
            throw new IllegalArgumentException(
                    "Item inventory journal transition is invalid");
        }
    }

    public static ItemInventoryJournalTransition prepare(
            ItemInventoryMutationIntent intent
    ) {
        return new ItemInventoryJournalTransition(
                ItemInventoryJournalTransitionType.PREPARE,
                Optional.of(Objects.requireNonNull(intent, "intent")),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static ItemInventoryJournalTransition commit(
            ItemInventoryMutationReceipt receipt
    ) {
        return new ItemInventoryJournalTransition(
                ItemInventoryJournalTransitionType.COMMIT,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(receipt, "receipt")),
                Optional.empty(), Optional.empty());
    }

    public static ItemInventoryJournalTransition abort(
            ItemInventoryMutationAbort abort
    ) {
        return new ItemInventoryJournalTransition(
                ItemInventoryJournalTransitionType.ABORT,
                Optional.empty(), Optional.empty(),
                Optional.of(Objects.requireNonNull(abort, "abort")),
                Optional.empty());
    }

    public static ItemInventoryJournalTransition quarantine(
            ItemInventoryMutationQuarantine quarantine
    ) {
        return new ItemInventoryJournalTransition(
                ItemInventoryJournalTransitionType.QUARANTINE,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(Objects.requireNonNull(
                        quarantine, "quarantine")));
    }

    public ItemInventoryMutationToken token() {
        return switch (type) {
            case PREPARE -> intent.orElseThrow().token();
            case COMMIT -> receipt.orElseThrow().token();
            case ABORT -> abort.orElseThrow().token();
            case QUARANTINE -> quarantine.orElseThrow().token();
        };
    }

    public UUID requestId() {
        return token().requestId();
    }

    public UUID playerId() {
        return token().playerId();
    }
}
