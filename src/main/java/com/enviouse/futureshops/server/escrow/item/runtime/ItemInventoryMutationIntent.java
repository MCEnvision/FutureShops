package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlotChange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ItemInventoryMutationIntent {
    public static final int MAX_EVIDENCE_BYTES = 16 * 1024 * 1024;

    private final ItemInventoryMutationToken token;
    private final ItemInventoryMutationReceipt plannedReceipt;
    private final List<ItemInventorySlotMutationEvidence> slotEvidence;

    ItemInventoryMutationIntent(
            ItemInventoryMutationToken token,
            ItemInventoryMutationReceipt plannedReceipt,
            List<ItemInventorySlotMutationEvidence> slotEvidence
    ) {
        this.token = Objects.requireNonNull(token, "token");
        this.plannedReceipt = Objects.requireNonNull(
                plannedReceipt, "plannedReceipt");
        this.slotEvidence = List.copyOf(Objects.requireNonNull(
                slotEvidence, "slotEvidence"));
        if (!this.plannedReceipt.token().equals(this.token)
                || this.slotEvidence.size() != token.changes().size()) {
            throw new IllegalArgumentException(
                    "Item inventory intent identity is invalid");
        }
        long evidenceBytes = 0L;
        Set<com.enviouse.futureshops.server.escrow.item.ItemInventorySlot>
                slots = new HashSet<>();
        for (int index = 0; index < this.slotEvidence.size(); index++) {
            ItemInventorySlotMutationEvidence evidence = Objects.requireNonNull(
                    this.slotEvidence.get(index), "evidence");
            ItemInventorySlotChange change = token.changes().get(index);
            if (!evidence.slot().equals(change.slot())
                    || !slots.add(evidence.slot())
                    || !evidence.hashesMatch(change.beforeHash(),
                    change.afterHash())) {
                throw new IllegalArgumentException(
                        "Item inventory intent slot evidence is invalid");
            }
            evidenceBytes = Math.addExact(evidenceBytes,
                    Math.addExact(evidence.beforeSnapshot().length,
                            evidence.afterSnapshot().length));
            if (evidenceBytes > MAX_EVIDENCE_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory intent evidence exceeds its limit");
            }
        }
    }

    public static ItemInventoryMutationIntent create(
            ItemInventoryMutationToken token,
            ItemInventoryMutationPlan plan,
            ItemInventoryMutationReceipt plannedReceipt
    ) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(plan, "plan");
        if (!token.matches(plan)) {
            throw new IllegalArgumentException(
                    "Item inventory intent plan does not match its token");
        }
        List<ItemInventorySlotMutationEvidence> evidence = new ArrayList<>(
                plan.changes().size());
        for (ItemInventorySlotChange change : plan.changes()) {
            evidence.add(ItemInventorySlotMutationEvidence.captureSnapshots(
                    change.slot(), plan.before().slotSnapshot(change.slot()),
                    plan.after().slotSnapshot(change.slot())));
        }
        return new ItemInventoryMutationIntent(token, plannedReceipt,
                evidence);
    }

    public ItemInventoryMutationToken token() {
        return token;
    }

    public ItemInventoryMutationReceipt plannedReceipt() {
        return plannedReceipt;
    }

    public List<ItemInventorySlotMutationEvidence> slotEvidence() {
        return slotEvidence;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryMutationIntent other
                && token.equals(other.token)
                && plannedReceipt.equals(other.plannedReceipt)
                && slotEvidence.equals(other.slotEvidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, plannedReceipt, slotEvidence);
    }
}
