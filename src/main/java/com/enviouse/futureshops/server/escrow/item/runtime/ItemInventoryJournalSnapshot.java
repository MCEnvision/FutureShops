package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ItemInventoryJournalSnapshot(
        long revision,
        List<ItemInventoryJournalEntry> entries,
        List<ItemInventoryQuarantineAdministration> administrations,
        List<ItemInventoryTerminalTombstone> tombstones
) {
    public ItemInventoryJournalSnapshot(
            long revision,
            List<ItemInventoryJournalEntry> entries
    ) {
        this(revision, entries, List.of(), List.of());
    }

    public ItemInventoryJournalSnapshot(
            long revision,
            List<ItemInventoryJournalEntry> entries,
            List<ItemInventoryQuarantineAdministration> administrations
    ) {
        this(revision, entries, administrations, List.of());
    }

    public ItemInventoryJournalSnapshot {
        if (revision < 0L) {
            throw new IllegalArgumentException(
                    "Item inventory journal revision is invalid");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        administrations = List.copyOf(Objects.requireNonNull(
                administrations, "administrations"));
        tombstones = List.copyOf(Objects.requireNonNull(
                tombstones, "tombstones"));
        if (entries.size() > PersistentItemInventoryJournal.MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot has too many entries");
        }
        if (tombstones.size()
                > PersistentItemInventoryJournal.MAX_TOMBSTONES) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot has too many tombstones");
        }
        Set<UUID> requestIds = new HashSet<>();
        for (ItemInventoryJournalEntry entry : entries) {
            ItemInventoryJournalEntry value = Objects.requireNonNull(
                    entry, "entry");
            if (!requestIds.add(value.intent().token().requestId())) {
                throw new IllegalArgumentException(
                        "Item inventory journal snapshot repeats a request");
            }
        }
        Set<UUID> commandIds = new HashSet<>();
        for (ItemInventoryQuarantineAdministration administration
                : administrations) {
            ItemInventoryQuarantineAdministration value =
                    Objects.requireNonNull(administration,
                            "administration");
            if (!commandIds.add(value.commandId())
                    || !requestIds.contains(value.requestId())) {
                throw new IllegalArgumentException(
                        "Item inventory journal administration is invalid");
            }
        }
        for (ItemInventoryTerminalTombstone tombstone : tombstones) {
            ItemInventoryTerminalTombstone value = Objects.requireNonNull(
                    tombstone, "tombstone");
            if (!requestIds.add(value.requestId())) {
                throw new IllegalArgumentException(
                        "Item inventory journal tombstone repeats a request");
            }
        }
        if (revision < Math.addExact(entries.size(), tombstones.size())) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot revision is inconsistent");
        }
    }

    public static ItemInventoryJournalSnapshot empty() {
        return new ItemInventoryJournalSnapshot(0L, List.of(), List.of(),
                List.of());
    }
}
