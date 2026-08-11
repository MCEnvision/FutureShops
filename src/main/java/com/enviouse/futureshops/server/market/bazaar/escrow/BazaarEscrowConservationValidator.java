package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.market.bazaar.BazaarEscrowSettlement;
import com.enviouse.futureshops.server.market.bazaar.BazaarFill;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationResult;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarSettlementKind;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;

import java.util.ArrayList;
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

public final class BazaarEscrowConservationValidator {
    private BazaarEscrowConservationValidator() {
    }

    public static void validate(BazaarEscrowCommit commit) {
        Objects.requireNonNull(commit, "commit");
        BazaarOperationResult result = commit.bookMutation()
                .requestReceipt().orElseThrow().result();
        Optional<CreateBazaarOrderCommand> create = commit.bookMutation()
                .requestReceipt().orElseThrow().createCommand();
        if (result.status() != BazaarOperationStatus.APPLIED) {
            validateRejected(commit, create);
            return;
        }
        validateTransitions(commit, result, create);
        Map<UUID, BazaarFill> fills = new HashMap<>();
        for (BazaarFill fill : result.fills()) {
            if (fills.put(fill.fillId(), fill) != null) {
                throw invalid("Bazaar fill is duplicated");
            }
        }
        Set<UUID> expectedTransactions = new LinkedHashSet<>();
        Map<UUID, EscrowOperation> expectedOperations = new HashMap<>();
        Map<UUID, Map<LedgerAccountId, Long>> expectedLedgers =
                new LinkedHashMap<>();
        Set<UUID> matchedClaims = new HashSet<>();
        if (create.isPresent()) {
            CreateBazaarOrderCommand command = create.orElseThrow();
            expectedTransactions.add(command.activationTransactionId());
            expectedOperations.put(command.activationTransactionId(),
                    command.side() == BazaarOrderSide.BUY
                            ? EscrowOperation.BAZAAR_BUY_ORDER
                            : EscrowOperation.BAZAAR_SELL_ORDER);
            if (command.side() == BazaarOrderSide.BUY) {
                long reserve = BazaarCreateEscrowIntent.initialReserve(
                        command);
                Map<LedgerAccountId, Long> legs = new LinkedHashMap<>();
                merge(legs, BazaarEscrowLedgerAccounts.wallet(
                        command.ownerId()), Math.negateExact(reserve));
                merge(legs, BazaarEscrowLedgerAccounts.hold(
                        command.moneyHoldAccountId().orElseThrow()), reserve);
                expectedLedgers.put(command.activationTransactionId(),
                        legs);
            }
        }

        LinkedHashMap<UUID, List<BazaarEscrowSettlement>> groups =
                groups(result.settlements());
        for (Map.Entry<UUID, List<BazaarEscrowSettlement>> entry
                : groups.entrySet()) {
            UUID transactionId = entry.getKey();
            List<BazaarEscrowSettlement> settlements = entry.getValue();
            expectedTransactions.add(transactionId);
            boolean fillGroup = settlements.get(0).fillId().isPresent();
            expectedOperations.put(transactionId, fillGroup
                    ? EscrowOperation.BAZAAR_FILL
                    : EscrowOperation.BAZAAR_CANCEL);
            Map<LedgerAccountId, Long> legs = new LinkedHashMap<>();
            if (fillGroup) {
                BazaarFill fill = fills.get(settlements.get(0).fillId()
                        .orElseThrow());
                if (fill == null) {
                    throw invalid("Bazaar settlement fill is missing");
                }
                long release = Math.addExact(
                        Math.addExact(fill.grossMinor(),
                                fill.buyerFeeMinor()),
                        fill.buyerPriceImprovementMinor());
                merge(legs, BazaarEscrowLedgerAccounts.hold(
                        holdId(commit, create, fill.buyOrderId())),
                        Math.negateExact(release));
            }
            for (BazaarEscrowSettlement settlement : settlements) {
                switch (settlement.kind()) {
                    case SELLER_MONEY_CLAIM, BUYER_CHANGE_CLAIM,
                            BUYER_REFUND_CLAIM -> {
                        EscrowClaim claim = requireMoneyClaim(commit,
                                settlement);
                        matchedClaims.add(claim.claimId());
                        merge(legs, BazaarEscrowLedgerAccounts.claim(
                                claim.claimId()), settlement.moneyMinor());
                        if (settlement.kind()
                                == BazaarSettlementKind.BUYER_REFUND_CLAIM) {
                            merge(legs, BazaarEscrowLedgerAccounts.hold(
                                    holdId(commit, create,
                                            settlement.orderId()
                                                    .orElseThrow())),
                                    Math.negateExact(
                                            settlement.moneyMinor()));
                        }
                    }
                    case FEE_DESTINATION -> merge(legs,
                            BazaarEscrowLedgerAccounts.fee(),
                            settlement.moneyMinor());
                    case BUYER_ITEM_CLAIM -> {
                        BazaarFill fill = fills.get(settlement.fillId()
                                .orElseThrow());
                        if (fill == null) {
                            throw invalid(
                                    "Bazaar item settlement fill is missing");
                        }
                        matchedClaims.addAll(requireItemClaims(commit,
                                settlement, fill.sellOrderId(),
                                BazaarEscrowLifecyclePlanner
                                        .BUYER_ITEM_LABEL));
                    }
                    case SELLER_ITEM_REFUND_CLAIM -> matchedClaims.addAll(
                            requireItemClaims(commit, settlement,
                                    settlement.orderId().orElseThrow(),
                                    BazaarEscrowLifecyclePlanner
                                            .SELLER_REFUND_LABEL));
                }
            }
            if (!legs.isEmpty()) {
                expectedLedgers.put(transactionId, legs);
            }
        }
        if (matchedClaims.size() != commit.claims().size()) {
            throw invalid("Bazaar commit contains an unquoted claim");
        }
        validateTransactions(commit, expectedTransactions,
                expectedOperations);
        validateLedgers(commit, expectedLedgers);
        validateReserveDeltas(commit, result, create, fills);
    }

