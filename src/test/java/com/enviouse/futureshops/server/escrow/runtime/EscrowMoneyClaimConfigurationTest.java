package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowMoneyClaimConfigurationTest {
    @Test
    void sameGenerationCannotHideAChangedWalletLimit() {
        EscrowMoneyClaimService.ClaimConfiguration before =
                new EscrowMoneyClaimService.ClaimConfiguration(7L, 100L);
        EscrowMoneyClaimService.ClaimConfiguration after =
                new EscrowMoneyClaimService.ClaimConfiguration(7L, 101L);

        assertFalse(before.sameSemantics(after));
    }

    @Test
    void UnchangedWalletLimitSurvivesGenerationChange() {
        EscrowMoneyClaimService.ClaimConfiguration before =
                new EscrowMoneyClaimService.ClaimConfiguration(7L, 100L);
        EscrowMoneyClaimService.ClaimConfiguration after =
                new EscrowMoneyClaimService.ClaimConfiguration(8L, 100L);

        assertTrue(before.sameSemantics(after));
    }
}
