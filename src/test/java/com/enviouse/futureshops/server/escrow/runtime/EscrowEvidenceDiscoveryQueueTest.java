package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowEvidenceDiscoveryQueueTest {
    @Test
    void protectedCorruptFirstPreservesValidOrphanBehindIt() {
        ArrayDeque<DiscoveryEntry> pending = entries(
                "protected corrupt", "protected valid");
        List<String> recovered = new ArrayList<>();

        EscrowEvidenceDiscoveryQueue.StepResult corrupt =
                EscrowEvidenceDiscoveryQueue.processOne(
                        pending, entry -> recover(entry, recovered));
        EscrowEvidenceDiscoveryQueue.StepResult valid =
                EscrowEvidenceDiscoveryQueue.processOne(
                        pending, entry -> recover(entry, recovered));

        assertEquals(1, corrupt.examined());
        assertFalse(corrupt.complete());
        assertTrue(corrupt.failure().isPresent());
        assertEquals(1, valid.examined());
        assertTrue(valid.complete());
        assertTrue(valid.failure().isEmpty());
        assertEquals(List.of("protected valid"), recovered);
    }

    @Test
    void foreignCorruptFirstPreservesValidOrphanBehindIt() {
        ArrayDeque<DiscoveryEntry> pending = entries(
                "foreign corrupt", "foreign valid");
        List<String> recovered = new ArrayList<>();

        EscrowEvidenceDiscoveryQueue.StepResult corrupt =
                EscrowEvidenceDiscoveryQueue.processOne(
                        pending, entry -> recover(entry, recovered));
        EscrowEvidenceDiscoveryQueue.StepResult valid =
                EscrowEvidenceDiscoveryQueue.processOne(
                        pending, entry -> recover(entry, recovered));

        assertEquals(1, corrupt.examined());
        assertFalse(corrupt.complete());
        assertTrue(corrupt.failure().isPresent());
        assertEquals(1, valid.examined());
        assertTrue(valid.complete());
        assertTrue(valid.failure().isEmpty());
        assertEquals(List.of("foreign valid"), recovered);
    }

    private static ArrayDeque<DiscoveryEntry> entries(
            String corrupt,
            String valid
    ) {
        ArrayDeque<DiscoveryEntry> pending = new ArrayDeque<>();
        pending.addLast(new DiscoveryEntry(corrupt, true));
        pending.addLast(new DiscoveryEntry(valid, false));
        return pending;
    }

    private static void recover(
            DiscoveryEntry entry,
            List<String> recovered
    ) {
        if (entry.corrupt()) {
            throw new EscrowRuntimeException("Corrupt discovery evidence");
        }
        recovered.add(entry.name());
    }

    private record DiscoveryEntry(String name, boolean corrupt) {
    }
}
