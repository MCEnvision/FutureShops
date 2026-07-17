package com.enviouse.futureshops.server.escrow.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalCrashCutPropertyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void everyPossibleTailCutKeepsExactlyTheCompletePrefix() throws Exception {
        Path completePath = temporaryDirectory.resolve("complete.wal");
        long firstEnd;
        try (WriteAheadJournal journal = WriteAheadJournal.open(completePath)) {
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("first"));
            firstEnd = Files.size(completePath);
            journal.append(UUID.randomUUID(), UUID.randomUUID(), bytes("second payload"));
        }
        byte[] complete = Files.readAllBytes(completePath);

        for (int cut = (int) firstEnd; cut <= complete.length; cut++) {
            Path candidate = temporaryDirectory.resolve("cut " + cut + ".wal");
            Files.write(candidate, Arrays.copyOf(complete, cut));
            try (WriteAheadJournal recovered = WriteAheadJournal.open(candidate)) {
                int expectedRecords = cut == complete.length ? 2 : 1;
                assertEquals(expectedRecords, recovered.recovery().recordCount(), "cut " + cut);
                assertEquals(expectedRecords + 1L, recovered.nextSequence(), "cut " + cut);
            }
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
