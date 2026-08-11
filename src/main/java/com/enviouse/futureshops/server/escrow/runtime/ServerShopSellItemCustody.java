package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.DurableItemInventoryMutationGateway;
import com.enviouse.futureshops.server.escrow.item.runtime.ExactItemInventoryRuntime;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryAccess;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryExecutionResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalEntry;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface ServerShopSellItemCustody {
    Inspection inspect(UUID requestId);

    ItemInventoryExecutionResult extract(
            UUID transactionId,
            UUID requestId,
            List<ItemInventoryBatchEntry> entries
    );

    static ServerShopSellItemCustody exact(
            ExactItemInventoryRuntime runtime,
            ItemInventoryAccess access,
            DurableItemInventoryMutationGateway gateway
    ) {
        return new ExactBridge(runtime, access, gateway);
    }

    enum State {
        NONE,
        PREPARED,
        COMMITTED,
        ABORTED,
        QUARANTINED
    }

    record Inspection(
            State state,
            Optional<ItemInventoryMutationReceipt> receipt
    ) {
        public Inspection {
            state = Objects.requireNonNull(state, "state");
            receipt = Objects.requireNonNull(receipt, "receipt");
            if (state == State.NONE != receipt.isEmpty()) {
                throw new IllegalArgumentException(
                        "Server shop sell custody inspection is invalid");
            }
        }

        public static Inspection none() {
            return new Inspection(State.NONE, Optional.empty());
        }
    }

    final class ExactBridge implements ServerShopSellItemCustody {
        private final ExactItemInventoryRuntime runtime;
        private final ItemInventoryAccess access;
        private final DurableItemInventoryMutationGateway gateway;

        private ExactBridge(
                ExactItemInventoryRuntime runtime,
                ItemInventoryAccess access,
                DurableItemInventoryMutationGateway gateway
        ) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            this.access = Objects.requireNonNull(access, "access");
            this.gateway = Objects.requireNonNull(gateway, "gateway");
        }

        @Override
        public Inspection inspect(UUID requestId) {
            Optional<ItemInventoryJournalEntry> found = gateway.find(
                    Objects.requireNonNull(requestId, "requestId"));
            if (found.isEmpty()) {
                return Inspection.none();
            }
            ItemInventoryJournalEntry entry = found.orElseThrow();
            ItemInventoryMutationReceipt receipt = entry.status()
                    == ItemInventoryJournalStatus.COMMITTED
                    ? entry.committedReceipt().orElseThrow()
                    : entry.intent().plannedReceipt();
            State state = switch (entry.status()) {
                case PREPARED -> State.PREPARED;
                case COMMITTED -> State.COMMITTED;
                case ABORTED -> State.ABORTED;
                case QUARANTINED -> State.QUARANTINED;
            };
            return new Inspection(state, Optional.of(receipt));
        }

        @Override
        public ItemInventoryExecutionResult extract(
                UUID transactionId,
                UUID requestId,
                List<ItemInventoryBatchEntry> entries
        ) {
            return runtime.execute(access, transactionId, requestId,
                    entries);
        }
    }
}
