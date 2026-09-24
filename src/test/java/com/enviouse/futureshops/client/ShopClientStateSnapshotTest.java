package com.enviouse.futureshops.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopClientStateSnapshotTest {
    @AfterEach
    void reset() {
        ShopClientState.reset();
    }

    @Test
    void olderSameShopSnapshotCannotOverwriteCurrentState() {
        ShopClientState.applyShopData("default", 500L, "Coins", 2,
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                false, List.of(), "internal", 5L);
        ShopClientState.applyShopData("default", 100L, "Coins", 2,
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                false, List.of(), "internal", 4L);

        assertEquals(5L, ShopClientState.getSnapshotRevision());
        assertEquals(500L, ShopClientState.getCurrentBalanceMinorUnits());
    }
}
