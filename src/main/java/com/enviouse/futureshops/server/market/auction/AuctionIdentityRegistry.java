package com.enviouse.futureshops.server.market.auction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class AuctionIdentityRegistry {
    private final Map<UUID, Binding> owners = new HashMap<>();

    boolean isUnused(UUID identity) {
        return !owners.containsKey(Objects.requireNonNull(identity, "identity"));
    }

    boolean ownsHoldAccount(UUID identity, UUID listingId, UUID participantId) {
        return new Binding(listingId, Role.HOLD_ACCOUNT, participantId, null)
            .equals(owners.get(Objects.requireNonNull(identity, "identity")));
    }

    void claimListing(AuctionListing listing) {
        Objects.requireNonNull(listing, "listing");
        UUID listingId = listing.listingId();
        claim(listing.activationTransactionId(),
            new Binding(listingId, Role.ACTIVATION_TRANSACTION, null, null));
        claim(listing.itemLot().custodyLotId(),
            new Binding(listingId, Role.CUSTODY_LOT, null, null));
        listing.highestBid().ifPresent(bid -> claimBid(listingId, bid));
        listing.sale().ifPresent(sale -> claimSale(listingId, sale));
        listing.terminalTransactionId().ifPresent(identity -> claim(identity,
            new Binding(listingId, Role.TERMINAL_TRANSACTION, null, null)));
    }

    private void claimBid(UUID listingId, AuctionBidStanding bid) {
        claim(bid.bidId(), new Binding(
            listingId, Role.BID, bid.bidderId(), bid.holdAccountId()));
        claim(bid.holdAccountId(), new Binding(
            listingId, Role.HOLD_ACCOUNT, bid.bidderId(), null));
        claim(bid.holdTransactionId(), new Binding(
            listingId, Role.HOLD_TRANSACTION, bid.bidderId(), bid.bidId()));
    }

    private void claimSale(UUID listingId, AuctionSale sale) {
        claim(sale.holdAccountId(), new Binding(
            listingId, Role.HOLD_ACCOUNT, sale.buyerId(), null));
        if (sale.buyout()) {
            claim(sale.holdTransactionId(), new Binding(
                listingId, Role.HOLD_TRANSACTION, sale.buyerId(), sale.settlementTransactionId()));
        }
        claim(sale.settlementTransactionId(), new Binding(
            listingId, Role.TERMINAL_TRANSACTION, null, null));
    }

    private void claim(UUID identity, Binding binding) {
        Binding previous = owners.putIfAbsent(
            Objects.requireNonNull(identity, "identity"), Objects.requireNonNull(binding, "binding"));
        if (previous != null && !previous.equals(binding)) {
            throw new IllegalArgumentException("Auction identity has conflicting ownership.");
        }
    }

    private enum Role {
        ACTIVATION_TRANSACTION,
        CUSTODY_LOT,
        BID,
        HOLD_TRANSACTION,
        HOLD_ACCOUNT,
        TERMINAL_TRANSACTION
    }

    private record Binding(
        UUID listingId,
        Role role,
        UUID participantId,
        UUID contextId
    ) {
        private Binding {
            Objects.requireNonNull(listingId, "listingId");
            Objects.requireNonNull(role, "role");
        }
    }
}
