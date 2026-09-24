package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticLedgerClosureTest {
    private static final String FORGE_COMMIT =
            "78fad4069d778996c24ecf5acc5cbe0e1edea7a";
    private static final String DONOR_COMMIT =
            "cc2d69425beea2c93a4000e44d49381deba8eed2";

    @Test
    void semanticLedgerHasNoUnknownRowsAndClosesPhaseEightRows() throws Exception {
        List<String> rows = Files.readAllLines(Path.of(
                "docs/verification/phase-000/semantic-delta-ledger.tsv"));
        int dataRows = 0;
        int phaseEightRows = 0;
        int phaseEightApplicable = 0;
        for (String row : rows) {
            if (row.isBlank() || row.startsWith("#") || row.startsWith("delta_id\t")) {
                continue;
            }
            String[] fields = row.split("\\t", -1);
            assertEquals(12, fields.length, row);
            assertEquals(DONOR_COMMIT, fields[1], row);
            assertFalse(fields[5].equals("unknown"), row);
            assertFalse(fields[6].equals("unknown"), row);
            assertFalse(fields[8].isBlank(), row);
            assertFalse(fields[10].isBlank(), row);
            assertFalse(fields[11].isBlank(), row);
            dataRows++;
            if (fields[8].equals("CORE-PHASE-008")) {
                phaseEightRows++;
                if (fields[5].equals("applicable")) {
                    phaseEightApplicable++;
                    assertTrue(fields[6].equals("adapted later")
                                    || fields[6].equals("Forge only preservation"), row);
                    assertTrue(fields[9].equals("P008-TASK-001")
                                    || fields[9].equals("P008-TASK-003")
                                    || fields[9].equals("P008-TASK-004"), row);
                }
            }
        }
        assertEquals(1923, dataRows);
        assertEquals(334, phaseEightRows);
        assertEquals(328, phaseEightApplicable);
    }

    @Test
    void historyLedgerHasNoUnknownPhaseEightRows() throws Exception {
        List<String> rows = Files.readAllLines(Path.of(
                "docs/verification/phase-000/history-delta-ledger.tsv"));
        int dataRows = 0;
        int phaseEightRows = 0;
        int phaseEightApplicable = 0;
        for (String row : rows) {
            if (row.isBlank() || row.startsWith("#") || row.startsWith("history_id\t")) {
                continue;
            }
            String[] fields = row.split("\\t", -1);
            assertEquals(13, fields.length, row);
            assertEquals(DONOR_COMMIT, fields[1], row);
            assertFalse(fields[6].equals("unknown"), row);
            assertFalse(fields[7].equals("unknown"), row);
            assertFalse(fields[8].isBlank(), row);
            assertFalse(fields[10].isBlank(), row);
            assertFalse(fields[12].isBlank(), row);
            dataRows++;
            if (fields[9].equals("CORE-PHASE-008")) {
                phaseEightRows++;
                if (fields[6].equals("applicable")) {
                    phaseEightApplicable++;
                    assertEquals("adapted later", fields[7], row);
                    assertTrue(fields[10].equals("P008-TASK-001")
                                    || fields[10].equals("P008-TASK-003"), row);
                }
            }
        }
        assertEquals(525, dataRows);
        assertEquals(342, phaseEightRows);
        assertEquals(120, phaseEightApplicable);
    }

    @Test
    void ledgerPinsTheForgeBaselineAndEveryForgeTargetIsResolvable() throws Exception {
        List<String> rows = Files.readAllLines(Path.of(
                "docs/verification/phase-000/semantic-delta-ledger.tsv"));
        assertTrue(rows.get(1).contains(FORGE_COMMIT));
        for (String row : rows) {
            if (row.isBlank() || row.startsWith("#") || row.startsWith("delta_id\t")) {
                continue;
            }
            String forgePath = row.split("\\t", -1)[4];
            if (forgePath.equals("none")) {
                continue;
            }
            assertTrue(existsInPinnedForgeTree(forgePath), forgePath);
        }
    }

    private static boolean existsInPinnedForgeTree(String path) throws Exception {
        Process process = new ProcessBuilder(
                "git", "cat-file", "-e", FORGE_COMMIT + ":" + path)
                .redirectErrorStream(true)
                .start();
        return process.waitFor() == 0;
    }
}