    private static void validateRejected(
            BazaarEscrowCommit commit,
            Optional<CreateBazaarOrderCommand> create
    ) {
        if (!commit.orderTransitions().isEmpty()
                || !commit.ledgerTransactions().isEmpty()) {
            throw invalid("Rejected Bazaar request moved reserved value");
        }
        if (create.filter(value -> value.side()
                == BazaarOrderSide.SELL).isEmpty()) {
            if (!commit.completedTransactions().isEmpty()
                    || !commit.claims().isEmpty()) {
                throw invalid("Rejected Bazaar request moved value");
            }
            return;
        }
        CreateBazaarOrderCommand command = create.orElseThrow();
        UUID transactionId = BazaarEscrowIds
                .rejectedCustodyReturnTransactionId(command.requestId(),
                        command.orderId());
        if (commit.completedTransactions().size() != 1
                || !commit.completedTransactions().get(0).transactionId()
                .value().equals(transactionId)
                || commit.completedTransactions().get(0).operation()
                != EscrowOperation.BAZAAR_CANCEL) {
            throw invalid(
                    "Rejected Bazaar sell return transaction is invalid");
        }
        String sourceKey = BazaarEscrowLifecyclePlanner.itemSourceKey(
                transactionId,
                BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM,
                command.orderId());
        long quantity = 0L;
        for (EscrowClaim claim : commit.claims()) {
            ExactItemClaimPayload payload = requireItemClaim(claim,
                    transactionId, command.ownerId(), sourceKey,
                    BazaarEscrowLifecyclePlanner.SELLER_REFUND_LABEL);
            quantity = Math.addExact(quantity, payload.stackCount());
        }
        if (commit.claims().isEmpty() || quantity != command.quantity()) {
            throw invalid("Rejected Bazaar sell return is not conserved");
        }
    }

