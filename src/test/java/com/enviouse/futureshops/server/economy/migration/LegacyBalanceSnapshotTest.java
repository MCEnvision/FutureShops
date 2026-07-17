package com.enviouse.futureshops.server.economy.migration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyBalanceSnapshotTest {
    private static final UUID FIRST = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString(
            "10000000-0000-0000-0000-000000000000");
    private static final UUID THIRD = UUID.fromString(
            "ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test
    void snapshotUsesStableUuidStringOrderAndFingerprint() {
        Map<UUID, Long> firstOrder = new LinkedHashMap<>();
        firstOrder.put(THIRD, 0L);
        firstOrder.put(FIRST, 75L);
        firstOrder.put(SECOND, -10L);
        Map<UUID, Long> secondOrder = new LinkedHashMap<>();
        secondOrder.put(SECOND, -10L);
        secondOrder.put(THIRD, 0L);
        secondOrder.put(FIRST, 75L);

        LegacyBalanceSnapshot first =
                LegacyBalanceSnapshot.capture(firstOrder);
        LegacyBalanceSnapshot second =
                LegacyBalanceSnapshot.capture(secondOrder);

        assertEquals(first, second);
        assertEquals(List.of(FIRST, SECOND, THIRD), first.entries().stream()
                .map(LegacyBalanceEntry::playerId).toList());
        assertEquals(64, first.fingerprint().length());
    }

    @Test
    void signedValuesAndZeroAffectFingerprint() {
        LegacyBalanceSnapshot positive = LegacyBalanceSnapshot.capture(
                Map.of(FIRST, 1L, SECOND, 0L));
        LegacyBalanceSnapshot negative = LegacyBalanceSnapshot.capture(
                Map.of(FIRST, -1L, SECOND, 0L));
        LegacyBalanceSnapshot withoutZero = LegacyBalanceSnapshot.capture(
                Map.of(FIRST, 1L));

        assertNotEquals(positive.fingerprint(), negative.fingerprint());
        assertNotEquals(positive.fingerprint(), withoutZero.fingerprint());
    }

    @Test
    void tamperedFingerprintAndOrderFailClosed() {
        LegacyBalanceSnapshot captured = LegacyBalanceSnapshot.capture(
                Map.of(FIRST, 1L, SECOND, 2L));
        assertThrows(IllegalArgumentException.class,
                () -> new LegacyBalanceSnapshot(
                        captured.entries(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new LegacyBalanceSnapshot(
                        List.of(new LegacyBalanceEntry(SECOND, 2L),
                                new LegacyBalanceEntry(FIRST, 1L)),
                        captured.fingerprint()));
    }
}
