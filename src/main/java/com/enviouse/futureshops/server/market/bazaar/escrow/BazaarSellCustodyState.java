package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BazaarSellCustodyState(
        BazaarSellItemCustody custody,
        List<Integer> remainingCounts
) {
    public BazaarSellCustodyState {
        custody = Objects.requireNonNull(custody, "custody");
        remainingCounts = List.copyOf(Objects.requireNonNull(
                remainingCounts, "remainingCounts"));
        if (remainingCounts.size() != custody.exactItems().size()) {
            throw new IllegalArgumentException(
                    "Bazaar custody remainder count is invalid");
        }
        for (int index = 0; index < remainingCounts.size(); index++) {
            Integer remaining = Objects.requireNonNull(
                    remainingCounts.get(index), "remainingCount");
            if (remaining < 0 || remaining
                    > custody.exactItems().get(index).stackCount()) {
                throw new IllegalArgumentException(
                        "Bazaar custody remainder is invalid");
            }
        }
    }

    public static BazaarSellCustodyState full(
            BazaarSellItemCustody custody
    ) {
        Objects.requireNonNull(custody, "custody");
        return new BazaarSellCustodyState(custody,
                custody.exactItems().stream()
                        .map(ExactItemClaimPayload::stackCount).toList());
    }

    public int remainingQuantity() {
        int total = 0;
        for (int remaining : remainingCounts) {
            total = Math.addExact(total, remaining);
        }
        return total;
    }

    public Allocation allocate(
            int quantity,
            UUID transactionId,
            String sourceKey
    ) {
        BazaarEscrowIds.requireId(transactionId, "transactionId");
        Objects.requireNonNull(sourceKey, "sourceKey");
        if (quantity <= 0 || quantity > remainingQuantity()) {
            throw new IllegalArgumentException(
                    "Bazaar custody allocation quantity is invalid");
        }
        List<Integer> next = new ArrayList<>(remainingCounts);
        List<Fragment> fragments = new ArrayList<>();
        int needed = quantity;
        for (int index = 0; index < next.size() && needed > 0; index++) {
            int available = next.get(index);
            if (available == 0) {
                continue;
            }
            int taken = Math.min(available, needed);
            fragments.add(new Fragment(index, taken));
            next.set(index, Math.subtractExact(available, taken));
            needed = Math.subtractExact(needed, taken);
        }
        if (needed != 0) {
            throw new IllegalStateException(
                    "Bazaar custody allocation is incomplete");
        }
        List<ExactItemClaimPayload> outputs = new ArrayList<>(
                fragments.size());
        for (int index = 0; index < fragments.size(); index++) {
            Fragment fragment = fragments.get(index);
            ExactItemClaimPayload source = custody.exactItems().get(
                    fragment.sourceIndex());
            var stack = ItemStackSnapshotCodec.decode(
                    source.serializedStackSnapshot());
            stack.setCount(fragment.quantity());
            outputs.add(ExactItemClaimPayload.preserveRaw(transactionId,
                    sourceKey, index, fragments.size(),
                    source.registryItemId(), fragment.quantity(),
                    source.canonicalOneCountTemplate(),
                    ItemStackSnapshotCodec.encode(stack)));
        }
        return new Allocation(new BazaarSellCustodyState(custody, next),
                outputs);
    }

    public record Allocation(
            BazaarSellCustodyState remaining,
            List<ExactItemClaimPayload> claimPayloads
    ) {
        public Allocation {
            remaining = Objects.requireNonNull(remaining, "remaining");
            claimPayloads = List.copyOf(Objects.requireNonNull(
                    claimPayloads, "claimPayloads"));
            if (claimPayloads.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bazaar custody allocation has no payloads");
            }
        }
    }

    private record Fragment(int sourceIndex, int quantity) {
    }
}
