package com.enviouse.futureshops.server.escrow.custody;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CustodyBatchPlan(
        CustodyOperation operation,
        String requestKey,
        String adapterId,
        CustodyAdapterCapability capability,
        CustodyProtectionTier protectionTier,
        List<CustodyLot> lots,
        long requiredUnits
) {
    public static final int MAX_BATCH_LOTS = 4096;

    public CustodyBatchPlan {
        Objects.requireNonNull(operation, "operation");
        requestKey = CustodyLot.requireRequestKey(requestKey);
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(protectionTier, "protectionTier");
        Objects.requireNonNull(lots, "lots");
        adapterId = adapterId.strip();
        lots = List.copyOf(lots);
        if (adapterId.isEmpty() || adapterId.length() > CustodyEndpointEvidence.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Invalid custody batch adapter ID");
        }
        if (lots.isEmpty() || lots.size() > MAX_BATCH_LOTS) {
            throw new IllegalArgumentException("Custody batch lot count exceeds bounds");
        }
        Set<UUID> lotIds = new HashSet<>();
        long calculatedUnits = 0L;
        for (CustodyLot lot : lots) {
            Objects.requireNonNull(lot, "lot");
            if (!lotIds.add(lot.lotId())) {
                throw new IllegalArgumentException("Custody batch contains a duplicate lot");
            }
            CustodyEndpointEvidence endpoint = lot.holdEvidence().source();
            if (lot.protectionTier() != protectionTier
                    || (operation == CustodyOperation.RESERVE
                    && (!endpoint.adapterId().equals(adapterId)
                    || endpoint.capability() != capability))) {
                throw new IllegalArgumentException("Custody batch cannot mix adapters or protection tiers");
            }
            if (operation == CustodyOperation.RESERVE && lot.state() != CustodyLotState.HELD) {
                throw new IllegalArgumentException("Reserve batch requires new held lots");
            }
            if (operation != CustodyOperation.RESERVE && lot.state() != CustodyLotState.HELD) {
                throw new IllegalArgumentException("Terminal custody batch requires held lots");
            }
            calculatedUnits = Math.addExact(calculatedUnits, lot.units());
        }
        if (requiredUnits != calculatedUnits) {
            throw new IllegalArgumentException("Custody batch required units do not match its lots");
        }
    }

    public static CustodyBatchPlan create(CustodyOperation operation,
                                          String requestKey,
                                          List<CustodyLot> lots) {
        Objects.requireNonNull(lots, "lots");
        if (lots.isEmpty()) {
            throw new IllegalArgumentException("Custody batch requires lots");
        }
        CustodyLot first = lots.get(0);
        CustodyEndpointEvidence endpoint = first.holdEvidence().source();
        long units = lots.stream().mapToLong(CustodyLot::units).reduce(0L, Math::addExact);
        return new CustodyBatchPlan(operation, requestKey,
                endpoint.adapterId(), endpoint.capability(),
                first.protectionTier(), lots, units);
    }

    public Set<UUID> lotIds() {
        Set<UUID> ids = new HashSet<>();
        for (CustodyLot lot : lots) {
            ids.add(lot.lotId());
        }
        return Set.copyOf(ids);
    }
}
