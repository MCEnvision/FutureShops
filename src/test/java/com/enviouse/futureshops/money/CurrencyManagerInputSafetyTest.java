package com.enviouse.futureshops.money;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyManagerInputSafetyTest {
    @Test
    void missingConfigListsAreTreatedAsEmpty() {
        assertTrue(CurrencyManager.safeEntries(null).isEmpty());
    }

    @Test
    void configuredEntriesAreCopiedBeforePublication() {
        List<String> configured = new java.util.ArrayList<>(
                List.of("minecraft:emerald=100"));
        List<? extends String> safe =
                CurrencyManager.safeEntries(configured);

        configured.clear();
        assertEquals(List.of("minecraft:emerald=100"), safe);
    }
}
