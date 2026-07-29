package com.enviouse.futureshops.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.Optional;
import java.util.UUID;

/**
 * Fired AFTER a durable Auction House commit (plan §18 Phase 6 API events). Deliberately
 * post-commit only: the escrow path never runs listeners between a hold and a commit, so a
 * throwing listener can log-spam but can never poison, roll back, or double-apply a transaction
 * (plan §15). Not cancellable for the same reason — the economic result already happened.
 */
public class MarketListingEvent extends Event {

    public enum Type {
        CREATED,
        BID,
        SOLD,
        CANCELLED,
        EXPIRED,
        SETTLED
    }

    private final Type type;
    private final UUID listingId;
    private final UUID sellerId;
    private final Optional<UUID> counterpartyId;
    private final long amountMinor;
    private final long revision;

    public MarketListingEvent(Type type, UUID listingId, UUID sellerId,
                              Optional<UUID> counterpartyId, long amountMinor, long revision) {
        this.type = type;
        this.listingId = listingId;
        this.sellerId = sellerId;
        this.counterpartyId = counterpartyId;
        this.amountMinor = amountMinor;
        this.revision = revision;
    }

    public Type getType() { return type; }
    public UUID getListingId() { return listingId; }
    public UUID getSellerId() { return sellerId; }
    /** Buyer / bidder when the operation has one. */
    public Optional<UUID> getCounterpartyId() { return counterpartyId; }
    /** Bid amount, buyout price, or 0 for value-free transitions. */
    public long getAmountMinor() { return amountMinor; }
    public long getRevision() { return revision; }
}
