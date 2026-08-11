package com.enviouse.futureshops.server.market.auction.escrow;

import java.util.UUID;

public record AuctionEscrowWalletSnapshot(
        UUID playerId,
        long walletMinor,
        long debtMinor,
        long reservedMinor,
        long walletLimitMinor,
        long configurationGeneration
) {
    public AuctionEscrowWalletSnapshot {
        playerId = AuctionEscrowIds.requireId(playerId, "playerId");
        if (walletMinor < 0L || debtMinor > 0L
                || walletMinor > 0L && debtMinor < 0L
                || reservedMinor < 0L || walletLimitMinor < 0L
                || configurationGeneration < 0L) {
            throw new IllegalArgumentException(
                    "Auction wallet snapshot is invalid");
        }
        Math.addExact(walletMinor, debtMinor);
        Math.addExact(Math.addExact(walletMinor, debtMinor), reservedMinor);
    }

    public void requireAvailable(long amountMinor) {
        if (amountMinor < 0L || walletMinor < amountMinor) {
            throw new IllegalArgumentException(
                    "Auction wallet funds are insufficient");
        }
    }

    public Payout allocatePayout(long amountMinor) {
        if (amountMinor < 0L) {
            throw new IllegalArgumentException(
                    "Auction payout is invalid");
        }
        long balance = Math.addExact(walletMinor, debtMinor);
        long exposure = Math.addExact(balance, reservedMinor);
        long accepted;
        if (exposure >= walletLimitMinor) {
            accepted = 0L;
        } else {
            long capacity;
            try {
                capacity = Math.subtractExact(walletLimitMinor, exposure);
            } catch (ArithmeticException exception) {
                capacity = Long.MAX_VALUE;
            }
            accepted = Math.min(amountMinor, capacity);
        }
        long debtCredit = 0L;
        if (accepted > 0L && debtMinor < 0L) {
            debtCredit = debtMinor == Long.MIN_VALUE
                    ? accepted
                    : Math.min(accepted, Math.negateExact(debtMinor));
        }
        long walletCredit = Math.subtractExact(accepted, debtCredit);
        long overflow = Math.subtractExact(amountMinor, accepted);
        return new Payout(debtCredit, walletCredit, overflow);
    }

    public record Payout(
            long debtCreditMinor,
            long walletCreditMinor,
            long overflowClaimMinor
    ) {
        public Payout {
            if (debtCreditMinor < 0L || walletCreditMinor < 0L
                    || overflowClaimMinor < 0L) {
                throw new IllegalArgumentException(
                        "Auction payout allocation is invalid");
            }
            Math.addExact(Math.addExact(debtCreditMinor,
                    walletCreditMinor), overflowClaimMinor);
        }

        public long totalMinor() {
            return Math.addExact(Math.addExact(debtCreditMinor,
                    walletCreditMinor), overflowClaimMinor);
        }
    }
}