    private static void validateTransitions(
            BazaarEscrowCommit commit,
            BazaarOperationResult result,
            Optional<CreateBazaarOrderCommand> create
    ) {
        Set<UUID> expected = new LinkedHashSet<>();
        expected.add(result.orderId());
        for (BazaarFill fill : result.fills()) {
            expected.add(fill.buyOrderId());
            expected.add(fill.sellOrderId());
        }
        expected.addAll(result.cancelledMakerOrderIds());
        for (BazaarEscrowSettlement settlement : result.settlements()) {
            settlement.orderId().ifPresent(expected::add);
        }
        Map<UUID, BazaarEscrowOrderTransition> transitions =
                transitionIndex(commit);
        if (!transitions.keySet().equals(expected)) {
            throw invalid("Bazaar order transition coverage is invalid");
        }
        if (create.isPresent()) {
            BazaarEscrowOrderTransition incoming = transitions.get(
                    result.orderId());
            if (incoming.beforeOrder().isPresent()
                    || incoming.beforeBacking().isPresent()
                    || result.order().isEmpty()
                    || !incoming.afterOrder().orElseThrow().equals(
                    BazaarEscrowOrderView.from(
                            result.order().orElseThrow()))) {
                throw invalid(
                        "Bazaar incoming order transition is invalid");
            }
        } else {
            BazaarEscrowOrderTransition target = transitions.get(
                    result.orderId());
            if (target.beforeOrder().isEmpty()
                    || result.order().isEmpty()
                    || !target.afterOrder().orElseThrow().equals(
                    BazaarEscrowOrderView.from(
                            result.order().orElseThrow()))) {
                throw invalid("Bazaar terminal order transition is invalid");
            }
        }
    }

    private static void validateTransactions(
            BazaarEscrowCommit commit,
            Set<UUID> expectedIds,
            Map<UUID, EscrowOperation> expectedOperations
    ) {
        Map<UUID, EscrowTransaction> actual = new HashMap<>();
        for (EscrowTransaction transaction : commit.completedTransactions()) {
            actual.put(transaction.transactionId().value(), transaction);
        }
        if (!actual.keySet().equals(expectedIds)) {
            throw invalid("Bazaar transaction coverage is invalid");
        }
        for (UUID transactionId : expectedIds) {
            EscrowTransaction transaction = actual.get(transactionId);
            if (transaction.operation()
                    != expectedOperations.get(transactionId)
                    || !transaction.timestamps().createdAt().equals(
                    commit.decidedAt())
                    || !transaction.timestamps().updatedAt().equals(
                    commit.decidedAt())) {
                throw invalid("Bazaar transaction evidence is invalid");
            }
            transaction.assetLots().forEach(asset -> asset.money()
                    .ifPresent(money -> {
                        if (!money.currencyId().equals(commit.currencyId())) {
                            throw invalid(
                                    "Bazaar transaction currency is invalid");
                        }
                    }));
        }
    }

    private static void validateLedgers(
            BazaarEscrowCommit commit,
            Map<UUID, Map<LedgerAccountId, Long>> expected
    ) {
        Map<UUID, LedgerTransaction> actual = new HashMap<>();
        for (LedgerTransaction transaction : commit.ledgerTransactions()) {
            actual.put(transaction.transactionId(), transaction);
        }
        if (!actual.keySet().equals(expected.keySet())) {
            throw invalid("Bazaar ledger coverage is invalid");
        }
        for (Map.Entry<UUID, Map<LedgerAccountId, Long>> entry
                : expected.entrySet()) {
            LedgerTransaction transaction = actual.get(entry.getKey());
            if (!transaction.idempotencyKey().equals(
                    BazaarEscrowLifecyclePlanner.ledgerKey(entry.getKey()))
                    || !legMap(transaction.legs()).equals(
                    entry.getValue())) {
                throw invalid("Bazaar ledger conservation is invalid");
            }
        }
    }

