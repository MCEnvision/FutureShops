package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.runtime.ProtectedCashClaimPayload;
import com.enviouse.futureshops.server.escrow.runtime.ProtectedCashClaimPayloadCodec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmCashClaimCenterPolicyTest {
    private static final Path CLAIM_CENTER_SOURCE = Path.of(
            "src/main/java/com/enviouse/futureshops/server/economy/AtmCashClaimCenter.java");
    private static final Path ATM_SERVICE_SOURCE = Path.of(
            "src/main/java/com/enviouse/futureshops/server/economy/AtmService.java");
    private static final Path COORDINATOR_SOURCE = Path.of(
            "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRuntimeCoordinator.java");
    private static final UUID PLAYER_ID = UUID.fromString(
            "52000000-0000-0000-0000-000000000001");
    private static final UUID CLAIM_ONE = UUID.fromString(
            "52000000-0000-0000-0000-000000000011");
    private static final UUID CLAIM_TWO = UUID.fromString(
            "52000000-0000-0000-0000-000000000012");
    private static final UUID CLAIM_THREE = UUID.fromString(
            "52000000-0000-0000-0000-000000000013");
    private static final UUID TRANSACTION_ID = UUID.fromString(
            "52000000-0000-0000-0000-000000000021");
    private static final Instant CREATED = Instant.parse(
            "2026-07-18T18:00:00.123456789Z");

    @Test
    void cashCollectionAndMaintenanceFailuresStayObservable()
            throws Exception {
        String claimCenter = Files.readString(CLAIM_CENTER_SOURCE);
        String atmService = Files.readString(ATM_SERVICE_SOURCE);
        String coordinator = Files.readString(COORDINATOR_SOURCE);

        assertFalse(claimCenter.contains(
                "catch (RuntimeException ignored)"));
        assertTrue(claimCenter.contains(
                "ATM cash claim delivery was not applied"));
        assertTrue(claimCenter.contains(
                "ATM cash claim delivery failed"));
        assertTrue(atmService.contains(
                "ATM cash collection was rejected by the request gate"));
        assertTrue(coordinator.contains(
                "Escrow runtime entered maintenance"));
    }

    @Test
    void requestBindingRejectsDifferentClaimsWithoutCacheState() {
        List<UUID> original = List.of(CLAIM_ONE, CLAIM_TWO);
        UUID requestId = C2SAtmCollectCashPacket.deriveRequestId(
                PLAYER_ID, original);

        assertTrue(AtmCashClaimCenter.validRequestIdentity(
                PLAYER_ID, requestId, original));
        assertFalse(AtmCashClaimCenter.validRequestIdentity(
                PLAYER_ID, requestId, List.of(CLAIM_ONE, CLAIM_THREE)));
        assertFalse(AtmCashClaimCenter.validRequestIdentity(
                PLAYER_ID, requestId, List.of(CLAIM_TWO, CLAIM_ONE)));
        assertFalse(AtmCashClaimCenter.validRequestIdentity(
                UUID.randomUUID(), requestId, original));
    }

    @Test
    void terminalReplayIsReconstructedWithoutRequestState() {
        EscrowClaim completed = protectedCash(
                CLAIM_ONE, PLAYER_ID, 3).deliver(
                300L, CREATED.plusSeconds(1));

        AtmCashClaimCollectionPolicy.Outcome first =
                AtmCashClaimCollectionPolicy.outcome(
                        AtmCashClaimCollectionPolicy.inspect(
                                PLAYER_ID, List.of(CLAIM_ONE),
                                List.of(completed)), false, false);
        AtmCashClaimCollectionPolicy.Outcome afterRestart =
                AtmCashClaimCollectionPolicy.outcome(
                        AtmCashClaimCollectionPolicy.inspect(
                                PLAYER_ID, List.of(CLAIM_ONE),
                                List.of(completed)), false, false);

        assertEquals(first, afterRestart);
        assertEquals("DELIVERED", afterRestart.status());
        assertEquals(3, afterRestart.completedBillCount());
        assertTrue(afterRestart.replayed());
        assertFalse(afterRestart.retryable());
    }

    @Test
    void completedPlusPendingClaimsRemainExactAndRetryable() {
        EscrowClaim completed = protectedCash(
                CLAIM_ONE, PLAYER_ID, 2).deliver(
                200L, CREATED.plusSeconds(1));
        EscrowClaim pending = protectedCash(
                CLAIM_TWO, PLAYER_ID, 4);
        AtmCashClaimCollectionPolicy.Snapshot snapshot =
                AtmCashClaimCollectionPolicy.inspect(
                        PLAYER_ID, List.of(CLAIM_ONE, CLAIM_TWO),
                        List.of(completed, pending));

        AtmCashClaimCollectionPolicy.Outcome result =
                AtmCashClaimCollectionPolicy.outcome(
                        snapshot, false, false);

        assertEquals("PARTIALLY_DELIVERED", result.status());
        assertTrue(result.retryable());
        assertTrue(result.replayed());
        assertEquals(2, result.completedBillCount());
        assertEquals(List.of(CLAIM_TWO), snapshot.pendingClaimIds());
    }

    @Test
    void quarantineKeepsHealthyPendingClaimRetryable() {
        EscrowClaim quarantined = protectedCash(
                CLAIM_ONE, PLAYER_ID, 2).quarantine(
                CREATED.plusSeconds(1));
        EscrowClaim pending = protectedCash(
                CLAIM_TWO, PLAYER_ID, 4);
        AtmCashClaimCollectionPolicy.Outcome result =
                AtmCashClaimCollectionPolicy.outcome(
                        AtmCashClaimCollectionPolicy.inspect(
                                PLAYER_ID, List.of(CLAIM_ONE, CLAIM_TWO),
                                List.of(quarantined, pending)),
                        false, false);

        assertEquals("MANUAL_REVIEW", result.status());
        assertTrue(result.retryable());
        assertEquals(List.of(CLAIM_ONE),
                result.quarantinedClaimIds());
    }

    @Test
    void invalidExactClaimRecordsExposeBoundedRecoveryHandles() {
        EscrowClaim valid = protectedCash(CLAIM_ONE, PLAYER_ID, 2);
        EscrowClaim partial = valid.deliver(
                100L, CREATED.plusSeconds(1));
        EscrowClaim wrongOwner = protectedCash(
                CLAIM_ONE, UUID.randomUUID(), 2);
        EscrowClaim noncash = new EscrowClaim(
                CLAIM_ONE, TRANSACTION_ID, PLAYER_ID,
                "atm.policy.item", ClaimKind.ITEM,
                1L, 1L, new byte[]{1}, ClaimStatus.PENDING,
                "Item", CREATED, CREATED);
        EscrowClaim corrupt = new EscrowClaim(
                CLAIM_ONE, TRANSACTION_ID, PLAYER_ID,
                "atm.policy.corrupt", ClaimKind.PROTECTED_CASH,
                1L, 1L, new byte[]{1}, ClaimStatus.PENDING,
                "Corrupt cash", CREATED, CREATED);

        assertThrows(IllegalArgumentException.class, () ->
                AtmCashClaimCollectionPolicy.inspect(PLAYER_ID,
                        List.of(CLAIM_ONE, CLAIM_TWO), List.of(valid)));
        assertEquals(List.of(CLAIM_ONE),
                AtmCashClaimCollectionPolicy.inspect(PLAYER_ID,
                        List.of(CLAIM_ONE), List.of(partial))
                        .quarantinedClaimIds());
        assertEquals(List.of(CLAIM_ONE),
                AtmCashClaimCollectionPolicy.inspect(PLAYER_ID,
                        List.of(CLAIM_ONE), List.of(wrongOwner))
                        .quarantinedClaimIds());
        assertEquals(List.of(CLAIM_ONE),
                AtmCashClaimCollectionPolicy.inspect(PLAYER_ID,
                        List.of(CLAIM_ONE), List.of(noncash))
                        .quarantinedClaimIds());
        assertEquals(List.of(CLAIM_ONE),
                AtmCashClaimCollectionPolicy.inspect(PLAYER_ID,
                        List.of(CLAIM_ONE), List.of(corrupt))
                        .quarantinedClaimIds());
        assertEquals(0,
                AtmCashClaimCollectionPolicy.cashBillCountOrZero(corrupt));
    }

    @Test
    void malformedClaimDoesNotDiscardHealthyPendingClaim() {
        EscrowClaim healthy = protectedCash(CLAIM_ONE, PLAYER_ID, 2);
        EscrowClaim corrupt = new EscrowClaim(
                CLAIM_TWO, TRANSACTION_ID, PLAYER_ID,
                "atm.policy.corrupt.two", ClaimKind.PROTECTED_CASH,
                1L, 1L, new byte[]{1}, ClaimStatus.PENDING,
                "Corrupt cash", CREATED, CREATED);

        AtmCashClaimCollectionPolicy.Snapshot snapshot =
                AtmCashClaimCollectionPolicy.inspect(
                        PLAYER_ID, List.of(CLAIM_ONE, CLAIM_TWO),
                        List.of(healthy, corrupt));

        assertEquals(List.of(CLAIM_ONE), snapshot.pendingClaimIds());
        assertEquals(List.of(CLAIM_TWO),
                snapshot.quarantinedClaimIds());
        AtmCashClaimCollectionPolicy.Outcome result =
                AtmCashClaimCollectionPolicy.outcome(
                        snapshot, false, false);
        assertEquals("MANUAL_REVIEW", result.status());
        assertTrue(result.retryable());
        assertEquals(List.of(CLAIM_TWO),
                result.quarantinedClaimIds());
    }

    @Test
    void summarySelectionDoesNotLetMalformedClaimsStarveHealthyCash() {
        EscrowClaim malformedOne = new EscrowClaim(
                CLAIM_ONE, TRANSACTION_ID, PLAYER_ID,
                "atm.policy.summary.one", ClaimKind.PROTECTED_CASH,
                1L, 1L, new byte[]{1}, ClaimStatus.PENDING,
                "Corrupt cash", CREATED, CREATED);
        EscrowClaim malformedTwo = new EscrowClaim(
                CLAIM_TWO, TRANSACTION_ID, PLAYER_ID,
                "atm.policy.summary.two", ClaimKind.PROTECTED_CASH,
                1L, 1L, new byte[]{2}, ClaimStatus.PENDING,
                "Corrupt cash", CREATED, CREATED);
        EscrowClaim healthy = protectedCash(CLAIM_THREE, PLAYER_ID, 2);

        List<AtmCashClaimCollectionPolicy.SummaryClaim> selected =
                AtmCashClaimCollectionPolicy.selectForSummary(
                        List.of(malformedOne, malformedTwo, healthy), 2);

        assertEquals(List.of(CLAIM_THREE, CLAIM_ONE),
                selected.stream()
                        .map(AtmCashClaimCollectionPolicy.SummaryClaim::claimId)
                        .toList());
        assertEquals(List.of(2, 0), selected.stream()
                .map(AtmCashClaimCollectionPolicy.SummaryClaim::billCount)
                .toList());
    }

    private static EscrowClaim protectedCash(
            UUID claimId,
            UUID ownerId,
            int billCount
    ) {
        ProtectedCashClaimPayload payload =
                new ProtectedCashClaimPayload(
                        UUID.nameUUIDFromBytes(claimId.toString().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                        100L, billCount, 0, 1, billCount,
                        "atm policy server", "atm policy checksum");
        long units = Math.multiplyExact(100L, (long) billCount);
        return new EscrowClaim(claimId, TRANSACTION_ID, ownerId,
                "atm.policy." + claimId, ClaimKind.PROTECTED_CASH,
                units, units,
                ProtectedCashClaimPayloadCodec.encode(payload),
                ClaimStatus.PENDING, "Protected cash", CREATED, CREATED);
    }
}
