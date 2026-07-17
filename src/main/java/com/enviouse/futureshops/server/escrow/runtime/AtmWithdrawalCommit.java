package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AtmWithdrawalCommit(
        UUID playerId,
        EscrowTransaction committedTransaction,
        LedgerTransaction ledgerTransaction,
        List<ProtectedMintJournalEvent> mintIssues,
        List<EscrowClaim> cashClaims
) {
    public static final int MAX_MINT_ISSUES = 64;
    public static final int MAX_CASH_CLAIMS = 4095;
    public static final long MAX_TOTAL_CLAIM_PAYLOAD_BYTES = 2_097_152L;
    public static final String LEDGER_REASON = "ATM withdrawal";
    static final String SYSTEM_PARTY_ID = "protected_currency";

    public AtmWithdrawalCommit {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(committedTransaction, "committedTransaction");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        Objects.requireNonNull(mintIssues, "mintIssues");
        Objects.requireNonNull(cashClaims, "cashClaims");
        if (mintIssues.isEmpty() || mintIssues.size() > MAX_MINT_ISSUES
                || cashClaims.isEmpty() || cashClaims.size() > MAX_CASH_CLAIMS) {
            throw new IllegalArgumentException("ATM withdrawal component count is invalid");
        }
        mintIssues = canonicalIssues(mintIssues);
        cashClaims = canonicalClaims(cashClaims);
        UUID transactionId = committedTransaction.transactionId().value();
        if (committedTransaction.operation() != EscrowOperation.ATM_WITHDRAWAL
                || committedTransaction.state() != EscrowState.COMMIT_DECIDED
                || committedTransaction.shopReference().isPresent()
                || !ledgerTransaction.transactionId().equals(transactionId)) {
            throw new IllegalArgumentException("ATM withdrawal transaction identity is invalid");
        }
        EscrowParty player = requirePlayer(committedTransaction, playerId);
        Map<UUID, ProtectedMintBatch> batches = requireIssues(mintIssues, transactionId);
        long total = issueValue(batches.values());
        requireLedger(ledgerTransaction, transactionId, playerId, total);
        Map<ClaimPortionKey, EscrowClaim> claimsByPortion = requireClaims(
                cashClaims, batches, transactionId, playerId,
                committedTransaction.timestamps().updatedAt());
        requireAssets(committedTransaction, player, claimsByPortion, total);
    }

    public UUID transactionId() {
        return committedTransaction.transactionId().value();
    }

    public long amountMinorUnits() {
        return ledgerTransaction.legs().get(1).deltaMinor();
    }

    public String fingerprint() {
        return AtmWithdrawalCommitCodec.fingerprint(this);
    }

    public static String ledgerIdempotencyKey(UUID transactionId) {
        return "atm.withdrawal." + Objects.requireNonNull(transactionId, "transactionId")
                + ".ledger";
    }

    public static UUID claimId(UUID transactionId, UUID batchId, int portionIndex) {
        if (portionIndex < 0) {
            throw new IllegalArgumentException("ATM withdrawal claim portion is invalid");
        }
        return UUID.nameUUIDFromBytes(("futureshops atm withdrawal claim "
                + Objects.requireNonNull(transactionId, "transactionId") + " "
                + Objects.requireNonNull(batchId, "batchId") + " " + portionIndex)
                .getBytes(StandardCharsets.UTF_8));
    }

    public static String claimSourceKey(UUID transactionId, UUID batchId, int portionIndex) {
        if (portionIndex < 0) {
            throw new IllegalArgumentException("ATM withdrawal claim portion is invalid");
        }
        return "atm.withdrawal." + Objects.requireNonNull(transactionId, "transactionId")
                + ".cash." + Objects.requireNonNull(batchId, "batchId")
                + "." + portionIndex;
    }

    private static List<ProtectedMintJournalEvent> canonicalIssues(
            List<ProtectedMintJournalEvent> values
    ) {
        List<ProtectedMintJournalEvent> ordered = new ArrayList<>(
                Objects.requireNonNull(values, "mintIssues"));
        ordered.forEach(value -> Objects.requireNonNull(value, "mintIssue"));
        ordered.sort(Comparator
                .comparingLong((ProtectedMintJournalEvent value) ->
                        value.batch().orElseThrow().denominationMinorUnits())
                .thenComparing(value -> value.batch().orElseThrow().batchId().toString()));
        return List.copyOf(ordered);
    }

    private static List<EscrowClaim> canonicalClaims(List<EscrowClaim> values) {
        List<EscrowClaim> ordered = new ArrayList<>(
                Objects.requireNonNull(values, "cashClaims"));
        ordered.forEach(value -> Objects.requireNonNull(value, "cashClaim"));
        ordered.sort(Comparator
                .comparing((EscrowClaim value) ->
                        payload(value).batchId().toString())
                .thenComparingInt(value -> payload(value).portionIndex())
                .thenComparing(value -> value.claimId().toString()));
        return List.copyOf(ordered);
    }

    private static EscrowParty requirePlayer(EscrowTransaction transaction, UUID playerId) {
        EscrowParty player = EscrowParty.player(playerId);
        EscrowParty system = EscrowParty.system(SYSTEM_PARTY_ID);
        if (transaction.participants().size() != 2) {
            throw new IllegalArgumentException(
                    "ATM withdrawal participant count is invalid");
        }
        EscrowParticipant foundPlayer = null;
        EscrowParticipant foundSystem = null;
        for (EscrowParticipant participant : transaction.participants()) {
            if (participant.party().equals(player)) {
                foundPlayer = participant;
            } else if (participant.party().equals(system)) {
                foundSystem = participant;
            } else {
                throw new IllegalArgumentException(
                        "ATM withdrawal participant identity is invalid");
            }
        }
        if (foundPlayer == null
                || !foundPlayer.roles().equals(Set.of(
                EscrowParticipantRole.INITIATOR,
                EscrowParticipantRole.PAYER,
                EscrowParticipantRole.RECIPIENT))
                || foundSystem == null
                || !foundSystem.roles().equals(Set.of(
                EscrowParticipantRole.BENEFICIARY,
                EscrowParticipantRole.CUSTODIAN))) {
            throw new IllegalArgumentException("ATM withdrawal participant roles are invalid");
        }
        return player;
    }

    private static Map<UUID, ProtectedMintBatch> requireIssues(
            List<ProtectedMintJournalEvent> issues,
            UUID transactionId
    ) {
        Map<UUID, ProtectedMintBatch> batches = new HashMap<>();
        Set<String> requestKeys = new HashSet<>();
        for (ProtectedMintJournalEvent issue : issues) {
            if (issue.operation() != ProtectedMintOperation.ISSUE
                    || !issue.transactionId().equals(transactionId)) {
                throw new IllegalArgumentException("ATM withdrawal mint issue is invalid");
            }
            ProtectedMintBatch batch = issue.batch().orElseThrow();
            if (batches.put(batch.batchId(), batch) != null
                    || !requestKeys.add(issue.requestKey())) {
                throw new IllegalArgumentException(
                        "ATM withdrawal mint identity is duplicated");
            }
        }
        return Map.copyOf(batches);
    }

    private static long issueValue(java.util.Collection<ProtectedMintBatch> batches) {
        long total = 0L;
        for (ProtectedMintBatch batch : batches) {
            total = Math.addExact(total, Math.multiplyExact(
                    batch.denominationMinorUnits(), (long) batch.authorizedCount()));
        }
        if (total <= 0L) {
            throw new IllegalArgumentException("ATM withdrawal amount is invalid");
        }
        return total;
    }

    private static void requireLedger(LedgerTransaction ledger,
                                      UUID transactionId,
                                      UUID playerId,
                                      long total) {
        if (!ledger.idempotencyKey().equals(ledgerIdempotencyKey(transactionId))
                || !ledger.reason().equals(LEDGER_REASON)
                || ledger.legs().size() != 2) {
            throw new IllegalArgumentException("ATM withdrawal ledger identity is invalid");
        }
        LedgerLeg wallet = ledger.legs().get(0);
        LedgerLeg outstanding = ledger.legs().get(1);
        if (wallet.account().type() != LedgerAccountType.PLAYER_WALLET
                || !wallet.account().ownerKey().equals(playerId.toString())
                || wallet.deltaMinor() != Math.negateExact(total)
                || outstanding.account().type()
                != LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING
                || !outstanding.account().ownerKey().equals("system")
                || outstanding.deltaMinor() != total) {
            throw new IllegalArgumentException("ATM withdrawal ledger legs are invalid");
        }
    }

    private static Map<ClaimPortionKey, EscrowClaim> requireClaims(
            List<EscrowClaim> claims,
            Map<UUID, ProtectedMintBatch> batches,
            UUID transactionId,
            UUID playerId,
            java.time.Instant committedAt
    ) {
        Map<ClaimPortionKey, EscrowClaim> claimsByPortion = new HashMap<>();
        Map<UUID, Integer> countsByBatch = new HashMap<>();
        Map<UUID, Integer> portionsByBatch = new HashMap<>();
        long payloadBytes = 0L;
        for (EscrowClaim claim : claims) {
            ProtectedCashClaimPayload payload = payload(claim);
            byte[] canonicalPayload = ProtectedCashClaimPayloadCodec.encode(payload);
            if (!Arrays.equals(canonicalPayload, claim.payload())
                    || !claim.transactionId().equals(transactionId)
                    || !claim.ownerId().equals(playerId)
                    || claim.kind() != ClaimKind.PROTECTED_CASH
                    || claim.status() != ClaimStatus.PENDING
                    || claim.originalUnits() != payload.billCount()
                    || claim.remainingUnits() != payload.billCount()
                    || !claim.createdAt().equals(committedAt)
                    || !claim.updatedAt().equals(committedAt)
                    || !claim.claimId().equals(claimId(
                    transactionId, payload.batchId(), payload.portionIndex()))
                    || !claim.sourceKey().equals(claimSourceKey(
                    transactionId, payload.batchId(), payload.portionIndex()))) {
                throw new IllegalArgumentException("ATM withdrawal cash claim is invalid");
            }
            ProtectedMintBatch batch = batches.get(payload.batchId());
            if (batch == null
                    || batch.denominationMinorUnits() != payload.denominationMinorUnits()
                    || batch.authorizedCount() != payload.authorizedCount()
                    || !batch.serverIdentityEvidence().equals(
                    payload.serverIdentityEvidence())
                    || !batch.checksumEvidence().equals(payload.checksumEvidence())) {
                throw new IllegalArgumentException(
                        "ATM withdrawal cash claim does not match its mint batch");
            }
            ClaimPortionKey key = ClaimPortionKey.from(payload);
            if (claimsByPortion.put(key, claim) != null) {
                throw new IllegalArgumentException(
                        "ATM withdrawal cash claim portion is duplicated");
            }
            Integer priorPortionCount = portionsByBatch.putIfAbsent(
                    payload.batchId(), payload.portionCount());
            if (priorPortionCount != null
                    && priorPortionCount != payload.portionCount()) {
                throw new IllegalArgumentException(
                        "ATM withdrawal cash claim portion count changed");
            }
            countsByBatch.merge(payload.batchId(), payload.billCount(), Math::addExact);
            payloadBytes = Math.addExact(payloadBytes, claim.payload().length);
        }
        if (payloadBytes > MAX_TOTAL_CLAIM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "ATM withdrawal claim payload total exceeds its limit");
        }
        for (ProtectedMintBatch batch : batches.values()) {
            int portionCount = portionsByBatch.getOrDefault(batch.batchId(), 0);
            if (countsByBatch.getOrDefault(batch.batchId(), 0) != batch.authorizedCount()
                    || portionCount <= 0) {
                throw new IllegalArgumentException(
                        "ATM withdrawal claims do not cover their mint batch");
            }
            for (int index = 0; index < portionCount; index++) {
                if (!claimsByPortion.containsKey(
                        new ClaimPortionKey(batch.batchId(), index))) {
                    throw new IllegalArgumentException(
                            "ATM withdrawal claim portions are incomplete");
                }
            }
            long actualPortions = claimsByPortion.keySet().stream()
                    .filter(value -> value.batchId().equals(batch.batchId())).count();
            if (actualPortions != portionCount) {
                throw new IllegalArgumentException(
                        "ATM withdrawal claim portion count does not match");
            }
        }
        return Map.copyOf(claimsByPortion);
    }

    private static void requireAssets(EscrowTransaction transaction,
                                      EscrowParty player,
                                      Map<ClaimPortionKey, EscrowClaim> claimsByPortion,
                                      long total) {
        List<EscrowAssetLot> lots = transaction.assetLots();
        if (lots.size() != Math.addExact(claimsByPortion.size(), 1)) {
            throw new IllegalArgumentException("ATM withdrawal asset count is invalid");
        }
        EscrowAssetLot wallet = lots.get(0);
        MoneyAmount walletMoney = wallet.money().orElseThrow();
        if (wallet.type() != EscrowAssetLotType.WALLET_MONEY
                || wallet.protectionLevel() != EscrowProtectionLevel.PROTECTED
                || !wallet.source().equals(player)
                || !wallet.destination().equals(EscrowParty.system(SYSTEM_PARTY_ID))
                || wallet.quantity() != 1L
                || walletMoney.minorUnits() != total) {
            throw new IllegalArgumentException("ATM withdrawal wallet asset is invalid");
        }
        EscrowParticipant systemParticipant = transaction.participants().stream()
                .filter(value -> value.party().equals(wallet.destination()))
                .findFirst().orElseThrow();
        if (!systemParticipant.roles().equals(Set.of(
                EscrowParticipantRole.BENEFICIARY,
                EscrowParticipantRole.CUSTODIAN))) {
            throw new IllegalArgumentException("ATM withdrawal system roles are invalid");
        }
        long cashValue = 0L;
        Set<ClaimPortionKey> matched = new HashSet<>();
        List<EscrowClaim> orderedClaims = claimsByPortion.values().stream()
                .sorted(Comparator
                        .comparing((EscrowClaim value) ->
                                payload(value).batchId().toString())
                        .thenComparingInt(value -> payload(value).portionIndex()))
                .toList();
        for (int index = 0; index < orderedClaims.size(); index++) {
            EscrowClaim claim = orderedClaims.get(index);
            ProtectedCashClaimPayload payload = payload(claim);
            EscrowAssetLot lot = lots.get(index + 1);
            MoneyAmount money = lot.money().orElseThrow();
            ClaimPortionKey key = ClaimPortionKey.from(payload);
            if (lot.type() != EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY
                    || lot.protectionLevel() != EscrowProtectionLevel.PROTECTED
                    || !lot.source().equals(wallet.destination())
                    || !lot.destination().equals(player)
                    || lot.quantity() != payload.billCount()
                    || !money.currencyId().equals(walletMoney.currencyId())
                    || money.minorUnits() != payload.denominationMinorUnits()
                    || !Arrays.equals(lot.serializedPayload(), claim.payload())
                    || !matched.add(key)) {
                throw new IllegalArgumentException(
                        "ATM withdrawal protected cash asset is invalid");
            }
            cashValue = Math.addExact(cashValue, Math.multiplyExact(
                    money.minorUnits(), lot.quantity()));
        }
        if (matched.size() != claimsByPortion.size() || cashValue != total) {
            throw new IllegalArgumentException(
                    "ATM withdrawal protected cash assets do not balance");
        }
    }

    private static ProtectedCashClaimPayload payload(EscrowClaim claim) {
        return ProtectedCashClaimPayloadCodec.decode(
                Objects.requireNonNull(claim, "claim").payload());
    }

    private record ClaimPortionKey(UUID batchId, int portionIndex) {
        private ClaimPortionKey {
            Objects.requireNonNull(batchId, "batchId");
            if (portionIndex < 0) {
                throw new IllegalArgumentException("ATM withdrawal claim portion is invalid");
            }
        }

        private static ClaimPortionKey from(ProtectedCashClaimPayload payload) {
            return new ClaimPortionKey(payload.batchId(), payload.portionIndex());
        }
    }
}
