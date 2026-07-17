package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.market.auction.AuctionBidStanding;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionOperationResult;
import com.enviouse.futureshops.server.market.auction.AuctionOperationType;
import com.enviouse.futureshops.server.market.auction.AuctionSale;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuctionEscrowConservationValidator {
    private AuctionEscrowConservationValidator() {
    }

    static void validate(
            UUID requestId,
            UUID listingId,
            AuctionOperationType operation,
            List<AuctionHouseMutation> mutations,
            Optional<AuctionEscrowItemCustody> custody,
            List<AuctionEscrowWalletSnapshot> wallets,
            String currencyId,
            Instant decidedAt,
            Optional<EscrowTransaction> transaction,
            Optional<LedgerTransaction> ledger,
            List<EscrowClaim> claims
    ) {
        AuctionOperationResult result = mutations.get(0)
                .requestReceipt().result();
        AuctionListing listing = result.listing().orElseThrow();
        requireTransaction(operation, listing, currencyId,
                transaction);
        Map<LedgerAccountId, Long> expected = new HashMap<>();
        Set<UUID> expectedClaims = new HashSet<>();
        switch (operation) {
            case CREATE -> validateCreate(requestId, listing, custody,
                    wallets, claims, expected);
            case BID -> validateBid(requestId, result, listing, wallets,
                    claims, expected, expectedClaims);
            case BUY_NOW -> validateSettlement(requestId, result,
                    listing, custody, wallets, claims, expected,
                    expectedClaims, true);
            case CANCEL -> {
                requireNoWallets(wallets,
                        "Auction cancellation wallet evidence is invalid");
                validateItemReturn(requestId, listing,
                        listing.sellerId(), custody, claims,
                        expectedClaims);
            }
            case EXPIRE -> {
                if (listing.sale().isPresent()) {
                    validateSettlement(requestId, result, listing,
                            custody, wallets, claims, expected,
                            expectedClaims, false);
                } else {
                    if (listing.state()
                            != AuctionListingState.ENDED_UNSOLD) {
                        throw invalid(
                                "Auction unsold expiration state is invalid");
                    }
                    requireNoWallets(wallets,
                            "Auction unsold expiration wallet evidence is invalid");
                    validateItemReturn(requestId, listing,
                            listing.sellerId(), custody, claims,
                            expectedClaims);
                }
            }
            case SETTLE -> validateSettlement(requestId, result,
                    listing, custody, wallets, claims, expected,
                    expectedClaims, false);
            case FREEZE, RESUME -> {
                if (ledger.isPresent()) {
                    throw invalid(
                            "Auction control transition has a ledger");
                }
                return;
            }
        }
        for (EscrowClaim claim : claims) {
            if (!claim.createdAt().equals(decidedAt)
                    || !claim.updatedAt().equals(decidedAt)
                    || claim.status() != ClaimStatus.PENDING) {
                throw invalid("Auction claim timestamp is invalid");
            }
        }
        if (!expectedClaims.equals(claimIds(claims))) {
            throw invalid("Auction claim set is not conserved");
        }
        expected.entrySet().removeIf(entry -> entry.getValue() == 0L);
        if (expected.isEmpty()) {
            if (ledger.isPresent()) {
                throw invalid("Auction value free commit has a ledger");
            }
        } else {
            LedgerTransaction value = ledger.orElseThrow(() -> invalid(
                    "Auction value commit is missing its ledger"));
            if (!value.transactionId().equals(
                    AuctionEscrowIds.ledgerTransactionId(requestId))
                    || !value.idempotencyKey().equals(
                    "auction.escrow." + requestId)
                    || !aggregate(value).equals(expected)) {
                throw invalid("Auction ledger is not conserved");
            }
        }
    }

    private static void validateCreate(
            UUID requestId,
            AuctionListing listing,
            Optional<AuctionEscrowItemCustody> custody,
            List<AuctionEscrowWalletSnapshot> wallets,
            List<EscrowClaim> claims,
            Map<LedgerAccountId, Long> expected
    ) {
        AuctionEscrowItemCustody items = custody.orElseThrow(() -> invalid(
                "Auction creation custody is missing"));
        if (!items.receipt().token().transactionId().equals(
                listing.activationTransactionId()) || !claims.isEmpty()) {
            throw invalid("Auction creation custody is invalid");
        }
        AuctionEscrowWalletSnapshot seller = wallet(wallets,
                listing.sellerId());
        long fee = listing.rules().listingFeeMinor();
        seller.requireAvailable(fee);
        if (wallets.size() != 1) {
            throw invalid("Auction creation wallet evidence is invalid");
        }
        if (fee > 0L) {
            add(expected, AuctionEscrowLedgerAccounts.wallet(
                    listing.sellerId()), Math.negateExact(fee));
            add(expected, AuctionEscrowLedgerAccounts.fee(), fee);
        }
    }

    private static void validateBid(
            UUID requestId,
            AuctionOperationResult result,
            AuctionListing listing,
            List<AuctionEscrowWalletSnapshot> wallets,
            List<EscrowClaim> claims,
            Map<LedgerAccountId, Long> expected,
            Set<UUID> expectedClaims
    ) {
        AuctionBidStanding standing = listing.highestBid().orElseThrow();
        long delta = result.requiredHoldDeltaMinor();
        AuctionEscrowWalletSnapshot bidder = wallet(wallets,
                standing.bidderId());
        bidder.requireAvailable(delta);
        if (wallets.size() != 1) {
            throw invalid("Auction bid wallet evidence is invalid");
        }
        add(expected, AuctionEscrowLedgerAccounts.wallet(
                standing.bidderId()), Math.negateExact(delta));
        add(expected, AuctionEscrowLedgerAccounts.hold(
                standing.holdAccountId()), delta);
        validateRefund(requestId, result, claims, expected,
                expectedClaims);
    }

    private static void validateSettlement(
            UUID requestId,
            AuctionOperationResult result,
            AuctionListing listing,
            Optional<AuctionEscrowItemCustody> custody,
            List<AuctionEscrowWalletSnapshot> wallets,
            List<EscrowClaim> claims,
            Map<LedgerAccountId, Long> expected,
            Set<UUID> expectedClaims,
            boolean buyNow
    ) {
        AuctionSale sale = listing.sale().orElseThrow(() -> invalid(
                "Auction sale evidence is missing"));
        if (buyNow) {
            AuctionEscrowWalletSnapshot buyer = wallet(wallets,
                    sale.buyerId());
            buyer.requireAvailable(result.requiredHoldDeltaMinor());
            add(expected, AuctionEscrowLedgerAccounts.wallet(
                    sale.buyerId()), Math.negateExact(
                    result.requiredHoldDeltaMinor()));
            add(expected, AuctionEscrowLedgerAccounts.hold(
                    sale.holdAccountId()),
                    result.requiredHoldDeltaMinor());
            validateRefund(requestId, result, claims, expected,
                    expectedClaims);
        } else if (result.requiredHoldDeltaMinor() != 0L
                || result.refundMinor() != 0L) {
            throw invalid("Auction settlement hold delta is invalid");
        }
        add(expected, AuctionEscrowLedgerAccounts.hold(
                sale.holdAccountId()), Math.negateExact(sale.priceMinor()));
        long tax = listing.rules().saleTax(sale.priceMinor());
        long sellerNet = Math.subtractExact(sale.priceMinor(), tax);
        if (sellerNet > 0L) {
            UUID claimId = AuctionEscrowIds.sellerProceedsClaimId(
                    requestId, listing.sellerId());
            EscrowClaim claim = claim(claims, claimId);
            requireMoneyClaim(claim, listing.sellerId(), ClaimKind.MONEY,
                    sellerNet,
                    "auction." + listing.listingId()
                            + ".seller.proceeds");
            expectedClaims.add(claimId);
            add(expected, AuctionEscrowLedgerAccounts.claim(claimId),
                    sellerNet);
        }
        if (tax > 0L) {
            add(expected, AuctionEscrowLedgerAccounts.treasury(), tax);
        }
        validateItemReturn(requestId, listing, sale.buyerId(), custody,
                claims, expectedClaims);
        int requiredWallets = buyNow ? 1 : 0;
        if (wallets.size() != requiredWallets) {
            throw invalid("Auction settlement wallet evidence is invalid");
        }
    }

    private static void validateRefund(
            UUID requestId,
            AuctionOperationResult result,
            List<EscrowClaim> claims,
            Map<LedgerAccountId, Long> expected,
            Set<UUID> expectedClaims
    ) {
        if (result.refundMinor() == 0L) {
            return;
        }
        UUID owner = result.refundPlayerId().orElseThrow();
        AuctionBidStanding displaced = result.displacedBid()
                .orElseThrow();
        if (!displaced.bidderId().equals(owner)
                || displaced.amountMinor() != result.refundMinor()) {
            throw invalid("Auction displaced bid evidence is invalid");
        }
        UUID claimId = AuctionEscrowIds.refundClaimId(requestId, owner);
        EscrowClaim claim = claim(claims, claimId);
        requireMoneyClaim(claim, owner, ClaimKind.REFUND,
                result.refundMinor(), "auction."
                        + result.listingId() + ".outbid.refund");
        expectedClaims.add(claimId);
        add(expected, AuctionEscrowLedgerAccounts.hold(
                displaced.holdAccountId()),
                Math.negateExact(result.refundMinor()));
        add(expected, AuctionEscrowLedgerAccounts.claim(claimId),
                result.refundMinor());
    }

    private static void validateItemReturn(
            UUID requestId,
            AuctionListing listing,
            UUID ownerId,
            Optional<AuctionEscrowItemCustody> custody,
            List<EscrowClaim> claims,
            Set<UUID> expectedClaims
    ) {
        AuctionEscrowItemCustody items = custody.orElseThrow(() -> invalid(
                "Auction terminal custody is missing"));
        if (!items.activationTransactionId().equals(
                listing.activationTransactionId())) {
            throw invalid("Auction terminal custody identity is invalid");
        }
        for (int index = 0; index < items.exactItems().size(); index++) {
            var item = items.exactItems().get(index);
            UUID claimId = AuctionEscrowIds.itemClaimId(requestId,
                    ownerId, index);
            EscrowClaim claim = claim(claims, claimId);
            if (!claim.ownerId().equals(ownerId)
                    || claim.kind() != ClaimKind.ITEM
                    || claim.originalUnits() != item.stackCount()
                    || claim.remainingUnits() != item.stackCount()
                    || !claim.sourceKey().equals("auction."
                    + listing.listingId() + ".item." + index)
                    || !Arrays.equals(claim.payload(),
                    ExactItemClaimPayloadCodec.encode(item))) {
                throw invalid("Auction exact item claim is invalid");
            }
            expectedClaims.add(claimId);
        }
    }

    private static void requireTransaction(
            AuctionOperationType operation,
            AuctionListing listing,
            String currencyId,
            Optional<EscrowTransaction> transaction
    ) {
        if (operation == AuctionOperationType.FREEZE
                || operation == AuctionOperationType.RESUME) {
            return;
        }
        EscrowTransaction value = transaction.orElseThrow();
        EscrowOperation expected = switch (operation) {
            case CREATE -> EscrowOperation.AUCTION_LISTING;
            case BID -> EscrowOperation.AUCTION_BID;
            case BUY_NOW -> EscrowOperation.AUCTION_BUY_NOW;
            case CANCEL, EXPIRE, SETTLE ->
                    EscrowOperation.AUCTION_SETTLEMENT;
            case FREEZE, RESUME -> throw new IllegalStateException();
        };
        if (value.operation() != expected
                || value.configRevision() != listing.rules().configRevision()) {
            throw invalid("Auction escrow transaction policy is invalid");
        }
        for (EscrowAssetLot lot : value.assetLots()) {
            lot.money().ifPresent(money -> {
                if (!money.currencyId().equals(currencyId)) {
                    throw invalid(
                            "Auction escrow transaction currency differs");
                }
            });
        }
    }

    private static AuctionEscrowWalletSnapshot wallet(
            List<AuctionEscrowWalletSnapshot> wallets,
            UUID ownerId
    ) {
        return wallets.stream().filter(value ->
                value.playerId().equals(ownerId)).findFirst()
                .orElseThrow(() -> invalid(
                        "Auction wallet snapshot is missing"));
    }

    private static void requireNoWallets(
            List<AuctionEscrowWalletSnapshot> wallets,
            String message
    ) {
        if (!wallets.isEmpty()) {
            throw invalid(message);
        }
    }

    private static EscrowClaim claim(
            List<EscrowClaim> claims,
            UUID claimId
    ) {
        return claims.stream().filter(value ->
                value.claimId().equals(claimId)).findFirst()
                .orElseThrow(() -> invalid("Auction claim is missing"));
    }

    private static void requireMoneyClaim(
            EscrowClaim claim,
            UUID ownerId,
            ClaimKind kind,
            long amount,
            String sourceKey
    ) {
        if (!claim.ownerId().equals(ownerId) || claim.kind() != kind
                || claim.originalUnits() != amount
                || claim.remainingUnits() != amount
                || claim.payload().length != 0
                || !claim.sourceKey().equals(sourceKey)) {
            throw invalid("Auction money claim is invalid");
        }
    }

    private static Set<UUID> claimIds(List<EscrowClaim> claims) {
        Set<UUID> result = new HashSet<>();
        for (EscrowClaim claim : claims) {
            result.add(claim.claimId());
        }
        return result;
    }

    private static Map<LedgerAccountId, Long> aggregate(
            LedgerTransaction transaction
    ) {
        Map<LedgerAccountId, Long> result = new HashMap<>();
        for (LedgerLeg leg : transaction.legs()) {
            add(result, leg.account(), leg.deltaMinor());
        }
        result.entrySet().removeIf(entry -> entry.getValue() == 0L);
        return result;
    }

    private static void add(
            Map<LedgerAccountId, Long> values,
            LedgerAccountId account,
            long delta
    ) {
        values.merge(account, delta, Math::addExact);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
