package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import com.enviouse.futureshops.server.market.auction.AuctionBuyNowCommand;
import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionOperationResult;
import com.enviouse.futureshops.server.market.auction.AuctionOperationType;
import com.enviouse.futureshops.server.market.auction.CancelAuctionCommand;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;
import com.enviouse.futureshops.server.market.auction.ExpireAuctionCommand;
import com.enviouse.futureshops.server.market.auction.FreezeAuctionCommand;
import com.enviouse.futureshops.server.market.auction.PlaceAuctionBidCommand;
import com.enviouse.futureshops.server.market.auction.ResumeAuctionCommand;
import com.enviouse.futureshops.server.market.auction.SettleAuctionCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuctionEscrowLifecyclePlanner {
    private static final EscrowParty MODULE = EscrowParty.module(
            "auction_house");
    private static final EscrowParty TREASURY = EscrowParty.system(
            "auction_treasury");

    private AuctionEscrowLifecyclePlanner() {
    }

    public static AuctionCreateEscrowIntent prepareCreate(
            CreateAuctionCommand command,
            ItemInventoryMutationIntent itemMutationIntent,
            AuctionEscrowItemCustody plannedCustody,
            AuctionEscrowWalletSnapshot sellerWallet,
            String currencyId,
            Instant preparedAt
    ) {
        return AuctionCreateEscrowIntent.prepared(command,
                itemMutationIntent, plannedCustody, sellerWallet,
                currencyId, preparedAt);
    }

    public static AuctionEscrowCommit commitCreate(
            AuctionHouseSnapshot snapshot,
            AuctionCreateEscrowIntent intent,
            ItemInventoryMutationReceipt committedReceipt
    ) {
        requirePrepared(intent);
        if (!intent.plannedCustody().receipt().equals(
                Objects.requireNonNull(committedReceipt,
                        "committedReceipt"))) {
            throw new IllegalArgumentException(
                    "Auction committed custody differs from its durable intent");
        }
        MutationPlan plan = create(snapshot, intent.command());
        AuctionListing listing = plan.firstResult().listing()
                .orElseThrow();
        long fee = listing.rules().listingFeeMinor();
        List<LedgerLeg> legs = new ArrayList<>();
        if (fee > 0L) {
            legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.wallet(
                    listing.sellerId()), Math.negateExact(fee)));
            legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.fee(),
                    fee));
        }
        Optional<LedgerTransaction> ledger = ledger(
                intent.requestId(), "Auction listing", legs);
        EscrowTransaction transaction = transaction(intent.requestId(),
                EscrowOperation.AUCTION_LISTING, listing,
                intent.currencyId(), intent.plannedCustody().receipt()
                        .appliedAt(), listing.sellerId(),
                intent.plannedCustody(), Optional.empty(), 0L, fee);
        return new AuctionEscrowCommit(intent.requestId(),
                intent.listingId(), AuctionOperationType.CREATE,
                plan.mutations(), Optional.of(intent.intentFingerprint()),
                Optional.of(intent.plannedCustody()),
                List.of(intent.sellerWallet()), intent.currencyId(),
                intent.plannedCustody().receipt().appliedAt(),
                Optional.of(transaction), ledger, List.of());
    }

    public static AuctionEscrowCommit bid(
            AuctionHouseSnapshot snapshot,
            PlaceAuctionBidCommand command,
            AuctionEscrowWalletSnapshot bidderWallet,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = bidMutation(snapshot, command);
        AuctionOperationResult result = plan.firstResult();
        AuctionListing listing = result.listing().orElseThrow();
        if (!bidderWallet.playerId().equals(command.bidderId())) {
            throw new IllegalArgumentException(
                    "Auction bid wallet owner is invalid");
        }
        bidderWallet.requireAvailable(result.requiredHoldDeltaMinor());
        List<EscrowClaim> claims = new ArrayList<>();
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.wallet(
                command.bidderId()), Math.negateExact(
                result.requiredHoldDeltaMinor())));
        legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.hold(
                command.holdAccountId()),
                result.requiredHoldDeltaMinor()));
        addRefund(result, command.requestId(), decidedAt, claims, legs);
        LedgerTransaction ledger = ledger(command.requestId(),
                "Auction bid", legs).orElseThrow();
        EscrowTransaction transaction = transaction(command.requestId(),
                EscrowOperation.AUCTION_BID, listing, currencyId,
                decidedAt, command.bidderId(), null,
                result.refundPlayerId(),
                result.refundMinor(),
                result.requiredHoldDeltaMinor());
        return new AuctionEscrowCommit(command.requestId(),
                command.listingId(), AuctionOperationType.BID,
                plan.mutations(), Optional.empty(), Optional.empty(),
                List.of(bidderWallet), currencyId, decidedAt,
                Optional.of(transaction), Optional.of(ledger), claims);
    }

    public static AuctionEscrowCommit buyNow(
            AuctionHouseSnapshot snapshot,
            AuctionBuyNowCommand command,
            AuctionEscrowWalletSnapshot buyerWallet,
            AuctionEscrowWalletSnapshot sellerWallet,
            AuctionEscrowItemCustody custody,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = buyNowMutation(snapshot, command);
        AuctionOperationResult result = plan.firstResult();
        AuctionListing listing = result.listing().orElseThrow();
        if (!buyerWallet.playerId().equals(command.buyerId())
                || !sellerWallet.playerId().equals(listing.sellerId())) {
            throw new IllegalArgumentException(
                    "Auction buy now wallet owner is invalid");
        }
        buyerWallet.requireAvailable(result.requiredHoldDeltaMinor());
        Settlement settlement = settlement(command.requestId(), result,
                listing, buyerWallet, custody, true,
                decidedAt);
        EscrowTransaction transaction = transaction(command.requestId(),
                EscrowOperation.AUCTION_BUY_NOW, listing, currencyId,
                decidedAt, command.buyerId(), custody,
                result.refundPlayerId(), result.refundMinor(),
                listing.sale().orElseThrow()
                        .priceMinor());
        return new AuctionEscrowCommit(command.requestId(),
                command.listingId(), AuctionOperationType.BUY_NOW,
                plan.mutations(), Optional.empty(), Optional.of(custody),
                List.of(buyerWallet), currencyId, decidedAt,
                Optional.of(transaction), Optional.of(settlement.ledger()),
                settlement.claims());
    }

    public static AuctionEscrowCommit cancel(
            AuctionHouseSnapshot snapshot,
            CancelAuctionCommand command,
            AuctionEscrowItemCustody custody,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = cancelMutation(snapshot, command);
        AuctionListing listing = plan.firstResult().listing()
                .orElseThrow();
        AuctionOperationResult result = plan.firstResult();
        List<EscrowClaim> claims = new ArrayList<>(itemClaims(
                command.requestId(), listing, listing.sellerId(),
                custody, decidedAt));
        List<LedgerLeg> legs = new ArrayList<>();
        addRefund(result, command.requestId(), decidedAt, claims, legs);
        EscrowTransaction transaction = transaction(command.requestId(),
                EscrowOperation.AUCTION_SETTLEMENT, listing, currencyId,
                decidedAt, listing.sellerId(), custody,
                result.refundPlayerId(), result.refundMinor(), 0L);
        return new AuctionEscrowCommit(command.requestId(),
                command.listingId(), AuctionOperationType.CANCEL,
                plan.mutations(), Optional.empty(), Optional.of(custody),
                List.of(), currencyId, decidedAt,
                Optional.of(transaction), ledger(command.requestId(),
                "Auction forced cancellation refund", legs), claims);
    }

    public static AuctionEscrowCommit expire(
            AuctionHouseSnapshot snapshot,
            ExpireAuctionCommand command,
            AuctionEscrowItemCustody custody,
            Optional<AuctionEscrowWalletSnapshot> sellerWallet,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = expireMutation(snapshot, command);
        AuctionOperationResult result = plan.firstResult();
        AuctionListing listing = result.listing().orElseThrow();
        if (listing.sale().isEmpty()) {
            if (sellerWallet.isPresent()) {
                throw new IllegalArgumentException(
                        "Unsold auction expiration cannot carry payout evidence");
            }
            List<EscrowClaim> claims = itemClaims(command.requestId(),
                    listing, listing.sellerId(), custody, decidedAt);
            EscrowTransaction transaction = transaction(
                    command.requestId(), EscrowOperation.AUCTION_SETTLEMENT,
                    listing, currencyId, decidedAt, listing.sellerId(),
                    custody, Optional.empty(), 0L, 0L);
            return new AuctionEscrowCommit(command.requestId(),
                    command.listingId(), AuctionOperationType.EXPIRE,
                    plan.mutations(), Optional.empty(),
                    Optional.of(custody), List.of(), currencyId, decidedAt,
                    Optional.of(transaction), Optional.empty(), claims);
        }
        AuctionEscrowWalletSnapshot seller = sellerWallet.orElseThrow(
                () -> new IllegalArgumentException(
                        "Sold auction expiration needs payout evidence"));
        if (!seller.playerId().equals(listing.sellerId())) {
            throw new IllegalArgumentException(
                    "Auction seller payout owner is invalid");
        }
        Settlement settlement = settlement(command.requestId(), result,
                listing, null, custody, false, decidedAt);
        UUID buyerId = listing.sale().orElseThrow().buyerId();
        EscrowTransaction transaction = transaction(command.requestId(),
                EscrowOperation.AUCTION_SETTLEMENT, listing, currencyId,
                decidedAt, buyerId, custody, Optional.empty(), 0L,
                listing.sale().orElseThrow().priceMinor());
        return new AuctionEscrowCommit(command.requestId(),
                command.listingId(), AuctionOperationType.EXPIRE,
                plan.mutations(), Optional.empty(), Optional.of(custody),
                List.of(), currencyId, decidedAt,
                Optional.of(transaction), Optional.of(settlement.ledger()),
                settlement.claims());
    }

    public static AuctionEscrowCommit settle(
            AuctionHouseSnapshot snapshot,
            SettleAuctionCommand command,
            AuctionEscrowItemCustody custody,
            AuctionEscrowWalletSnapshot sellerWallet,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = settleMutation(snapshot, command);
        AuctionOperationResult result = plan.firstResult();
        AuctionListing listing = result.listing().orElseThrow();
        if (!sellerWallet.playerId().equals(listing.sellerId())) {
            throw new IllegalArgumentException(
                    "Auction seller payout owner is invalid");
        }
        Settlement settlement = settlement(command.requestId(), result,
                listing, null, custody, false, decidedAt);
        UUID buyerId = listing.sale().orElseThrow().buyerId();
        EscrowTransaction transaction = transaction(command.requestId(),
                EscrowOperation.AUCTION_SETTLEMENT, listing, currencyId,
                decidedAt, buyerId, custody, Optional.empty(), 0L,
                listing.sale().orElseThrow().priceMinor());
        return new AuctionEscrowCommit(command.requestId(),
                command.listingId(), AuctionOperationType.SETTLE,
                plan.mutations(), Optional.empty(), Optional.of(custody),
                List.of(), currencyId, decidedAt,
                Optional.of(transaction), Optional.of(settlement.ledger()),
                settlement.claims());
    }

    public static AuctionEscrowCommit freeze(
            AuctionHouseSnapshot snapshot,
            FreezeAuctionCommand command,
            String currencyId,
            Instant decidedAt
    ) {
        return control(freezeMutation(snapshot, command),
                command.requestId(), command.listingId(),
                AuctionOperationType.FREEZE, currencyId, decidedAt);
    }

    public static AuctionEscrowCommit resume(
            AuctionHouseSnapshot snapshot,
            ResumeAuctionCommand command,
            String currencyId,
            Instant decidedAt
    ) {
        return control(resumeMutation(snapshot, command),
                command.requestId(), command.listingId(),
                AuctionOperationType.RESUME, currencyId, decidedAt);
    }

    private static AuctionEscrowCommit control(
            MutationPlan plan,
            UUID requestId,
            UUID listingId,
            AuctionOperationType operation,
            String currencyId,
            Instant decidedAt
    ) {
        return new AuctionEscrowCommit(requestId, listingId, operation,
                plan.mutations(), Optional.empty(), Optional.empty(),
                List.of(), currencyId, decidedAt, Optional.empty(),
                Optional.empty(), List.of());
    }

    private static Settlement settlement(
            UUID requestId,
            AuctionOperationResult result,
            AuctionListing listing,
            AuctionEscrowWalletSnapshot buyerWallet,
            AuctionEscrowItemCustody custody,
            boolean buyNow,
            Instant at
    ) {
        var sale = listing.sale().orElseThrow();
        List<EscrowClaim> claims = new ArrayList<>(itemClaims(requestId,
                listing, sale.buyerId(), custody, at));
        List<LedgerLeg> legs = new ArrayList<>();
        if (buyNow) {
            legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.wallet(
                    sale.buyerId()), Math.negateExact(
                    result.requiredHoldDeltaMinor())));
            legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.hold(
                    sale.holdAccountId()),
                    result.requiredHoldDeltaMinor()));
            addRefund(result, requestId, at, claims, legs);
        }
        legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.hold(
                sale.holdAccountId()), Math.negateExact(
                sale.priceMinor())));
        long tax = listing.rules().saleTax(sale.priceMinor());
        long sellerNet = Math.subtractExact(sale.priceMinor(), tax);
        if (sellerNet > 0L) {
            UUID claimId = AuctionEscrowIds.sellerProceedsClaimId(
                    requestId, listing.sellerId());
            claims.add(new EscrowClaim(claimId, requestId,
                    listing.sellerId(), "auction." + listing.listingId()
                    + ".seller.proceeds", ClaimKind.MONEY,
                    sellerNet, sellerNet, new byte[0],
                    ClaimStatus.PENDING, "Auction seller proceeds", at,
                    at));
            legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.claim(
                    claimId), sellerNet));
        }
        if (tax > 0L) {
            legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.treasury(),
                    tax));
        }
        return new Settlement(new LedgerTransaction(
                AuctionEscrowIds.ledgerTransactionId(requestId),
                "auction.escrow." + requestId, "Auction settlement",
                legs), claims);
    }

    private static void addRefund(
            AuctionOperationResult result,
            UUID requestId,
            Instant at,
            List<EscrowClaim> claims,
            List<LedgerLeg> legs
    ) {
        if (result.refundMinor() == 0L) {
            return;
        }
        UUID ownerId = result.refundPlayerId().orElseThrow();
        UUID claimId = AuctionEscrowIds.refundClaimId(requestId, ownerId);
        claims.add(new EscrowClaim(claimId, requestId, ownerId,
                "auction." + result.listingId() + ".outbid.refund",
                ClaimKind.REFUND, result.refundMinor(),
                result.refundMinor(), new byte[0], ClaimStatus.PENDING,
                "Auction outbid refund", at, at));
        legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.hold(
                result.displacedBid().orElseThrow().holdAccountId()),
                Math.negateExact(result.refundMinor())));
        legs.add(new LedgerLeg(AuctionEscrowLedgerAccounts.claim(claimId),
                result.refundMinor()));
    }

    private static List<EscrowClaim> itemClaims(
            UUID requestId,
            AuctionListing listing,
            UUID ownerId,
            AuctionEscrowItemCustody custody,
            Instant at
    ) {
        requireCustody(listing, custody);
        List<EscrowClaim> claims = new ArrayList<>();
        for (int index = 0; index < custody.exactItems().size(); index++) {
            var item = custody.exactItems().get(index);
            claims.add(new EscrowClaim(AuctionEscrowIds.itemClaimId(
                    requestId, ownerId, index), requestId, ownerId,
                    "auction." + listing.listingId() + ".item." + index,
                    ClaimKind.ITEM, item.stackCount(), item.stackCount(),
                    ExactItemClaimPayloadCodec.encode(item),
                    ClaimStatus.PENDING, "Auction item", at, at));
        }
        return claims;
    }

    private static Optional<LedgerTransaction> ledger(
            UUID requestId,
            String reason,
            List<LedgerLeg> legs
    ) {
        return legs.isEmpty() ? Optional.empty() : Optional.of(
                new LedgerTransaction(
                        AuctionEscrowIds.ledgerTransactionId(requestId),
                        "auction.escrow." + requestId, reason, legs));
    }

    private static EscrowTransaction transaction(
            UUID requestId,
            EscrowOperation operation,
            AuctionListing listing,
            String currencyId,
            Instant at,
            UUID initiatorId,
            AuctionEscrowItemCustody custody,
            Optional<UUID> refundOwner,
            long refundMinor,
            long moneyMinor
    ) {
        EscrowParty initiator = EscrowParty.player(initiatorId);
        List<EscrowAssetLot> assets = new ArrayList<>();
        int assetIndex = 0;
        if (custody != null) {
            boolean creation = operation == EscrowOperation.AUCTION_LISTING;
            UUID itemOwner = creation ? listing.sellerId()
                    : listing.sale().map(value -> value.buyerId())
                    .orElse(listing.sellerId());
            EscrowParty source = creation
                    ? EscrowParty.player(itemOwner) : MODULE;
            EscrowParty destination = creation
                    ? MODULE : EscrowParty.player(itemOwner);
            for (var item : custody.exactItems()) {
                assets.add(new EscrowAssetLot(
                        AuctionEscrowIds.assetLotId(requestId,
                                "item asset", assetIndex++),
                        EscrowAssetLotType.ITEM_STACK,
                        EscrowProtectionLevel.PROTECTED, source,
                        destination, item.stackCount(), Optional.empty(),
                        item.serializedStackSnapshot(), Map.of(
                        "custody_fingerprint", custody.fingerprint(),
                        "listing_id", listing.listingId().toString())));
            }
        }
        if (moneyMinor > 0L) {
            EscrowParty source = operation == EscrowOperation.AUCTION_BID
                    || operation == EscrowOperation.AUCTION_LISTING
                    || operation == EscrowOperation.AUCTION_BUY_NOW
                    ? initiator : MODULE;
            EscrowParty destination = operation
                    == EscrowOperation.AUCTION_SETTLEMENT
                    ? EscrowParty.player(listing.sellerId()) : MODULE;
            if (!source.equals(destination)) {
                assets.add(new EscrowAssetLot(
                        AuctionEscrowIds.assetLotId(requestId,
                                "money asset", assetIndex++),
                        operation == EscrowOperation.AUCTION_LISTING
                                ? EscrowAssetLotType.FEE
                                : EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED, source,
                        destination, 1L,
                        Optional.of(new MoneyAmount(currencyId,
                                moneyMinor)), new byte[0], Map.of(
                        "listing_id", listing.listingId().toString(),
                        "operation", operation.name())));
            }
        }
        if (refundMinor < 0L
                || refundOwner.isPresent() != (refundMinor > 0L)) {
            throw new IllegalArgumentException(
                    "Auction refund asset evidence is invalid");
        }
        if (refundOwner.isPresent()) {
            UUID owner = refundOwner.orElseThrow();
            assets.add(new EscrowAssetLot(
                    AuctionEscrowIds.assetLotId(requestId,
                            "refund asset", assetIndex),
                    EscrowAssetLotType.WALLET_MONEY,
                    EscrowProtectionLevel.PROTECTED, MODULE,
                    EscrowParty.player(owner), 1L,
                    Optional.of(new MoneyAmount(currencyId,
                            refundMinor)),
                    new byte[0], Map.of(
                    "listing_id", listing.listingId().toString(),
                    "operation", "REFUND")));
        }
        if (assets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Auction escrow transaction requires value evidence");
        }
        Map<EscrowParty, EnumSet<EscrowParticipantRole>> roles =
                new HashMap<>();
        roles.computeIfAbsent(initiator, ignored -> EnumSet.noneOf(
                EscrowParticipantRole.class)).add(
                EscrowParticipantRole.INITIATOR);
        for (EscrowAssetLot asset : assets) {
            roles.computeIfAbsent(asset.source(), ignored -> EnumSet.noneOf(
                    EscrowParticipantRole.class)).add(
                    EscrowParticipantRole.PAYER);
            roles.computeIfAbsent(asset.destination(), ignored ->
                    EnumSet.noneOf(EscrowParticipantRole.class)).add(
                    EscrowParticipantRole.RECIPIENT);
        }
        roles.computeIfAbsent(MODULE, ignored -> EnumSet.noneOf(
                EscrowParticipantRole.class)).add(
                EscrowParticipantRole.CUSTODIAN);
        Set<EscrowParticipant> participants = roles.entrySet().stream()
                .map(entry -> new EscrowParticipant(entry.getKey(),
                        entry.getValue())).collect(java.util.stream
                        .Collectors.toUnmodifiableSet());
        Optional<EscrowTransactionId> parent = operation
                == EscrowOperation.AUCTION_LISTING ? Optional.empty()
                : Optional.of(new EscrowTransactionId(
                listing.activationTransactionId()));
        EscrowTransaction created = EscrowTransaction.create(
                new EscrowTransactionId(requestId), parent,
                new EscrowRequestKey("auction."
                        + operation.name().toLowerCase(java.util.Locale.ROOT)
                        + "." + requestId), operation, participants,
                assets, at, listing.rules().configRevision(),
                Optional.empty());
        return created.transitionTo(EscrowState.VALIDATED, at)
                .transitionTo(EscrowState.HOLDING, at)
                .transitionTo(EscrowState.HELD, at)
                .transitionTo(EscrowState.COMMIT_DECIDED, at)
                .transitionTo(EscrowState.COMMITTED, at)
                .transitionTo(EscrowState.CLAIMS_CREATED, at)
                .transitionTo(EscrowState.COMPLETED, at);
    }

    private static MutationPlan create(
            AuctionHouseSnapshot snapshot,
            CreateAuctionCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.create(command));
        return oneMutation(snapshot, book, command.requestId(), result);
    }

    private static MutationPlan bidMutation(
            AuctionHouseSnapshot snapshot,
            PlaceAuctionBidCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.bid(command));
        return oneMutation(snapshot, book, command.requestId(), result);
    }

    private static MutationPlan buyNowMutation(
            AuctionHouseSnapshot snapshot,
            AuctionBuyNowCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.buyNow(command));
        AuctionHouseSnapshot afterSale = book.snapshot();
        AuctionHouseMutation sale = AuctionHouseMutation.between(snapshot,
                afterSale, command.requestId());
        UUID settlementRequest = AuctionEscrowIds
                .settlementBookRequestId(command.requestId());
        AuctionListing sold = result.listing().orElseThrow();
        requireApplied(book.settle(new SettleAuctionCommand(
                settlementRequest, sold.listingId(), sold.revision(),
                sold.lastObservedTimeMillis())));
        AuctionHouseMutation settled = AuctionHouseMutation.between(
                afterSale, book.snapshot(), settlementRequest);
        return new MutationPlan(List.of(sale, settled), result);
    }

    private static MutationPlan cancelMutation(
            AuctionHouseSnapshot snapshot,
            CancelAuctionCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.cancel(command));
        return oneMutation(snapshot, book, command.requestId(), result);
    }

    private static MutationPlan expireMutation(
            AuctionHouseSnapshot snapshot,
            ExpireAuctionCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.expire(command));
        AuctionHouseSnapshot afterExpiry = book.snapshot();
        AuctionHouseMutation expiry = AuctionHouseMutation.between(snapshot,
                afterExpiry, command.requestId());
        if (result.listing().orElseThrow().state()
                != AuctionListingState.SOLD_PENDING) {
            return new MutationPlan(List.of(expiry), result);
        }
        UUID settlementRequest = AuctionEscrowIds
                .settlementBookRequestId(command.requestId());
        AuctionListing sold = result.listing().orElseThrow();
        requireApplied(book.settle(new SettleAuctionCommand(
                settlementRequest, sold.listingId(), sold.revision(),
                sold.lastObservedTimeMillis())));
        AuctionHouseMutation settled = AuctionHouseMutation.between(
                afterExpiry, book.snapshot(), settlementRequest);
        return new MutationPlan(List.of(expiry, settled), result);
    }

    private static MutationPlan freezeMutation(
            AuctionHouseSnapshot snapshot,
            FreezeAuctionCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.freeze(command));
        return oneMutation(snapshot, book, command.requestId(), result);
    }

    private static MutationPlan resumeMutation(
            AuctionHouseSnapshot snapshot,
            ResumeAuctionCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.resume(command));
        return oneMutation(snapshot, book, command.requestId(), result);
    }

    private static MutationPlan settleMutation(
            AuctionHouseSnapshot snapshot,
            SettleAuctionCommand command
    ) {
        AuctionHouseBook book = new AuctionHouseBook(snapshot);
        AuctionOperationResult result = requireApplied(book.settle(command));
        return oneMutation(snapshot, book, command.requestId(), result);
    }

    private static MutationPlan oneMutation(
            AuctionHouseSnapshot previous,
            AuctionHouseBook book,
            UUID requestId,
            AuctionOperationResult result
    ) {
        return new MutationPlan(List.of(AuctionHouseMutation.between(
                previous, book.snapshot(), requestId)), result);
    }

    private static AuctionOperationResult requireApplied(
            AuctionOperationResult result
    ) {
        if (!result.applied()) {
            throw new IllegalArgumentException(
                    "Auction operation was rejected with status "
                            + result.status());
        }
        return result;
    }

    private static void requirePrepared(AuctionCreateEscrowIntent intent) {
        if (Objects.requireNonNull(intent, "intent").status()
                != AuctionCreateEscrowIntent.Status.PREPARED) {
            throw new IllegalArgumentException(
                    "Auction creation intent is not prepared");
        }
    }

    private static void requireCustody(
            AuctionListing listing,
            AuctionEscrowItemCustody custody
    ) {
        if (!Objects.requireNonNull(custody, "custody").listingId()
                .equals(listing.listingId())
                || !custody.activationTransactionId().equals(
                listing.activationTransactionId())
                || !custody.matches(listing.itemLot())) {
            throw new IllegalArgumentException(
                    "Auction custody differs from its listing");
        }
    }

    private record MutationPlan(
            List<AuctionHouseMutation> mutations,
            AuctionOperationResult firstResult
    ) {
        private MutationPlan {
            mutations = List.copyOf(mutations);
            Objects.requireNonNull(firstResult, "firstResult");
        }
    }

    private record Settlement(
            LedgerTransaction ledger,
            List<EscrowClaim> claims
    ) {
        private Settlement {
            Objects.requireNonNull(ledger, "ledger");
            claims = List.copyOf(claims);
        }
    }
}
