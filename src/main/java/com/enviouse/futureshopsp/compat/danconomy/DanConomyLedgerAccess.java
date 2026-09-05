package com.enviouse.futureshopsp.compat.danconomy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public interface DanConomyLedgerAccess {
    ProviderResult<BalanceSnapshot> futureshopsBalance(UUID accountId, String currencyId);

    ProviderResult<MutationReceipt> futureshopsMutate(ServerLevel level, UUID accountId, String currencyId,
                                                       RequestId requestId, MutationKind kind, long amountMinorUnits);

    ProviderResult<MutationReceipt> futureshopsLookup(ServerLevel level, RequestId requestId);

    boolean futureshopsReceiptIntegrityValid();

    boolean futureshopsHasPendingReceipts();

    boolean futureshopsReceiptCapacityAvailable();

    void futureshopsLoadReceipts(CompoundTag tag);
}
