package com.enviouse.futureshops.server.economy.migration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletInitializationIdsTest {
    @Test
    void requestIdsAreDeterministicAndSourceSpecific() {
        UUID player = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");

        assertEquals(WalletInitializationIds.legacyBalance(player),
                WalletInitializationIds.legacyBalance(player));
        assertEquals(WalletInitializationIds.startingGrant(player),
                WalletInitializationIds.startingGrant(player));
        assertNotEquals(WalletInitializationIds.legacyBalance(player),
                WalletInitializationIds.startingGrant(player));
    }

    @Test
    void zeroCreatesAValidInitializationRequest() {
        UUID player = UUID.randomUUID();
        WalletInitializationRequest request =
                WalletInitializationIds.startingGrantRequest(player, 0L);

        assertEquals(0L, request.balanceMinorUnits());
        assertEquals(WalletInitializationSource.STARTING_GRANT,
                request.source());
    }

    @Test
    void negativeStartingGrantIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WalletInitializationIds.startingGrantRequest(
                        UUID.randomUUID(), -1L));
    }
}
