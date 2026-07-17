package com.enviouse.futureshops.server.escrow.ledger;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryLedger {
    public static final int DEFAULT_MAXIMUM_ACCOUNTS = 1_000_000;
    public static final int DEFAULT_MAXIMUM_TRANSACTIONS = 1_000_000;

    private final int maximumAccounts;
    private final int maximumTransactions;
    private final Map<LedgerAccountId, Long> balances = new HashMap<>();
    private final Map<UUID, String> appliedFingerprints = new HashMap<>();
    private final Map<String, UUID> idempotencyKeys = new HashMap<>();
    private final Map<UUID, LedgerTransactionReceipt> transactionReceipts = new HashMap<>();

    public InMemoryLedger() {
        this(DEFAULT_MAXIMUM_ACCOUNTS, DEFAULT_MAXIMUM_TRANSACTIONS);
    }

    InMemoryLedger(int maximumAccounts, int maximumTransactions) {
        if (maximumAccounts <= 0 || maximumTransactions <= 0) {
            throw new IllegalArgumentException("Ledger limits must be positive");
        }
        this.maximumAccounts = maximumAccounts;
        this.maximumTransactions = maximumTransactions;
    }

    public synchronized LedgerApplyResult apply(LedgerTransaction transaction) {
        return evaluate(transaction, true);
    }

    public synchronized LedgerApplyResult preflight(LedgerTransaction transaction) {
        return evaluate(transaction, false);
    }

    private LedgerApplyResult evaluate(LedgerTransaction transaction, boolean commit) {
        Objects.requireNonNull(transaction, "transaction");
        String fingerprint = transaction.fingerprint();
        String existingFingerprint = appliedFingerprints.get(transaction.transactionId());
        if (existingFingerprint != null) {
            LedgerTransactionReceipt receipt = transactionReceipts.get(
                    transaction.transactionId());
            if (!existingFingerprint.equals(fingerprint) || receipt == null
                    || !receipt.fingerprint().equals(fingerprint)) {
                throw new LedgerConflictException("Transaction ID reused with different ledger legs");
            }
            return new LedgerApplyResult(false, true, balancesFor(transaction));
        }
        UUID existingId = idempotencyKeys.get(transaction.idempotencyKey());
        if (existingId != null && !existingId.equals(transaction.transactionId())) {
            throw new LedgerConflictException("Idempotency key reused by another transaction");
        }
        if (appliedFingerprints.size() >= maximumTransactions) {
            throw new LedgerConflictException("Ledger transaction limit is exceeded");
        }

        Map<LedgerAccountId, Long> accountDeltas = new LinkedHashMap<>();
        for (LedgerLeg leg : LedgerTransaction.canonicalLegs(transaction.legs())) {
            accountDeltas.merge(leg.account(), leg.deltaMinor(), Math::addExact);
        }

        Map<LedgerAccountId, Long> proposed = new LinkedHashMap<>();
        for (Map.Entry<LedgerAccountId, Long> entry : accountDeltas.entrySet()) {
            long current = balances.getOrDefault(entry.getKey(), 0L);
            long next = Math.addExact(current, entry.getValue());
            if (next < 0L && !entry.getKey().type().negativeAllowed()) {
                throw new LedgerConflictException("Ledger account would become negative");
            }
            proposed.put(entry.getKey(), next);
        }
        long newAccounts = proposed.keySet().stream().filter(account -> !balances.containsKey(account)).count();
        if (Math.addExact((long) balances.size(), newAccounts) > maximumAccounts) {
            throw new LedgerConflictException("Ledger account limit is exceeded");
        }

        if (commit) {
            balances.putAll(proposed);
            appliedFingerprints.put(transaction.transactionId(), fingerprint);
            idempotencyKeys.put(transaction.idempotencyKey(), transaction.transactionId());
            transactionReceipts.put(transaction.transactionId(),
                    LedgerTransactionReceipt.create(transactionReceipts.size(), transaction));
        }
        return new LedgerApplyResult(true, false, proposed);
    }

    public synchronized long balance(LedgerAccountId account) {
        return balances.getOrDefault(account, 0L);
    }

    public synchronized boolean containsAccount(LedgerAccountId account) {
        return balances.containsKey(account);
    }

    public synchronized boolean wasApplied(UUID transactionId) {
        return appliedFingerprints.containsKey(transactionId);
    }

    public synchronized Optional<LedgerTransactionReceipt> transactionReceipt(
            UUID transactionId
    ) {
        return Optional.ofNullable(transactionReceipts.get(transactionId));
    }

    public synchronized boolean hasMaterializedState() {
        return !balances.isEmpty() || !appliedFingerprints.isEmpty()
                || !idempotencyKeys.isEmpty() || !transactionReceipts.isEmpty();
    }

    public synchronized Map<LedgerAccountId, Long> snapshotBalances() {
        return Collections.unmodifiableMap(new HashMap<>(balances));
    }

    public synchronized Map<UUID, String> snapshotAppliedFingerprints() {
        return Collections.unmodifiableMap(new HashMap<>(appliedFingerprints));
    }

    public synchronized Map<String, UUID> snapshotIdempotencyKeys() {
        return Collections.unmodifiableMap(new HashMap<>(idempotencyKeys));
    }

    public synchronized Map<UUID, LedgerTransactionReceipt> snapshotTransactionReceipts() {
        return Collections.unmodifiableMap(new HashMap<>(transactionReceipts));
    }

    public synchronized void restore(Map<LedgerAccountId, Long> restoredBalances,
                                     Map<UUID, String> restoredFingerprints,
                                     Map<String, UUID> restoredIdempotencyKeys,
                                     Map<UUID, LedgerTransactionReceipt> restoredReceipts) {
        Objects.requireNonNull(restoredBalances, "restoredBalances");
        Objects.requireNonNull(restoredFingerprints, "restoredFingerprints");
        Objects.requireNonNull(restoredIdempotencyKeys, "restoredIdempotencyKeys");
        Objects.requireNonNull(restoredReceipts, "restoredReceipts");
        for (Map.Entry<LedgerAccountId, Long> entry : restoredBalances.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "restored account");
            Objects.requireNonNull(entry.getValue(), "restored balance");
            if (entry.getValue() < 0L && !entry.getKey().type().negativeAllowed()) {
                throw new IllegalArgumentException("Invalid restored balance");
            }
        }
        for (Map.Entry<UUID, String> entry : restoredFingerprints.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "restored transaction id");
            String fingerprint = Objects.requireNonNull(entry.getValue(), "restored fingerprint");
            if (!fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid restored fingerprint");
            }
        }
        if (restoredIdempotencyKeys.size() != restoredFingerprints.size()
                || restoredReceipts.size() != restoredFingerprints.size()) {
            throw new IllegalArgumentException("Restored ledger transaction indexes do not match");
        }
        if (restoredBalances.size() > maximumAccounts
                || restoredFingerprints.size() > maximumTransactions
                || restoredReceipts.size() > maximumTransactions) {
            throw new IllegalArgumentException("Restored ledger exceeds its limits");
        }

        List<LedgerTransactionReceipt> orderedReceipts = restoredReceipts.entrySet().stream()
                .map(entry -> requireReceiptEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(
                        LedgerTransactionReceipt::applicationSequence))
                .toList();
        Map<String, UUID> derivedIdempotency = new HashMap<>();
        long expectedSequence = 0L;
        for (LedgerTransactionReceipt receipt : orderedReceipts) {
            LedgerTransaction transaction = receipt.transaction();
            if (receipt.applicationSequence() != expectedSequence) {
                throw new IllegalArgumentException(
                        "Restored ledger receipt sequence is not contiguous");
            }
            expectedSequence = Math.addExact(expectedSequence, 1L);
            String fingerprint = restoredFingerprints.get(transaction.transactionId());
            if (fingerprint == null || !fingerprint.equals(receipt.fingerprint())
                    || !fingerprint.equals(transaction.fingerprint())) {
                throw new IllegalArgumentException(
                        "Restored ledger receipt fingerprint does not match");
            }
            UUID old = derivedIdempotency.putIfAbsent(
                    transaction.idempotencyKey(), transaction.transactionId());
            if (old != null) {
                throw new IllegalArgumentException(
                        "Restored ledger idempotency key is duplicated");
            }
        }
        if (!derivedIdempotency.equals(restoredIdempotencyKeys)) {
            throw new IllegalArgumentException(
                    "Restored ledger idempotency index does not match receipts");
        }

        Map<LedgerAccountId, Long> reconstructed;
        try {
            reconstructed = reconstructBalances(orderedReceipts);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Restored ledger receipt arithmetic overflows", exception);
        }
        if (!reconstructed.equals(restoredBalances)) {
            throw new IllegalArgumentException(
                    "Restored ledger balances do not match transaction receipts");
        }
        balances.clear();
        appliedFingerprints.clear();
        idempotencyKeys.clear();
        transactionReceipts.clear();
        balances.putAll(restoredBalances);
        appliedFingerprints.putAll(restoredFingerprints);
        idempotencyKeys.putAll(restoredIdempotencyKeys);
        transactionReceipts.putAll(restoredReceipts);
    }

    private static LedgerTransactionReceipt requireReceiptEntry(
            UUID transactionId,
            LedgerTransactionReceipt receipt
    ) {
        Objects.requireNonNull(transactionId, "restored receipt transaction id");
        Objects.requireNonNull(receipt, "restored receipt");
        if (!transactionId.equals(receipt.transaction().transactionId())) {
            throw new IllegalArgumentException("Restored ledger receipt index is invalid");
        }
        return receipt;
    }

    private static Map<LedgerAccountId, Long> reconstructBalances(
            List<LedgerTransactionReceipt> receipts
    ) {
        Map<LedgerAccountId, Long> reconstructed = new HashMap<>();
        for (LedgerTransactionReceipt receipt : receipts) {
            Map<LedgerAccountId, Long> deltas = new LinkedHashMap<>();
            for (LedgerLeg leg : LedgerTransaction.canonicalLegs(
                    receipt.transaction().legs())) {
                deltas.merge(leg.account(), leg.deltaMinor(), Math::addExact);
            }
            for (Map.Entry<LedgerAccountId, Long> entry : deltas.entrySet()) {
                long next = Math.addExact(
                        reconstructed.getOrDefault(entry.getKey(), 0L), entry.getValue());
                if (next < 0L && !entry.getKey().type().negativeAllowed()) {
                    throw new IllegalArgumentException(
                            "Restored ledger receipt creates a negative balance");
                }
                reconstructed.put(entry.getKey(), next);
            }
        }
        return reconstructed;
    }

    private Map<LedgerAccountId, Long> balancesFor(LedgerTransaction transaction) {
        Map<LedgerAccountId, Long> result = new LinkedHashMap<>();
        for (LedgerLeg leg : transaction.legs()) {
            result.put(leg.account(), balances.getOrDefault(leg.account(), 0L));
        }
        return result;
    }
}
