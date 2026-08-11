package com.enviouse.futureshops.server.escrow.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemWireIdentityTest {
    @Test
    void stableCodesRoundTripAndRejectUnknownValues() {
        assertEquals(1, ItemInventoryMutationDirection.INSERT.wireCode());
        assertEquals(2, ItemInventoryMutationDirection.EXTRACT.wireCode());
        assertEquals(ItemInventoryMutationDirection.INSERT,
                ItemInventoryMutationDirection.fromWireCode(1));
        assertEquals(ItemInventoryMutationDirection.EXTRACT,
                ItemInventoryMutationDirection.fromWireCode(2));
        assertEquals(1, ItemMatchMode.EXACT.fingerprintCode());
        assertEquals(2, ItemMatchMode.ITEM_ONLY.fingerprintCode());
        assertEquals(ItemMatchMode.EXACT,
                ItemMatchMode.fromFingerprintCode(1));
        assertEquals(ItemMatchMode.ITEM_ONLY,
                ItemMatchMode.fromFingerprintCode(2));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationDirection.fromWireCode(0));
        assertThrows(IllegalArgumentException.class,
                () -> ItemMatchMode.fromFingerprintCode(0));
    }
}
