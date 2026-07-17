package com.enviouse.futureshops.server.market.bazaar;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BazaarOrderBook {
    public static final int MAXIMUM_FILLS_PER_OPERATION = 4096;

    private static final UUID ZERO = new UUID(0L, 0L);
    private static final UUID FEE_ACCOUNT = BazaarIds.systemAccount("fees");

    private final Map<String, BazaarProduct> products = new LinkedHashMap<>();
    private final Map<BazaarProductVersionKey, BazaarProduct> productVersions =
            new LinkedHashMap<>();
    private final Map<Long, BazaarRuleSnapshot> ruleSnapshots = new LinkedHashMap<>();
    private final Map<UUID, BazaarOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, BazaarFill> fills = new LinkedHashMap<>();
    private final Map<UUID, BazaarRequestReceipt> receipts = new LinkedHashMap<>();
    private final Map<UUID, String> lifecycleReceipts = new LinkedHashMap<>();
    private final Map<String, Long> referencePrices = new LinkedHashMap<>();
    private final Set<UUID> activationTransactions = new LinkedHashSet<>();
    private final Set<UUID> moneyHoldAccounts = new LinkedHashSet<>();
    private final Set<UUID> custodyLots = new LinkedHashSet<>();
    private final Set<UUID> terminalTransactions = new LinkedHashSet<>();
    private final Set<UUID> settlementTransactions = new LinkedHashSet<>();
    private final BazaarRetentionPolicy retentionPolicy;
    private long effectiveRuleRevision = -1L;
    private long nextSequence = 1L;

    public BazaarOrderBook() {
        this(BazaarRetentionPolicy.DEFAULT);
    }

    public BazaarOrderBook(BazaarRetentionPolicy retentionPolicy) {
        this.retentionPolicy = Objects.requireNonNull(retentionPolicy,
                "retentionPolicy");
    }

    public synchronized void setEffectiveRules(BazaarRuleSnapshot rules) {
        Objects.requireNonNull(rules, "rules");
        BazaarRuleSnapshot sameRevision = ruleSnapshots.get(
                rules.configRevision());
        if (sameRevision != null && !sameRevision.equals(rules)) {
            if (!orders.isEmpty() || !receipts.isEmpty()
                    || !lifecycleReceipts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Bazaar rule revision cannot change meaning");
            }
            ruleSnapshots.clear();
        }
        if (effectiveRuleRevision > rules.configRevision()) {
            throw new IllegalArgumentException(
                    "Bazaar rule revision cannot move backward");
        }
        ruleSnapshots.putIfAbsent(rules.configRevision(), rules);
        effectiveRuleRevision = rules.configRevision();
    }

    public synchronized Optional<BazaarRuleSnapshot> effectiveRules() {
        return Optional.ofNullable(ruleSnapshots.get(effectiveRuleRevision));
    }

    public synchronized void registerProduct(BazaarProduct product) {
        Objects.requireNonNull(product, "product");
        BazaarProduct existing = products.get(product.productId());
        if (existing != null && product.version() < existing.version()) {
            throw new IllegalArgumentException("Bazaar product version cannot move backward");
        }
        if (existing != null && product.version() == existing.version()
                && !sameProductDefinition(product, existing)) {
            throw new IllegalArgumentException("Bazaar product version cannot change identity");
        }
        BazaarProductVersionKey key = BazaarProductVersionKey.of(product);
        BazaarProduct historical = productVersions.get(key);
        if (historical != null && !sameProductDefinition(product, historical)) {
            throw new IllegalArgumentException("Bazaar product version cannot change identity");
        }
        Map<UUID, BazaarOrder> transitioned =
                new LinkedHashMap<>(orders);
        if (existing != null && product.version() > existing.version()
                || !product.status().matches()) {
            freezeOrders(transitioned, product.productId());
        } else {
            resumeOrders(transitioned, product.productId(), product.version());
        }
        orders.clear();
        orders.putAll(transitioned);
        products.put(product.productId(), product);
        productVersions.put(key, product);
        if (existing != null && product.version() > existing.version()) {
            referencePrices.remove(product.productId());
        }
    }

    public synchronized void setReferencePrice(String productId, long priceMinor) {
        BazaarProduct product = requireProduct(productId);
        product.requirePrice(priceMinor);
        referencePrices.put(productId, priceMinor);
    }

    public synchronized void setProductStatus(String productId, BazaarProductStatus status) {
        BazaarProduct product = requireProduct(productId);
        Objects.requireNonNull(status, "status");
        Map<UUID, BazaarOrder> transitioned =
                new LinkedHashMap<>(orders);
        if (status == BazaarProductStatus.HALTED || status == BazaarProductStatus.RETIRED) {
            freezeOrders(transitioned, productId);
        } else {
            resumeOrders(transitioned, productId, product.version());
        }
        orders.clear();
        orders.putAll(transitioned);
        BazaarProduct transitionedProduct = copyProductStatus(product, status);
        products.put(productId, transitionedProduct);
        productVersions.put(BazaarProductVersionKey.of(transitionedProduct),
                transitionedProduct);
    }

    public synchronized BazaarOperationResult create(CreateBazaarOrderCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = BazaarRequestFingerprints.create(command);
        Optional<BazaarOperationResult> prior = replayOrConflict(
                command.requestId(), command.orderId(), BazaarOperationType.CREATE, fingerprint);
        if (prior.isPresent()) {
            return prior.orElseThrow();
        }
        if (persistedReceiptCount() >= retentionPolicy.maximumReceipts()) {
            return rejected(command.requestId(), command.orderId(),
                    BazaarOperationType.CREATE,
                    BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                    Optional.empty());
        }
        BazaarIdentityRegistry identities = currentIdentities();
        try {
            identities.claimRequest(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE);
        } catch (IllegalArgumentException exception) {
            return rejected(command.requestId(), command.orderId(),
                    BazaarOperationType.CREATE,
                    BazaarOperationStatus.REQUEST_CONFLICT,
                    Optional.empty());
        }
        BazaarRuleSnapshot trustedRules = ruleSnapshots.get(
                effectiveRuleRevision);
        if (trustedRules == null || !trustedRules.equals(command.rules())) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE,
                    BazaarOperationStatus.RULE_REVISION_CHANGED,
                    Optional.empty()));
        }
        try {
            identities.claimCreate(command);
        } catch (IllegalArgumentException exception) {
            return record(command, fingerprint,
                    rejected(command.requestId(), command.orderId(),
                            BazaarOperationType.CREATE,
                            BazaarOperationStatus.INVALID_ORDER,
                            Optional.empty()));
        }

        BazaarOperationStatus validation = validateCreate(command);
        if (validation != BazaarOperationStatus.APPLIED) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE, validation, Optional.empty()));
        }

        BazaarProduct product = products.get(command.productId());
        long acceptedSequence = nextSequence;
        long initialMoneyReserve;
        try {
            long notional = Math.multiplyExact(command.limitPriceMinor(), command.quantity());
            int maximumRate = Math.max(trustedRules.makerFeeBasisPoints(),
                    trustedRules.takerFeeBasisPoints());
            initialMoneyReserve = command.side() == BazaarOrderSide.BUY
                    ? Math.addExact(notional, BazaarFeeMath.cumulativeFee(notional, maximumRate)) : 0L;
        } catch (ArithmeticException exception) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE,
                    BazaarOperationStatus.ARITHMETIC_OVERFLOW, Optional.empty()));
        }

        BazaarOrder incoming;
        try {
            incoming = new BazaarOrder(command.orderId(), command.ownerId(),
                    command.activationTransactionId(), command.moneyHoldAccountId(),
                    command.custodyLotId(), command.productId(), command.productVersion(),
                    command.side(), command.type(), command.timeInForce(),
                    BazaarOrderState.OPEN, 0L, command.limitPriceMinor(), command.quantity(),
                    command.quantity(), 0, 0L, 0L, 0L, initialMoneyReserve,
                    command.side() == BazaarOrderSide.SELL ? command.quantity() : 0,
                    acceptedSequence, command.createdAtMillis(), command.expiresAtMillis(),
                    trustedRules);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE,
                    BazaarOperationStatus.INVALID_ORDER, Optional.empty()));
        }

        WorkingState working = new WorkingState(new LinkedHashMap<>(orders),
                new LinkedHashMap<>(products), new LinkedHashMap<>(referencePrices),
                new LinkedHashSet<>(terminalTransactions),
                Math.addExact(nextSequence, 1L));
        working.orders().put(incoming.orderId(), incoming);
        MatchOutcome outcome;
        try {
            outcome = match(command.requestId(), incoming.orderId(), product, working,
                    command.createdAtMillis());
        } catch (ArithmeticException | IllegalStateException exception) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE,
                    BazaarOperationStatus.ARITHMETIC_OVERFLOW, Optional.empty()));
        }

        BazaarOrder matchedIncoming = working.orders().get(incoming.orderId());
        List<BazaarEscrowSettlement> committedSettlements =
                new ArrayList<>(outcome.settlements());
        if (outcome.matchBudgetExhausted()
                && command.timeInForce().rests()) {
            return record(command, fingerprint,
                    rejected(command.requestId(), command.orderId(),
                            BazaarOperationType.CREATE,
                            BazaarOperationStatus.MATCH_BUDGET_EXCEEDED,
                            Optional.empty()));
        }
        if (command.timeInForce() == BazaarTimeInForce.FILL_OR_KILL
                && matchedIncoming.remainingQuantity() > 0) {
            if (outcome.productHalted()) {
                try {
                    commitCircuitHalt(command.productId());
                } catch (ArithmeticException | IllegalStateException exception) {
                    return record(command, fingerprint,
                            rejected(command.requestId(), command.orderId(),
                                    BazaarOperationType.CREATE,
                                    BazaarOperationStatus.ARITHMETIC_OVERFLOW,
                                    Optional.empty()));
                }
                return record(command, fingerprint,
                        rejected(command.requestId(), command.orderId(),
                                BazaarOperationType.CREATE,
                                BazaarOperationStatus.PRICE_OUTSIDE_BAND,
                                Optional.empty()));
            }
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE,
                    BazaarOperationStatus.FILL_OR_KILL_UNAVAILABLE, Optional.empty()));
        }

        if (matchedIncoming.remainingQuantity() > 0
                && !command.timeInForce().rests()) {
            BazaarOrder.TerminalApplication terminal = matchedIncoming.cancel(BazaarOrderState.CANCELLED);
            matchedIncoming = terminal.order();
            working.orders().put(matchedIncoming.orderId(), matchedIncoming);
            UUID terminalTransaction = BazaarIds.immediateTerminal(
                    command.requestId(), command.orderId(), "remainder");
            if (!working.terminalTransactions().add(
                    terminalTransaction)) {
                throw new IllegalStateException(
                        "Bazaar terminal transaction was reused");
            }
            addTerminalSettlements(terminalTransaction, matchedIncoming,
                    terminal, committedSettlements);
        }
        if (working.terminalTransactions().size()
                > retentionPolicy.maximumTerminalTransactions()) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CREATE,
                    BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                    Optional.empty()));
        }

        BazaarIdentityRegistry committedIdentities = currentIdentities();
        committedIdentities.claimCreate(command);
        for (BazaarFill fill : outcome.fills()) {
            committedIdentities.claimFill(fill);
        }
        for (UUID terminalTransaction : working.terminalTransactions()) {
            committedIdentities.claimTerminal(terminalTransaction);
        }

        orders.clear();
        orders.putAll(working.orders());
        products.clear();
        products.putAll(working.products());
        for (BazaarProduct current : products.values()) {
            productVersions.put(BazaarProductVersionKey.of(current), current);
        }
        referencePrices.clear();
        referencePrices.putAll(working.referencePrices());
        terminalTransactions.clear();
        terminalTransactions.addAll(working.terminalTransactions());
        nextSequence = working.nextSequence();
        for (BazaarFill fill : outcome.fills()) {
            if (fills.putIfAbsent(fill.fillId(), fill) != null) {
                throw new IllegalStateException("Bazaar fill identity was reused");
            }
            if (!settlementTransactions.add(fill.settlementTransactionId())) {
                throw new IllegalStateException("Bazaar settlement identity was reused");
            }
        }
        activationTransactions.add(command.activationTransactionId());
        command.moneyHoldAccountId().ifPresent(moneyHoldAccounts::add);
        command.custodyLotId().ifPresent(custodyLots::add);
        BazaarOrder committed = orders.get(command.orderId());
        BazaarOperationResult result = new BazaarOperationResult(command.requestId(),
                command.orderId(), BazaarOperationType.CREATE, BazaarOperationStatus.APPLIED,
                false, committed.revision(), Optional.of(committed), outcome.fills(),
                committedSettlements, outcome.cancelledMakerOrderIds(), outcome.productHalted());
        return record(command, fingerprint, result);
    }

    public synchronized BazaarOperationResult cancel(CancelBazaarOrderCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = BazaarRequestFingerprints.cancel(command);
        Optional<BazaarOperationResult> prior = replayOrConflict(
                command.requestId(), command.orderId(), BazaarOperationType.CANCEL, fingerprint);
        if (prior.isPresent()) {
            return prior.orElseThrow();
        }
        if (persistedReceiptCount() >= retentionPolicy.maximumReceipts()) {
            return rejected(command.requestId(), command.orderId(),
                    BazaarOperationType.CANCEL,
                    BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                    Optional.ofNullable(orders.get(command.orderId())));
        }
        BazaarIdentityRegistry identities = currentIdentities();
        try {
            identities.claimRequest(command.requestId(),
                    command.orderId(), BazaarOperationType.CANCEL);
        } catch (IllegalArgumentException exception) {
            return rejected(command.requestId(), command.orderId(),
                    BazaarOperationType.CANCEL,
                    BazaarOperationStatus.REQUEST_CONFLICT,
                    Optional.ofNullable(orders.get(command.orderId())));
        }
        try {
            if (!command.terminalTransactionId().equals(BazaarIds.terminal(
                    command.requestId(), command.orderId(),
                    BazaarOperationType.CANCEL))) {
                throw new IllegalArgumentException(
                        "Bazaar terminal transaction is not canonical");
            }
            identities.claimTerminal(command.terminalTransactionId());
        } catch (IllegalArgumentException exception) {
            return record(command, fingerprint,
                    rejected(command.requestId(), command.orderId(),
                            BazaarOperationType.CANCEL,
                            BazaarOperationStatus.INVALID_ORDER,
                            Optional.ofNullable(orders.get(
                                    command.orderId()))));
        }
        BazaarOrder order = orders.get(command.orderId());
        if (order == null) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CANCEL,
                    BazaarOperationStatus.ORDER_MISSING, Optional.empty()));
        }
        BazaarOperationStatus status = validateTerminal(order, command.actorId(),
                command.terminalTransactionId(), command.expectedRevision(), command.nowMillis(), true);
        if (status != BazaarOperationStatus.APPLIED) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CANCEL, status, Optional.of(order)));
        }
        if (terminalTransactions.size()
                >= retentionPolicy.maximumTerminalTransactions()) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.CANCEL,
                    BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                    Optional.of(order)));
        }
        BazaarOrder.TerminalApplication terminal = order.cancel(BazaarOrderState.CANCELLED);
        orders.put(order.orderId(), terminal.order());
        terminalTransactions.add(command.terminalTransactionId());
        List<BazaarEscrowSettlement> settlements = new ArrayList<>();
        addTerminalSettlements(command.terminalTransactionId(), terminal.order(), terminal, settlements);
        BazaarOperationResult result = new BazaarOperationResult(command.requestId(),
                command.orderId(), BazaarOperationType.CANCEL, BazaarOperationStatus.APPLIED,
                false, terminal.order().revision(), Optional.of(terminal.order()), List.of(),
                settlements, List.of(), false);
        return record(command, fingerprint, result);
    }

    public synchronized BazaarOperationResult expire(ExpireBazaarOrderCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = BazaarRequestFingerprints.expire(command);
        Optional<BazaarOperationResult> prior = replayOrConflict(
                command.requestId(), command.orderId(), BazaarOperationType.EXPIRE, fingerprint);
        if (prior.isPresent()) {
            return prior.orElseThrow();
        }
        if (persistedReceiptCount() >= retentionPolicy.maximumReceipts()) {
            return rejected(command.requestId(), command.orderId(),
                    BazaarOperationType.EXPIRE,
                    BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                    Optional.ofNullable(orders.get(command.orderId())));
        }
        BazaarIdentityRegistry identities = currentIdentities();
        try {
            identities.claimRequest(command.requestId(),
                    command.orderId(), BazaarOperationType.EXPIRE);
        } catch (IllegalArgumentException exception) {
            return rejected(command.requestId(), command.orderId(),
                    BazaarOperationType.EXPIRE,
                    BazaarOperationStatus.REQUEST_CONFLICT,
                    Optional.ofNullable(orders.get(command.orderId())));
        }
        try {
            if (!command.terminalTransactionId().equals(BazaarIds.terminal(
                    command.requestId(), command.orderId(),
                    BazaarOperationType.EXPIRE))) {
                throw new IllegalArgumentException(
                        "Bazaar terminal transaction is not canonical");
            }
            identities.claimTerminal(command.terminalTransactionId());
        } catch (IllegalArgumentException exception) {
            return record(command, fingerprint,
                    rejected(command.requestId(), command.orderId(),
                            BazaarOperationType.EXPIRE,
                            BazaarOperationStatus.INVALID_ORDER,
                            Optional.ofNullable(orders.get(
                                    command.orderId()))));
        }
        BazaarOrder order = orders.get(command.orderId());
        if (order == null) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.EXPIRE,
                    BazaarOperationStatus.ORDER_MISSING, Optional.empty()));
        }
        BazaarOperationStatus status = validateTerminal(order, order.ownerId(),
                command.terminalTransactionId(), command.expectedRevision(), command.nowMillis(), false);
        if (status == BazaarOperationStatus.APPLIED && !order.expiredAt(command.nowMillis())) {
            status = BazaarOperationStatus.INVALID_ORDER;
        }
        if (status != BazaarOperationStatus.APPLIED) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.EXPIRE, status, Optional.of(order)));
        }
        if (terminalTransactions.size()
                >= retentionPolicy.maximumTerminalTransactions()) {
            return record(command, fingerprint, rejected(command.requestId(),
                    command.orderId(), BazaarOperationType.EXPIRE,
                    BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                    Optional.of(order)));
        }
        BazaarOrder.TerminalApplication terminal = order.cancel(BazaarOrderState.EXPIRED);
        orders.put(order.orderId(), terminal.order());
        terminalTransactions.add(command.terminalTransactionId());
        List<BazaarEscrowSettlement> settlements = new ArrayList<>();
        addTerminalSettlements(command.terminalTransactionId(), terminal.order(), terminal, settlements);
        BazaarOperationResult result = new BazaarOperationResult(command.requestId(),
                command.orderId(), BazaarOperationType.EXPIRE, BazaarOperationStatus.APPLIED,
                false, terminal.order().revision(), Optional.of(terminal.order()), List.of(),
                settlements, List.of(), false);
        return record(command, fingerprint, result);
    }

    public synchronized Optional<BazaarProduct> product(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    public synchronized Optional<BazaarProduct> productVersion(
            String productId, long version) {
        return Optional.ofNullable(productVersions.get(
                new BazaarProductVersionKey(productId, version)));
    }

    public synchronized Optional<BazaarOrder> order(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public synchronized Optional<BazaarFill> fill(UUID fillId) {
        return Optional.ofNullable(fills.get(fillId));
    }

    public synchronized Optional<BazaarRequestReceipt> receipt(UUID requestId) {
        return Optional.ofNullable(receipts.get(requestId));
    }

    public synchronized List<BazaarOrder> openOrders(String productId, BazaarOrderSide side) {
        return sortedCandidates(orders.values(), productId, -1L, side, null, Long.MIN_VALUE);
    }

    public synchronized List<BazaarFill> fills() {
        return List.copyOf(fills.values());
    }

    public synchronized long nextSequence() {
        return nextSequence;
    }

    public synchronized BazaarOrderBookSnapshot snapshot() {
        BazaarOrderBookSnapshot snapshot = new BazaarOrderBookSnapshot(
                BazaarOrderBookSnapshot.CURRENT_SCHEMA_VERSION, nextSequence,
                List.copyOf(productVersions.values()), List.copyOf(orders.values()),
                List.copyOf(fills.values()), Map.copyOf(receipts),
                Map.copyOf(referencePrices), Set.copyOf(terminalTransactions),
                List.copyOf(ruleSnapshots.values()), effectiveRuleRevision,
                retentionPolicy, Map.copyOf(lifecycleReceipts));
        BazaarSnapshotValidator.validate(snapshot);
        return snapshot;
    }

    public static BazaarOrderBook restore(BazaarOrderBookSnapshot snapshot) {
        BazaarSnapshotValidator.validate(snapshot);
        BazaarOrderBook book = new BazaarOrderBook(snapshot.retentionPolicy());
        for (BazaarProduct product : snapshot.products()) {
            book.productVersions.put(BazaarProductVersionKey.of(product), product);
            BazaarProduct current = book.products.get(product.productId());
            if (current == null || product.version() > current.version()) {
                book.products.put(product.productId(), product);
            }
        }
        for (BazaarRuleSnapshot rules : snapshot.ruleSnapshots()) {
            book.ruleSnapshots.put(rules.configRevision(), rules);
        }
        for (BazaarOrder order : snapshot.orders()) {
            book.orders.put(order.orderId(), order);
            book.activationTransactions.add(order.activationTransactionId());
            order.moneyHoldAccountId().ifPresent(book.moneyHoldAccounts::add);
            order.custodyLotId().ifPresent(book.custodyLots::add);
        }
        for (BazaarFill fill : snapshot.fills()) {
            book.fills.put(fill.fillId(), fill);
            book.settlementTransactions.add(fill.settlementTransactionId());
        }
        book.receipts.putAll(snapshot.receipts());
        book.lifecycleReceipts.putAll(snapshot.lifecycleReceipts());
        book.referencePrices.putAll(snapshot.referencePrices());
        book.terminalTransactions.addAll(snapshot.terminalTransactions());
        book.effectiveRuleRevision = snapshot.effectiveRuleRevision();
        book.nextSequence = snapshot.nextSequence();
        return book;
    }

    private MatchOutcome match(UUID requestId, UUID incomingId, BazaarProduct product,
                               WorkingState working, long nowMillis) {
        List<BazaarFill> newFills = new ArrayList<>();
        List<BazaarEscrowSettlement> settlements = new ArrayList<>();
        List<UUID> cancelledMakers = new ArrayList<>();
        int ordinal = 0;
        boolean halted = false;
        while (ordinal < MAXIMUM_FILLS_PER_OPERATION) {
            BazaarOrder incoming = working.orders().get(incomingId);
            if (!incoming.matchable()) {
                break;
            }
            List<BazaarOrder> candidates = sortedCandidates(working.orders().values(),
                    incoming.productId(), incoming.productVersion(), opposite(incoming.side()),
                    incoming.orderId(), nowMillis);
            BazaarOrder maker = firstCrossing(candidates, incoming);
            if (maker == null) {
                break;
            }
            if (maker.ownerId().equals(incoming.ownerId())) {
                BazaarSelfTradePolicy policy = incoming.rules().selfTradePolicy();
                if (policy == BazaarSelfTradePolicy.SKIP_SELF) {
                    BazaarOrder next = firstCrossing(candidates.stream()
                            .filter(candidate -> !candidate.ownerId().equals(incoming.ownerId())).toList(), incoming);
                    if (next == null) {
                        break;
                    }
                    maker = next;
                } else if (policy == BazaarSelfTradePolicy.CANCEL_TAKER) {
                    BazaarOrder.TerminalApplication terminal = incoming.cancel(BazaarOrderState.CANCELLED);
                    working.orders().put(incoming.orderId(), terminal.order());
                    UUID terminalTransaction = BazaarIds.immediateTerminal(
                            requestId, incoming.orderId(), "self_trade");
                    if (!working.terminalTransactions().add(
                            terminalTransaction)) {
                        throw new IllegalStateException(
                                "Bazaar terminal transaction was reused");
                    }
                    addTerminalSettlements(terminalTransaction,
                            terminal.order(), terminal, settlements);
                    break;
                } else {
                    BazaarOrder.TerminalApplication terminal = maker.cancel(BazaarOrderState.CANCELLED);
                    working.orders().put(maker.orderId(), terminal.order());
                    UUID terminalTransaction = BazaarIds.derive("self_trade_cancel",
                            requestId, maker.orderId());
                    if (!working.terminalTransactions().add(terminalTransaction)) {
                        throw new IllegalStateException("Bazaar terminal transaction was reused");
                    }
                    addTerminalSettlements(terminalTransaction, terminal.order(), terminal, settlements);
                    cancelledMakers.add(maker.orderId());
                    continue;
                }
            }
            long executionPrice = executionPrice(product, maker, incoming);
            if (outsidePriceBand(working.referencePrices().get(product.productId()), executionPrice,
                    incoming.rules())) {
                BazaarProduct current = working.products().get(product.productId());
                working.products().put(product.productId(),
                        copyProductStatus(current, BazaarProductStatus.HALTED));
                freezeOrders(working.orders(), product.productId());
                halted = true;
                break;
            }
            int quantity = Math.min(maker.remainingQuantity(), incoming.remainingQuantity());
            BazaarOrder.FillApplication makerApplication = maker.applyFill(executionPrice, quantity, true);
            BazaarOrder.FillApplication takerApplication = incoming.applyFill(executionPrice, quantity, false);
            BazaarOrder buyOrder = incoming.side() == BazaarOrderSide.BUY
                    ? takerApplication.order() : makerApplication.order();
            BazaarOrder sellOrder = incoming.side() == BazaarOrderSide.SELL
                    ? takerApplication.order() : makerApplication.order();
            BazaarOrder.FillApplication buyApplication = incoming.side() == BazaarOrderSide.BUY
                    ? takerApplication : makerApplication;
            BazaarOrder.FillApplication sellApplication = incoming.side() == BazaarOrderSide.SELL
                    ? takerApplication : makerApplication;
            UUID fillId = BazaarIds.fill(requestId, maker.orderId(), incoming.orderId(), ordinal);
            UUID settlementId = BazaarIds.settlement(fillId);
            long fillSequence = working.nextSequence();
            working.nextSequence(Math.addExact(fillSequence, 1L));
            BazaarFill fill = new BazaarFill(fillId, buyOrder.orderId(), sellOrder.orderId(),
                    maker.orderId(), incoming.orderId(), incoming.productId(), incoming.productVersion(),
                    quantity, executionPrice, buyApplication.grossMinor(), buyApplication.feeMinor(),
                    sellApplication.feeMinor(), buyApplication.moneyReserveReleaseMinor(),
                    fillSequence, nowMillis, settlementId);
            newFills.add(fill);
            working.orders().put(maker.orderId(), makerApplication.order());
            working.orders().put(incoming.orderId(), takerApplication.order());
            working.referencePrices().put(product.productId(), executionPrice);
            addFillSettlements(fill, buyOrder, sellOrder, settlements);
            ordinal++;
        }
        boolean budgetExhausted = false;
        if (!halted && ordinal == MAXIMUM_FILLS_PER_OPERATION) {
            BazaarOrder incoming = working.orders().get(incomingId);
            if (incoming.matchable()) {
                List<BazaarOrder> candidates = sortedCandidates(
                        working.orders().values(), incoming.productId(),
                        incoming.productVersion(),
                        opposite(incoming.side()), incoming.orderId(),
                        nowMillis);
                budgetExhausted = firstCrossing(
                        candidates, incoming) != null;
            }
        }
        return new MatchOutcome(List.copyOf(newFills),
                List.copyOf(settlements),
                List.copyOf(cancelledMakers), halted,
                budgetExhausted);
    }

    private BazaarOperationStatus validateCreate(CreateBazaarOrderCommand command) {
        if (invalidId(command.requestId()) || invalidId(command.orderId())
                || invalidId(command.ownerId()) || invalidId(command.activationTransactionId())
                || orders.containsKey(command.orderId())
                || activationTransactions.contains(command.activationTransactionId())
                || terminalTransactions.contains(command.activationTransactionId())
                || settlementTransactions.contains(command.activationTransactionId())
                || command.moneyHoldAccountId().map(id -> moneyHoldAccounts.contains(id)
                || custodyLots.contains(id)).orElse(false)
                || command.custodyLotId().map(id -> custodyLots.contains(id)
                || moneyHoldAccounts.contains(id)).orElse(false)) {
            return BazaarOperationStatus.INVALID_ORDER;
        }
        BazaarProduct product = products.get(command.productId());
        if (product == null) {
            return BazaarOperationStatus.PRODUCT_MISSING;
        }
        if (product.version() != command.productVersion()) {
            return BazaarOperationStatus.PRODUCT_VERSION_CHANGED;
        }
        if (!product.status().matches()) {
            return BazaarOperationStatus.PRODUCT_UNAVAILABLE;
        }
        if ((command.side() == BazaarOrderSide.BUY) != command.moneyHoldAccountId().isPresent()
                || (command.side() == BazaarOrderSide.SELL) != command.custodyLotId().isPresent()
                || command.type() == BazaarOrderType.INSTANT && command.timeInForce().rests()
                || command.timeInForce() == BazaarTimeInForce.GOOD_UNTIL_TIME
                && command.expiresAtMillis() <= command.createdAtMillis()
                || command.timeInForce() != BazaarTimeInForce.GOOD_UNTIL_TIME
                && command.expiresAtMillis() != 0L
                || command.createdAtMillis() < 0L) {
            return BazaarOperationStatus.INVALID_ORDER;
        }
        try {
            product.requirePrice(command.limitPriceMinor());
            product.requireQuantity(command.quantity());
            if (command.quantity() > command.rules().maximumOrderQuantity()) {
                return BazaarOperationStatus.LIMIT_EXCEEDED;
            }
            long notional = Math.multiplyExact(command.limitPriceMinor(), command.quantity());
            if (notional > command.rules().maximumNotionalMinor()) {
                return BazaarOperationStatus.LIMIT_EXCEEDED;
            }
            long ownerOpen = orders.values().stream()
                    .filter(order -> order.ownerId().equals(command.ownerId()))
                    .filter(order -> !order.state().terminal())
                    .count();
            long ownerProductOpen = orders.values().stream()
                    .filter(order -> order.ownerId().equals(command.ownerId()))
                    .filter(order -> order.productId().equals(command.productId()))
                    .filter(order -> !order.state().terminal())
                    .count();
            if (ownerOpen >= command.rules().maximumOpenOrdersPerPlayer()
                    || ownerProductOpen >= command.rules().maximumOpenOrdersPerProductPerPlayer()) {
                return BazaarOperationStatus.LIMIT_EXCEEDED;
            }
            if (command.side() == BazaarOrderSide.BUY) {
                int maximumRate = Math.max(command.rules().makerFeeBasisPoints(),
                        command.rules().takerFeeBasisPoints());
                long requestedReserve = Math.addExact(notional,
                        BazaarFeeMath.cumulativeFee(notional, maximumRate));
                long currentReserve = 0L;
                for (BazaarOrder order : orders.values()) {
                    if (order.ownerId().equals(command.ownerId())) {
                        currentReserve = Math.addExact(currentReserve, order.reservedMoneyMinor());
                    }
                }
                if (Math.addExact(currentReserve, requestedReserve)
                        > command.rules().maximumEscrowedValuePerPlayerMinor()) {
                    return BazaarOperationStatus.LIMIT_EXCEEDED;
                }
            }
        } catch (IllegalArgumentException exception) {
            return BazaarOperationStatus.INVALID_ORDER;
        } catch (ArithmeticException exception) {
            return BazaarOperationStatus.ARITHMETIC_OVERFLOW;
        }
        return BazaarOperationStatus.APPLIED;
    }

    private BazaarOperationStatus validateTerminal(BazaarOrder order, UUID actorId,
                                                    UUID terminalTransactionId,
                                                    long expectedRevision, long nowMillis,
                                                    boolean enforceOwner) {
        if (invalidId(actorId) || invalidId(terminalTransactionId)
                || terminalTransactions.contains(terminalTransactionId)
                || activationTransactions.contains(terminalTransactionId)
                || settlementTransactions.contains(terminalTransactionId)) {
            return BazaarOperationStatus.INVALID_ORDER;
        }
        if (enforceOwner && !order.ownerId().equals(actorId)) {
            return BazaarOperationStatus.OWNER_MISMATCH;
        }
        if (order.revision() != expectedRevision) {
            return BazaarOperationStatus.REVISION_CHANGED;
        }
        if (!order.matchable() && order.state() != BazaarOrderState.FROZEN) {
            return BazaarOperationStatus.ORDER_NOT_OPEN;
        }
        if (nowMillis < 0L) {
            return BazaarOperationStatus.INVALID_ORDER;
        }
        if (enforceOwner && Math.subtractExact(nowMillis, order.createdAtMillis())
                < order.rules().minimumLifetimeMillis()) {
            return BazaarOperationStatus.INVALID_ORDER;
        }
        return BazaarOperationStatus.APPLIED;
    }

    private Optional<BazaarOperationResult> replayOrConflict(UUID requestId, UUID orderId,
                                                              BazaarOperationType operation,
                                                              String fingerprint) {
        BazaarRequestReceipt receipt = receipts.get(requestId);
        if (receipt == null) {
            return Optional.empty();
        }
        if (receipt.fingerprint().equals(fingerprint)) {
            return Optional.of(receipt.result().asReplay());
        }
        BazaarOrder order = orders.get(orderId);
        return Optional.of(rejected(requestId, orderId, operation,
                BazaarOperationStatus.REQUEST_CONFLICT, Optional.ofNullable(order)));
    }

    private BazaarOperationResult record(CreateBazaarOrderCommand command,
                                          String fingerprint,
                                          BazaarOperationResult result) {
        return record(command.requestId(), BazaarRequestReceipt.create(
                fingerprint, command, result), result);
    }

    private BazaarOperationResult record(CancelBazaarOrderCommand command,
                                          String fingerprint,
                                          BazaarOperationResult result) {
        return record(command.requestId(), BazaarRequestReceipt.cancel(
                fingerprint, command, result), result);
    }

    private BazaarOperationResult record(ExpireBazaarOrderCommand command,
                                          String fingerprint,
                                          BazaarOperationResult result) {
        return record(command.requestId(), BazaarRequestReceipt.expire(
                fingerprint, command, result), result);
    }

    private BazaarOperationResult record(UUID requestId,
                                          BazaarRequestReceipt receipt,
                                          BazaarOperationResult result) {
        BazaarRequestReceipt previous = receipts.putIfAbsent(requestId, receipt);
        if (previous != null) {
            throw new IllegalStateException("Bazaar request receipt changed during operation");
        }
        return result;
    }

    synchronized Optional<String> lifecycleReceipt(UUID mutationId) {
        return Optional.ofNullable(lifecycleReceipts.get(
                Objects.requireNonNull(mutationId, "mutationId")));
    }

    synchronized void recordLifecycleReceipt(UUID mutationId,
                                             String fingerprint) {
        Objects.requireNonNull(mutationId, "mutationId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (persistedReceiptCount() >= retentionPolicy.maximumReceipts()) {
            throw new IllegalStateException(
                    "Bazaar lifecycle receipt capacity is exhausted");
        }
        BazaarIdentityRegistry identities = currentIdentities();
        identities.claimLifecycle(mutationId);
        String previous = lifecycleReceipts.putIfAbsent(mutationId,
                fingerprint);
        if (previous != null) {
            throw new IllegalStateException(
                    "Bazaar lifecycle receipt changed during operation");
        }
    }

    private int persistedReceiptCount() {
        return Math.addExact(receipts.size(), lifecycleReceipts.size());
    }

    private static BazaarOperationResult rejected(UUID requestId, UUID orderId,
                                                   BazaarOperationType operation,
                                                   BazaarOperationStatus status,
                                                   Optional<BazaarOrder> order) {
        return new BazaarOperationResult(requestId, orderId, operation, status, false,
                order.map(BazaarOrder::revision).orElse(-1L), order, List.of(), List.of(),
                List.of(), false);
    }

    private BazaarIdentityRegistry currentIdentities() {
        BazaarIdentityRegistry identities = new BazaarIdentityRegistry();
        for (BazaarOrder order : orders.values()) {
            identities.claimOrder(order);
        }
        for (BazaarFill fill : fills.values()) {
            identities.claimFill(fill);
        }
        for (Map.Entry<UUID, BazaarRequestReceipt> receipt
                : receipts.entrySet()) {
            BazaarOperationResult result = receipt.getValue().result();
            identities.claimRequest(receipt.getKey(), result.orderId(),
                    result.operation());
        }
        for (UUID mutationId : lifecycleReceipts.keySet()) {
            identities.claimLifecycle(mutationId);
        }
        for (UUID terminalTransaction : terminalTransactions) {
            identities.claimTerminal(terminalTransaction);
        }
        return identities;
    }

    private static BazaarOrder firstCrossing(List<BazaarOrder> candidates, BazaarOrder incoming) {
        for (BazaarOrder candidate : candidates) {
            if (crosses(incoming, candidate)) {
                return candidate;
            }
            break;
        }
        return null;
    }

    private static boolean crosses(BazaarOrder incoming, BazaarOrder maker) {
        return incoming.side() == BazaarOrderSide.BUY
                ? incoming.limitPriceMinor() >= maker.limitPriceMinor()
                : incoming.limitPriceMinor() <= maker.limitPriceMinor();
    }

    private static BazaarOrderSide opposite(BazaarOrderSide side) {
        return side == BazaarOrderSide.BUY ? BazaarOrderSide.SELL : BazaarOrderSide.BUY;
    }

    private static List<BazaarOrder> sortedCandidates(Collection<BazaarOrder> source,
                                                       String productId, long productVersion,
                                                       BazaarOrderSide side, UUID excludedOrder,
                                                       long nowMillis) {
        Comparator<BazaarOrder> price = side == BazaarOrderSide.BUY
                ? Comparator.comparingLong(BazaarOrder::limitPriceMinor).reversed()
                : Comparator.comparingLong(BazaarOrder::limitPriceMinor);
        return source.stream()
                .filter(BazaarOrder::matchable)
                .filter(order -> order.productId().equals(productId))
                .filter(order -> productVersion < 0L || order.productVersion() == productVersion)
                .filter(order -> order.side() == side)
                .filter(order -> excludedOrder == null || !order.orderId().equals(excludedOrder))
                .filter(order -> nowMillis == Long.MIN_VALUE || !order.expiredAt(nowMillis))
                .sorted(price.thenComparingLong(BazaarOrder::acceptedSequence)
                        .thenComparing(BazaarOrder::orderId))
                .toList();
    }

    private static long executionPrice(BazaarProduct product, BazaarOrder maker,
                                       BazaarOrder taker) {
        return switch (taker.rules().executionPricePolicy()) {
            case MAKER -> maker.limitPriceMinor();
            case TAKER -> taker.limitPriceMinor();
            case MIDPOINT -> midpoint(product, maker.limitPriceMinor(), taker.limitPriceMinor());
        };
    }

    private static long midpoint(BazaarProduct product, long first, long second) {
        BigInteger sum = BigInteger.valueOf(first).add(BigInteger.valueOf(second));
        long midpoint = sum.divide(BigInteger.TWO).longValueExact();
        long tick = product.priceTickMinor();
        long rounded = Math.multiplyExact(midpoint / tick, tick);
        long low = Math.min(first, second);
        long high = Math.max(first, second);
        return Math.max(low, Math.min(high, rounded));
    }

    private static boolean outsidePriceBand(Long referencePrice, long executionPrice,
                                            BazaarRuleSnapshot rules) {
        if (!rules.circuitBreakerEnabled() || referencePrice == null) {
            return false;
        }
        BigInteger difference = BigInteger.valueOf(executionPrice)
                .subtract(BigInteger.valueOf(referencePrice)).abs()
                .multiply(BigInteger.valueOf(10_000L));
        BigInteger allowed = BigInteger.valueOf(referencePrice)
                .multiply(BigInteger.valueOf(rules.priceBandBasisPoints()));
        return difference.compareTo(allowed) > 0;
    }

    private static void addFillSettlements(BazaarFill fill, BazaarOrder buyer,
                                           BazaarOrder seller,
                                           List<BazaarEscrowSettlement> settlements) {
        settlements.add(new BazaarEscrowSettlement(fill.settlementTransactionId(),
                buyer.ownerId(), Optional.of(buyer.orderId()),
                BazaarSettlementKind.BUYER_ITEM_CLAIM, 0L, fill.quantity(),
                Optional.of(seller.ownerId()), Optional.of(fill.fillId())));
        long sellerNet = Math.subtractExact(fill.grossMinor(), fill.sellerFeeMinor());
        if (sellerNet > 0L) {
            settlements.add(new BazaarEscrowSettlement(fill.settlementTransactionId(),
                    seller.ownerId(), Optional.of(seller.orderId()),
                    BazaarSettlementKind.SELLER_MONEY_CLAIM, sellerNet, 0,
                    Optional.of(buyer.ownerId()), Optional.of(fill.fillId())));
        }
        if (fill.buyerPriceImprovementMinor() > 0L) {
            settlements.add(new BazaarEscrowSettlement(fill.settlementTransactionId(),
                    buyer.ownerId(), Optional.of(buyer.orderId()),
                    BazaarSettlementKind.BUYER_CHANGE_CLAIM,
                    fill.buyerPriceImprovementMinor(), 0,
                    Optional.of(seller.ownerId()), Optional.of(fill.fillId())));
        }
        long fees = Math.addExact(fill.buyerFeeMinor(), fill.sellerFeeMinor());
        if (fees > 0L) {
            settlements.add(new BazaarEscrowSettlement(fill.settlementTransactionId(),
                    FEE_ACCOUNT, Optional.empty(), BazaarSettlementKind.FEE_DESTINATION,
                    fees, 0, Optional.empty(), Optional.of(fill.fillId())));
        }
    }

    private static void addTerminalSettlements(UUID transactionId, BazaarOrder order,
                                               BazaarOrder.TerminalApplication terminal,
                                               List<BazaarEscrowSettlement> settlements) {
        if (terminal.moneyRefundMinor() > 0L) {
            settlements.add(new BazaarEscrowSettlement(transactionId, order.ownerId(),
                    Optional.of(order.orderId()), BazaarSettlementKind.BUYER_REFUND_CLAIM,
                    terminal.moneyRefundMinor(), 0, Optional.empty(), Optional.empty()));
        }
        if (terminal.itemRefundQuantity() > 0) {
            settlements.add(new BazaarEscrowSettlement(transactionId, order.ownerId(),
                    Optional.of(order.orderId()), BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM,
                    0L, terminal.itemRefundQuantity(), Optional.empty(), Optional.empty()));
        }
    }

    private BazaarProduct requireProduct(String productId) {
        BazaarProduct product = products.get(productId);
        if (product == null) {
            throw new IllegalArgumentException("Bazaar product does not exist");
        }
        return product;
    }

    private void freezeOrders(String productId) {
        freezeOrders(orders, productId);
    }

    private static void freezeOrders(Map<UUID, BazaarOrder> target, String productId) {
        for (Map.Entry<UUID, BazaarOrder> entry : new ArrayList<>(target.entrySet())) {
            BazaarOrder order = entry.getValue();
            if (order.productId().equals(productId) && order.matchable()) {
                target.put(entry.getKey(), order.freeze());
            }
        }
    }

    private static void resumeOrders(Map<UUID, BazaarOrder> target,
                                     String productId,
                                     long productVersion) {
        for (Map.Entry<UUID, BazaarOrder> entry
                : new ArrayList<>(target.entrySet())) {
            BazaarOrder order = entry.getValue();
            if (order.productId().equals(productId)
                    && order.productVersion() == productVersion
                    && order.state() == BazaarOrderState.FROZEN) {
                target.put(entry.getKey(), order.resume());
            }
        }
    }

    private void commitCircuitHalt(String productId) {
        BazaarProduct current = requireProduct(productId);
        Map<UUID, BazaarOrder> haltedOrders =
                new LinkedHashMap<>(orders);
        freezeOrders(haltedOrders, productId);
        orders.clear();
        orders.putAll(haltedOrders);
        products.put(productId, copyProductStatus(
                current, BazaarProductStatus.HALTED));
        BazaarProduct halted = products.get(productId);
        productVersions.put(BazaarProductVersionKey.of(halted), halted);
    }

    private static BazaarProduct copyProductStatus(BazaarProduct product,
                                                   BazaarProductStatus status) {
        return new BazaarProduct(product.productId(), product.version(), product.registryId(),
                product.exactIdentity(), product.categoryId(), product.lotSize(),
                product.priceTickMinor(), product.minimumPriceMinor(),
                product.maximumPriceMinor(), product.maximumQuantity(), status);
    }

    private static boolean sameProductDefinition(BazaarProduct first,
                                                 BazaarProduct second) {
        return first.productId().equals(second.productId())
                && first.version() == second.version()
                && first.registryId().equals(second.registryId())
                && first.exactIdentity().equals(second.exactIdentity())
                && first.categoryId().equals(second.categoryId())
                && first.lotSize() == second.lotSize()
                && first.priceTickMinor() == second.priceTickMinor()
                && first.minimumPriceMinor() == second.minimumPriceMinor()
                && first.maximumPriceMinor() == second.maximumPriceMinor()
                && first.maximumQuantity() == second.maximumQuantity();
    }

    private static boolean invalidId(UUID id) {
        return id == null || ZERO.equals(id);
    }

    private record MatchOutcome(List<BazaarFill> fills,
                                List<BazaarEscrowSettlement> settlements,
                                List<UUID> cancelledMakerOrderIds,
                                boolean productHalted,
                                boolean matchBudgetExhausted) {
    }

    private static final class WorkingState {
        private final Map<UUID, BazaarOrder> orders;
        private final Map<String, BazaarProduct> products;
        private final Map<String, Long> referencePrices;
        private final Set<UUID> terminalTransactions;
        private long nextSequence;

        private WorkingState(Map<UUID, BazaarOrder> orders,
                             Map<String, BazaarProduct> products,
                             Map<String, Long> referencePrices,
                             Set<UUID> terminalTransactions,
                             long nextSequence) {
            this.orders = orders;
            this.products = products;
            this.referencePrices = referencePrices;
            this.terminalTransactions = terminalTransactions;
            this.nextSequence = nextSequence;
        }

        private Map<UUID, BazaarOrder> orders() {
            return orders;
        }

        private Map<String, BazaarProduct> products() {
            return products;
        }

        private Map<String, Long> referencePrices() {
            return referencePrices;
        }

        private Set<UUID> terminalTransactions() {
            return terminalTransactions;
        }

        private long nextSequence() {
            return nextSequence;
        }

        private void nextSequence(long nextSequence) {
            this.nextSequence = nextSequence;
        }
    }
}
