package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In memory receipt store used by lightweight server fixtures. */
public final class InMemoryInternalEconomyReceiptStore implements InternalEconomyReceiptStore {
    private final Map<RequestId, MutationReceipt> receipts = new LinkedHashMap<>();

    @Override
    public synchronized Optional<MutationReceipt> find(RequestId requestId) {
        return Optional.ofNullable(receipts.get(Objects.requireNonNull(requestId, "requestId")));
    }

    @Override
    public synchronized void put(MutationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        MutationReceipt existing = receipts.putIfAbsent(receipt.requestId(), receipt);
        if (existing != null && !existing.equals(receipt)) {
            throw new IllegalStateException("receipt request conflicts with existing outcome");
        }
    }
}
