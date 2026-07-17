package com.enviouse.futureshops.server.escrow.custody;

public final class CashClaimCustodySupport {
    public static final String PLAYER_INVENTORY_ADAPTER_ID =
            "futureshops.player_inventory";

    private CashClaimCustodySupport() {
    }

    public static boolean isTransientInventoryRelease(
            CustodyPreparedOperation operation
    ) {
        if (operation == null
                || operation.operation() != CustodyOperation.RELEASE
                || !operation.adapterId().equals(
                PLAYER_INVENTORY_ADAPTER_ID)
                || operation.adapterCapability()
                != CustodyAdapterCapability.RECONCILABLE) {
            return false;
        }
        CustodyAssetType type = operation.lotSnapshot().assetType();
        return type == CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY
                || type == CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY;
    }
}
