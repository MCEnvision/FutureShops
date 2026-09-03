package com.enviouse.futureshopsp.server.economy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceManagerRecoverySourceTest {
    @Test
    void startupReconcilesJournalRecordsBeforeExposingProvider() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "economy", "BalanceManager.java")));

        assertTrue(source.contains("recoverIncompleteJournalRecords();\n            provider = new CoordinatedEconomyProvider"));
        assertTrue(source.contains("recoverIncompleteJournalRecords();\n            provider = new ExternalLegacyEconomyProvider"));
        assertTrue(source.contains("for (EconomyJournalRecord record : journal.snapshot())"));
        assertTrue(source.contains("coordinator.recover(record.request().requestId())"));
        assertTrue(source.contains("ProviderLifecycle.FROZEN"));
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}
