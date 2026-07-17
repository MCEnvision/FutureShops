package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.shop.ShopResultCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdministrativeBalanceEvidenceCodecTest {
    @Test
    void transferOutcomeRoundTripsEverySemanticField() {
        AdministrativeBalanceEvidence evidence = evidence();

        assertEquals(evidence, AdministrativeBalanceEvidenceCodec.decode(
                AdministrativeBalanceEvidenceCodec.encode(evidence)));
    }

    @Test
    void malformedAndTrailingPayloadsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AdministrativeBalanceEvidenceCodec.decode("%%%"));
        byte[] valid = Base64.getUrlDecoder().decode(
                AdministrativeBalanceEvidenceCodec.encode(evidence()));
        byte[] trailing = java.util.Arrays.copyOf(valid,
                valid.length + 1);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(trailing);
        assertThrows(IllegalArgumentException.class,
                () -> AdministrativeBalanceEvidenceCodec.decode(encoded));
    }

    @Test
    void incompleteCounterpartyEvidenceIsRejected() {
        AdministrativeBalanceEvidence value = evidence();
        assertThrows(IllegalArgumentException.class,
                () -> new AdministrativeBalanceEvidence(
                        value.evidenceId(), value.mutationRequestId(),
                        value.mutationFingerprint(), value.phase(),
                        value.operation(), value.targetPlayerId(),
                        Optional.empty(), value.amountMinor(),
                        value.allowNegative(), value.confirmation(),
                        value.counterpartyBalanceBefore(),
                        value.balanceBefore(), value.resultingBalance(),
                        value.counterpartyResultingBalance(),
                        value.successful(), value.resultCode(),
                        value.recordedAt()));
    }

    private static AdministrativeBalanceEvidence evidence() {
        return new AdministrativeBalanceEvidence(
                UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64),
                AdministrativeBalanceEvidencePhase.OUTCOME,
                AdministrativeBalanceOperation.TRANSFER,
                UUID.randomUUID(), Optional.of(UUID.randomUUID()),
                35L, false,
                AdministrativeBalanceConfirmation.EXPLICIT_API,
                OptionalLong.of(12L), 70L, 35L,
                OptionalLong.of(47L), true, ShopResultCode.OK,
                Instant.parse("2026-07-17T19:00:00.987654321Z"));
    }
}
