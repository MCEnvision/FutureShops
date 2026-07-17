package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.runtime.ForeignCashClaimPayload;
import com.enviouse.futureshops.server.escrow.runtime.ForeignCashClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.runtime.ProtectedCashClaimPayload;
import com.enviouse.futureshops.server.escrow.runtime.ProtectedCashClaimPayloadCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class AtmCashClaimCollectionPolicy {
    private static final int MAX_SUMMARY_CLAIMS = 4;

    private AtmCashClaimCollectionPolicy() {
    }

    static Snapshot inspect(
            UUID playerId,
            List<UUID> exactClaimIds,
            List<EscrowClaim> exactClaims
    ) {
        UUID owner = Objects.requireNonNull(playerId, "playerId");
        List<UUID> claimIds = List.copyOf(Objects.requireNonNull(
                exactClaimIds, "exactClaimIds"));
        Objects.requireNonNull(exactClaims, "exactClaims");
        if (claimIds.isEmpty() || claimIds.size() != exactClaims.size()) {
            throw new IllegalArgumentException(
                    "ATM cash collection claims are missing");
        }
        int completedBills = 0;
        List<UUID> pending = new ArrayList<>();
        List<UUID> quarantined = new ArrayList<>();
        for (int index = 0; index < claimIds.size(); index++) {
            UUID expectedId = claimIds.get(index);
            EscrowClaim claim = exactClaims.get(index);
            if (claim == null
                    || !claim.claimId().equals(expectedId)
                    || !claim.ownerId().equals(owner)
                    || (claim.kind() != ClaimKind.PROTECTED_CASH
                    && claim.kind() != ClaimKind.FOREIGN_CASH)) {
                quarantined.add(expectedId);
                continue;
            }
            try {
                CashValue cash = cashValue(claim);
                int billCount = cash.billCount();
                long expectedUnits = cash.units();
                if (claim.originalUnits() != expectedUnits
                        || claim.status() == ClaimStatus.PENDING
                        && claim.remainingUnits() != expectedUnits
                        || claim.status() == ClaimStatus.COMPLETED
                        && claim.remainingUnits() != 0L
                        || claim.status() == ClaimStatus.QUARANTINED
                        && claim.remainingUnits() != expectedUnits) {
                    quarantined.add(claim.claimId());
                } else if (claim.status() == ClaimStatus.COMPLETED) {
                    completedBills = Math.addExact(
                            completedBills, billCount);
                } else if (claim.status() == ClaimStatus.PENDING) {
                    pending.add(claim.claimId());
                } else {
                    quarantined.add(claim.claimId());
                }
            } catch (RuntimeException exception) {
                quarantined.add(claim.claimId());
            }
        }
        return new Snapshot(completedBills, pending, quarantined);
    }

    static Outcome outcome(
            Snapshot snapshot,
            boolean unavailable,
            boolean changedThisAttempt
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        boolean replayed = !changedThisAttempt
                && (snapshot.completedBillCount() > 0
                || !snapshot.quarantinedClaimIds().isEmpty());
        if (!snapshot.quarantinedClaimIds().isEmpty()) {
            return new Outcome(snapshot.completedBillCount() > 0
                    ? "PARTIALLY_DELIVERED" : "MANUAL_REVIEW",
                    !snapshot.pendingClaimIds().isEmpty(), replayed,
                    snapshot.completedBillCount(),
                    snapshot.quarantinedClaimIds());
        }
        if (snapshot.pendingClaimIds().isEmpty()) {
            return new Outcome("DELIVERED", false, replayed,
                    snapshot.completedBillCount(), List.of());
        }
        if (snapshot.completedBillCount() > 0) {
            return new Outcome("PARTIALLY_DELIVERED", true, replayed,
                    snapshot.completedBillCount(), List.of());
        }
        return new Outcome(unavailable ? "UNAVAILABLE" : "RETRYABLE",
                true, false, 0, List.of());
    }

    static int cashBillCountOrZero(EscrowClaim claim) {
        Objects.requireNonNull(claim, "claim");
        try {
            CashValue cash = cashValue(claim);
            if (claim.status() != ClaimStatus.PENDING
                    || claim.originalUnits() != cash.units()
                    || claim.remainingUnits() != cash.units()) {
                return 0;
            }
            return cash.billCount();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    static List<SummaryClaim> selectForSummary(
            List<EscrowClaim> pendingClaims,
            int limit
    ) {
        List<EscrowClaim> claims = List.copyOf(Objects.requireNonNull(
                pendingClaims, "pendingClaims"));
        if (limit <= 0 || limit > MAX_SUMMARY_CLAIMS) {
            throw new IllegalArgumentException(
                    "ATM cash summary limit is invalid");
        }
        List<SummaryClaim> healthy = new ArrayList<>();
        List<SummaryClaim> malformed = new ArrayList<>();
        for (EscrowClaim claim : claims) {
            int billCount = cashBillCountOrZero(claim);
            List<SummaryClaim> target = billCount > 0
                    ? healthy : malformed;
            if (target.size() < limit) {
                target.add(new SummaryClaim(claim.claimId(),
                        claim.kind(), billCount));
            }
        }
        List<SummaryClaim> selected = new ArrayList<>(limit);
        selected.addAll(healthy);
        int remaining = limit - selected.size();
        selected.addAll(malformed.subList(
                0, Math.min(remaining, malformed.size())));
        return List.copyOf(selected);
    }

    private static CashValue cashValue(EscrowClaim claim) {
        if (claim.kind() == ClaimKind.PROTECTED_CASH) {
            ProtectedCashClaimPayload payload = protectedPayload(claim);
            return new CashValue(payload.billCount(), Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.billCount()));
        }
        if (claim.kind() == ClaimKind.FOREIGN_CASH) {
            ForeignCashClaimPayload payload = foreignPayload(claim);
            return new CashValue(payload.stackCount(), Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.stackCount()));
        }
        throw new IllegalArgumentException("Claim is not physical cash");
    }

    private static ProtectedCashClaimPayload protectedPayload(
            EscrowClaim claim
    ) {
        return ProtectedCashClaimPayloadCodec.decode(claim.payload());
    }

    private static ForeignCashClaimPayload foreignPayload(
            EscrowClaim claim
    ) {
        return ForeignCashClaimPayloadCodec.decode(claim.payload());
    }

    record Snapshot(
            int completedBillCount,
            List<UUID> pendingClaimIds,
            List<UUID> quarantinedClaimIds
    ) {
        Snapshot {
            pendingClaimIds = List.copyOf(Objects.requireNonNull(
                    pendingClaimIds, "pendingClaimIds"));
            quarantinedClaimIds = List.copyOf(Objects.requireNonNull(
                    quarantinedClaimIds, "quarantinedClaimIds"));
            if (completedBillCount < 0) {
                throw new IllegalArgumentException(
                        "ATM cash collection bill count is invalid");
            }
        }
    }

    record Outcome(
            String status,
            boolean retryable,
            boolean replayed,
            int completedBillCount,
            List<UUID> quarantinedClaimIds
    ) {
        Outcome {
            status = Objects.requireNonNull(status, "status");
            quarantinedClaimIds = List.copyOf(Objects.requireNonNull(
                    quarantinedClaimIds, "quarantinedClaimIds"));
        }
    }

    private record CashValue(int billCount, long units) {
    }

    record SummaryClaim(UUID claimId, ClaimKind kind, int billCount) {
        SummaryClaim {
            Objects.requireNonNull(claimId, "claimId");
            Objects.requireNonNull(kind, "kind");
            if (billCount < 0) {
                throw new IllegalArgumentException(
                        "ATM cash summary bill count is invalid");
            }
        }
    }
}
