package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignAtmWithdrawalPlanTest {
    private static final UUID REQUEST_ID =
            UUID.fromString("75000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_ID =
            UUID.fromString("76000000-0000-0000-0000-000000000001");
    private static final Instant REQUESTED_AT =
            Instant.parse("2026-07-18T14:00:00.123456789Z");
    private static final String SIGNATURE = "a".repeat(64);

    @Test
    void requestCanonicalizesPortionsAndSealsTheirExactSnapshots() {
        ForeignAtmStackSelection second = stack(
                0, "example:cash", 100L, 6, 1, 2,
                new byte[]{4, 5, 6});
        ForeignAtmStackSelection first = stack(
                0, "example:cash", 100L, 64, 0, 2,
                new byte[]{1, 2, 3});
        ForeignAtmWithdrawalRequest request = request(
                List.of(second, first), REQUESTED_AT);
        ForeignAtmWithdrawalRequest delayed = request(
                List.of(first, second), REQUESTED_AT.plusSeconds(30));

        assertEquals(List.of(first, second), request.stacks());
        assertEquals(70, request.billCount());
        assertEquals(7000L, request.amountMinorUnits());
        assertEquals(request.fingerprint(), delayed.fingerprint());
        assertEquals(REQUESTED_AT.plusSeconds(60),
                request.at(REQUESTED_AT.plusSeconds(60)).requestedAt());
    }

    @Test
    void requestRejectsProtectedProviderIncompletePortionsAndLimits() {
        ForeignAtmStackSelection first = stack(
                0, "example:cash", 100L, 64, 0, 2,
                new byte[]{1});
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalRequest(
                        REQUEST_ID, PLAYER_ID, "futureshops", SIGNATURE,
                        List.of(first), REQUESTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(first), REQUESTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(
                        stack(0, "example:cash", 1L, 4096,
                                0, 1, new byte[]{1}),
                        stack(1, "example:coin", 1L, 1,
                                0, 1, new byte[]{2})), REQUESTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(
                        stack(0, "example:cash", 100L, 1,
                                0, 1, new byte[]{1}),
                        stack(1, "example:cash", 25L, 1,
                                0, 1, new byte[]{2})), REQUESTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> stack(0, "futureshops:money", 100L, 1,
                        0, 1, new byte[]{1}));
        ForeignAtmStackSelection single = stack(
                0, "example:cash", 1L, 1,
                0, 1, new byte[]{1});
        assertThrows(IllegalArgumentException.class,
                () -> request(Collections.nCopies(
                        ForeignAtmWithdrawalCommit.MAX_CASH_CLAIMS + 1,
                        single), REQUESTED_AT));
    }

    @Test
    void planUsesForeignSinkAndExternalClaimsWithoutProtectedMintData() {
        ForeignAtmWithdrawalRequest request = request(List.of(
                stack(0, "example:cash", 100L, 64,
                        0, 2, new byte[]{1, 2, 3}),
                stack(0, "example:cash", 100L, 6,
                        1, 2, new byte[]{4, 5, 6}),
                stack(1, "example:coin", 25L, 4,
                        0, 1, new byte[]{7, 8, 9})), REQUESTED_AT);
        ForeignAtmWithdrawalPlan plan =
                ForeignAtmWithdrawalPlan.create(request);
        ForeignAtmWithdrawalCommit commit = plan.commitFor(
                plan.heldTransaction());

        assertEquals(EscrowState.CREATED,
                plan.createdTransaction().state());
        assertEquals(EscrowState.HELD,
                plan.heldTransaction().state());
        assertEquals(7100L, commit.amountMinorUnits());
        assertEquals(3, commit.cashClaims().size());
        assertEquals(LedgerAccountType.FOREIGN_CURRENCY_SINK,
                commit.ledgerTransaction().legs().get(1).account().type());
        assertTrue(commit.committedTransaction().assetLots().stream()
                .skip(1)
                .allMatch(value -> value.protectionLevel()
                        == EscrowProtectionLevel.EXTERNAL));
        assertTrue(commit.cashClaims().stream().allMatch(value ->
                ForeignCashClaimPayloadCodec.decode(value.payload())
                        .fingerprint().length() == 64));
    }

    @Test
    void retryReconstructionIsDeterministicAndChangedSnapshotConflicts() {
        ForeignAtmWithdrawalRequest original = request(List.of(
                stack(0, "example:cash", 100L, 2,
                        0, 1, new byte[]{1, 2, 3})), REQUESTED_AT);
        ForeignAtmWithdrawalPlan first =
                ForeignAtmWithdrawalPlan.create(original);
        ForeignAtmWithdrawalPlan retry = ForeignAtmWithdrawalPlan.create(
                request(original.stacks(), REQUESTED_AT.plusSeconds(20)).at(
                        first.createdTransaction().timestamps().createdAt()));

        assertEquals(first, retry);
        first.requireImmutableTransaction(retry.heldTransaction());

        ForeignAtmWithdrawalPlan changed =
                ForeignAtmWithdrawalPlan.create(request(List.of(
                        stack(0, "example:cash", 100L, 2,
                                0, 1, new byte[]{9, 9, 9})), REQUESTED_AT));
        assertNotEquals(first.request().fingerprint(),
                changed.request().fingerprint());
        assertThrows(IllegalArgumentException.class,
                () -> changed.requireImmutableTransaction(
                        first.heldTransaction()));
        assertThrows(IllegalArgumentException.class,
                () -> new ForeignAtmWithdrawalPlan(
                        original, changed.createdTransaction(),
                        changed.cashClaims()));
    }

    private static ForeignAtmWithdrawalRequest request(
            List<ForeignAtmStackSelection> stacks,
            Instant requestedAt
    ) {
        return new ForeignAtmWithdrawalRequest(
                REQUEST_ID, PLAYER_ID, "custom", SIGNATURE,
                stacks, requestedAt);
    }

    private static ForeignAtmStackSelection stack(
            int denominationIndex,
            String itemId,
            long denomination,
            int count,
            int portionIndex,
            int portionCount,
            byte[] nbt
    ) {
        return new ForeignAtmStackSelection(
                denominationIndex, itemId, denomination, count,
                portionIndex, portionCount, nbt);
    }
}
