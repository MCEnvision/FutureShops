package com.enviouse.futureshops.server.market.bazaar;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class BazaarSnapshotValidator {
    private static final UUID ZERO = new UUID(0L, 0L);
    private static final UUID FEE_ACCOUNT = BazaarIds.systemAccount("fees");

    private BazaarSnapshotValidator() {
    }

    public static void validate(BazaarOrderBookSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            validateSnapshot(snapshot);
        } catch (ArithmeticException | IllegalStateException exception) {
            throw new IllegalArgumentException(
                    "Bazaar snapshot arithmetic is invalid", exception);
        }
    }

    private static void validateSnapshot(BazaarOrderBookSnapshot snapshot) {
        Map<Long, BazaarRuleSnapshot> rules = validateRules(snapshot);
        ProductCatalog catalog = validateProducts(snapshot.products());
        Map<UUID, BazaarOrder> orders = new LinkedHashMap<>();
        BazaarIdentityRegistry identities = new BazaarIdentityRegistry();
        Set<UUID> activationTransactions = new HashSet<>();
        Set<UUID> holdAccounts = new HashSet<>();
        Set<UUID> custodyLots = new HashSet<>();
        Set<Long> canonicalSequences = new TreeSet<>();
        long maximumSequence = 0L;
        for (BazaarOrder order : snapshot.orders()) {
            identities.claimOrder(order);
            if (orders.putIfAbsent(order.orderId(), order) != null
                    || !activationTransactions.add(order.activationTransactionId())
                    || order.moneyHoldAccountId().isPresent()
                    && (!holdAccounts.add(order.moneyHoldAccountId().orElseThrow())
                    || custodyLots.contains(order.moneyHoldAccountId().orElseThrow()))
                    || order.custodyLotId().isPresent()
                    && (!custodyLots.add(order.custodyLotId().orElseThrow())
                    || holdAccounts.contains(order.custodyLotId().orElseThrow()))) {
                throw invalid("Bazaar snapshot has duplicate order identity");
            }
            BazaarProduct product = catalog.versions().get(new BazaarProductVersionKey(
                    order.productId(), order.productVersion()));
            BazaarProduct current = catalog.current().get(order.productId());
            BazaarRuleSnapshot trusted = rules.get(order.rules().configRevision());
            if (product == null || current == null) {
                throw invalid("Bazaar order product version is unavailable");
            }
            if (trusted == null || !trusted.equals(order.rules())) {
                throw invalid("Bazaar order rule snapshot is not trusted");
            }
            product.requirePrice(order.limitPriceMinor());
            product.requireQuantity(order.originalQuantity());
            if (order.side() == BazaarOrderSide.BUY && !order.state().terminal()
                    && order.reservedMoneyMinor()
                    != order.requiredRemainingMoneyReserve()) {
                throw invalid("Bazaar buyer reserve does not match its order");
            }
            if ((!current.status().matches()
                    || current.version() != order.productVersion())
                    && order.matchable()) {
                throw invalid("Unavailable Bazaar product has a matchable order");
            }
            if (!canonicalSequences.add(order.acceptedSequence())) {
                throw invalid("Bazaar snapshot has duplicate canonical sequence");
            }
            maximumSequence = Math.max(maximumSequence,
                    order.acceptedSequence());
        }

        Map<UUID, RunningOrder> running = new HashMap<>();
        Map<UUID, BazaarFill> fills = new LinkedHashMap<>();
        Set<Long> fillSequences = new HashSet<>();
        Set<UUID> settlementTransactions = new HashSet<>();
        List<BazaarFill> orderedFills = snapshot.fills().stream()
                .sorted(Comparator.comparingLong(BazaarFill::sequence)).toList();
        for (BazaarFill fill : orderedFills) {
            identities.claimFill(fill);
            if (fills.putIfAbsent(fill.fillId(), fill) != null
                    || !fillSequences.add(fill.sequence())
                    || !settlementTransactions.add(
                    fill.settlementTransactionId())) {
                throw invalid("Bazaar snapshot has duplicate fill identity");
            }
            if (!canonicalSequences.add(fill.sequence())) {
                throw invalid("Bazaar snapshot has duplicate canonical sequence");
            }
            validateFill(fill, orders, catalog, running);
            maximumSequence = Math.max(maximumSequence, fill.sequence());
        }
        for (BazaarOrder order : orders.values()) {
            RunningOrder evidence = running.getOrDefault(order.orderId(),
                    new RunningOrder());
            if (evidence.quantity != order.filledQuantity()
                    || evidence.makerGross != order.makerGrossMinor()
                    || evidence.takerGross != order.takerGrossMinor()
                    || evidence.fee != order.accruedFeeMinor()) {
                throw invalid("Bazaar order fill totals do not reconcile");
            }
        }

        ReceiptEvidence receiptEvidence = validateReceipts(snapshot, identities,
                rules, catalog, orders, orderedFills);
        for (Map.Entry<UUID, String> lifecycle
                : snapshot.lifecycleReceipts().entrySet()) {
            identities.claimLifecycle(lifecycle.getKey());
            if (!lifecycle.getValue().matches("[0-9a-f]{64}")) {
                throw invalid("Bazaar lifecycle receipt is invalid");
            }
        }
        if (!receiptEvidence.fillIds().equals(fills.keySet())) {
            throw invalid("Bazaar fill receipt chain is incomplete");
        }
        if (!receiptEvidence.createdOrderIds().equals(orders.keySet())) {
            throw invalid("Bazaar order receipt chain is incomplete");
        }

        for (Map.Entry<String, Long> entry
                : snapshot.referencePrices().entrySet()) {
            BazaarProduct product = catalog.current().get(entry.getKey());
            if (product == null) {
                throw invalid("Bazaar reference price product is missing");
            }
            product.requirePrice(entry.getValue());
        }
        for (UUID terminalTransaction : snapshot.terminalTransactions()) {
            identities.claimTerminal(terminalTransaction);
            if (terminalTransaction == null || ZERO.equals(terminalTransaction)
                    || activationTransactions.contains(terminalTransaction)
                    || settlementTransactions.contains(terminalTransaction)) {
                throw invalid("Bazaar terminal transaction identity is invalid");
            }
        }
        if (!receiptEvidence.terminalTransactions().equals(
                snapshot.terminalTransactions())) {
            throw invalid("Bazaar terminal receipt chain is incomplete");
        }
        long expectedSequence = 1L;
        for (long sequence : canonicalSequences) {
            if (sequence != expectedSequence) {
                throw invalid("Bazaar canonical sequence contains a gap");
            }
            expectedSequence = Math.incrementExact(expectedSequence);
        }
        if (snapshot.nextSequence() != expectedSequence
                || snapshot.nextSequence() <= maximumSequence) {
            throw invalid("Bazaar next sequence does not follow persisted state");
        }
    }

    private static Map<Long, BazaarRuleSnapshot> validateRules(
            BazaarOrderBookSnapshot snapshot) {
        Map<Long, BazaarRuleSnapshot> rules = new LinkedHashMap<>();
        long prior = -1L;
        for (BazaarRuleSnapshot rule : snapshot.ruleSnapshots().stream()
                .sorted(Comparator.comparingLong(
                        BazaarRuleSnapshot::configRevision)).toList()) {
            if (rules.putIfAbsent(rule.configRevision(), rule) != null
                    || rule.configRevision() <= prior) {
                throw invalid("Bazaar snapshot has duplicate rule revisions");
            }
            prior = rule.configRevision();
        }
        if (snapshot.effectiveRuleRevision() == -1L) {
            if (!rules.isEmpty()) {
                throw invalid("Bazaar effective rule revision is missing");
            }
        } else if (!rules.containsKey(snapshot.effectiveRuleRevision())
                || snapshot.effectiveRuleRevision() != prior) {
            throw invalid("Bazaar effective rule revision is not trusted");
        }
        return rules;
    }

    private static ProductCatalog validateProducts(List<BazaarProduct> source) {
        Map<BazaarProductVersionKey, BazaarProduct> versions =
                new LinkedHashMap<>();
        Map<String, BazaarProduct> current = new LinkedHashMap<>();
        for (BazaarProduct product : source) {
            if (versions.putIfAbsent(BazaarProductVersionKey.of(product),
                    product) != null) {
                throw invalid("Bazaar snapshot has duplicate product versions");
            }
            BazaarProduct previous = current.get(product.productId());
            if (previous == null || product.version() > previous.version()) {
                current.put(product.productId(), product);
            }
        }
        return new ProductCatalog(Map.copyOf(versions), Map.copyOf(current));
    }

    private static void validateFill(BazaarFill fill,
                                     Map<UUID, BazaarOrder> orders,
                                     ProductCatalog catalog,
                                     Map<UUID, RunningOrder> running) {
        BazaarOrder buy = orders.get(fill.buyOrderId());
        BazaarOrder sell = orders.get(fill.sellOrderId());
        BazaarOrder maker = orders.get(fill.makerOrderId());
        BazaarOrder taker = orders.get(fill.takerOrderId());
        BazaarProduct product = catalog.versions().get(
                new BazaarProductVersionKey(fill.productId(),
                        fill.productVersion()));
        if (buy == null || sell == null || maker == null || taker == null
                || product == null || buy.side() != BazaarOrderSide.BUY
                || sell.side() != BazaarOrderSide.SELL
                || maker.orderId().equals(taker.orderId())
                || maker.ownerId().equals(taker.ownerId())
                || !Set.of(buy.orderId(), sell.orderId()).equals(
                Set.of(maker.orderId(), taker.orderId()))
                || !buy.productId().equals(fill.productId())
                || !sell.productId().equals(fill.productId())
                || buy.productVersion() != fill.productVersion()
                || sell.productVersion() != fill.productVersion()
                || maker.acceptedSequence() >= taker.acceptedSequence()
                || fill.sequence() <= taker.acceptedSequence()
                || fill.filledAtMillis() != taker.createdAtMillis()
                || maker.expiredAt(fill.filledAtMillis())
                || taker.expiredAt(fill.filledAtMillis())
                || !crosses(taker, maker)
                || fill.priceMinor() != executionPrice(product, maker, taker)
                || !fill.settlementTransactionId().equals(
                BazaarIds.settlement(fill.fillId()))) {
            throw invalid("Bazaar fill order evidence is invalid");
        }
        product.requirePrice(fill.priceMinor());
        product.requireQuantity(fill.quantity());
        RunningOrder makerEvidence = running.computeIfAbsent(
                maker.orderId(), ignored -> new RunningOrder());
        RunningOrder takerEvidence = running.computeIfAbsent(
                taker.orderId(), ignored -> new RunningOrder());
        int makerAvailable = Math.subtractExact(maker.originalQuantity(),
                makerEvidence.quantity);
        int takerAvailable = Math.subtractExact(taker.originalQuantity(),
                takerEvidence.quantity);
        if (fill.quantity() != Math.min(makerAvailable, takerAvailable)) {
            throw invalid("Bazaar fill quantity is not canonical");
        }
        RunningOrder buyerEvidence = running.computeIfAbsent(
                buy.orderId(), ignored -> new RunningOrder());
        long expectedImprovement = expectedBuyerRelease(fill, buy,
                buyerEvidence, fill.makerOrderId().equals(buy.orderId()));
        if (expectedImprovement != fill.buyerPriceImprovementMinor()) {
            throw invalid("Bazaar buyer price improvement is invalid");
        }
        applyFillEvidence(fill, buy, fill.makerOrderId().equals(buy.orderId()),
                fill.buyerFeeMinor(), running);
        applyFillEvidence(fill, sell,
                fill.makerOrderId().equals(sell.orderId()),
                fill.sellerFeeMinor(), running);
    }

    private static long expectedBuyerRelease(BazaarFill fill,
                                             BazaarOrder buyer,
                                             RunningOrder evidence,
                                             boolean maker) {
        if (Math.addExact(evidence.quantity, fill.quantity())
                > buyer.originalQuantity()) {
            throw invalid("Bazaar fill quantity exceeds the buyer order");
        }
        long priorRoleGross = maker ? evidence.makerGross
                : evidence.takerGross;
        int roleRate = maker ? buyer.rules().makerFeeBasisPoints()
                : buyer.rules().takerFeeBasisPoints();
        long roleFeeBefore = BazaarFeeMath.cumulativeFee(priorRoleGross,
                roleRate);
        long fee = BazaarFeeMath.incrementalFee(priorRoleGross,
                roleFeeBefore, fill.grossMinor(), roleRate);
        int maximumRate = Math.max(buyer.rules().makerFeeBasisPoints(),
                buyer.rules().takerFeeBasisPoints());
        long maximumGross = Math.multiplyExact(buyer.limitPriceMinor(),
                buyer.originalQuantity());
        long maximumFee = BazaarFeeMath.cumulativeFee(maximumGross,
                maximumRate);
        int priorRemaining = Math.subtractExact(buyer.originalQuantity(),
                evidence.quantity);
        int nextRemaining = Math.subtractExact(priorRemaining,
                fill.quantity());
        long priorReserve = Math.addExact(Math.multiplyExact(
                buyer.limitPriceMinor(), priorRemaining),
                Math.subtractExact(maximumFee, evidence.fee));
        long nextReserve = nextRemaining == 0 ? 0L : Math.addExact(
                Math.multiplyExact(buyer.limitPriceMinor(), nextRemaining),
                Math.subtractExact(maximumFee,
                        Math.addExact(evidence.fee, fee)));
        return Math.subtractExact(Math.subtractExact(priorReserve,
                Math.addExact(fill.grossMinor(), fee)), nextReserve);
    }

    private static void applyFillEvidence(BazaarFill fill,
                                          BazaarOrder order,
                                          boolean maker,
                                          long reportedFee,
                                          Map<UUID, RunningOrder> running) {
        RunningOrder evidence = running.computeIfAbsent(order.orderId(),
                ignored -> new RunningOrder());
        if (Math.addExact(evidence.quantity, fill.quantity())
                > order.originalQuantity()) {
            throw invalid("Bazaar fill quantity exceeds its order");
        }
        long priorGross = maker ? evidence.makerGross : evidence.takerGross;
        int rate = maker ? order.rules().makerFeeBasisPoints()
                : order.rules().takerFeeBasisPoints();
        long priorRoleFee = BazaarFeeMath.cumulativeFee(priorGross, rate);
        long expectedFee = BazaarFeeMath.incrementalFee(priorGross,
                priorRoleFee, fill.grossMinor(), rate);
        if (reportedFee != expectedFee) {
            throw invalid("Bazaar fill fee does not match its rule snapshot");
        }
        evidence.quantity = Math.addExact(evidence.quantity, fill.quantity());
        if (maker) {
            evidence.makerGross = Math.addExact(evidence.makerGross,
                    fill.grossMinor());
        } else {
            evidence.takerGross = Math.addExact(evidence.takerGross,
                    fill.grossMinor());
        }
        evidence.fee = Math.addExact(evidence.fee, reportedFee);
    }

    private static ReceiptEvidence validateReceipts(
            BazaarOrderBookSnapshot snapshot,
            BazaarIdentityRegistry identities,
            Map<Long, BazaarRuleSnapshot> rules,
            ProductCatalog catalog,
            Map<UUID, BazaarOrder> orders,
            List<BazaarFill> fills) {
        Set<UUID> createdOrders = new HashSet<>();
        Set<UUID> receiptFills = new HashSet<>();
        Set<UUID> terminalTransactions = new HashSet<>();
        Set<UUID> terminalOrders = new HashSet<>();
        for (Map.Entry<UUID, BazaarRequestReceipt> entry
                : snapshot.receipts().entrySet()) {
            BazaarRequestReceipt receipt = entry.getValue();
            BazaarOperationResult result = receipt.result();
            identities.claimRequest(entry.getKey(), result.orderId(),
                    result.operation());
            if (!entry.getKey().equals(receipt.requestId())
                    || !entry.getKey().equals(result.requestId())
                    || !receipt.fingerprint().equals(
                    receipt.canonicalFingerprint())) {
                throw invalid("Bazaar request receipt identity is invalid");
            }
            if (result.status() != BazaarOperationStatus.APPLIED) {
                continue;
            }
            if (result.operation() == BazaarOperationType.CREATE) {
                validateCreateReceipt(receipt, rules, catalog, orders, fills,
                        createdOrders, receiptFills, terminalTransactions,
                        terminalOrders);
            } else {
                validateTerminalReceipt(receipt, orders, terminalTransactions,
                        terminalOrders);
            }
        }
        for (BazaarOrder order : orders.values()) {
            if ((order.state() == BazaarOrderState.CANCELLED
                    || order.state() == BazaarOrderState.EXPIRED)
                    && !terminalOrders.contains(order.orderId())) {
                throw invalid("Bazaar terminal order has no canonical receipt");
            }
        }
        return new ReceiptEvidence(Set.copyOf(createdOrders),
                Set.copyOf(receiptFills), Set.copyOf(terminalTransactions));
    }

    private static void validateCreateReceipt(
            BazaarRequestReceipt receipt,
            Map<Long, BazaarRuleSnapshot> rules,
            ProductCatalog catalog,
            Map<UUID, BazaarOrder> orders,
            List<BazaarFill> fills,
            Set<UUID> createdOrders,
            Set<UUID> receiptFills,
            Set<UUID> terminalTransactions,
            Set<UUID> terminalOrders) {
        CreateBazaarOrderCommand command = receipt.createCommand()
                .orElseThrow();
        BazaarOperationResult result = receipt.result();
        BazaarOrder evidence = result.order().orElseThrow(() -> invalid(
                "Applied Bazaar create receipt has no order evidence"));
        BazaarOrder canonical = orders.get(command.orderId());
        BazaarProduct product = catalog.versions().get(
                new BazaarProductVersionKey(command.productId(),
                        command.productVersion()));
        BazaarRuleSnapshot trusted = rules.get(
                command.rules().configRevision());
        if (canonical == null || product == null || trusted == null
                || !trusted.equals(command.rules())
                || !createdOrders.add(command.orderId())
                || !sameCreationIdentity(command, evidence)
                || !sameOrderIdentity(evidence, canonical)
                || evidence.state().terminal()
                && !evidence.equals(canonical)) {
            throw invalid("Bazaar create receipt is not canonical");
        }
        product.requirePrice(command.limitPriceMinor());
        product.requireQuantity(command.quantity());
        List<BazaarFill> expectedFills = fills.stream()
                .filter(fill -> fill.takerOrderId().equals(command.orderId()))
                .sorted(Comparator.comparingLong(BazaarFill::sequence)).toList();
        if (!expectedFills.equals(result.fills())) {
            throw invalid("Bazaar create receipt fill chain is invalid");
        }
        for (int ordinal = 0; ordinal < expectedFills.size(); ordinal++) {
            BazaarFill fill = expectedFills.get(ordinal);
            if (!fill.fillId().equals(BazaarIds.fill(command.requestId(),
                    fill.makerOrderId(), command.orderId(), ordinal))
                    || !receiptFills.add(fill.fillId())) {
                throw invalid("Bazaar create receipt fill identity is invalid");
            }
        }
        validateCreateOrderEvidence(command, evidence, expectedFills,
                result.productHalted());

        List<BazaarEscrowSettlement> expectedSettlements = new ArrayList<>();
        for (BazaarFill fill : expectedFills) {
            addFillSettlements(fill, orders.get(fill.buyOrderId()),
                    orders.get(fill.sellOrderId()), expectedSettlements);
        }
        Set<UUID> cancelledMakers = new HashSet<>();
        for (UUID makerId : result.cancelledMakerOrderIds()) {
            BazaarOrder maker = orders.get(makerId);
            UUID transaction = BazaarIds.derive("self_trade_cancel",
                    command.requestId(), makerId);
            if (maker == null || maker.state() != BazaarOrderState.CANCELLED
                    || !maker.ownerId().equals(command.ownerId())
                    || maker.acceptedSequence() >= evidence.acceptedSequence()
                    || maker.side() == command.side()
                    || command.rules().selfTradePolicy()
                    != BazaarSelfTradePolicy.CANCEL_MAKER
                    || !cancelledMakers.add(makerId)) {
                throw invalid("Bazaar cancelled maker receipt is invalid");
            }
            addTerminalSettlements(transaction, maker,
                    expectedSettlements);
            if (!terminalTransactions.add(transaction)
                    || !terminalOrders.add(makerId)) {
                throw invalid("Bazaar cancelled maker transition is duplicated");
            }
        }
        if (evidence.state() == BazaarOrderState.CANCELLED) {
            UUID selfTrade = BazaarIds.immediateTerminal(command.requestId(),
                    command.orderId(), "self_trade");
            UUID remainder = BazaarIds.immediateTerminal(command.requestId(),
                    command.orderId(), "remainder");
            UUID transaction = terminalTransactionForOrder(result.settlements(),
                    evidence.orderId());
            if (!transaction.equals(selfTrade) && !transaction.equals(remainder)
                    || transaction.equals(selfTrade)
                    && command.rules().selfTradePolicy()
                    != BazaarSelfTradePolicy.CANCEL_TAKER
                    || transaction.equals(remainder)
                    && command.timeInForce().rests()) {
                throw invalid("Bazaar immediate terminal receipt is invalid");
            }
            addTerminalSettlements(transaction, evidence,
                    expectedSettlements);
            if (!terminalTransactions.add(transaction)
                    || !terminalOrders.add(evidence.orderId())) {
                throw invalid("Bazaar immediate terminal transition is duplicated");
            }
        }
        if (!frequencies(expectedSettlements).equals(
                frequencies(result.settlements()))) {
            throw invalid("Bazaar create settlement chain is invalid");
        }
    }

    private static void validateCreateOrderEvidence(
            CreateBazaarOrderCommand command,
            BazaarOrder order,
            List<BazaarFill> fills,
            boolean productHalted) {
        long requestedNotional = Math.multiplyExact(command.limitPriceMinor(),
                command.quantity());
        int maximumRate = Math.max(command.rules().makerFeeBasisPoints(),
                command.rules().takerFeeBasisPoints());
        long requestedReserve = command.side() == BazaarOrderSide.BUY
                ? Math.addExact(requestedNotional,
                BazaarFeeMath.cumulativeFee(requestedNotional, maximumRate))
                : 0L;
        if (command.quantity() > command.rules().maximumOrderQuantity()
                || requestedNotional > command.rules().maximumNotionalMinor()
                || requestedReserve
                > command.rules().maximumEscrowedValuePerPlayerMinor()
                || command.type() == BazaarOrderType.INSTANT
                && command.timeInForce().rests()
                || command.timeInForce() == BazaarTimeInForce.GOOD_UNTIL_TIME
                && command.expiresAtMillis() <= command.createdAtMillis()
                || command.timeInForce() != BazaarTimeInForce.GOOD_UNTIL_TIME
                && command.expiresAtMillis() != 0L) {
            throw invalid("Bazaar create command evidence is invalid");
        }
        int quantity = 0;
        long gross = 0L;
        long fee = 0L;
        for (BazaarFill fill : fills) {
            quantity = Math.addExact(quantity, fill.quantity());
            gross = Math.addExact(gross, fill.grossMinor());
            fee = Math.addExact(fee, command.side() == BazaarOrderSide.BUY
                    ? fill.buyerFeeMinor() : fill.sellerFeeMinor());
        }
        int remaining = Math.subtractExact(command.quantity(), quantity);
        BazaarOrderState expectedFilledState = remaining == 0
                ? BazaarOrderState.FILLED
                : quantity == 0 ? BazaarOrderState.OPEN
                : BazaarOrderState.PARTIALLY_FILLED;
        boolean transitioned = order.state() == BazaarOrderState.CANCELLED
                || order.state() == BazaarOrderState.FROZEN;
        boolean allowedState = order.state() == BazaarOrderState.OPEN
                || order.state() == BazaarOrderState.PARTIALLY_FILLED
                || order.state() == BazaarOrderState.FILLED
                || order.state() == BazaarOrderState.FROZEN
                || order.state() == BazaarOrderState.CANCELLED;
        if (!allowedState || order.filledQuantity() != quantity
                || order.remainingQuantity() != remaining
                || order.makerGrossMinor() != 0L
                || order.takerGrossMinor() != gross
                || order.accruedFeeMinor() != fee
                || order.revision() != fills.size() + (transitioned ? 1L : 0L)
                || order.state() == BazaarOrderState.FILLED
                && expectedFilledState != BazaarOrderState.FILLED
                || order.state() == BazaarOrderState.OPEN
                && expectedFilledState != BazaarOrderState.OPEN
                || order.state() == BazaarOrderState.PARTIALLY_FILLED
                && expectedFilledState != BazaarOrderState.PARTIALLY_FILLED
                || order.state() == BazaarOrderState.FROZEN
                && (!productHalted || remaining == 0)
                || order.state() == BazaarOrderState.CANCELLED
                && remaining == 0
                || productHalted && !command.rules().circuitBreakerEnabled()
                || productHalted && remaining == 0
                || !command.timeInForce().rests()
                && remaining > 0
                && order.state() != BazaarOrderState.CANCELLED
                || command.timeInForce() == BazaarTimeInForce.FILL_OR_KILL
                && remaining > 0) {
            throw invalid("Bazaar create order transition is invalid");
        }
        long expectedReserve = order.side() == BazaarOrderSide.BUY
                && !order.state().terminal()
                ? order.requiredRemainingMoneyReserve() : 0L;
        int expectedItems = order.side() == BazaarOrderSide.SELL
                && !order.state().terminal() ? remaining : 0;
        if (order.reservedMoneyMinor() != expectedReserve
                || order.reservedItemQuantity() != expectedItems) {
            throw invalid("Bazaar create order reserve is invalid");
        }
    }

    private static void validateTerminalReceipt(
            BazaarRequestReceipt receipt,
            Map<UUID, BazaarOrder> orders,
            Set<UUID> terminalTransactions,
            Set<UUID> terminalOrders) {
        BazaarOperationResult result = receipt.result();
        BazaarOrder canonical = orders.get(result.orderId());
        BazaarOrder evidence = result.order().orElseThrow(() -> invalid(
                "Applied Bazaar terminal receipt has no order evidence"));
        UUID transaction;
        long expectedRevision;
        if (result.operation() == BazaarOperationType.CANCEL) {
            CancelBazaarOrderCommand command = receipt.cancelCommand()
                    .orElseThrow();
            transaction = command.terminalTransactionId();
            expectedRevision = command.expectedRevision();
            if (!command.actorId().equals(evidence.ownerId())
                    || command.nowMillis() < evidence.createdAtMillis()
                    || Math.subtractExact(command.nowMillis(),
                    evidence.createdAtMillis())
                    < evidence.rules().minimumLifetimeMillis()
                    || evidence.state() != BazaarOrderState.CANCELLED) {
                throw invalid("Bazaar cancellation receipt is invalid");
            }
        } else {
            ExpireBazaarOrderCommand command = receipt.expireCommand()
                    .orElseThrow();
            transaction = command.terminalTransactionId();
            expectedRevision = command.expectedRevision();
            if (!evidence.expiredAt(command.nowMillis())
                    || evidence.state() != BazaarOrderState.EXPIRED) {
                throw invalid("Bazaar expiration receipt is invalid");
            }
        }
        if (expectedRevision < 0L || canonical == null
                || !canonical.equals(evidence)
                || evidence.revision() != Math.addExact(expectedRevision, 1L)
                || !transaction.equals(BazaarIds.terminal(result.requestId(),
                result.orderId(), result.operation()))
                || !terminalTransactions.add(transaction)
                || !terminalOrders.add(result.orderId())) {
            throw invalid("Bazaar terminal receipt transition is invalid");
        }
        List<BazaarEscrowSettlement> expected = new ArrayList<>();
        addTerminalSettlements(transaction, evidence, expected);
        if (!frequencies(expected).equals(frequencies(result.settlements()))) {
            throw invalid("Bazaar terminal settlement chain is invalid");
        }
    }

    private static boolean sameCreationIdentity(
            CreateBazaarOrderCommand command, BazaarOrder order) {
        return command.orderId().equals(order.orderId())
                && command.ownerId().equals(order.ownerId())
                && command.activationTransactionId().equals(
                order.activationTransactionId())
                && command.moneyHoldAccountId().equals(
                order.moneyHoldAccountId())
                && command.custodyLotId().equals(order.custodyLotId())
                && command.productId().equals(order.productId())
                && command.productVersion() == order.productVersion()
                && command.side() == order.side()
                && command.type() == order.type()
                && command.timeInForce() == order.timeInForce()
                && command.limitPriceMinor() == order.limitPriceMinor()
                && command.quantity() == order.originalQuantity()
                && command.createdAtMillis() == order.createdAtMillis()
                && command.expiresAtMillis() == order.expiresAtMillis()
                && command.rules().equals(order.rules());
    }

    private static boolean sameOrderIdentity(BazaarOrder first,
                                             BazaarOrder second) {
        return first.orderId().equals(second.orderId())
                && first.ownerId().equals(second.ownerId())
                && first.activationTransactionId().equals(
                second.activationTransactionId())
                && first.moneyHoldAccountId().equals(second.moneyHoldAccountId())
                && first.custodyLotId().equals(second.custodyLotId())
                && first.productId().equals(second.productId())
                && first.productVersion() == second.productVersion()
                && first.side() == second.side()
                && first.type() == second.type()
                && first.timeInForce() == second.timeInForce()
                && first.limitPriceMinor() == second.limitPriceMinor()
                && first.originalQuantity() == second.originalQuantity()
                && first.acceptedSequence() == second.acceptedSequence()
                && first.createdAtMillis() == second.createdAtMillis()
                && first.expiresAtMillis() == second.expiresAtMillis()
                && first.rules().equals(second.rules())
                && first.takerGrossMinor() == second.takerGrossMinor()
                && first.revision() <= second.revision()
                && first.filledQuantity() <= second.filledQuantity()
                && first.remainingQuantity() >= second.remainingQuantity()
                && first.makerGrossMinor() <= second.makerGrossMinor()
                && first.accruedFeeMinor() <= second.accruedFeeMinor();
    }

    private static UUID terminalTransactionForOrder(
            List<BazaarEscrowSettlement> settlements, UUID orderId) {
        Set<UUID> transactions = new HashSet<>();
        for (BazaarEscrowSettlement settlement : settlements) {
            if (settlement.fillId().isEmpty()
                    && settlement.orderId().filter(orderId::equals).isPresent()) {
                transactions.add(settlement.transactionId());
            }
        }
        if (transactions.size() != 1) {
            throw invalid("Bazaar terminal settlement transaction is ambiguous");
        }
        return transactions.iterator().next();
    }

    private static Map<BazaarEscrowSettlement, Integer> frequencies(
            List<BazaarEscrowSettlement> settlements) {
        Map<BazaarEscrowSettlement, Integer> frequencies = new HashMap<>();
        for (BazaarEscrowSettlement settlement : settlements) {
            frequencies.merge(settlement, 1, Math::addExact);
        }
        return frequencies;
    }

    private static void addFillSettlements(BazaarFill fill,
                                           BazaarOrder buyer,
                                           BazaarOrder seller,
                                           List<BazaarEscrowSettlement> settlements) {
        settlements.add(new BazaarEscrowSettlement(
                fill.settlementTransactionId(), buyer.ownerId(),
                Optional.of(buyer.orderId()),
                BazaarSettlementKind.BUYER_ITEM_CLAIM, 0L, fill.quantity(),
                Optional.of(seller.ownerId()), Optional.of(fill.fillId())));
        long sellerNet = Math.subtractExact(fill.grossMinor(),
                fill.sellerFeeMinor());
        if (sellerNet > 0L) {
            settlements.add(new BazaarEscrowSettlement(
                    fill.settlementTransactionId(), seller.ownerId(),
                    Optional.of(seller.orderId()),
                    BazaarSettlementKind.SELLER_MONEY_CLAIM, sellerNet, 0,
                    Optional.of(buyer.ownerId()), Optional.of(fill.fillId())));
        }
        if (fill.buyerPriceImprovementMinor() > 0L) {
            settlements.add(new BazaarEscrowSettlement(
                    fill.settlementTransactionId(), buyer.ownerId(),
                    Optional.of(buyer.orderId()),
                    BazaarSettlementKind.BUYER_CHANGE_CLAIM,
                    fill.buyerPriceImprovementMinor(), 0,
                    Optional.of(seller.ownerId()), Optional.of(fill.fillId())));
        }
        long fees = Math.addExact(fill.buyerFeeMinor(),
                fill.sellerFeeMinor());
        if (fees > 0L) {
            settlements.add(new BazaarEscrowSettlement(
                    fill.settlementTransactionId(), FEE_ACCOUNT,
                    Optional.empty(), BazaarSettlementKind.FEE_DESTINATION,
                    fees, 0, Optional.empty(), Optional.of(fill.fillId())));
        }
    }

    private static void addTerminalSettlements(UUID transaction,
                                               BazaarOrder order,
                                               List<BazaarEscrowSettlement> settlements) {
        if (order.side() == BazaarOrderSide.BUY) {
            long refund = order.requiredRemainingMoneyReserve();
            if (refund > 0L) {
                settlements.add(new BazaarEscrowSettlement(transaction,
                        order.ownerId(), Optional.of(order.orderId()),
                        BazaarSettlementKind.BUYER_REFUND_CLAIM, refund, 0,
                        Optional.empty(), Optional.empty()));
            }
        } else if (order.remainingQuantity() > 0) {
            settlements.add(new BazaarEscrowSettlement(transaction,
                    order.ownerId(), Optional.of(order.orderId()),
                    BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM, 0L,
                    order.remainingQuantity(), Optional.empty(),
                    Optional.empty()));
        }
    }

    private static boolean crosses(BazaarOrder taker, BazaarOrder maker) {
        return taker.side() == BazaarOrderSide.BUY
                ? taker.limitPriceMinor() >= maker.limitPriceMinor()
                : taker.limitPriceMinor() <= maker.limitPriceMinor();
    }

    private static long executionPrice(BazaarProduct product,
                                       BazaarOrder maker,
                                       BazaarOrder taker) {
        return switch (taker.rules().executionPricePolicy()) {
            case MAKER -> maker.limitPriceMinor();
            case TAKER -> taker.limitPriceMinor();
            case MIDPOINT -> midpoint(product, maker.limitPriceMinor(),
                    taker.limitPriceMinor());
        };
    }

    private static long midpoint(BazaarProduct product, long first,
                                 long second) {
        BigInteger sum = BigInteger.valueOf(first).add(
                BigInteger.valueOf(second));
        long midpoint = sum.divide(BigInteger.TWO).longValueExact();
        long tick = product.priceTickMinor();
        long rounded = Math.multiplyExact(midpoint / tick, tick);
        long low = Math.min(first, second);
        long high = Math.max(first, second);
        return Math.max(low, Math.min(high, rounded));
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record ProductCatalog(
            Map<BazaarProductVersionKey, BazaarProduct> versions,
            Map<String, BazaarProduct> current) {
    }

    private record ReceiptEvidence(Set<UUID> createdOrderIds,
                                   Set<UUID> fillIds,
                                   Set<UUID> terminalTransactions) {
    }

    private static final class RunningOrder {
        private int quantity;
        private long makerGross;
        private long takerGross;
        private long fee;
    }
}
