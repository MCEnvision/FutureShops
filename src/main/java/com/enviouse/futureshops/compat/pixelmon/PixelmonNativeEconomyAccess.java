package com.enviouse.futureshops.compat.pixelmon;

import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.RequestId;

/** Narrow optional account seam implemented by the exact Pixelmon storage mixin. */
public interface PixelmonNativeEconomyAccess {
    ProviderResult<BalanceSnapshot> futureshops$balance();

    ProviderResult<MutationReceipt> futureshops$mutate(RequestId requestId,
                                                        MutationKind kind,
                                                        long amountMinorUnits,
                                                        String payloadFingerprint);

    ProviderResult<MutationReceipt> futureshops$lookup(RequestId requestId);
}
