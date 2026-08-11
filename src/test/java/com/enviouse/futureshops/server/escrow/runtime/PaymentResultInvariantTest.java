package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentResultInvariantTest {
    @Test
    void playerPaymentResultRejectsContradictorySuccessAndFailureData() {
        UUID request = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        PlayerPaymentService.PlayerPaymentResult success =
                new PlayerPaymentService.PlayerPaymentResult(
                        PlayerPaymentService.Status.SUCCESS,
                        request, payer, recipient, Optional.of(request),
                        100L, 50L, 50L, 900L, 50L,
                        100L, "Coins", 2, false, 0L);

        assertEquals(100L, success.amountMinorUnits());
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPaymentService.PlayerPaymentResult(
                        PlayerPaymentService.Status.SUCCESS,
                        request, payer, recipient, Optional.of(request),
                        100L, 50L, 49L, 900L, 50L,
                        100L, "Coins", 2, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPaymentService.PlayerPaymentResult(
                        PlayerPaymentService.Status.INSUFFICIENT_FUNDS,
                        request, payer, recipient, Optional.empty(),
                        100L, 0L, 0L, 1L, 0L,
                        0L, "", 0, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPaymentService.PlayerPaymentResult(
                        PlayerPaymentService.Status.RATE_LIMITED,
                        request, payer, recipient, Optional.empty(),
                        100L, 0L, 0L, 0L, 0L,
                        0L, "", 0, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerPaymentService.PlayerPaymentResult(
                        PlayerPaymentService.Status.RATE_LIMITED,
                        request, payer, recipient, Optional.empty(),
                        100L, 0L, 0L, 0L, 0L,
                        0L, "", 0, false,
                        PlayerPaymentService.MAX_RETRY_AFTER_MILLIS + 1L));
    }

    @Test
    void collectionResultRequiresPositiveValueOnlyForSuccess() {
        UUID request = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        EscrowMoneyClaimService.CollectionResult success =
                new EscrowMoneyClaimService.CollectionResult(
                        EscrowMoneyClaimService.Status.SUCCESS,
                        request, claim, 10L, -40L, true);

        assertEquals(10L, success.collectedMinorUnits());
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowMoneyClaimService.CollectionResult(
                        EscrowMoneyClaimService.Status.SUCCESS,
                        request, claim, 0L, 0L, false));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowMoneyClaimService.CollectionResult(
                        EscrowMoneyClaimService.Status.NOT_FOUND,
                        request, claim, 1L, 0L, false));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowMoneyClaimService.CollectionResult(
                        EscrowMoneyClaimService.Status.NOT_FOUND,
                        request, claim, 0L, 0L, true));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowMoneyClaimService.CollectionResult(
                        EscrowMoneyClaimService.Status.NOT_FOUND,
                        new UUID(0L, 0L), claim,
                        0L, 0L, false));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowMoneyClaimService.CollectionResult(
                        EscrowMoneyClaimService.Status.ESCROW_UNAVAILABLE,
                        request, claim, 0L, 1L, false));
    }
}
