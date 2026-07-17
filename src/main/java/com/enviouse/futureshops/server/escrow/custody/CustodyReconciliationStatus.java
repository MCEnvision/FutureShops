package com.enviouse.futureshops.server.escrow.custody;

public enum CustodyReconciliationStatus {
    MATCHED,
    ASSET_MISSING,
    ASSET_EXCESS,
    ASSET_MISMATCH,
    SOURCE_MISMATCH,
    DESTINATION_MISMATCH,
    MULTIPLE_MISMATCHES
}
