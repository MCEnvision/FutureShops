package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DurableItemInventoryMutationGateway {
    ItemInventoryGatewayResult appendPreparedDurably(
            ItemInventoryMutationIntent intent
    );

    ItemInventoryGatewayResult appendCommittedDurably(
            ItemInventoryMutationReceipt receipt
    );

    ItemInventoryGatewayResult appendAbortedDurably(
            ItemInventoryMutationAbort abort
    );

    ItemInventoryGatewayResult appendQuarantinedDurably(
            ItemInventoryMutationQuarantine quarantine
    );

    Optional<ItemInventoryJournalEntry> find(UUID requestId);

    default Optional<ItemInventoryTerminalTombstone> findTerminal(
            UUID requestId
    ) {
        return Optional.empty();
    }

    List<ItemInventoryJournalEntry> preparedForPlayer(
            UUID playerId,
            int limit
    );

    default boolean playerQuarantined(UUID playerId) {
        return false;
    }

    default boolean hasLaterRequestForPlayer(
            UUID playerId,
            UUID requestId
    ) {
        return false;
    }
}
