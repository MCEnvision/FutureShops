package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MoneyClaimSettlement(
        int formatVersion,
        UUID requestId,
        long walletBeforeMinorUnits,
        long debtBeforeMinorUnits,
        long reservedBeforeMinorUnits,
        long claimRemainingBeforeUnits,
        long walletBalanceLimitMinorUnits,
        long configurationGeneration,
        ClaimDeliveryCommit delivery,
        LedgerTransaction ledgerTransaction
) {
    public static final int LEGACY_FORMAT_VERSION = 1;
    public static final int CURRENT_FORMAT_VERSION = 2;
    public static final String LEDGER_REASON = "Money claim collection";

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public MoneyClaimSettlement {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        if ((formatVersion != LEGACY_FORMAT_VERSION
                && formatVersion != CURRENT_FORMAT_VERSION)
                || ZERO_UUID.equals(requestId)
                || walletBeforeMinorUnits < 0L
                || debtBeforeMinorUnits > 0L
                || walletBeforeMinorUnits > 0L
                && debtBeforeMinorUnits < 0L
                || reservedBeforeMinorUnits < 0L
                || claimRemainingBeforeUnits <= 0L
                || walletBalanceLimitMinorUnits < 0L
                || configurationGeneration < 0L) {
            throw new IllegalArgumentException(
                    "Money claim settlement snapshot is invalid");
        }
        if (formatVersion == LEGACY_FORMAT_VERSION) {
            validateLegacy(
                    requestId, walletBeforeMinorUnits,
                    debtBeforeMinorUnits, reservedBeforeMinorUnits,
                    claimRemainingBeforeUnits,
                    walletBalanceLimitMinorUnits,
                    configurationGeneration, delivery,
                    ledgerTransaction);
        } else {
            validateCurrent(
                    requestId, walletBeforeMinorUnits,
                    debtBeforeMinorUnits, reservedBeforeMinorUnits,
                    claimRemainingBeforeUnits,
                    walletBalanceLimitMinorUnits, delivery,
                    ledgerTransaction);
        }
    }

    public MoneyClaimSettlement(
            UUID requestId,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long claimRemainingBeforeUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            ClaimDeliveryCommit delivery,
            LedgerTransaction ledgerTransaction
    ) {
        this(CURRENT_FORMAT_VERSION, requestId,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, claimRemainingBeforeUnits,
                walletBalanceLimitMinorUnits, configurationGeneration,
                delivery, ledgerTransaction);
    }

    public static MoneyClaimSettlement create(
            UUID requestId,
            UUID ownerId,
            UUID claimId,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long claimRemainingBeforeUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            Instant deliveredAt
    ) {
        long units = expectedDeliveryUnits(
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, claimRemainingBeforeUnits,
                walletBalanceLimitMinorUnits);
        if (units <= 0L) {
            throw new IllegalArgumentException(
                    "Money claim settlement has no deliverable value");
        }
        String key = requestKey(requestId, claimId);
        ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(
                ownerId, claimId, key, units, deliveredAt);
        long debtCredit = debtCreditMinorUnits(
                units, debtBeforeMinorUnits);
        long walletCredit = Math.subtractExact(units, debtCredit);
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(new LedgerAccountId(
                LedgerAccountType.PLAYER_CLAIM,
                claimId.toString()), Math.negateExact(units)));
        if (debtCredit > 0L) {
            legs.add(new LedgerLeg(
                    PlayerPaymentCommit.debtAccount(ownerId), debtCredit));
        }
        if (walletCredit > 0L) {
            legs.add(new LedgerLeg(
                    PlayerPaymentCommit.walletAccount(ownerId),
                    walletCredit));
        }
        return new MoneyClaimSettlement(
                CURRENT_FORMAT_VERSION, requestId,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, claimRemainingBeforeUnits,
                walletBalanceLimitMinorUnits, configurationGeneration,
                delivery, new LedgerTransaction(
                requestId, key, LEDGER_REASON, legs));
    }

    static MoneyClaimSettlement legacy(
            ClaimDeliveryCommit delivery,
            LedgerTransaction ledgerTransaction
    ) {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        return new MoneyClaimSettlement(
                LEGACY_FORMAT_VERSION,
                ledgerTransaction.transactionId(),
                0L, 0L, 0L,
                delivery.units(), delivery.units(), 0L,
                delivery, ledgerTransaction);
    }

    public boolean legacyFormat() {
        return formatVersion == LEGACY_FORMAT_VERSION;
    }

    public long deliveredUnits() {
        return delivery.units();
    }

    public static String requestKey(UUID requestId, UUID claimId) {
        return "money.claim." + Objects.requireNonNull(
                requestId, "requestId") + "." + Objects.requireNonNull(
                claimId, "claimId");
    }

    static long expectedDeliveryUnits(
            long wallet,
            long debt,
            long reserved,
            long claimRemaining,
            long limit
    ) {
        long balance = Math.addExact(wallet, debt);
        long exposure = Math.addExact(balance, reserved);
        if (exposure >= limit) {
            return 0L;
        }
        long capacity;
        try {
            capacity = Math.subtractExact(limit, exposure);
        } catch (ArithmeticException exception) {
            capacity = Long.MAX_VALUE;
        }
        return Math.min(claimRemaining, capacity);
    }

    private static long debtCreditMinorUnits(long units, long debt) {
        if (debt >= 0L) {
            return 0L;
        }
        if (debt == Long.MIN_VALUE) {
            return units;
        }
        return Math.min(units, Math.negateExact(debt));
    }

    private static void validateLegacy(
            UUID requestId,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long claimRemainingBeforeUnits,
            long walletBalanceLimitMinorUnits,
            long configurationGeneration,
            ClaimDeliveryCommit delivery,
            LedgerTransaction ledgerTransaction
    ) {
        if (!ledgerTransaction.transactionId().equals(requestId)
                || walletBeforeMinorUnits != 0L
                || debtBeforeMinorUnits != 0L
                || reservedBeforeMinorUnits != 0L
                || claimRemainingBeforeUnits != delivery.units()
                || walletBalanceLimitMinorUnits != delivery.units()
                || configurationGeneration != 0L
                || ledgerTransaction.legs().size() != 2
                || !ledgerTransaction.idempotencyKey().equals(
                delivery.requestKey())) {
            throw new IllegalArgumentException(
                    "Legacy money claim settlement is ambiguous");
        }
        requireNonzero(delivery.ownerId(), "owner");
        requireNonzero(delivery.claimId(), "claim");
        Map<LedgerAccountId, Long> expected = Map.of(
                new LedgerAccountId(
                        LedgerAccountType.PLAYER_CLAIM,
                        delivery.claimId().toString()),
                Math.negateExact(delivery.units()),
                PlayerPaymentCommit.walletAccount(delivery.ownerId()),
                delivery.units());
        Map<LedgerAccountId, Long> actual = new HashMap<>();
        for (LedgerLeg leg : ledgerTransaction.legs()) {
            if (actual.put(leg.account(), leg.deltaMinor()) != null) {
                throw new IllegalArgumentException(
                        "Legacy money claim ledger account is duplicated");
            }
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "Legacy money claim ledger split is invalid");
        }
    }

    private static void validateCurrent(
            UUID requestId,
            long walletBeforeMinorUnits,
            long debtBeforeMinorUnits,
            long reservedBeforeMinorUnits,
            long claimRemainingBeforeUnits,
            long walletBalanceLimitMinorUnits,
            ClaimDeliveryCommit delivery,
            LedgerTransaction ledgerTransaction
    ) {
        long units = expectedDeliveryUnits(
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, claimRemainingBeforeUnits,
                walletBalanceLimitMinorUnits);
        if (units <= 0L || delivery.units() != units
                || !delivery.requestKey().equals(requestKey(
                requestId, delivery.claimId()))) {
            throw new IllegalArgumentException(
                    "Money claim settlement delivery is invalid");
        }
        if (!ledgerTransaction.transactionId().equals(requestId)
                || !ledgerTransaction.idempotencyKey().equals(
                delivery.requestKey())
                || !ledgerTransaction.reason().equals(LEDGER_REASON)) {
            throw new IllegalArgumentException(
                    "Money claim settlement ledger identity is invalid");
        }
        long debtCredit = debtCreditMinorUnits(
                units, debtBeforeMinorUnits);
        long walletCredit = Math.subtractExact(units, debtCredit);
        Map<LedgerAccountId, Long> expected = new HashMap<>();
        expected.put(new LedgerAccountId(
                LedgerAccountType.PLAYER_CLAIM,
                delivery.claimId().toString()), Math.negateExact(units));
        if (debtCredit > 0L) {
            expected.put(PlayerPaymentCommit.debtAccount(
                    delivery.ownerId()), debtCredit);
        }
        if (walletCredit > 0L) {
            expected.put(PlayerPaymentCommit.walletAccount(
                    delivery.ownerId()), walletCredit);
        }
        Map<LedgerAccountId, Long> actual = new HashMap<>();
        for (LedgerLeg leg : ledgerTransaction.legs()) {
            if (actual.put(leg.account(), leg.deltaMinor()) != null) {
                throw new IllegalArgumentException(
                        "Money claim settlement ledger account is duplicated");
            }
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "Money claim settlement ledger split is invalid");
        }
    }

    private static void requireNonzero(UUID value, String label) {
        if (ZERO_UUID.equals(value)) {
            throw new IllegalArgumentException(
                    "Legacy money claim " + label + " identity is invalid");
        }
    }
}
