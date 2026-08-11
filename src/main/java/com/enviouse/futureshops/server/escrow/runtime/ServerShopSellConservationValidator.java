package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ServerShopSellConservationValidator {
    private ServerShopSellConservationValidator() {
    }

    public static void validate(ServerShopSellCommit commit) {
        Objects.requireNonNull(commit, "commit");
        validate(new ServerShopSellCommit.ServerShopSellCommitView(
                commit.requestId(), commit.playerId(), commit.shopId(),
                commit.listingId(), commit.itemId(), commit.quantity(),
                commit.unitPriceMinorUnits(), commit.quoteRevision(),
                commit.expectedStockRevision(), commit.quoteCreatedAt(),
                commit.walletBeforeMinorUnits(),
                commit.debtBeforeMinorUnits(),
                commit.reservedBeforeMinorUnits(),
                commit.walletBalanceLimitMinorUnits(),
                commit.configurationGeneration(), commit.currencyName(),
                commit.currencyDecimals(), commit.exactItemTemplate(),
                commit.itemCustodyReceipt(),
                commit.completedTransaction(),
                commit.ledgerTransaction(), commit.stockReservation(),
                commit.stockCommit(), commit.overflowClaim()));
    }

    static void validate(
            ServerShopSellCommit.ServerShopSellCommitView commit
    ) {
        Objects.requireNonNull(commit, "commit");
        ServerShopSellCommit.CanonicalInput input =
                commit.canonicalInput();
        requireCustody(commit);
        ServerShopSellCommit.CanonicalComponents expected =
                ServerShopSellCommit.canonical(input);
        if (!expected.transaction().equals(
                commit.completedTransaction())
                || !expected.ledger().equals(
                commit.ledgerTransaction())
                || !expected.reserve().equals(
                commit.stockReservation())
                || !expected.commit().equals(commit.stockCommit())
                || !expected.claim().equals(commit.overflowClaim())) {
            throw new IllegalArgumentException(
                    "Server shop sell commit evidence conflicts");
        }
        requireTransactionShape(commit);
        requireStockShape(commit);
        requirePayoutConservation(commit);
    }

    private static void requireCustody(
            ServerShopSellCommit.ServerShopSellCommitView commit
    ) {
        ItemInventoryMutationReceipt receipt =
                commit.itemCustodyReceipt();
        ItemInventoryMutationToken token = receipt.token();
        byte[] expectedBatch = ItemInventoryBatchPlanner.fingerprint(
                ServerShopSellCommit.custodyEntries(commit.requestId(),
                        commit.quantity(), commit.exactItemTemplate()));
        if (!token.playerId().equals(commit.playerId())
                || !token.transactionId().equals(commit.requestId())
                || !token.requestId().equals(
                ServerShopSellCommit.itemCustodyRequestId(
                        commit.requestId()))
                || token.direction()
                != ItemInventoryMutationDirection.EXTRACT
                || !MessageDigest.isEqual(
                token.batchFingerprint(), expectedBatch)) {
            throw new IllegalArgumentException(
                    "Server shop sell item custody identity is invalid");
        }
        int total = 0;
        for (ItemInventoryAllocation portion
                : receipt.actualPortions()) {
            if (!portion.entryId().equals(
                    ServerShopSellCommit.itemEntryId(commit.requestId()))
                    || !ServerShopSellCommit.exactPortionMatches(
                    commit.exactItemTemplate(), portion)) {
                throw new IllegalArgumentException(
                        "Server shop sell item custody is not exact");
            }
            total = Math.addExact(total, portion.count());
        }
        if (total != commit.quantity()) {
            throw new IllegalArgumentException(
                    "Server shop sell item custody quantity is invalid");
        }
    }

    private static void requireTransactionShape(
            ServerShopSellCommit.ServerShopSellCommitView commit
    ) {
        if (!commit.completedTransaction().transactionId().value().equals(
                commit.requestId())
                || commit.completedTransaction()
                .parentTransactionId().isPresent()
                || commit.completedTransaction().operation()
                != EscrowOperation.SERVER_SHOP_SELL
                || commit.completedTransaction().state()
                != EscrowState.COMPLETED
                || commit.completedTransaction().configRevision()
                != commit.quoteRevision()
                || commit.completedTransaction().shopReference().isEmpty()
                || !commit.completedTransaction().shopReference()
                .orElseThrow().shopId().equals(commit.shopId())) {
            throw new IllegalArgumentException(
                    "Server shop sell parent transaction is invalid");
        }
    }

    private static void requireStockShape(
            ServerShopSellCommit.ServerShopSellCommitView commit
    ) {
        StockKey key = new StockKey(commit.shopId(), commit.listingId());
        if (commit.stockReservation().reservations().size() != 1
                || commit.stockCommit().reservations().size() != 1
                || !commit.stockReservation().requestId().equals(
                ServerShopSellCommit.stockReserveRequestId(
                        commit.requestId()))
                || !commit.stockCommit().requestId().equals(
                ServerShopSellCommit.stockCommitRequestId(
                        commit.requestId()))
                || !commit.stockReservation().transactionId().equals(
                commit.requestId())
                || !commit.stockCommit().transactionId().equals(
                commit.requestId())
                || commit.stockCommit().operation()
                != StockMutationType.COMMIT_BATCH
                || !commit.stockReservation().reservations().get(0)
                .stockKey().equals(key)
                || commit.stockReservation().reservations().get(0)
                .direction() != StockReservationDirection.INBOUND
                || commit.stockReservation().reservations().get(0)
                .quantity() != commit.quantity()
                || commit.stockReservation().reservations().get(0)
                .expectedListingRevision()
                != commit.expectedStockRevision()
                || !commit.stockCommit().reservations().get(0)
                .reservationId().equals(
                StockReservationId.forTransaction(commit.requestId(), key,
                        StockReservationDirection.INBOUND))
                || commit.stockCommit().reservations().get(0)
                .expectedReservationRevision() != 0L) {
            throw new IllegalArgumentException(
                    "Server shop sell stock evidence is invalid");
        }
    }

    private static void requirePayoutConservation(
            ServerShopSellCommit.ServerShopSellCommitView commit
    ) {
        long payout = Math.multiplyExact(
                commit.unitPriceMinorUnits(), commit.quantity());
        long accepted = ServerShopSellCommit.acceptedMinorUnits(payout,
                commit.walletBeforeMinorUnits(),
                commit.debtBeforeMinorUnits(),
                commit.reservedBeforeMinorUnits(),
                commit.walletBalanceLimitMinorUnits());
        long debtCredit = ServerShopSellCommit.debtCreditMinorUnits(
                accepted, commit.debtBeforeMinorUnits());
        long walletCredit = Math.subtractExact(accepted, debtCredit);
        long overflow = Math.subtractExact(payout, accepted);
        Map<LedgerAccountId, Long> actual = new HashMap<>();
        for (LedgerLeg leg : commit.ledgerTransaction().legs()) {
            if (actual.put(leg.account(), leg.deltaMinor()) != null) {
                throw new IllegalArgumentException(
                        "Server shop sell ledger account is duplicated");
            }
        }
        Map<LedgerAccountId, Long> expected = new HashMap<>();
        expected.put(ServerShopSellCommit.sourceAccount(commit.shopId()),
                Math.negateExact(payout));
        if (debtCredit > 0L) {
            expected.put(ServerShopSellCommit.debtAccount(
                    commit.playerId()), debtCredit);
        }
        if (walletCredit > 0L) {
            expected.put(ServerShopSellCommit.walletAccount(
                    commit.playerId()), walletCredit);
        }
        if (overflow > 0L) {
            expected.put(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM,
                    ServerShopSellCommit.overflowClaimId(
                            commit.requestId()).toString()), overflow);
        }
        if (!actual.equals(expected)
                || !commit.ledgerTransaction().transactionId().equals(
                commit.requestId())
                || !commit.ledgerTransaction().idempotencyKey().equals(
                ServerShopSellCommit.ledgerIdempotencyKey(
                        commit.requestId()))
                || !commit.ledgerTransaction().reason().equals(
                ServerShopSellCommit.LEDGER_REASON)) {
            throw new IllegalArgumentException(
                    "Server shop sell payout ledger is invalid");
        }
        long conserved = Math.addExact(debtCredit, walletCredit);
        conserved = Math.addExact(conserved, overflow);
        if (conserved != payout) {
            throw new IllegalArgumentException(
                    "Server shop sell payout is not conserved");
        }
        requireClaim(commit, overflow);
    }

    private static void requireClaim(
            ServerShopSellCommit.ServerShopSellCommitView commit,
            long overflow
    ) {
        Optional<EscrowClaim> optional = commit.overflowClaim();
        if (overflow == 0L) {
            if (optional.isPresent()) {
                throw new IllegalArgumentException(
                        "Server shop sell has an unexpected claim");
            }
            return;
        }
        EscrowClaim claim = optional.orElseThrow(() ->
                new IllegalArgumentException(
                        "Server shop sell overflow claim is missing"));
        if (!claim.claimId().equals(
                ServerShopSellCommit.overflowClaimId(commit.requestId()))
                || !claim.transactionId().equals(commit.requestId())
                || !claim.ownerId().equals(commit.playerId())
                || !claim.sourceKey().equals(
                ServerShopSellCommit.overflowClaimSourceKey(
                        commit.requestId()))
                || claim.kind() != ClaimKind.MONEY
                || claim.status() != ClaimStatus.PENDING
                || claim.originalUnits() != overflow
                || claim.remainingUnits() != overflow
                || claim.payload().length != 0
                || !claim.label().equals(ServerShopSellCommit.CLAIM_LABEL)
                || !claim.createdAt().equals(
                commit.itemCustodyReceipt().appliedAt())
                || !claim.updatedAt().equals(claim.createdAt())) {
            throw new IllegalArgumentException(
                    "Server shop sell overflow claim is invalid");
        }
    }
}
