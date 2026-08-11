package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PlayerPaymentConservationValidator {
    private PlayerPaymentConservationValidator() {
    }

    public static void validate(PlayerPaymentCommit commit) {
        PlayerPaymentCommit.requireNonzeroUuid(
                commit.requestId(), "Player payment request ID");
        PlayerPaymentCommit.requireNonzeroUuid(
                commit.payerId(), "Player payment payer ID");
        PlayerPaymentCommit.requireNonzeroUuid(
                commit.recipientId(), "Player payment recipient ID");
        if (commit.payerId().equals(commit.recipientId())) {
            throw new IllegalArgumentException(
                    "Player payment cannot pay the same player");
        }
        requireNormalized(commit.payerWalletBeforeMinorUnits(),
                commit.payerDebtBeforeMinorUnits(), "payer");
        requireNormalized(commit.recipientWalletBeforeMinorUnits(),
                commit.recipientDebtBeforeMinorUnits(), "recipient");
        if (commit.recipientReservedBeforeMinorUnits() < 0L) {
            throw new IllegalArgumentException(
                    "Player payment recipient reservation is invalid");
        }
        if (commit.walletBalanceLimitMinorUnits() < 0L
                || commit.currencyName().isEmpty()
                || commit.currencyName().length()
                > PlayerPaymentCommit.MAX_CURRENCY_NAME_LENGTH
                || commit.currencyDecimals() < 0
                || commit.currencyDecimals() > 6) {
            throw new IllegalArgumentException(
                    "Player payment currency policy is invalid");
        }
        EscrowTransaction transaction = commit.completedTransaction();
        if (!transaction.transactionId().value().equals(commit.requestId())
                || transaction.parentTransactionId().isPresent()
                || transaction.operation() != EscrowOperation.PLAYER_PAYMENT
                || transaction.state() != EscrowState.COMPLETED
                || transaction.shopReference().isPresent()
                || transaction.configRevision()
                != PlayerPaymentCommit.configurationRevision(
                commit.walletBalanceLimitMinorUnits(),
                commit.currencyName(), commit.currencyDecimals())) {
            throw new IllegalArgumentException(
                    "Player payment transaction identity is invalid");
        }
        requireParticipants(commit, transaction);
        long amount = requireAsset(commit, transaction);
        if (amount <= 0L
                || commit.payerDebtBeforeMinorUnits() != 0L
                || commit.payerWalletBeforeMinorUnits() < amount) {
            throw new IllegalArgumentException(
                    "Player payment amount or payer funds are invalid");
        }
        String expectedRequestKey = PlayerPaymentCommit.requestKey(
                commit.requestId(), commit.payerId(), commit.recipientId(),
                amount, commit.walletBalanceLimitMinorUnits(),
                commit.currencyName(), commit.currencyDecimals(),
                transaction.configRevision());
        if (!transaction.requestKey().value().equals(expectedRequestKey)) {
            throw new IllegalArgumentException(
                    "Player payment request key is invalid");
        }
        requireLedgerAndClaim(commit, amount);
    }

    private static void requireParticipants(
            PlayerPaymentCommit commit,
            EscrowTransaction transaction
    ) {
        EscrowParty payer = EscrowParty.player(commit.payerId());
        EscrowParty recipient = EscrowParty.player(commit.recipientId());
        if (transaction.participants().size() != 2) {
            throw new IllegalArgumentException(
                    "Player payment participant count is invalid");
        }
        Map<EscrowParty, Set<EscrowParticipantRole>> roles = new HashMap<>();
        for (EscrowParticipant participant : transaction.participants()) {
            roles.put(participant.party(), participant.roles());
        }
        if (!roles.getOrDefault(payer, Set.of()).equals(Set.of(
                EscrowParticipantRole.INITIATOR,
                EscrowParticipantRole.PAYER))
                || !roles.getOrDefault(recipient, Set.of()).equals(Set.of(
                EscrowParticipantRole.BENEFICIARY,
                EscrowParticipantRole.RECIPIENT))) {
            throw new IllegalArgumentException(
                    "Player payment participant roles are invalid");
        }
    }

    private static long requireAsset(
            PlayerPaymentCommit commit,
            EscrowTransaction transaction
    ) {
        if (transaction.assetLots().size() != 1) {
            throw new IllegalArgumentException(
                    "Player payment asset count is invalid");
        }
        EscrowAssetLot asset = transaction.assetLots().get(0);
        Map<String, String> expectedAttributes = Map.of(
                PlayerPaymentCommit.ATTRIBUTE_REQUEST_ID,
                commit.requestId().toString(),
                PlayerPaymentCommit.ATTRIBUTE_PAYER_WALLET,
                Long.toString(commit.payerWalletBeforeMinorUnits()),
                PlayerPaymentCommit.ATTRIBUTE_PAYER_DEBT,
                Long.toString(commit.payerDebtBeforeMinorUnits()),
                PlayerPaymentCommit.ATTRIBUTE_RECIPIENT_WALLET,
                Long.toString(commit.recipientWalletBeforeMinorUnits()),
                PlayerPaymentCommit.ATTRIBUTE_RECIPIENT_DEBT,
                Long.toString(commit.recipientDebtBeforeMinorUnits()),
                PlayerPaymentCommit.ATTRIBUTE_RECIPIENT_RESERVED,
                Long.toString(commit.recipientReservedBeforeMinorUnits()),
                PlayerPaymentCommit.ATTRIBUTE_WALLET_LIMIT,
                Long.toString(commit.walletBalanceLimitMinorUnits()),
                PlayerPaymentCommit.ATTRIBUTE_CURRENCY_NAME,
                commit.currencyName(),
                PlayerPaymentCommit.ATTRIBUTE_CURRENCY_DECIMALS,
                Integer.toString(commit.currencyDecimals()));
        if (!asset.lotId().equals(PlayerPaymentCommit.assetLotId(
                commit.requestId()))
                || asset.type() != EscrowAssetLotType.WALLET_MONEY
                || asset.protectionLevel()
                != EscrowProtectionLevel.PROTECTED
                || !asset.source().equals(EscrowParty.player(
                commit.payerId()))
                || !asset.destination().equals(EscrowParty.player(
                commit.recipientId()))
                || asset.quantity() != 1L
                || asset.serializedPayload().length != 0
                || !asset.attributes().equals(expectedAttributes)
                || asset.money().isEmpty()
                || !asset.money().orElseThrow().currencyId().equals(
                PlayerPaymentCommit.CURRENCY_ID)) {
            throw new IllegalArgumentException(
                    "Player payment asset is invalid");
        }
        return asset.money().orElseThrow().minorUnits();
    }

    private static void requireLedgerAndClaim(
            PlayerPaymentCommit commit,
            long amount
    ) {
        long accepted = PlayerPaymentCommit.acceptedMinorUnits(
                amount,
                commit.recipientWalletBeforeMinorUnits(),
                commit.recipientDebtBeforeMinorUnits(),
                commit.recipientReservedBeforeMinorUnits(),
                commit.walletBalanceLimitMinorUnits());
        long debtCredit = PlayerPaymentCommit.debtCreditMinorUnits(
                accepted, commit.recipientDebtBeforeMinorUnits());
        long walletCredit = Math.subtractExact(accepted, debtCredit);
        long overflow = Math.subtractExact(amount, accepted);
        if (!commit.ledgerTransaction().transactionId().equals(
                commit.requestId())
                || !commit.ledgerTransaction().idempotencyKey().equals(
                PlayerPaymentCommit.ledgerIdempotencyKey(
                        commit.requestId()))
                || !commit.ledgerTransaction().reason().equals(
                PlayerPaymentCommit.LEDGER_REASON)) {
            throw new IllegalArgumentException(
                    "Player payment ledger identity is invalid");
        }
        Map<LedgerAccountId, Long> actual = new HashMap<>();
        for (LedgerLeg leg : commit.ledgerTransaction().legs()) {
            if (actual.put(leg.account(), leg.deltaMinor()) != null) {
                throw new IllegalArgumentException(
                        "Player payment ledger account is duplicated");
            }
        }
        Map<LedgerAccountId, Long> expected = new HashMap<>();
        expected.put(PlayerPaymentCommit.walletAccount(commit.payerId()),
                Math.negateExact(amount));
        if (debtCredit > 0L) {
            expected.put(PlayerPaymentCommit.debtAccount(
                    commit.recipientId()), debtCredit);
        }
        if (walletCredit > 0L) {
            expected.put(PlayerPaymentCommit.walletAccount(
                    commit.recipientId()), walletCredit);
        }
        if (overflow > 0L) {
            expected.put(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM,
                    PlayerPaymentCommit.overflowClaimId(
                            commit.requestId()).toString()), overflow);
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "Player payment ledger legs are invalid");
        }
        requireClaim(commit, overflow);
        long conserved = 0L;
        conserved = Math.addExact(conserved, debtCredit);
        conserved = Math.addExact(conserved, walletCredit);
        conserved = Math.addExact(conserved, overflow);
        if (conserved != amount) {
            throw new IllegalArgumentException(
                    "Player payment value is not conserved");
        }
    }

    private static void requireClaim(
            PlayerPaymentCommit commit,
            long overflow
    ) {
        Optional<EscrowClaim> optionalClaim = commit.overflowClaim();
        if (overflow == 0L) {
            if (optionalClaim.isPresent()) {
                throw new IllegalArgumentException(
                        "Player payment has an unexpected overflow claim");
            }
            return;
        }
        EscrowClaim claim = optionalClaim.orElseThrow(() ->
                new IllegalArgumentException(
                        "Player payment overflow claim is missing"));
        if (!claim.claimId().equals(
                PlayerPaymentCommit.overflowClaimId(commit.requestId()))
                || !claim.transactionId().equals(commit.requestId())
                || !claim.ownerId().equals(commit.recipientId())
                || !claim.sourceKey().equals(
                PlayerPaymentCommit.overflowClaimSourceKey(
                        commit.requestId()))
                || claim.kind() != ClaimKind.MONEY
                || claim.status() != ClaimStatus.PENDING
                || claim.originalUnits() != overflow
                || claim.remainingUnits() != overflow
                || claim.payload().length != 0
                || !claim.label().equals(PlayerPaymentCommit.CLAIM_LABEL)
                || !claim.createdAt().equals(commit.completedTransaction()
                .timestamps().terminalAt().orElseThrow())
                || !claim.updatedAt().equals(claim.createdAt())) {
            throw new IllegalArgumentException(
                    "Player payment overflow claim is invalid");
        }
    }

    private static void requireNormalized(
            long wallet,
            long debt,
            String label
    ) {
        if (wallet < 0L || debt > 0L || wallet > 0L && debt < 0L) {
            throw new IllegalArgumentException(
                    "Player payment " + label
                            + " wallet evidence is not normalized");
        }
    }
}
