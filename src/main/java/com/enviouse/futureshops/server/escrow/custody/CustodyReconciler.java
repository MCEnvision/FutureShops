package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class CustodyReconciler {
    public CustodyReconciliationResult reconcile(CustodyLot lot,
                                                 List<CustodyItemSnapshot> observedSnapshots,
                                                 byte[] observedSourceState,
                                                 byte[] observedDestinationState,
                                                 Instant now) {
        Objects.requireNonNull(lot, "lot");
        Objects.requireNonNull(observedSnapshots, "observedSnapshots");
        Objects.requireNonNull(observedSourceState, "observedSourceState");
        Objects.requireNonNull(observedDestinationState, "observedDestinationState");
        Objects.requireNonNull(now, "now");
        byte[] observedFingerprint = CustodyLot.fingerprint(lot.assetType(), lot.protectionTier(),
                lot.units(), lot.currencyProvider(), observedSnapshots, lot.protectedProvenance());
        boolean assetMatches = CustodyHashes.equal(lot.assetFingerprint(), observedFingerprint);
        boolean sourceMatches = lot.holdEvidence().source().matchesObservedAfterState(observedSourceState);
        boolean destinationMatches = lot.holdEvidence().destination()
                .matchesObservedAfterState(observedDestinationState);

        CustodyReconciliationStatus status;
        String detail;
        if (assetMatches && sourceMatches && destinationMatches) {
            status = CustodyReconciliationStatus.MATCHED;
            detail = "Custody assets and endpoint evidence match";
        } else if (!assetMatches && sourceMatches && destinationMatches) {
            long expectedCount = lot.itemSnapshots().stream().mapToLong(CustodyItemSnapshot::count).sum();
            long observedCount = observedSnapshots.stream().mapToLong(CustodyItemSnapshot::count).sum();
            if (observedCount < expectedCount) {
                status = CustodyReconciliationStatus.ASSET_MISSING;
                detail = "Observed custody assets are below the exact snapshot quantity";
            } else if (observedCount > expectedCount) {
                status = CustodyReconciliationStatus.ASSET_EXCESS;
                detail = "Observed custody assets exceed the exact snapshot quantity";
            } else {
                status = CustodyReconciliationStatus.ASSET_MISMATCH;
                detail = "Observed custody assets differ from the exact snapshot payload";
            }
        } else if (assetMatches && !sourceMatches && destinationMatches) {
            status = CustodyReconciliationStatus.SOURCE_MISMATCH;
            detail = "Custody source evidence does not match the recorded post state";
        } else if (assetMatches && sourceMatches) {
            status = CustodyReconciliationStatus.DESTINATION_MISMATCH;
            detail = "Custody destination evidence does not match the recorded post state";
        } else {
            status = CustodyReconciliationStatus.MULTIPLE_MISMATCHES;
            detail = "Multiple custody assets or endpoint evidence values do not match";
        }
        return new CustodyReconciliationResult(lot.lotId(), status, lot.assetFingerprint(),
                observedFingerprint, sourceMatches, destinationMatches,
                status != CustodyReconciliationStatus.MATCHED, detail, now);
    }
}
