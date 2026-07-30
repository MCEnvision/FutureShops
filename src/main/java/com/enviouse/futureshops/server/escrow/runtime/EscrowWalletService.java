package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerConflictException;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class EscrowWalletService {
    private static final LedgerAccountId ADMIN_SOURCE = LedgerAccountId.system(
            LedgerAccountType.ADMIN_SOURCE);
    private static final LedgerAccountId ADMIN_SINK = LedgerAccountId.system(
            LedgerAccountType.ADMIN_SINK);
    private static final ThreadLocal<Set<UUID>> ACTIVE_ACCOUNTS =
            ThreadLocal.withInitial(HashSet::new);

    private final WalletLedgerBackend backend;

    EscrowWalletService(WalletLedgerBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public static EscrowWalletService live() {
        return new EscrowWalletService(new RuntimeWalletLedgerBackend(
                EscrowRuntimeManager.requireReady()));
    }

    public long balance(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return netBalance(playerId);
    }

    public static OptionalLong storedBalance(LedgerSavedData ledger,
                                             UUID playerId) {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(playerId, "playerId");
        LedgerAccountId wallet = walletAccount(playerId);
        LedgerAccountId debt = debtAccount(playerId);
        if (!ledger.containsAccount(wallet) && !ledger.containsAccount(debt)) {
            return OptionalLong.empty();
        }
        long walletBalance = ledger.balance(wallet);
        long debtBalance = ledger.balance(debt);
        requireNormalized(playerId, walletBalance, debtBalance);
        return OptionalLong.of(Math.addExact(walletBalance, debtBalance));
    }

    public boolean isInitialized(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return backend.containsAccount(walletAccount(playerId))
                || backend.containsAccount(debtAccount(playerId));
    }

    public boolean wasRequestApplied(UUID requestId) {
        return backend.wasApplied(Objects.requireNonNull(
                requestId, "requestId"));
    }

    public Map<UUID, Long> snapshotBalances() {
        Map<LedgerAccountId, Long> ledger = backend.snapshotBalances();
        Set<UUID> playerIds = new HashSet<>();
        for (LedgerAccountId account : ledger.keySet()) {
            if (account.type() == LedgerAccountType.PLAYER_WALLET
                    || account.type() == LedgerAccountType.PLAYER_DEBT) {
                playerIds.add(parsePlayer(account.ownerKey()));
            }
        }
        Map<UUID, Long> result = new LinkedHashMap<>();
        playerIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(playerId -> result.put(playerId,
                        netBalance(ledger, playerId)));
        return Collections.unmodifiableMap(result);
    }

    public WalletMutationResult initialize(UUID requestId, UUID playerId,
                                           long initialBalance,
                                           boolean allowNegative,
                                           String reason) {
        requireRequest(requestId, playerId, reason);
        return withAccounts(List.of(playerId), () -> {
            String key = semanticKey("initialize", requestId,
                    playerId, null, initialBalance, reason);
            WalletMutationResult replay = replaySingle(
                    requestId, key, reason, playerId);
            if (replay != null) {
                return replay;
            }
            if (initialBalance < 0L && !allowNegative) {
                return WalletMutationResult.single(
                        WalletMutationStatus.NEGATIVE_NOT_ALLOWED,
                        netBalance(playerId));
            }
            LedgerTransaction transaction;
            try {
                transaction = initializationTransaction(requestId, playerId,
                        initialBalance, allowNegative, reason, key);
            } catch (ArithmeticException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.ARITHMETIC_OVERFLOW,
                        netBalance(playerId));
            }
            if (isInitialized(playerId)
                    && !backend.wasApplied(requestId)) {
                return WalletMutationResult.single(
                        WalletMutationStatus.ALREADY_INITIALIZED,
                        netBalance(playerId));
            }
            return commitSingle(transaction, playerId);
        });
    }

    public WalletMutationResult credit(UUID requestId, UUID playerId,
                                       long amountMinor, long maximumBalance,
                                       String reason) {
        requireRequest(requestId, playerId, reason);
        return withAccounts(List.of(playerId), () -> {
            String key = semanticKey("credit", requestId,
                    playerId, null, amountMinor, reason);
            WalletMutationResult replay = replaySingle(
                    requestId, key, reason, playerId);
            if (replay != null) {
                return replay;
            }
            long current = netBalance(playerId);
            if (amountMinor <= 0L) {
                return WalletMutationResult.single(
                        WalletMutationStatus.INVALID_AMOUNT, current);
            }
            if (maximumBalance < 0L) {
                return WalletMutationResult.single(
                        WalletMutationStatus.MAX_BALANCE_EXCEEDED, current);
            }
            try {
                long next = Math.addExact(current, amountMinor);
                if (next > maximumBalance) {
                    return WalletMutationResult.single(
                            WalletMutationStatus.MAX_BALANCE_EXCEEDED, current);
                }
                return commitSingle(new LedgerTransaction(
                        requestId, key,
                        ledgerReason(reason), creditLegs(playerId, amountMinor)),
                        playerId);
            } catch (ArithmeticException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.ARITHMETIC_OVERFLOW, current);
            } catch (LedgerConflictException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.CONFLICT, netBalance(playerId));
            }
        });
    }

    public WalletMutationResult debit(UUID requestId, UUID playerId,
                                      long amountMinor, boolean allowNegative,
                                      String reason) {
        requireRequest(requestId, playerId, reason);
        return withAccounts(List.of(playerId), () -> {
            String key = semanticKey("debit", requestId,
                    playerId, null, amountMinor, reason);
            WalletMutationResult replay = replaySingle(
                    requestId, key, reason, playerId);
            if (replay != null) {
                return replay;
            }
            long current = netBalance(playerId);
            if (amountMinor <= 0L) {
                return WalletMutationResult.single(
                        WalletMutationStatus.INVALID_AMOUNT, current);
            }
            long wallet = backend.balance(walletAccount(playerId));
            if (!allowNegative && wallet < amountMinor) {
                return WalletMutationResult.single(
                        WalletMutationStatus.INSUFFICIENT_FUNDS, current);
            }
            try {
                Math.subtractExact(current, amountMinor);
                return commitSingle(new LedgerTransaction(
                        requestId, key,
                        ledgerReason(reason), debitLegs(
                                playerId, amountMinor, allowNegative)), playerId);
            } catch (ArithmeticException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.ARITHMETIC_OVERFLOW, current);
            } catch (LedgerConflictException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.CONFLICT, netBalance(playerId));
            }
        });
    }

    public WalletMutationResult transfer(UUID requestId, UUID fromPlayerId,
                                         UUID toPlayerId, long amountMinor,
                                         long recipientMaximumBalance,
                                         String reason) {
        requireRequest(requestId, fromPlayerId, reason);
        Objects.requireNonNull(toPlayerId, "toPlayerId");
        if (fromPlayerId.equals(toPlayerId)) {
            return WalletMutationResult.pair(
                    WalletMutationStatus.INVALID_TARGET,
                    netBalance(fromPlayerId), netBalance(toPlayerId));
        }
        return withAccounts(List.of(fromPlayerId, toPlayerId), () -> {
            String key = semanticKey("transfer", requestId,
                    fromPlayerId, toPlayerId, amountMinor, reason);
            WalletMutationResult replay = replayPair(
                    requestId, key, reason, fromPlayerId, toPlayerId);
            if (replay != null) {
                return replay;
            }
            long fromBalance = netBalance(fromPlayerId);
            long toBalance = netBalance(toPlayerId);
            if (amountMinor <= 0L) {
                return WalletMutationResult.pair(
                        WalletMutationStatus.INVALID_AMOUNT,
                        fromBalance, toBalance);
            }
            long fromWallet = backend.balance(walletAccount(fromPlayerId));
            if (fromWallet < amountMinor) {
                return WalletMutationResult.pair(
                        WalletMutationStatus.INSUFFICIENT_FUNDS,
                        fromBalance, toBalance);
            }
            try {
                long nextRecipient = Math.addExact(toBalance, amountMinor);
                if (nextRecipient > recipientMaximumBalance) {
                    return WalletMutationResult.pair(
                            WalletMutationStatus.MAX_BALANCE_EXCEEDED,
                            fromBalance, toBalance);
                }
                List<LedgerLeg> legs = new ArrayList<>();
                legs.add(new LedgerLeg(walletAccount(fromPlayerId),
                        Math.negateExact(amountMinor)));
                addPlayerCreditLegs(legs, toPlayerId, amountMinor);
                return commitPair(new LedgerTransaction(
                        requestId, key,
                        ledgerReason(reason), legs), fromPlayerId, toPlayerId);
            } catch (ArithmeticException exception) {
                return WalletMutationResult.pair(
                        WalletMutationStatus.ARITHMETIC_OVERFLOW,
                        fromBalance, toBalance);
            } catch (LedgerConflictException exception) {
                return WalletMutationResult.pair(
                        WalletMutationStatus.CONFLICT,
                        netBalance(fromPlayerId), netBalance(toPlayerId));
            }
        });
    }

    public WalletMutationResult setBalance(UUID requestId, UUID playerId,
                                           long targetBalance,
                                           boolean allowNegative,
                                           String reason) {
        requireRequest(requestId, playerId, reason);
        return withAccounts(List.of(playerId), () -> {
            String key = semanticKey("set", requestId,
                    playerId, null, targetBalance, reason);
            WalletMutationResult replay = replaySingle(
                    requestId, key, reason, playerId);
            if (replay != null) {
                return replay;
            }
            long current = netBalance(playerId);
            if (targetBalance < 0L && !allowNegative) {
                return WalletMutationResult.single(
                        WalletMutationStatus.NEGATIVE_NOT_ALLOWED, current);
            }
            try {
                List<LedgerLeg> legs = setBalanceLegs(
                        playerId, current, targetBalance);
                return commitSingle(new LedgerTransaction(
                        requestId, key,
                        ledgerReason(reason), legs), playerId);
            } catch (ArithmeticException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.ARITHMETIC_OVERFLOW, current);
            } catch (LedgerConflictException exception) {
                return WalletMutationResult.single(
                        WalletMutationStatus.CONFLICT, netBalance(playerId));
            }
        });
    }

    private WalletMutationResult commitSingle(LedgerTransaction transaction,
                                              UUID playerId) {
        try {
            boolean replayed = backend.commit(transaction);
            return WalletMutationResult.single(replayed
                            ? WalletMutationStatus.REPLAYED
                            : WalletMutationStatus.APPLIED,
                    netBalance(playerId));
        } catch (LedgerConflictException exception) {
            return WalletMutationResult.single(
                    WalletMutationStatus.CONFLICT, netBalance(playerId));
        }
    }

    private WalletMutationResult commitPair(LedgerTransaction transaction,
                                            UUID firstPlayerId,
                                            UUID secondPlayerId) {
        try {
            boolean replayed = backend.commit(transaction);
            return WalletMutationResult.pair(replayed
                            ? WalletMutationStatus.REPLAYED
                            : WalletMutationStatus.APPLIED,
                    netBalance(firstPlayerId), netBalance(secondPlayerId));
        } catch (LedgerConflictException exception) {
            return WalletMutationResult.pair(
                    WalletMutationStatus.CONFLICT,
                    netBalance(firstPlayerId), netBalance(secondPlayerId));
        }
    }

    private LedgerTransaction initializationTransaction(
            UUID requestId, UUID playerId, long initialBalance,
            boolean allowNegative, String reason, String key
    ) {
        if (initialBalance < 0L && !allowNegative) {
            throw new IllegalArgumentException(
                    "Negative wallet initialization is not allowed");
        }
        List<LedgerLeg> legs;
        if (initialBalance > 0L) {
            legs = List.of(
                    new LedgerLeg(ADMIN_SOURCE,
                            Math.negateExact(initialBalance)),
                    new LedgerLeg(walletAccount(playerId), initialBalance));
        } else if (initialBalance < 0L) {
            legs = List.of(
                    new LedgerLeg(debtAccount(playerId), initialBalance),
                    new LedgerLeg(ADMIN_SINK,
                            Math.negateExact(initialBalance)));
        } else {
            legs = markerLegs(playerId);
        }
        return new LedgerTransaction(requestId,
                key,
                ledgerReason(reason), legs);
    }

    private WalletMutationResult replaySingle(UUID requestId, String key,
                                              String reason, UUID playerId) {
        if (!backend.wasApplied(requestId)) {
            return null;
        }
        if (!matchesAppliedRequest(requestId, key, reason)) {
            return WalletMutationResult.single(
                    WalletMutationStatus.CONFLICT, netBalance(playerId));
        }
        return WalletMutationResult.single(
                WalletMutationStatus.REPLAYED, netBalance(playerId));
    }

    private WalletMutationResult replayPair(UUID requestId, String key,
                                            String reason, UUID firstPlayerId,
                                            UUID secondPlayerId) {
        if (!backend.wasApplied(requestId)) {
            return null;
        }
        if (!matchesAppliedRequest(requestId, key, reason)) {
            return WalletMutationResult.pair(
                    WalletMutationStatus.CONFLICT,
                    netBalance(firstPlayerId), netBalance(secondPlayerId));
        }
        return WalletMutationResult.pair(
                WalletMutationStatus.REPLAYED,
                netBalance(firstPlayerId), netBalance(secondPlayerId));
    }

    private boolean matchesAppliedRequest(UUID requestId, String key,
                                          String reason) {
        return backend.appliedTransaction(requestId)
                .filter(transaction -> transaction.idempotencyKey().equals(key))
                .filter(transaction -> transaction.reason().equals(
                        ledgerReason(reason)))
                .isPresent();
    }

    private List<LedgerLeg> creditLegs(UUID playerId, long amountMinor) {
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(ADMIN_SOURCE, Math.negateExact(amountMinor)));
        addPlayerCreditLegs(legs, playerId, amountMinor);
        return legs;
    }

    private void addPlayerCreditLegs(List<LedgerLeg> legs, UUID playerId,
                                     long amountMinor) {
        long debt = backend.balance(debtAccount(playerId));
        requireNormalized(playerId, backend.balance(walletAccount(playerId)), debt);
        if (debt < 0L) {
            long nextDebt = Math.addExact(debt, amountMinor);
            if (nextDebt <= 0L) {
                legs.add(new LedgerLeg(debtAccount(playerId), amountMinor));
                return;
            }
            legs.add(new LedgerLeg(debtAccount(playerId), Math.negateExact(debt)));
            legs.add(new LedgerLeg(walletAccount(playerId), nextDebt));
            return;
        }
        legs.add(new LedgerLeg(walletAccount(playerId), amountMinor));
    }

    private List<LedgerLeg> debitLegs(UUID playerId, long amountMinor,
                                     boolean allowNegative) {
        long wallet = backend.balance(walletAccount(playerId));
        long debt = backend.balance(debtAccount(playerId));
        requireNormalized(playerId, wallet, debt);
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(ADMIN_SINK, amountMinor));
        if (wallet >= amountMinor) {
            legs.add(new LedgerLeg(walletAccount(playerId),
                    Math.negateExact(amountMinor)));
            return legs;
        }
        if (!allowNegative) {
            throw new LedgerConflictException("Wallet has insufficient funds");
        }
        if (wallet > 0L) {
            legs.add(new LedgerLeg(walletAccount(playerId),
                    Math.negateExact(wallet)));
        }
        long remainder = Math.subtractExact(amountMinor, wallet);
        legs.add(new LedgerLeg(debtAccount(playerId),
                Math.negateExact(remainder)));
        return legs;
    }

    private List<LedgerLeg> setBalanceLegs(UUID playerId, long current,
                                           long target) {
        long delta = Math.subtractExact(target, current);
        if (delta == 0L) {
            return markerLegs(playerId);
        }
        long wallet = backend.balance(walletAccount(playerId));
        long debt = backend.balance(debtAccount(playerId));
        requireNormalized(playerId, wallet, debt);
        long targetWallet = Math.max(target, 0L);
        long targetDebt = Math.min(target, 0L);
        List<LedgerLeg> legs = new ArrayList<>();
        addNonzeroLeg(legs, walletAccount(playerId),
                Math.subtractExact(targetWallet, wallet));
        addNonzeroLeg(legs, debtAccount(playerId),
                Math.subtractExact(targetDebt, debt));
        if (delta > 0L) {
            legs.add(new LedgerLeg(ADMIN_SOURCE, Math.negateExact(delta)));
        } else {
            legs.add(new LedgerLeg(ADMIN_SINK, Math.negateExact(delta)));
        }
        return legs;
    }

    private static List<LedgerLeg> markerLegs(UUID playerId) {
        LedgerAccountId account = walletAccount(playerId);
        return List.of(new LedgerLeg(account, 1L),
                new LedgerLeg(account, -1L));
    }

    private static void addNonzeroLeg(List<LedgerLeg> legs,
                                     LedgerAccountId account, long delta) {
        if (delta != 0L) {
            legs.add(new LedgerLeg(account, delta));
        }
    }

    private long netBalance(UUID playerId) {
        long wallet = backend.balance(walletAccount(playerId));
        long debt = backend.balance(debtAccount(playerId));
        requireNormalized(playerId, wallet, debt);
        return Math.addExact(wallet, debt);
    }

    private static long netBalance(Map<LedgerAccountId, Long> balances,
                                   UUID playerId) {
        long wallet = balances.getOrDefault(walletAccount(playerId), 0L);
        long debt = balances.getOrDefault(debtAccount(playerId), 0L);
        requireNormalized(playerId, wallet, debt);
        return Math.addExact(wallet, debt);
    }

    private static void requireNormalized(UUID playerId, long wallet,
                                          long debt) {
        if (wallet < 0L || debt > 0L || wallet > 0L && debt < 0L) {
            throw new LedgerConflictException(
                    "Wallet accounts are not normalized for " + playerId);
        }
    }

    private static LedgerAccountId walletAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                playerId.toString());
    }

    private static LedgerAccountId debtAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_DEBT,
                playerId.toString());
    }

    private static UUID parsePlayer(String ownerKey) {
        try {
            return UUID.fromString(ownerKey);
        } catch (IllegalArgumentException exception) {
            throw new LedgerConflictException(
                    "Wallet account owner is not a player UUID");
        }
    }

    private static String semanticKey(String operation, UUID requestId,
                                      UUID firstPlayerId,
                                      UUID secondPlayerId, long amount,
                                      String reason) {
        String payload = operation + "," + requestId + ","
                + firstPlayerId + "," + Objects.toString(secondPlayerId, "")
                + "," + amount + "," + ledgerReason(reason);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    payload.getBytes(StandardCharsets.UTF_8));
            return "wallet." + operation + "." + requestId + "."
                    + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String ledgerReason(String reason) {
        String normalized = Objects.requireNonNull(reason, "reason").trim();
        if (normalized.isEmpty() || normalized.length() > 89) {
            throw new IllegalArgumentException("Invalid wallet reason");
        }
        return "wallet." + normalized;
    }

    private static void requireRequest(UUID requestId, UUID playerId,
                                       String reason) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        ledgerReason(reason);
    }

    private static <T> T withAccounts(List<UUID> playerIds,
                                      Supplier<T> operation) {
        Set<UUID> active = ACTIVE_ACCOUNTS.get();
        List<UUID> distinct = playerIds.stream().distinct().toList();
        if (distinct.stream().anyMatch(active::contains)) {
            throw new EscrowRuntimeException(
                    "Reentrant wallet mutation is not allowed");
        }
        active.addAll(distinct);
        try {
            return operation.get();
        } finally {
            active.removeAll(distinct);
            if (active.isEmpty()) {
                ACTIVE_ACCOUNTS.remove();
            }
        }
    }
}
