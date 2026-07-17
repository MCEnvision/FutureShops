package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionOperationType;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AuctionEscrowCommit(
        UUID requestId,
        UUID listingId,
        AuctionOperationType operation,
        List<AuctionHouseMutation> bookMutations,
        Optional<String> createIntentFingerprint,
        Optional<AuctionEscrowItemCustody> itemCustody,
        List<AuctionEscrowWalletSnapshot> walletSnapshots,
        String currencyId,
        Instant decidedAt,
        Optional<EscrowTransaction> completedTransaction,
        Optional<LedgerTransaction> ledgerTransaction,
        List<EscrowClaim> claims
) {
    public static final int MAX_BOOK_MUTATIONS = 2;
    public static final int MAX_WALLET_SNAPSHOTS = 2;
    public static final int MAX_CLAIMS = 260;

    public AuctionEscrowCommit {
        requestId = AuctionEscrowIds.requireId(requestId, "requestId");
        listingId = AuctionEscrowIds.requireId(listingId, "listingId");
        operation = Objects.requireNonNull(operation, "operation");
        bookMutations = List.copyOf(Objects.requireNonNull(
                bookMutations, "bookMutations"));
        createIntentFingerprint = Objects.requireNonNull(
                createIntentFingerprint, "createIntentFingerprint");
        itemCustody = Objects.requireNonNull(itemCustody,
                "itemCustody");
        walletSnapshots = Objects.requireNonNull(walletSnapshots,
                "walletSnapshots").stream()
                .sorted(Comparator.comparing(value ->
                        value.playerId().toString())).toList();
        currencyId = AuctionCreateEscrowIntent.requireCurrencyId(
                currencyId);
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        completedTransaction = Objects.requireNonNull(
                completedTransaction, "completedTransaction");
        ledgerTransaction = Objects.requireNonNull(
                ledgerTransaction, "ledgerTransaction");
        claims = Objects.requireNonNull(claims, "claims").stream()
                .sorted(Comparator.comparing(value ->
                        value.claimId().toString())).toList();
        validateShape(requestId, listingId, operation, bookMutations,
                createIntentFingerprint, itemCustody, walletSnapshots,
                completedTransaction, ledgerTransaction, claims);
        AuctionEscrowConservationValidator.validate(requestId, listingId,
                operation, bookMutations, itemCustody, walletSnapshots,
                currencyId, decidedAt, completedTransaction,
                ledgerTransaction, claims);
    }

    public AuctionHouseMutation firstMutation() {
        return bookMutations.get(0);
    }

    public AuctionListing firstListing() {
        return firstMutation().requestReceipt().result().listing()
                .orElseThrow();
    }

    public AuctionListing finalListing() {
        return bookMutations.get(bookMutations.size() - 1)
                .requestReceipt().result().listing().orElseThrow();
    }

    public String fingerprint() {
        return AuctionEscrowCommitCodec.fingerprint(this);
    }

    private static void validateShape(
            UUID requestId,
            UUID listingId,
            AuctionOperationType operation,
            List<AuctionHouseMutation> mutations,
            Optional<String> createFingerprint,
            Optional<AuctionEscrowItemCustody> custody,
            List<AuctionEscrowWalletSnapshot> wallets,
            Optional<EscrowTransaction> transaction,
            Optional<LedgerTransaction> ledger,
            List<EscrowClaim> claims
    ) {
        if (mutations.isEmpty()
                || mutations.size() > MAX_BOOK_MUTATIONS
                || wallets.size() > MAX_WALLET_SNAPSHOTS
                || claims.size() > MAX_CLAIMS) {
            throw new IllegalArgumentException(
                    "Auction escrow composite size is invalid");
        }
        Set<UUID> walletOwners = new HashSet<>();
        for (AuctionEscrowWalletSnapshot wallet : wallets) {
            if (!walletOwners.add(wallet.playerId())) {
                throw new IllegalArgumentException(
                        "Auction wallet snapshot owner is duplicated");
            }
        }
        Set<UUID> claimIds = new HashSet<>();
        for (EscrowClaim claim : claims) {
            if (!claimIds.add(claim.claimId())
                    || !claim.transactionId().equals(requestId)) {
                throw new IllegalArgumentException(
                        "Auction escrow claim identity is invalid");
            }
        }
        for (int index = 0; index < mutations.size(); index++) {
            AuctionHouseMutation mutation = Objects.requireNonNull(
                    mutations.get(index), "mutation");
            var result = mutation.requestReceipt().result();
            if (!result.durablyApplied() || result.replayed()
                    || !result.listingId().equals(listingId)
                    || index == 0 && (!mutation.requestId().equals(requestId)
                    || result.operation() != operation)
                    || index > 0 && (result.operation()
                    != AuctionOperationType.SETTLE
                    || !mutation.requestId().equals(
                    AuctionEscrowIds.settlementBookRequestId(requestId)))) {
                throw new IllegalArgumentException(
                        "Auction escrow book mutation shape is invalid");
            }
        }
        if (mutations.size() == 2) {
            AuctionListing first = mutations.get(0).requestReceipt()
                    .result().listing().orElseThrow();
            AuctionListing last = mutations.get(1).requestReceipt()
                    .result().listing().orElseThrow();
            if (first.state() != AuctionListingState.SOLD_PENDING
                    || last.state() != AuctionListingState.SETTLED
                    || last.revision() != Math.addExact(
                    first.revision(), 1L)) {
                throw new IllegalArgumentException(
                        "Auction escrow settlement mutation chain is invalid");
            }
        }
        boolean create = operation == AuctionOperationType.CREATE;
        if (create != createFingerprint.isPresent()
                || createFingerprint.filter(value -> value.length() != 64
                || !value.chars().allMatch(character ->
                Character.digit(character, 16) >= 0)).isPresent()) {
            throw new IllegalArgumentException(
                    "Auction creation fingerprint shape is invalid");
        }
        boolean valueFree = operation == AuctionOperationType.FREEZE
                || operation == AuctionOperationType.RESUME;
        if (valueFree) {
            if (custody.isPresent() || !wallets.isEmpty()
                    || transaction.isPresent() || ledger.isPresent()
                    || !claims.isEmpty()) {
                throw new IllegalArgumentException(
                        "Auction control transition cannot move value");
            }
        } else if (transaction.isEmpty()
                || transaction.orElseThrow().state()
                != EscrowState.COMPLETED
                || !transaction.orElseThrow().transactionId().value()
                .equals(requestId)) {
            throw new IllegalArgumentException(
                    "Auction escrow transaction evidence is invalid");
        }
        if (custody.isPresent()
                && (!custody.orElseThrow().listingId().equals(listingId)
                || !custody.orElseThrow().matches(
                mutations.get(0).requestReceipt().result().listing()
                        .orElseThrow().itemLot()))) {
            throw new IllegalArgumentException(
                    "Auction escrow custody listing evidence is invalid");
        }
    }
}
