package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;

import java.util.Objects;
import java.util.Optional;

public record ItemInventoryJournalEntry(
        ItemInventoryMutationIntent intent,
        ItemInventoryJournalStatus status,
        Optional<ItemInventoryMutationReceipt> committedReceipt,
        Optional<ItemInventoryMutationAbort> abort,
        Optional<ItemInventoryMutationQuarantine> quarantine
) {
    public ItemInventoryJournalEntry {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(status, "status");
        committedReceipt = Objects.requireNonNull(
                committedReceipt, "committedReceipt");
        abort = Objects.requireNonNull(abort, "abort");
        quarantine = Objects.requireNonNull(quarantine, "quarantine");
        if (status == ItemInventoryJournalStatus.PREPARED
                && (committedReceipt.isPresent() || abort.isPresent()
                || quarantine.isPresent())
                || status == ItemInventoryJournalStatus.COMMITTED
                && (committedReceipt.isEmpty() || abort.isPresent()
                || quarantine.isPresent())
                || status == ItemInventoryJournalStatus.ABORTED
                && (committedReceipt.isPresent() || abort.isEmpty()
                || quarantine.isPresent())
                || status == ItemInventoryJournalStatus.QUARANTINED
                && (abort.isPresent() || quarantine.isEmpty())) {
            throw new IllegalArgumentException(
                    "Item inventory journal entry state is invalid");
        }
        committedReceipt.ifPresent(receipt -> {
            if (!receipt.token().equals(intent.token())) {
                throw new IllegalArgumentException(
                        "Item inventory committed receipt conflicts");
            }
        });
        abort.ifPresent(value -> {
            if (!value.token().equals(intent.token())) {
                throw new IllegalArgumentException(
                        "Item inventory abort conflicts");
            }
        });
        quarantine.ifPresent(value -> {
            if (!value.token().equals(intent.token())) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine conflicts");
            }
        });
    }

    public static ItemInventoryJournalEntry prepared(
            ItemInventoryMutationIntent intent
    ) {
        return new ItemInventoryJournalEntry(intent,
                ItemInventoryJournalStatus.PREPARED, Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    public static ItemInventoryJournalEntry committed(
            ItemInventoryMutationIntent intent,
            ItemInventoryMutationReceipt receipt
    ) {
        return new ItemInventoryJournalEntry(intent,
                ItemInventoryJournalStatus.COMMITTED,
                Optional.of(receipt), Optional.empty(), Optional.empty());
    }

    public static ItemInventoryJournalEntry aborted(
            ItemInventoryMutationIntent intent,
            ItemInventoryMutationAbort abort
    ) {
        return new ItemInventoryJournalEntry(intent,
                ItemInventoryJournalStatus.ABORTED, Optional.empty(),
                Optional.of(abort), Optional.empty());
    }

    public static ItemInventoryJournalEntry quarantined(
            ItemInventoryJournalEntry existing,
            ItemInventoryMutationQuarantine quarantine
    ) {
        ItemInventoryJournalEntry value = Objects.requireNonNull(
                existing, "existing");
        if (value.status() == ItemInventoryJournalStatus.ABORTED
                || value.status()
                == ItemInventoryJournalStatus.QUARANTINED) {
            throw new IllegalArgumentException(
                    "Item inventory entry cannot be quarantined");
        }
        return new ItemInventoryJournalEntry(value.intent(),
                ItemInventoryJournalStatus.QUARANTINED,
                value.committedReceipt(), Optional.empty(),
                Optional.of(quarantine));
    }
}
