package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record AtmWithdrawalOutcome(
        UUID requestId,
        AtmWithdrawalStatus status,
        boolean retryable,
        boolean replayed,
        boolean balanceKnown,
        long balanceMinorUnits,
        long amountMinorUnits,
        int deliveredBillCount,
        int claimedBillCount,
        String currencySignature,
        long retryAfterMillis
) {
    public static final long MAX_RETRY_AFTER_MILLIS = 3_600_000_000L;

    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

    public AtmWithdrawalOutcome {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        boolean rateLimited = status == AtmWithdrawalStatus.RATE_LIMITED;
        if (requestId.equals(new UUID(0L, 0L))
                || !SIGNATURE.matcher(currencySignature).matches()
                || !balanceKnown && balanceMinorUnits != 0L
                || amountMinorUnits < 0L
                || deliveredBillCount < 0
                || claimedBillCount < 0
                || retryAfterMillis < 0L
                || retryAfterMillis > MAX_RETRY_AFTER_MILLIS
                || retryAfterMillis > 0L && !retryable
                || rateLimited != (retryAfterMillis > 0L)
                || Math.addExact(deliveredBillCount, claimedBillCount)
                > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS
                || status.success() && retryable
                || status.success() && amountMinorUnits <= 0L
                || status == AtmWithdrawalStatus.DELIVERED
                && (deliveredBillCount <= 0 || claimedBillCount != 0)
                || status == AtmWithdrawalStatus.CLAIMED
                && (deliveredBillCount != 0 || claimedBillCount <= 0)
                || status == AtmWithdrawalStatus.PARTIALLY_DELIVERED
                && (deliveredBillCount <= 0 || claimedBillCount <= 0)) {
            throw new IllegalArgumentException(
                    "ATM withdrawal outcome is invalid");
        }
    }

    public AtmWithdrawalOutcome(
            UUID requestId,
            AtmWithdrawalStatus status,
            boolean retryable,
            boolean replayed,
            boolean balanceKnown,
            long balanceMinorUnits,
            long amountMinorUnits,
            int deliveredBillCount,
            int claimedBillCount,
            String currencySignature
    ) {
        this(requestId, status, retryable, replayed, balanceKnown,
                balanceMinorUnits, amountMinorUnits, deliveredBillCount,
                claimedBillCount, currencySignature, 0L);
    }

    public static AtmWithdrawalOutcome failure(
            UUID requestId,
            AtmWithdrawalStatus status,
            boolean retryable,
            boolean replayed,
            boolean balanceKnown,
            long balanceMinorUnits,
            long amountMinorUnits,
            int claimedBillCount,
            String currencySignature
    ) {
        return failure(requestId, status, retryable, replayed,
                balanceKnown, balanceMinorUnits, amountMinorUnits,
                claimedBillCount, currencySignature, 0L);
    }

    public static AtmWithdrawalOutcome failure(
            UUID requestId,
            AtmWithdrawalStatus status,
            boolean retryable,
            boolean replayed,
            boolean balanceKnown,
            long balanceMinorUnits,
            long amountMinorUnits,
            int claimedBillCount,
            String currencySignature,
            long retryAfterMillis
    ) {
        if (status.success()) {
            throw new IllegalArgumentException(
                    "ATM withdrawal failure status is invalid");
        }
        return new AtmWithdrawalOutcome(
                requestId, status, retryable, replayed,
                balanceKnown, balanceMinorUnits, amountMinorUnits,
                0, claimedBillCount, currencySignature, retryAfterMillis);
    }
}