    private static void validateReserveDeltas(
            BazaarEscrowCommit commit,
            BazaarOperationResult result,
            Optional<CreateBazaarOrderCommand> create,
            Map<UUID, BazaarFill> fills
    ) {
        for (BazaarEscrowOrderTransition transition
                : commit.orderTransitions()) {
            UUID orderId = transition.orderId();
            BazaarEscrowOrderView after = transition.afterOrder()
                    .orElseThrow();
            int initialQuantity;
            long initialMoney;
            int initialFilled;
            if (transition.beforeOrder().isPresent()) {
                BazaarEscrowOrderView before = transition.beforeOrder()
                        .orElseThrow();
                initialQuantity = before.reservedItemQuantity();
                initialMoney = before.reservedMoneyMinor();
                initialFilled = before.filledQuantity();
            } else {
                CreateBazaarOrderCommand command = create.orElseThrow();
                initialQuantity = command.side() == BazaarOrderSide.SELL
                        ? command.quantity() : 0;
                initialMoney = BazaarCreateEscrowIntent.initialReserve(
                        command);
                initialFilled = 0;
            }
            long moneyReleased = 0L;
            int itemsReleased = 0;
            int filled = 0;
            for (BazaarFill fill : fills.values()) {
                if (fill.buyOrderId().equals(orderId)) {
                    moneyReleased = Math.addExact(moneyReleased,
                            Math.addExact(Math.addExact(fill.grossMinor(),
                                    fill.buyerFeeMinor()),
                                    fill.buyerPriceImprovementMinor()));
                    filled = Math.addExact(filled, fill.quantity());
                }
                if (fill.sellOrderId().equals(orderId)) {
                    filled = Math.addExact(filled, fill.quantity());
                }
            }
            for (BazaarEscrowSettlement settlement
                    : result.settlements()) {
                if (settlement.kind()
                        == BazaarSettlementKind.BUYER_REFUND_CLAIM
                        && settlement.orderId().filter(orderId::equals)
                        .isPresent()) {
                    moneyReleased = Math.addExact(moneyReleased,
                            settlement.moneyMinor());
                }
                if (settlement.kind()
                        == BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM
                        && settlement.orderId().filter(orderId::equals)
                        .isPresent()) {
                    itemsReleased = Math.addExact(itemsReleased,
                            settlement.itemQuantity());
                }
                if (settlement.kind()
                        == BazaarSettlementKind.BUYER_ITEM_CLAIM
                        && settlement.fillId().map(fills::get)
                        .filter(value -> value.sellOrderId().equals(orderId))
                        .isPresent()) {
                    itemsReleased = Math.addExact(itemsReleased,
                            settlement.itemQuantity());
                }
            }
            if (Math.subtractExact(initialMoney, moneyReleased)
                    != after.reservedMoneyMinor()
                    || Math.subtractExact(initialQuantity, itemsReleased)
                    != after.reservedItemQuantity()
                    || Math.addExact(initialFilled, filled)
                    != after.filledQuantity()) {
                throw invalid("Bazaar reserve transition is not conserved");
            }
        }
    }

    private static EscrowClaim requireMoneyClaim(
            BazaarEscrowCommit commit,
            BazaarEscrowSettlement settlement
    ) {
        UUID orderId = settlement.orderId().orElseThrow();
        UUID claimId = BazaarEscrowIds.moneyClaimId(
                settlement.transactionId(), settlement.kind(), orderId,
                settlement.ownerId());
        EscrowClaim claim = commit.claims().stream()
                .filter(value -> value.claimId().equals(claimId))
                .findFirst().orElseThrow(() -> invalid(
                        "Bazaar money claim is missing"));
        String label = switch (settlement.kind()) {
            case SELLER_MONEY_CLAIM -> BazaarEscrowLifecyclePlanner
                    .SELLER_MONEY_LABEL;
            case BUYER_CHANGE_CLAIM -> BazaarEscrowLifecyclePlanner
                    .BUYER_CHANGE_LABEL;
            case BUYER_REFUND_CLAIM -> BazaarEscrowLifecyclePlanner
                    .BUYER_REFUND_LABEL;
            default -> throw invalid("Bazaar money claim kind is invalid");
        };
        if (!claim.transactionId().equals(settlement.transactionId())
                || !claim.ownerId().equals(settlement.ownerId())
                || !claim.sourceKey().equals(
                BazaarEscrowLifecyclePlanner.moneySourceKey(settlement))
                || claim.kind() != ClaimKind.MONEY
                || claim.originalUnits() != settlement.moneyMinor()
                || claim.remainingUnits() != settlement.moneyMinor()
                || claim.payload().length != 0
                || claim.status() != ClaimStatus.PENDING
                || !claim.label().equals(label)
                || !claim.createdAt().equals(commit.decidedAt())
                || !claim.updatedAt().equals(commit.decidedAt())) {
            throw invalid("Bazaar money claim is invalid");
        }
        return claim;
    }

