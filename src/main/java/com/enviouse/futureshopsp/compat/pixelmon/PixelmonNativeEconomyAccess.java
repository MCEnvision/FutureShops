package com.enviouse.futureshopsp.compat.pixelmon;

import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import net.minecraft.core.HolderLookup;

public interface PixelmonNativeEconomyAccess {
    ProviderResult<MutationReceipt> futureshopsMutate(RequestId requestId, MutationKind kind,
                                                       long amountMinorUnits,
                                                       HolderLookup.Provider registries);

    ProviderResult<MutationReceipt> futureshopsLookup(RequestId requestId);
}
