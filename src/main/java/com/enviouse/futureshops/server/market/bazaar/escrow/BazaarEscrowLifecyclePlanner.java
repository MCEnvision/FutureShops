package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
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
import com.enviouse.futureshops.server.market.bazaar.BazaarEscrowSettlement;
import com.enviouse.futureshops.server.market.bazaar.BazaarFill;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationResult;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarSettlementKind;
import com.enviouse.futureshops.server.market.bazaar.CancelBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.ExpireBazaarOrderCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BazaarEscrowLifecyclePlanner {
    public static final String BUYER_ITEM_LABEL = "Bazaar purchased item";
    public static final String SELLER_MONEY_LABEL = "Bazaar seller proceeds";
    public static final String BUYER_CHANGE_LABEL = "Bazaar price improvement";
    public static final String BUYER_REFUND_LABEL = "Bazaar buyer refund";
    public static final String SELLER_REFUND_LABEL = "Bazaar seller item refund";

    private static final EscrowParty MODULE = EscrowParty.module("bazaar");
    private static final EscrowParty FEE = EscrowParty.system("bazaar_fee");

    private BazaarEscrowLifecyclePlanner() {
    }

    public static BazaarCreateEscrowIntent prepareBuy(
            CreateBazaarOrderCommand command,
            BazaarBuyFundingEvidence funding,
            Instant preparedAt
    ) {
        return BazaarCreateEscrowIntent.preparedBuy(command, funding,
                preparedAt);
    }

    public static BazaarCreateEscrowIntent prepareSell(
            CreateBazaarOrderCommand command,
            BazaarProduct product,
            ItemInventoryMutationIntent itemIntent,
            BazaarSellItemCustody custody,
            String currencyId,
            Instant preparedAt
    ) {
        return BazaarCreateEscrowIntent.preparedSell(command, product,
                itemIntent, custody, currencyId, preparedAt);
    }

    public static PlannedCreate commitCreate(
            BazaarOrderBookSnapshot snapshot,
            BazaarEscrowLifecycleState lifecycle,
            BazaarCreateEscrowIntent intent,
            Optional<ItemInventoryMutationReceipt> committedReceipt,
            Instant decidedAt
    ) {
        Objects.requireNonNull(intent, "intent");
        committedReceipt = Objects.requireNonNull(committedReceipt,
                "committedReceipt");
        if (intent.terminal()
                || intent.status()
                != BazaarCreateEscrowIntent.Status.PREPARED
                && intent.status()
                != BazaarCreateEscrowIntent.Status.RECOVERY_REQUIRED) {
            throw new IllegalArgumentException(
                    "Bazaar creation intent cannot commit");
        }
        if (intent.command().side() == BazaarOrderSide.SELL) {
            if (!committedReceipt.filter(value -> value.equals(
                    intent.sellCustody().orElseThrow().receipt()))
                    .isPresent()) {
                throw new IllegalArgumentException(
                        "Bazaar committed item custody differs from its intent");
            }
        } else if (committedReceipt.isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar buy order cannot carry item custody evidence");
        }
        MutationPlan mutation = createMutation(snapshot, intent.command());
        BazaarCreateEscrowIntent.Status status = mutation.result().status()
                == BazaarOperationStatus.APPLIED
                ? BazaarCreateEscrowIntent.Status.COMMITTED
                : BazaarCreateEscrowIntent.Status.REJECTED;
        BazaarCreateEscrowIntent terminal = intent.transition(status);
        BazaarEscrowCommit commit = build(snapshot, mutation.next(),
                lifecycle, Optional.of(intent), mutation.mutation(),
                intent.currencyId(), decidedAt);
        return new PlannedCreate(terminal, commit);
    }

    public static BazaarEscrowCommit cancel(
            BazaarOrderBookSnapshot snapshot,
            BazaarEscrowLifecycleState lifecycle,
            CancelBazaarOrderCommand command,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = cancelMutation(snapshot, command);
        return build(snapshot, plan.next(), lifecycle, Optional.empty(),
                plan.mutation(), currencyId, decidedAt);
    }

    public static BazaarEscrowCommit expire(
            BazaarOrderBookSnapshot snapshot,
            BazaarEscrowLifecycleState lifecycle,
            ExpireBazaarOrderCommand command,
            String currencyId,
            Instant decidedAt
    ) {
        MutationPlan plan = expireMutation(snapshot, command);
        return build(snapshot, plan.next(), lifecycle, Optional.empty(),
                plan.mutation(), currencyId, decidedAt);
    }

    private static BazaarEscrowCommit build(
            BazaarOrderBookSnapshot previous,
            BazaarOrderBookSnapshot next,
            BazaarEscrowLifecycleState lifecycle,
            Optional<BazaarCreateEscrowIntent> createIntent,
            BazaarMutation mutation,
            String currencyId,
            Instant decidedAt
    ) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(lifecycle, "lifecycle");
        createIntent = Objects.requireNonNull(createIntent,
                "createIntent");
        Objects.requireNonNull(mutation, "mutation");
        currencyId = BazaarBuyFundingEvidence.requireCurrencyId(currencyId);
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        BazaarOperationResult result = mutation.requestReceipt()
                .orElseThrow().result();
        Evidence evidence = new Evidence();
        if (result.status() != BazaarOperationStatus.APPLIED) {
            if (createIntent.filter(value -> value.command().side()
                    == BazaarOrderSide.SELL).isPresent()) {
                rejectedSellReturn(createIntent.orElseThrow(), currencyId,
                        decidedAt, evidence);
            }
            return commit(mutation, createIntent, currencyId, decidedAt,
                    List.of(), evidence);
        }

        Map<UUID, BazaarOrder> beforeOrders = indexOrders(
                previous.orders());
        Map<UUID, BazaarOrder> afterOrders = indexOrders(next.orders());
        Map<UUID, BazaarEscrowOrderBacking> available = new HashMap<>(
                lifecycle.activeBackings());
        Map<UUID, BazaarSellCustodyState> sellStates = new HashMap<>();
        for (BazaarEscrowOrderBacking backing : available.values()) {
            backing.sellCustody().ifPresent(value -> sellStates.put(
                    backing.orderId(), value));
        }

        UUID incomingId = result.orderId();
        if (createIntent.isPresent()) {
            BazaarCreateEscrowIntent intent = createIntent.orElseThrow();
            BazaarEscrowOrderBacking incoming = intent.command().side()
                    == BazaarOrderSide.BUY
                    ? BazaarEscrowOrderBacking.buy(
                    intent.buyFunding().orElseThrow())
                    : BazaarEscrowOrderBacking.sell(
                    BazaarSellCustodyState.full(
                            intent.sellCustody().orElseThrow()));
            if (available.putIfAbsent(incomingId, incoming) != null) {
                throw new IllegalArgumentException(
                        "Bazaar incoming order already has backing");
            }
            incoming.sellCustody().ifPresent(value -> sellStates.put(
                    incomingId, value));
            activation(intent, currencyId, decidedAt, evidence);
        }

        Set<UUID> touched = touchedOrders(result);
        for (UUID orderId : touched) {
            if (createIntent.isPresent() && orderId.equals(incomingId)) {
                continue;
            }
            BazaarOrder before = requireOrder(beforeOrders, orderId,
                    "before");
            BazaarEscrowOrderBacking backing = available.get(orderId);
            if (backing == null) {
                throw new IllegalArgumentException(
                        "Bazaar touched order has no escrow backing");
            }
            backing.requireMatches(before);
        }

        Map<UUID, BazaarFill> fills = new HashMap<>();
        for (BazaarFill fill : result.fills()) {
            fills.put(fill.fillId(), fill);
        }
        LinkedHashMap<UUID, List<BazaarEscrowSettlement>> groups =
                settlementGroups(result.settlements());
        for (Map.Entry<UUID, List<BazaarEscrowSettlement>> entry
                : groups.entrySet()) {
            settle(entry.getKey(), entry.getValue(), fills, available,
                    sellStates, beforeOrders, afterOrders, currencyId,
                    decidedAt, evidence);
        }

        List<BazaarEscrowOrderTransition> transitions = new ArrayList<>();
        for (UUID orderId : touched) {
            Optional<BazaarEscrowOrderView> beforeView = Optional.ofNullable(
                    beforeOrders.get(orderId)).map(
                    BazaarEscrowOrderView::from);
            Optional<BazaarEscrowOrderBacking> beforeBacking =
                    createIntent.isPresent() && orderId.equals(incomingId)
                            ? Optional.empty()
                            : Optional.ofNullable(
                            lifecycle.activeBackings().get(orderId));
            BazaarOrder after = requireOrder(afterOrders, orderId,
                    "after");
            Optional<BazaarEscrowOrderBacking> afterBacking;
            if (after.state().terminal()) {
                afterBacking = Optional.empty();
            } else if (after.side() == BazaarOrderSide.BUY) {
                afterBacking = Optional.ofNullable(available.get(orderId));
            } else {
                BazaarSellCustodyState state = sellStates.get(orderId);
                if (state == null || state.remainingQuantity() <= 0) {
                    throw new IllegalArgumentException(
                            "Bazaar sell order lost its active custody");
                }
                afterBacking = Optional.of(
                        BazaarEscrowOrderBacking.sell(state));
            }
            transitions.add(new BazaarEscrowOrderTransition(orderId,
                    beforeView, Optional.of(BazaarEscrowOrderView.from(
                    after)), beforeBacking, afterBacking));
        }
        return commit(mutation, createIntent, currencyId, decidedAt,
                transitions, evidence);
    }

    private static BazaarEscrowCommit commit(
            BazaarMutation mutation,
            Optional<BazaarCreateEscrowIntent> intent,
            String currencyId,
            Instant decidedAt,
            List<BazaarEscrowOrderTransition> transitions,
            Evidence evidence
    ) {
        BazaarOperationResult result = mutation.requestReceipt()
                .orElseThrow().result();
        return new BazaarEscrowCommit(result.requestId(), result.orderId(),
                result.operation(), mutation,
                intent.map(BazaarCreateEscrowIntent::intentFingerprint),
                transitions, currencyId, decidedAt,
                evidence.transactions, evidence.ledgers, evidence.claims);
    }

    private static void activation(
            BazaarCreateEscrowIntent intent,
            String currencyId,
            Instant at,
            Evidence evidence
    ) {
        CreateBazaarOrderCommand command = intent.command();
        EscrowParty owner = EscrowParty.player(command.ownerId());
        if (command.side() == BazaarOrderSide.BUY) {
            long reserve = BazaarCreateEscrowIntent.initialReserve(command);
            BazaarBuyFundingEvidence funding = intent.buyFunding()
                    .orElseThrow();
            funding.requireReserve(reserve);
            evidence.ledgers.add(new LedgerTransaction(
                    command.activationTransactionId(),
                    ledgerKey(command.activationTransactionId()),
                    "Bazaar buy activation", List.of(
                    new LedgerLeg(BazaarEscrowLedgerAccounts.wallet(
                            command.ownerId()), Math.negateExact(reserve)),
                    new LedgerLeg(BazaarEscrowLedgerAccounts.hold(
                            funding.holdAccountId()), reserve))));
            EscrowAssetLot asset = moneyAsset(
                    command.activationTransactionId(), "buy reserve", 0,
                    owner, MODULE, reserve, currencyId,
                    Map.of("order_id", command.orderId().toString(),
                            "payment_source", funding.source().name()));
            evidence.transactions.add(transaction(
                    command.activationTransactionId(),
                    EscrowOperation.BAZAAR_BUY_ORDER, command.ownerId(),
                    List.of(asset), at, command.rules().configRevision()));
        } else {
            BazaarSellItemCustody custody = intent.sellCustody()
                    .orElseThrow();
            EscrowAssetLot asset = itemAsset(
                    command.activationTransactionId(), "sell custody", 0,
                    owner, MODULE, custody.totalQuantity(),
                    custody.receipt().digest(),
                    Map.of("custody_lot_id",
                                    custody.custodyLotId().toString(),
                            "order_id", command.orderId().toString(),
                            "product_id", command.productId()));
            evidence.transactions.add(transaction(
                    command.activationTransactionId(),
                    EscrowOperation.BAZAAR_SELL_ORDER, command.ownerId(),
                    List.of(asset), at, command.rules().configRevision()));
        }
    }

    private static void rejectedSellReturn(
            BazaarCreateEscrowIntent intent,
            String currencyId,
            Instant at,
            Evidence evidence
    ) {
        BazaarSellCustodyState state = BazaarSellCustodyState.full(
                intent.sellCustody().orElseThrow());
        UUID transactionId = BazaarEscrowIds
                .rejectedCustodyReturnTransactionId(intent.requestId(),
                        intent.orderId());
        String sourceKey = itemSourceKey(transactionId,
                BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM,
                intent.orderId());
        BazaarSellCustodyState.Allocation allocation = state.allocate(
                state.remainingQuantity(), transactionId, sourceKey);
        List<EscrowClaim> claims = itemClaims(transactionId,
                intent.command().ownerId(), allocation.claimPayloads(),
                SELLER_REFUND_LABEL, at);
        evidence.claims.addAll(claims);
        EscrowAssetLot asset = itemAsset(transactionId, "rejected return",
                0, MODULE, EscrowParty.player(intent.command().ownerId()),
                intent.command().quantity(), payloadDigest(claims),
                Map.of("order_id", intent.orderId().toString(),
                        "result", "rejected"));
        evidence.transactions.add(transaction(transactionId,
                EscrowOperation.BAZAAR_CANCEL,
                intent.command().ownerId(), List.of(asset), at,
                intent.command().rules().configRevision()));
    }

    private static void settle(
            UUID transactionId,
            List<BazaarEscrowSettlement> settlements,
            Map<UUID, BazaarFill> fills,
            Map<UUID, BazaarEscrowOrderBacking> available,
            Map<UUID, BazaarSellCustodyState> sellStates,
            Map<UUID, BazaarOrder> beforeOrders,
            Map<UUID, BazaarOrder> afterOrders,
            String currencyId,
            Instant at,
            Evidence evidence
    ) {
        List<LedgerLeg> legs = new ArrayList<>();
        List<EscrowAssetLot> assets = new ArrayList<>();
        Set<UUID> involvedPlayers = new LinkedHashSet<>();
        UUID initiator = null;
        EscrowOperation operation = EscrowOperation.BAZAAR_FILL;
        long configRevision = 0L;
        BazaarFill fill = settlements.get(0).fillId().map(fills::get)
                .orElse(null);
        if (fill != null) {
            BazaarOrder taker = requireOrder(afterOrders,
                    fill.takerOrderId(), "taker");
            initiator = taker.ownerId();
            configRevision = taker.rules().configRevision();
            BazaarOrder buy = requireOrder(afterOrders, fill.buyOrderId(),
                    "buy");
            BazaarEscrowOrderBacking buyBacking = available.get(
                    buy.orderId());
            if (buyBacking == null
                    || buyBacking.side() != BazaarOrderSide.BUY) {
                throw new IllegalArgumentException(
                        "Bazaar fill buyer has no money hold");
            }
            long holdRelease = Math.addExact(
                    Math.addExact(fill.grossMinor(), fill.buyerFeeMinor()),
                    fill.buyerPriceImprovementMinor());
            legs.add(new LedgerLeg(BazaarEscrowLedgerAccounts.hold(
                    buyBacking.buyFunding().orElseThrow().holdAccountId()),
                    Math.negateExact(holdRelease)));
        }
        int assetOrdinal = 0;
        for (BazaarEscrowSettlement settlement : settlements) {
            involvedPlayers.add(settlement.ownerId());
            settlement.counterpartyId().ifPresent(involvedPlayers::add);
            UUID orderId = settlement.orderId().orElse(null);
            switch (settlement.kind()) {
                case BUYER_ITEM_CLAIM -> {
                    BazaarFill currentFill = requireFill(fills, settlement);
                    UUID sellOrderId = currentFill.sellOrderId();
                    BazaarSellCustodyState state = sellStates.get(
                            sellOrderId);
                    if (state == null) {
                        throw new IllegalArgumentException(
                                "Bazaar fill seller has no item custody");
                    }
                    String sourceKey = itemSourceKey(transactionId,
                            settlement.kind(), sellOrderId);
                    BazaarSellCustodyState.Allocation allocation =
                            state.allocate(settlement.itemQuantity(),
                                    transactionId, sourceKey);
                    sellStates.put(sellOrderId, allocation.remaining());
                    List<EscrowClaim> claims = itemClaims(transactionId,
                            settlement.ownerId(), allocation.claimPayloads(),
                            BUYER_ITEM_LABEL, at);
                    evidence.claims.addAll(claims);
                    assets.add(itemAsset(transactionId, "buyer item",
                            assetOrdinal++, MODULE,
                            EscrowParty.player(settlement.ownerId()),
                            settlement.itemQuantity(), payloadDigest(claims),
                            attributes(settlement, sellOrderId)));
                }
                case SELLER_ITEM_REFUND_CLAIM -> {
                    BazaarSellCustodyState state = sellStates.get(orderId);
                    if (state == null) {
                        throw new IllegalArgumentException(
                                "Bazaar seller refund has no item custody");
                    }
                    String sourceKey = itemSourceKey(transactionId,
                            settlement.kind(), orderId);
                    BazaarSellCustodyState.Allocation allocation =
                            state.allocate(settlement.itemQuantity(),
                                    transactionId, sourceKey);
                    sellStates.put(orderId, allocation.remaining());
                    List<EscrowClaim> claims = itemClaims(transactionId,
                            settlement.ownerId(), allocation.claimPayloads(),
                            SELLER_REFUND_LABEL, at);
                    evidence.claims.addAll(claims);
                    assets.add(itemAsset(transactionId, "seller refund",
                            assetOrdinal++, MODULE,
                            EscrowParty.player(settlement.ownerId()),
                            settlement.itemQuantity(), payloadDigest(claims),
                            attributes(settlement, orderId)));
                    operation = EscrowOperation.BAZAAR_CANCEL;
                    initiator = settlement.ownerId();
                    configRevision = orderRules(beforeOrders, afterOrders,
                            orderId);
                }
                case SELLER_MONEY_CLAIM, BUYER_CHANGE_CLAIM,
                        BUYER_REFUND_CLAIM -> {
                    EscrowClaim claim = moneyClaim(settlement, at);
                    evidence.claims.add(claim);
                    legs.add(new LedgerLeg(
                            BazaarEscrowLedgerAccounts.claim(
                                    claim.claimId()),
                            settlement.moneyMinor()));
                    assets.add(moneyAsset(transactionId,
                            settlement.kind().name().toLowerCase(),
                            assetOrdinal++, MODULE,
                            EscrowParty.player(settlement.ownerId()),
                            settlement.moneyMinor(), currencyId,
                            attributes(settlement, orderId)));
                    if (settlement.kind()
                            == BazaarSettlementKind.BUYER_REFUND_CLAIM) {
                        BazaarEscrowOrderBacking backing = available.get(
                                orderId);
                        if (backing == null
                                || backing.side() != BazaarOrderSide.BUY) {
                            throw new IllegalArgumentException(
                                    "Bazaar buyer refund has no money hold");
                        }
                        legs.add(0, new LedgerLeg(
                                BazaarEscrowLedgerAccounts.hold(
                                        backing.buyFunding().orElseThrow()
                                                .holdAccountId()),
                                Math.negateExact(
                                        settlement.moneyMinor())));
                        operation = EscrowOperation.BAZAAR_CANCEL;
                        initiator = settlement.ownerId();
                        configRevision = orderRules(beforeOrders,
                                afterOrders, orderId);
                    }
                }
                case FEE_DESTINATION -> {
                    legs.add(new LedgerLeg(
                            BazaarEscrowLedgerAccounts.fee(),
                            settlement.moneyMinor()));
                    assets.add(new EscrowAssetLot(
                            BazaarEscrowIds.transactionAssetId(transactionId,
                                    "fee", assetOrdinal++),
                            EscrowAssetLotType.FEE,
                            EscrowProtectionLevel.PROTECTED, MODULE, FEE,
                            1L, Optional.of(new MoneyAmount(currencyId,
                            settlement.moneyMinor())), new byte[0],
                            attributes(settlement, null)));
                }
            }
        }
        if (!legs.isEmpty()) {
            evidence.ledgers.add(new LedgerTransaction(transactionId,
                    ledgerKey(transactionId), operation
                    == EscrowOperation.BAZAAR_FILL
                    ? "Bazaar fill" : "Bazaar terminal refund", legs));
        }
        if (assets.isEmpty() || initiator == null) {
            throw new IllegalArgumentException(
                    "Bazaar settlement evidence is incomplete");
        }
        evidence.transactions.add(transaction(transactionId, operation,
                initiator, assets, at, configRevision));
    }

    private static EscrowClaim moneyClaim(
            BazaarEscrowSettlement settlement,
            Instant at
    ) {
        UUID orderId = settlement.orderId().orElseThrow();
        UUID claimId = BazaarEscrowIds.moneyClaimId(
                settlement.transactionId(), settlement.kind(), orderId,
                settlement.ownerId());
        String label = switch (settlement.kind()) {
            case SELLER_MONEY_CLAIM -> SELLER_MONEY_LABEL;
            case BUYER_CHANGE_CLAIM -> BUYER_CHANGE_LABEL;
            case BUYER_REFUND_CLAIM -> BUYER_REFUND_LABEL;
            default -> throw new IllegalArgumentException(
                    "Bazaar settlement is not a money claim");
        };
        return new EscrowClaim(claimId, settlement.transactionId(),
                settlement.ownerId(), moneySourceKey(settlement),
                ClaimKind.MONEY, settlement.moneyMinor(),
                settlement.moneyMinor(), new byte[0], ClaimStatus.PENDING,
                label, at, at);
    }

    private static List<EscrowClaim> itemClaims(
            UUID transactionId,
            UUID ownerId,
            List<ExactItemClaimPayload> payloads,
            String label,
            Instant at
    ) {
        List<EscrowClaim> claims = new ArrayList<>(payloads.size());
        for (ExactItemClaimPayload payload : payloads) {
            claims.add(new EscrowClaim(payload.lotId(), transactionId,
                    ownerId, payload.sourceKey() + "."
                    + payload.portionIndex(), ClaimKind.ITEM,
                    payload.stackCount(), payload.stackCount(),
                    ExactItemClaimPayloadCodec.encode(payload),
                    ClaimStatus.PENDING, label, at, at));
        }
        return claims;
    }

    private static EscrowTransaction transaction(
            UUID transactionId,
            EscrowOperation operation,
            UUID initiatorId,
            List<EscrowAssetLot> assets,
            Instant at,
            long configRevision
    ) {
        EscrowParty initiator = EscrowParty.player(initiatorId);
        Map<EscrowParty, EnumSet<EscrowParticipantRole>> roles =
                new LinkedHashMap<>();
        roles.put(initiator, EnumSet.of(
                EscrowParticipantRole.INITIATOR,
                EscrowParticipantRole.OWNER));
        for (EscrowAssetLot asset : assets) {
            roles.computeIfAbsent(asset.source(), ignored -> EnumSet.of(
                    EscrowParticipantRole.CUSTODIAN));
            roles.computeIfAbsent(asset.destination(), ignored ->
                    EnumSet.of(asset.destination().equals(FEE)
                            ? EscrowParticipantRole.FEE_COLLECTOR
                            : EscrowParticipantRole.BENEFICIARY));
        }
        Set<EscrowParticipant> participants = new HashSet<>();
        for (Map.Entry<EscrowParty, EnumSet<EscrowParticipantRole>> entry
                : roles.entrySet()) {
            participants.add(new EscrowParticipant(entry.getKey(),
                    entry.getValue()));
        }
        EscrowTransaction transaction = EscrowTransaction.create(
                new EscrowTransactionId(transactionId), Optional.empty(),
                new EscrowRequestKey("bazaar."
                        + operation.name().toLowerCase() + "."
                        + transactionId), operation, participants, assets,
                at, configRevision, Optional.empty());
        return complete(transaction, at);
    }

    private static EscrowTransaction complete(
            EscrowTransaction transaction,
            Instant at
    ) {
        return transaction.transitionTo(EscrowState.VALIDATED, at)
                .transitionTo(EscrowState.HOLDING, at)
                .transitionTo(EscrowState.HELD, at)
                .transitionTo(EscrowState.COMMIT_DECIDED, at)
                .transitionTo(EscrowState.COMMITTED, at)
                .transitionTo(EscrowState.CLAIMS_CREATED, at)
                .transitionTo(EscrowState.COMPLETED, at);
    }

    private static EscrowAssetLot moneyAsset(
            UUID transactionId,
            String role,
            int ordinal,
            EscrowParty source,
            EscrowParty destination,
            long amount,
            String currencyId,
            Map<String, String> attributes
    ) {
        return new EscrowAssetLot(
                BazaarEscrowIds.transactionAssetId(transactionId, role,
                        ordinal), EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED, source, destination, 1L,
                Optional.of(new MoneyAmount(currencyId, amount)),
                new byte[0], attributes);
    }

    private static EscrowAssetLot itemAsset(
            UUID transactionId,
            String role,
            int ordinal,
            EscrowParty source,
            EscrowParty destination,
            int quantity,
            byte[] payload,
            Map<String, String> attributes
    ) {
        return new EscrowAssetLot(
                BazaarEscrowIds.transactionAssetId(transactionId, role,
                        ordinal), EscrowAssetLotType.ITEM_STACK,
                EscrowProtectionLevel.RECONCILED, source, destination,
                quantity, Optional.empty(), payload, attributes);
    }

    private static Map<String, String> attributes(
            BazaarEscrowSettlement settlement,
            UUID custodyOrderId
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("settlement_kind", settlement.kind().name());
        settlement.orderId().ifPresent(value -> values.put("order_id",
                value.toString()));
        settlement.fillId().ifPresent(value -> values.put("fill_id",
                value.toString()));
        if (custodyOrderId != null) {
            values.put("custody_order_id", custodyOrderId.toString());
        }
        return Map.copyOf(values);
    }

    static String ledgerKey(UUID transactionId) {
        return "bazaar.ledger."
                + BazaarEscrowIds.requireId(transactionId,
                "transactionId");
    }

    static String moneySourceKey(
            BazaarEscrowSettlement settlement
    ) {
        return "bazaar.money." + settlement.kind().wireCode() + "."
                + settlement.transactionId() + "."
                + settlement.orderId().orElseThrow();
    }

    static String itemSourceKey(
            UUID transactionId,
            BazaarSettlementKind kind,
            UUID orderId
    ) {
        return "bazaar.item." + kind.wireCode() + "." + transactionId
                + "." + orderId;
    }

    private static byte[] payloadDigest(List<EscrowClaim> claims) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (EscrowClaim claim : claims) {
                digest.update(claim.payload());
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static long orderRules(
            Map<UUID, BazaarOrder> before,
            Map<UUID, BazaarOrder> after,
            UUID orderId
    ) {
        BazaarOrder order = before.get(orderId);
        if (order == null) {
            order = after.get(orderId);
        }
        return Objects.requireNonNull(order,
                "Bazaar settlement order").rules().configRevision();
    }

    private static BazaarFill requireFill(
            Map<UUID, BazaarFill> fills,
            BazaarEscrowSettlement settlement
    ) {
        BazaarFill fill = settlement.fillId().map(fills::get).orElse(null);
        if (fill == null) {
            throw new IllegalArgumentException(
                    "Bazaar settlement fill is missing");
        }
        return fill;
    }

    private static BazaarOrder requireOrder(
            Map<UUID, BazaarOrder> orders,
            UUID orderId,
            String phase
    ) {
        BazaarOrder order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException(
                    "Bazaar " + phase + " order is missing");
        }
        return order;
    }

    private static Map<UUID, BazaarOrder> indexOrders(
            List<BazaarOrder> orders
    ) {
        Map<UUID, BazaarOrder> result = new HashMap<>();
        for (BazaarOrder order : orders) {
            if (result.put(order.orderId(), order) != null) {
                throw new IllegalArgumentException(
                        "Bazaar order snapshot is duplicated");
            }
        }
        return result;
    }

    private static Set<UUID> touchedOrders(BazaarOperationResult result) {
        Set<UUID> touched = new LinkedHashSet<>();
        touched.add(result.orderId());
        for (BazaarFill fill : result.fills()) {
            touched.add(fill.buyOrderId());
            touched.add(fill.sellOrderId());
        }
        touched.addAll(result.cancelledMakerOrderIds());
        for (BazaarEscrowSettlement settlement : result.settlements()) {
            settlement.orderId().ifPresent(touched::add);
            settlement.fillId().ifPresent(fillId -> result.fills().stream()
                    .filter(value -> value.fillId().equals(fillId))
                    .findFirst().ifPresent(value -> {
                        touched.add(value.buyOrderId());
                        touched.add(value.sellOrderId());
                    }));
        }
        return touched;
    }

    private static LinkedHashMap<UUID, List<BazaarEscrowSettlement>>
    settlementGroups(List<BazaarEscrowSettlement> settlements) {
        LinkedHashMap<UUID, List<BazaarEscrowSettlement>> mutable =
                new LinkedHashMap<>();
        for (BazaarEscrowSettlement settlement : settlements) {
            mutable.computeIfAbsent(settlement.transactionId(),
                    ignored -> new ArrayList<>()).add(settlement);
        }
        LinkedHashMap<UUID, List<BazaarEscrowSettlement>> result =
                new LinkedHashMap<>();
        for (Map.Entry<UUID, List<BazaarEscrowSettlement>> entry
                : mutable.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }

    private static MutationPlan createMutation(
            BazaarOrderBookSnapshot snapshot,
            CreateBazaarOrderCommand command
    ) {
        BazaarOrderBook book = BazaarOrderBook.restore(snapshot);
        BazaarOperationResult result = book.create(command);
        BazaarOrderBookSnapshot next = book.snapshot();
        return new MutationPlan(next,
                BazaarMutation.between(snapshot, next,
                        command.requestId()), result);
    }

    private static MutationPlan cancelMutation(
            BazaarOrderBookSnapshot snapshot,
            CancelBazaarOrderCommand command
    ) {
        BazaarOrderBook book = BazaarOrderBook.restore(snapshot);
        BazaarOperationResult result = book.cancel(command);
        BazaarOrderBookSnapshot next = book.snapshot();
        return new MutationPlan(next,
                BazaarMutation.between(snapshot, next,
                        command.requestId()), result);
    }

    private static MutationPlan expireMutation(
            BazaarOrderBookSnapshot snapshot,
            ExpireBazaarOrderCommand command
    ) {
        BazaarOrderBook book = BazaarOrderBook.restore(snapshot);
        BazaarOperationResult result = book.expire(command);
        BazaarOrderBookSnapshot next = book.snapshot();
        return new MutationPlan(next,
                BazaarMutation.between(snapshot, next,
                        command.requestId()), result);
    }

    public record PlannedCreate(
            BazaarCreateEscrowIntent terminalIntent,
            BazaarEscrowCommit commit
    ) {
        public PlannedCreate {
            terminalIntent = Objects.requireNonNull(terminalIntent,
                    "terminalIntent");
            commit = Objects.requireNonNull(commit, "commit");
            if (!terminalIntent.terminal()
                    || !terminalIntent.requestId().equals(
                    commit.requestId())) {
                throw new IllegalArgumentException(
                        "Bazaar planned creation is invalid");
            }
        }
    }

    private record MutationPlan(
            BazaarOrderBookSnapshot next,
            BazaarMutation mutation,
            BazaarOperationResult result
    ) {
    }

    private static final class Evidence {
        private final List<EscrowTransaction> transactions =
                new ArrayList<>();
        private final List<LedgerTransaction> ledgers = new ArrayList<>();
        private final List<EscrowClaim> claims = new ArrayList<>();
    }
}
