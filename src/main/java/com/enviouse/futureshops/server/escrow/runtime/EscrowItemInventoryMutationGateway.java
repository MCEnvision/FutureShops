package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.DurableItemInventoryMutationGateway;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryGatewayResult;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalEntry;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransition;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransitionCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationAbort;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationQuarantine;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryTerminalTombstone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class EscrowItemInventoryMutationGateway
        implements DurableItemInventoryMutationGateway {
    private final EscrowRuntimeCoordinator coordinator;
    private final ItemInventoryJournalSavedData journal;
    private final ClaimSavedData claims;
    private final BooleanSupplier serverThreadCheck;

    EscrowItemInventoryMutationGateway(
            EscrowRuntimeCoordinator coordinator,
            ItemInventoryJournalSavedData journal,
            BooleanSupplier serverThreadCheck
    ) {
        this(coordinator, journal, new ClaimSavedData(),
                serverThreadCheck);
    }

    EscrowItemInventoryMutationGateway(
            EscrowRuntimeCoordinator coordinator,
            ItemInventoryJournalSavedData journal,
            ClaimSavedData claims,
            BooleanSupplier serverThreadCheck
    ) {
        this.coordinator = Objects.requireNonNull(
                coordinator, "coordinator");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.serverThreadCheck = Objects.requireNonNull(
                serverThreadCheck, "serverThreadCheck");
    }

    @Override
    public ItemInventoryGatewayResult appendPreparedDurably(
            ItemInventoryMutationIntent intent
    ) {
        return append(ItemInventoryJournalTransition.prepare(intent));
    }

    @Override
    public ItemInventoryGatewayResult appendCommittedDurably(
            ItemInventoryMutationReceipt receipt
    ) {
        requireServerThread();
        Optional<ExactItemClaimDeliveryCommit> claimDelivery =
                ExactItemClaimDeliveryPlanner.match(receipt, claims);
        if (claimDelivery.isPresent()) {
            ExactItemClaimDeliveryCommit commit =
                    claimDelivery.orElseThrow();
            EscrowCommitResult committed = coordinator
                    .commitItemInventoryMutation(commit.requestId(),
                            new EscrowJournalEvent(
                                    EscrowJournalEventType
                                    .EXACT_ITEM_CLAIM_DELIVERY_COMMIT,
                                    ExactItemClaimDeliveryCommitCodec.encode(
                                            commit)));
            ItemInventoryJournalEntry entry = journal.find(
                    commit.requestId()).orElseThrow(() ->
                    new EscrowRuntimeException(
                            "Exact item claim delivery was not materialized"));
            return new ItemInventoryGatewayResult(entry,
                    committed.replayed());
        }
        return append(ItemInventoryJournalTransition.commit(receipt));
    }

    @Override
    public ItemInventoryGatewayResult appendAbortedDurably(
            ItemInventoryMutationAbort abort
    ) {
        return append(ItemInventoryJournalTransition.abort(abort));
    }

    @Override
    public ItemInventoryGatewayResult appendQuarantinedDurably(
            ItemInventoryMutationQuarantine quarantine
    ) {
        return append(ItemInventoryJournalTransition.quarantine(quarantine));
    }

    @Override
    public Optional<ItemInventoryJournalEntry> find(UUID requestId) {
        requireServerThread();
        return journal.find(Objects.requireNonNull(requestId, "requestId"));
    }

    @Override
    public Optional<ItemInventoryTerminalTombstone> findTerminal(
            UUID requestId
    ) {
        requireServerThread();
        return journal.findTombstone(Objects.requireNonNull(
                requestId, "requestId"));
    }

    @Override
    public List<ItemInventoryJournalEntry> preparedForPlayer(
            UUID playerId,
            int limit
    ) {
        requireServerThread();
        return journal.preparedForPlayer(
                Objects.requireNonNull(playerId, "playerId"), limit);
    }

    @Override
    public boolean playerQuarantined(UUID playerId) {
        requireServerThread();
        return journal.playerQuarantined(Objects.requireNonNull(
                playerId, "playerId"));
    }

    @Override
    public boolean hasLaterRequestForPlayer(
            UUID playerId,
            UUID requestId
    ) {
        requireServerThread();
        return journal.hasLaterRequestForPlayer(
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(requestId, "requestId"));
    }

    private ItemInventoryGatewayResult append(
            ItemInventoryJournalTransition transition
    ) {
        requireServerThread();
        byte[] body = ItemInventoryJournalTransitionCodec.encode(
                Objects.requireNonNull(transition, "transition"));
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.ITEM_INVENTORY_MUTATION, body);
        EscrowCommitResult committed = coordinator
                .commitItemInventoryMutation(
                        transition.requestId(), event);
        ItemInventoryJournalEntry entry = journal.find(
                transition.requestId()).orElseThrow(() ->
                new EscrowRuntimeException(
                        "Item inventory mutation was not materialized"));
        if (!entry.intent().token().equals(transition.token())) {
            throw new EscrowRuntimeException(
                    "Item inventory mutation materialized a conflicting request");
        }
        return new ItemInventoryGatewayResult(entry,
                committed.replayed());
    }

    private void requireServerThread() {
        if (!serverThreadCheck.getAsBoolean()) {
            throw new EscrowRuntimeException(
                    "Item inventory mutation requires the server thread");
        }
    }
}