    private static Set<UUID> requireItemClaims(
            BazaarEscrowCommit commit,
            BazaarEscrowSettlement settlement,
            UUID custodyOrderId,
            String label
    ) {
        String sourceKey = BazaarEscrowLifecyclePlanner.itemSourceKey(
                settlement.transactionId(), settlement.kind(),
                custodyOrderId);
        Set<UUID> ids = new HashSet<>();
        long quantity = 0L;
        for (EscrowClaim claim : commit.claims()) {
            if (!claim.transactionId().equals(settlement.transactionId())
                    || claim.kind() != ClaimKind.ITEM) {
                continue;
            }
            ExactItemClaimPayload payload;
            try {
                payload = ExactItemClaimPayloadCodec.decode(
                        claim.payload());
            } catch (RuntimeException exception) {
                continue;
            }
            if (!payload.sourceKey().equals(sourceKey)) {
                continue;
            }
            requireItemClaim(claim, settlement.transactionId(),
                    settlement.ownerId(), sourceKey, label);
            ids.add(claim.claimId());
            quantity = Math.addExact(quantity, payload.stackCount());
        }
        if (ids.isEmpty() || quantity != settlement.itemQuantity()) {
            throw invalid("Bazaar exact item claim is not conserved");
        }
        return ids;
    }

    private static ExactItemClaimPayload requireItemClaim(
            EscrowClaim claim,
            UUID transactionId,
            UUID ownerId,
            String sourceKey,
            String label
    ) {
        ExactItemClaimPayload payload = ExactItemClaimPayloadCodec.decode(
                claim.payload());
        if (!claim.claimId().equals(payload.lotId())
                || !claim.transactionId().equals(transactionId)
                || !claim.ownerId().equals(ownerId)
                || !claim.sourceKey().equals(sourceKey + "."
                + payload.portionIndex())
                || claim.kind() != ClaimKind.ITEM
                || claim.originalUnits() != payload.stackCount()
                || claim.remainingUnits() != payload.stackCount()
                || claim.status() != ClaimStatus.PENDING
                || !claim.label().equals(label)
                || !payload.sourceTransactionId().equals(transactionId)
                || !payload.sourceKey().equals(sourceKey)) {
            throw invalid("Bazaar exact item claim is invalid");
        }
        return payload;
    }

    private static UUID holdId(
            BazaarEscrowCommit commit,
            Optional<CreateBazaarOrderCommand> create,
            UUID orderId
    ) {
        if (create.filter(value -> value.orderId().equals(orderId)
                && value.side() == BazaarOrderSide.BUY).isPresent()) {
            return create.orElseThrow().moneyHoldAccountId().orElseThrow();
        }
        BazaarEscrowOrderTransition transition = transitionIndex(commit)
                .get(orderId);
        if (transition == null) {
            throw invalid("Bazaar buy transition is missing");
        }
        return transition.beforeBacking()
                .or(() -> transition.afterBacking())
                .flatMap(BazaarEscrowOrderBacking::buyFunding)
                .map(BazaarBuyFundingEvidence::holdAccountId)
                .orElseThrow(() -> invalid(
                        "Bazaar buy hold evidence is missing"));
    }

    private static Map<UUID, BazaarEscrowOrderTransition> transitionIndex(
            BazaarEscrowCommit commit
    ) {
        Map<UUID, BazaarEscrowOrderTransition> result = new HashMap<>();
        for (BazaarEscrowOrderTransition transition
                : commit.orderTransitions()) {
            result.put(transition.orderId(), transition);
        }
        return result;
    }

    private static LinkedHashMap<UUID, List<BazaarEscrowSettlement>> groups(
            List<BazaarEscrowSettlement> settlements
    ) {
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

    private static Map<LedgerAccountId, Long> legMap(List<LedgerLeg> legs) {
        Map<LedgerAccountId, Long> result = new LinkedHashMap<>();
        for (LedgerLeg leg : legs) {
            merge(result, leg.account(), leg.deltaMinor());
        }
        result.entrySet().removeIf(entry -> entry.getValue() == 0L);
        return result;
    }

    private static void merge(
            Map<LedgerAccountId, Long> values,
            LedgerAccountId account,
            long delta
    ) {
        values.merge(account, delta, Math::addExact);
        if (values.get(account) == 0L) {
            values.remove(account);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
