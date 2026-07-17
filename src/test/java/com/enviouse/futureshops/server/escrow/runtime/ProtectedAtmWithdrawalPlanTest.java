package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedAtmWithdrawalPlanTest {
    private static final UUID REQUEST_ID =
            UUID.fromString("73000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_ID =
            UUID.fromString("74000000-0000-0000-0000-000000000001");
    private static final Instant REQUESTED_AT =
            Instant.parse("2026-07-18T12:34:56.123456789Z");
    private static final String SIGNATURE =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void requestCanonicalizesSelectionsAndSealsSemanticFingerprint() {
        List<AtmBillSelection> reversed = List.of(
                new AtmBillSelection(2, 25L, 4),
                new AtmBillSelection(0, 100L, 3));
        ProtectedAtmWithdrawalRequest first = request(reversed, REQUESTED_AT);
        List<AtmBillSelection> ordered = new ArrayList<>(reversed);
        Collections.reverse(ordered);
        ProtectedAtmWithdrawalRequest retry = request(
                ordered, REQUESTED_AT.plusSeconds(30));

        assertEquals(List.of(
                new AtmBillSelection(0, 100L, 3),
                new AtmBillSelection(2, 25L, 4)), first.selections());
        assertEquals(400L, first.amountMinorUnits());
        assertEquals(first.fingerprint(), retry.fingerprint());
        assertEquals(REQUESTED_AT.plusSeconds(90),
                first.at(REQUESTED_AT.plusSeconds(90)).requestedAt());
    }

    @Test
    void requestRejectsDuplicateIndexesBillLimitsAndArithmeticOverflow() {
        assertThrows(IllegalArgumentException.class, () -> request(List.of(
                new AtmBillSelection(0, 100L, 1),
                new AtmBillSelection(0, 25L, 1)), REQUESTED_AT));
        assertThrows(IllegalArgumentException.class, () -> request(List.of(
                new AtmBillSelection(0, 1L, 4096),
                new AtmBillSelection(1, 1L, 1)), REQUESTED_AT));
        assertThrows(ArithmeticException.class,
                () -> new AtmBillSelection(0, Long.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new AtmBillSelection(-2, 1L, 1));
    }

    @Test
    void planCreatesOneProtectedMintPerDenominationAndStackSizedClaims() {
        withMintConfiguration(() -> {
            List<AtmBillSelection> selections = new ArrayList<>();
            selections.add(new AtmBillSelection(0, 1L, 4065));
            for (int index = 1; index < 32; index++) {
                selections.add(new AtmBillSelection(index, index + 1L, 1));
            }
            ProtectedAtmWithdrawalRequest request = request(
                    selections, REQUESTED_AT);
            ProtectedAtmWithdrawalPlan plan =
                    ProtectedAtmWithdrawalPlan.create(request);
            AtmWithdrawalCommit commit = plan.commitFor(
                    plan.heldTransaction());

            assertEquals(32, plan.mintIssues().size());
            assertEquals(95, plan.cashClaims().size());
            assertEquals(EscrowState.CREATED,
                    plan.createdTransaction().state());
            assertEquals(EscrowState.HELD,
                    plan.heldTransaction().state());
            assertEquals(request.amountMinorUnits(),
                    commit.amountMinorUnits());
            assertEquals(request.amountMinorUnits(), plan.cashClaims().stream()
                    .mapToLong(value -> value.originalUnits()).sum());
            assertTrue(plan.cashClaims().stream().allMatch(value ->
                    {
                        ProtectedCashClaimPayload payload =
                                ProtectedCashClaimPayloadCodec.decode(
                                        value.payload());
                        return payload.billCount()
                                <= ProtectedCashClaimPayload.MAX_STACK_BILLS
                                && value.originalUnits() == Math.multiplyExact(
                                payload.denominationMinorUnits(),
                                (long) payload.billCount())
                                && value.remainingUnits()
                                == value.originalUnits();
                    }));
        });
    }

    @Test
    void retryReconstructionIsDeterministicAndChangedPayloadConflicts() {
        withMintConfiguration(() -> {
            ProtectedAtmWithdrawalRequest original = request(List.of(
                    new AtmBillSelection(0, 100L, 3),
                    new AtmBillSelection(1, 25L, 4)), REQUESTED_AT);
            ProtectedAtmWithdrawalPlan first =
                    ProtectedAtmWithdrawalPlan.create(original);
            ProtectedAtmWithdrawalRequest delayedRetry = request(
                    original.selections(), REQUESTED_AT.plusSeconds(20));
            ProtectedAtmWithdrawalPlan reconstructed =
                    ProtectedAtmWithdrawalPlan.create(delayedRetry.at(
                            first.createdTransaction().timestamps().createdAt()));

            assertEquals(first, reconstructed);
            first.requireImmutableTransaction(
                    reconstructed.heldTransaction());

            ProtectedAtmWithdrawalPlan changed =
                    ProtectedAtmWithdrawalPlan.create(request(List.of(
                            new AtmBillSelection(0, 100L, 2),
                            new AtmBillSelection(1, 25L, 4)), REQUESTED_AT));
            assertNotEquals(original.fingerprint(),
                    changed.request().fingerprint());
            assertThrows(IllegalArgumentException.class,
                    () -> changed.requireImmutableTransaction(
                            first.heldTransaction()));
        });
    }

    private static ProtectedAtmWithdrawalRequest request(
            List<AtmBillSelection> selections,
            Instant requestedAt
    ) {
        return new ProtectedAtmWithdrawalRequest(
                REQUEST_ID, PLAYER_ID, "futureshops", SIGNATURE,
                selections, requestedAt);
    }

    private static void withMintConfiguration(Runnable action) {
        String priorServerId = Config.moneyMintServerId;
        String priorSalt = Config.moneyChecksumSalt;
        Config.moneyMintServerId = "protected atm plan test";
        Config.moneyChecksumSalt = "protected atm plan salt";
        try {
            action.run();
        } finally {
            Config.moneyMintServerId = priorServerId;
            Config.moneyChecksumSalt = priorSalt;
        }
    }
}
