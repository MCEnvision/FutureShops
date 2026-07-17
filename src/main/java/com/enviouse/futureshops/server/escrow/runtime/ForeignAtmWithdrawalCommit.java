package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
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

public record ForeignAtmWithdrawalCommit(
        UUID requestId,
        UUID playerId,
        EscrowTransaction committedTransaction,
        LedgerTransaction ledgerTransaction,
        List<EscrowClaim> cashClaims
) {
    public static final int MAX_CASH_CLAIMS = 4095;
    public static final int MAX_TOTAL_STACK_COUNT = 4096;
    public static final long MAX_TOTAL_CLAIM_PAYLOAD_BYTES = 2_097_152L;
    public static final String LEDGER_REASON = "Foreign ATM withdrawal";
    public static final String FOREIGN_CURRENCY_SYSTEM_PARTY_ID =
            "foreign_currency";

    public ForeignAtmWithdrawalCommit {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(committedTransaction, "committedTransaction");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        Objects.requireNonNull(cashClaims, "cashClaims");
        if (cashClaims.isEmpty() || cashClaims.size() > MAX_CASH_CLAIMS) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal claim count is invalid");
        }
        cashClaims = canonicalClaims(cashClaims);
        if (!committedTransaction.transactionId().value().equals(requestId)
                || !ledgerTransaction.transactionId().equals(requestId)
                || !committedTransaction.requestKey().value().equals(
                requestKey(requestId))
                || committedTransaction.parentTransactionId().isPresent()
                || committedTransaction.operation() != EscrowOperation.ATM_WITHDRAWAL
                || committedTransaction.state() != EscrowState.COMMIT_DECIDED
                || committedTransaction.shopReference().isPresent()) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal request identity is invalid");
        }
        EscrowParty player = requireParticipants(
                committedTransaction, playerId);
        ClaimsValidation claims = requireClaims(cashClaims, requestId,
                playerId, committedTransaction.timestamps().updatedAt());
        requireLedger(ledgerTransaction, requestId, playerId,
                claims.totalMinorUnits());
        requireAssets(committedTransaction, requestId, player,
                claims.claimsByPortion(), claims.totalMinorUnits(),
                requestAttributes(requestId, playerId,
                        committedTransaction, cashClaims));
    }

    public long amountMinorUnits() {
        return ledgerTransaction.legs().get(1).deltaMinor();
    }

    public String providerId() {
        return payload(cashClaims.get(0)).providerId();
    }

    public String configSignature() {
        return payload(cashClaims.get(0)).configSignature();
    }

    public String fingerprint() {
        return ForeignAtmWithdrawalCommitCodec.fingerprint(this);
    }

    public static String requestKey(UUID requestId) {
        return "foreign.atm.withdrawal."
                + Objects.requireNonNull(requestId, "requestId");
    }

    public static String ledgerIdempotencyKey(UUID requestId) {
        return requestKey(requestId) + ".ledger";
    }

    public static UUID claimId(UUID requestId, int denominationIndex,
                               int portionIndex) {
        requirePortion(denominationIndex, portionIndex);
        return deterministicId("claim", requestId, denominationIndex,
                portionIndex);
    }

    public static String claimSourceKey(UUID requestId,
                                        int denominationIndex,
                                        int portionIndex) {
        requirePortion(denominationIndex, portionIndex);
        return requestKey(requestId) + ".cash."
                + denominationIndex + "." + portionIndex;
    }

    public static UUID walletAssetLotId(UUID requestId) {
        return UUID.nameUUIDFromBytes(("futureshops foreign atm wallet v1 "
                + Objects.requireNonNull(requestId, "requestId"))
                .getBytes(StandardCharsets.UTF_8));
    }

    public static UUID cashAssetLotId(UUID requestId,
                                      int denominationIndex,
                                      int portionIndex) {
        requirePortion(denominationIndex, portionIndex);
        return deterministicId("asset", requestId, denominationIndex,
                portionIndex);
    }

    private static List<EscrowClaim> canonicalClaims(
            List<EscrowClaim> values) {
        List<EscrowClaim> ordered = new ArrayList<>(
                Objects.requireNonNull(values, "cashClaims"));
        ordered.forEach(value -> Objects.requireNonNull(value, "cashClaim"));
        ordered.sort(Comparator
                .comparingInt((EscrowClaim value) ->
                        payload(value).denominationIndex())
                .thenComparingInt(value -> payload(value).portionIndex())
                .thenComparing(value -> payload(value).registryItemId())
                .thenComparing(value -> value.claimId().toString()));
        return List.copyOf(ordered);
    }

    private static EscrowParty requireParticipants(
            EscrowTransaction transaction,
            UUID playerId
    ) {
        EscrowParty player = EscrowParty.player(playerId);
        EscrowParty system = EscrowParty.system(
                FOREIGN_CURRENCY_SYSTEM_PARTY_ID);
        if (transaction.participants().size() != 2) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal participant count is invalid");
        }
        EscrowParticipant playerParticipant = null;
        EscrowParticipant systemParticipant = null;
        for (EscrowParticipant participant : transaction.participants()) {
            if (participant.party().equals(player)) {
                playerParticipant = participant;
            } else if (participant.party().equals(system)) {
                systemParticipant = participant;
            } else {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal participant identity is invalid");
            }
        }
        if (playerParticipant == null
                || !playerParticipant.roles().equals(Set.of(
                EscrowParticipantRole.INITIATOR,
                EscrowParticipantRole.PAYER,
                EscrowParticipantRole.RECIPIENT))
                || systemParticipant == null
                || !systemParticipant.roles().equals(Set.of(
                EscrowParticipantRole.BENEFICIARY,
                EscrowParticipantRole.CUSTODIAN))) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal participant roles are invalid");
        }
        return player;
    }

    private static ClaimsValidation requireClaims(
            List<EscrowClaim> claims,
            UUID requestId,
            UUID playerId,
            java.time.Instant committedAt
    ) {
        Map<ClaimPortionKey, EscrowClaim> claimsByPortion = new HashMap<>();
        Map<Integer, DenominationDefinition> denominations = new HashMap<>();
        Map<String, Integer> itemIndexes = new HashMap<>();
        String providerId = null;
        String configSignature = null;
        long totalMinorUnits = 0L;
        long payloadBytes = 0L;
        int totalStackCount = 0;
        for (EscrowClaim claim : claims) {
            ForeignCashClaimPayload payload = payload(claim);
            byte[] canonicalPayload = ForeignCashClaimPayloadCodec.encode(payload);
            long claimUnits = Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.stackCount());
            if (!Arrays.equals(canonicalPayload, claim.payload())
                    || !claim.transactionId().equals(requestId)
                    || !claim.ownerId().equals(playerId)
                    || claim.kind() != ClaimKind.FOREIGN_CASH
                    || claim.status() != ClaimStatus.PENDING
                    || claim.originalUnits() != claimUnits
                    || claim.remainingUnits() != claimUnits
                    || !claim.createdAt().equals(committedAt)
                    || !claim.updatedAt().equals(committedAt)
                    || !claim.claimId().equals(claimId(requestId,
                    payload.denominationIndex(), payload.portionIndex()))
                    || !claim.sourceKey().equals(claimSourceKey(requestId,
                    payload.denominationIndex(), payload.portionIndex()))) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal cash claim is invalid");
            }
            if (providerId == null) {
                providerId = payload.providerId();
                configSignature = payload.configSignature();
            } else if (!providerId.equals(payload.providerId())
                    || !configSignature.equals(payload.configSignature())) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal config identity changed");
            }
            ClaimPortionKey key = ClaimPortionKey.from(payload);
            if (claimsByPortion.put(key, claim) != null) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal cash claim portion is duplicated");
            }
            DenominationDefinition definition = DenominationDefinition.from(payload);
            DenominationDefinition prior = denominations.putIfAbsent(
                    payload.denominationIndex(), definition);
            if (prior != null && !prior.equals(definition)) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal denomination changed");
            }
            Integer priorItemIndex = itemIndexes.putIfAbsent(
                    payload.registryItemId(), payload.denominationIndex());
            if (priorItemIndex != null
                    && priorItemIndex != payload.denominationIndex()) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal item has multiple denominations");
            }
            totalStackCount = Math.addExact(
                    totalStackCount, payload.stackCount());
            if (totalStackCount > MAX_TOTAL_STACK_COUNT) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal stack count exceeds its limit");
            }
            payloadBytes = Math.addExact(payloadBytes, claim.payload().length);
            if (payloadBytes > MAX_TOTAL_CLAIM_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal claim payload total exceeds its limit");
            }
            totalMinorUnits = Math.addExact(totalMinorUnits,
                    Math.multiplyExact(payload.denominationMinorUnits(),
                            (long) payload.stackCount()));
        }
        for (Map.Entry<Integer, DenominationDefinition> entry
                : denominations.entrySet()) {
            int matching = 0;
            for (int portionIndex = 0;
                 portionIndex < entry.getValue().portionCount();
                 portionIndex++) {
                if (!claimsByPortion.containsKey(new ClaimPortionKey(
                        entry.getKey(), portionIndex))) {
                    throw new IllegalArgumentException(
                            "Foreign ATM withdrawal claim portions are incomplete");
                }
                matching++;
            }
            int actual = 0;
            for (ClaimPortionKey key : claimsByPortion.keySet()) {
                if (key.denominationIndex() == entry.getKey()) {
                    actual++;
                }
            }
            if (actual != matching) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal claim portion count does not match");
            }
        }
        if (providerId == null || configSignature == null
                || totalMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal claims are invalid");
        }
        return new ClaimsValidation(Map.copyOf(claimsByPortion),
                totalMinorUnits, providerId, configSignature);
    }

    private static void requireLedger(LedgerTransaction ledger,
                                      UUID requestId,
                                      UUID playerId,
                                      long totalMinorUnits) {
        if (!ledger.idempotencyKey().equals(
                ledgerIdempotencyKey(requestId))
                || !ledger.reason().equals(LEDGER_REASON)
                || ledger.legs().size() != 2) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal ledger identity is invalid");
        }
        LedgerLeg wallet = ledger.legs().get(0);
        LedgerLeg sink = ledger.legs().get(1);
        if (wallet.account().type() != LedgerAccountType.PLAYER_WALLET
                || !wallet.account().ownerKey().equals(playerId.toString())
                || wallet.deltaMinor() != Math.negateExact(totalMinorUnits)
                || sink.account().type()
                != LedgerAccountType.FOREIGN_CURRENCY_SINK
                || !sink.account().ownerKey().equals("system")
                || sink.deltaMinor() != totalMinorUnits) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal ledger legs are invalid");
        }
    }

    private static void requireAssets(
            EscrowTransaction transaction,
            UUID requestId,
            EscrowParty player,
            Map<ClaimPortionKey, EscrowClaim> claimsByPortion,
            long totalMinorUnits,
            Map<String, String> requestAttributes
    ) {
        List<EscrowAssetLot> lots = transaction.assetLots();
        if (lots.size() != Math.addExact(claimsByPortion.size(), 1)) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal asset count is invalid");
        }
        EscrowParty system = EscrowParty.system(
                FOREIGN_CURRENCY_SYSTEM_PARTY_ID);
        EscrowAssetLot wallet = lots.get(0);
        MoneyAmount walletMoney = wallet.money().orElseThrow();
        if (!wallet.lotId().equals(walletAssetLotId(requestId))
                || wallet.type() != EscrowAssetLotType.WALLET_MONEY
                || wallet.protectionLevel() != EscrowProtectionLevel.PROTECTED
                || !wallet.source().equals(player)
                || !wallet.destination().equals(system)
                || wallet.quantity() != 1L
                || walletMoney.minorUnits() != totalMinorUnits
                || !wallet.attributes().equals(requestAttributes)) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal wallet asset is invalid");
        }
        List<EscrowClaim> orderedClaims = claimsByPortion.values().stream()
                .sorted(Comparator
                        .comparingInt((EscrowClaim value) ->
                                payload(value).denominationIndex())
                        .thenComparingInt(value ->
                                payload(value).portionIndex()))
                .toList();
        Set<ClaimPortionKey> matched = new HashSet<>();
        long cashValue = 0L;
        for (int index = 0; index < orderedClaims.size(); index++) {
            EscrowClaim claim = orderedClaims.get(index);
            ForeignCashClaimPayload payload = payload(claim);
            EscrowAssetLot lot = lots.get(index + 1);
            MoneyAmount money = lot.money().orElseThrow();
            ClaimPortionKey key = ClaimPortionKey.from(payload);
            if (!lot.lotId().equals(cashAssetLotId(requestId,
                    payload.denominationIndex(), payload.portionIndex()))
                    || lot.type()
                    != EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY
                    || lot.protectionLevel() != EscrowProtectionLevel.EXTERNAL
                    || !lot.source().equals(system)
                    || !lot.destination().equals(player)
                    || lot.quantity() != payload.stackCount()
                    || !money.currencyId().equals(walletMoney.currencyId())
                    || money.minorUnits()
                    != payload.denominationMinorUnits()
                    || !Arrays.equals(lot.serializedPayload(),
                    payload.serializedItemStackNbt())
                    || !lot.attributes().isEmpty()
                    || !matched.add(key)) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal cash asset is invalid");
            }
            cashValue = Math.addExact(cashValue,
                    Math.multiplyExact(money.minorUnits(), lot.quantity()));
        }
        if (matched.size() != claimsByPortion.size()
                || cashValue != totalMinorUnits) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal cash assets do not balance");
        }
    }

    private static ForeignCashClaimPayload payload(EscrowClaim claim) {
        return ForeignCashClaimPayloadCodec.decode(
                Objects.requireNonNull(claim, "claim").payload());
    }

    private static Map<String, String> requestAttributes(
            UUID requestId,
            UUID playerId,
            EscrowTransaction transaction,
            List<EscrowClaim> claims
    ) {
        List<ForeignAtmStackSelection> stacks = claims.stream()
                .map(ForeignAtmWithdrawalCommit::payload)
                .map(payload -> new ForeignAtmStackSelection(
                        payload.denominationIndex(),
                        payload.registryItemId(),
                        payload.denominationMinorUnits(),
                        payload.stackCount(),
                        payload.portionIndex(),
                        payload.portionCount(),
                        payload.serializedItemStackNbt()))
                .toList();
        ForeignCashClaimPayload first = payload(claims.get(0));
        ForeignAtmWithdrawalRequest request =
                new ForeignAtmWithdrawalRequest(
                        requestId, playerId, first.providerId(),
                        first.configSignature(), stacks,
                        transaction.timestamps().createdAt());
        return Map.of(
                ProtectedAtmWithdrawalPlan.REQUEST_FINGERPRINT_ATTRIBUTE,
                request.fingerprint(),
                ProtectedAtmWithdrawalPlan.PROVIDER_ATTRIBUTE,
                request.providerId(),
                ProtectedAtmWithdrawalPlan.SIGNATURE_ATTRIBUTE,
                request.currencySignature(),
                ProtectedAtmWithdrawalPlan.SELECTION_SHAPE_ATTRIBUTE,
                AtmRequestSemantics.foreignShape(request.stacks()));
    }

    private static void requirePortion(int denominationIndex,
                                       int portionIndex) {
        if (denominationIndex < 0
                || denominationIndex
                >= ForeignCashClaimPayload.MAX_DENOMINATIONS
                || portionIndex < 0
                || portionIndex >= ForeignCashClaimPayload.MAX_PORTIONS) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal claim portion is invalid");
        }
    }

    private static UUID deterministicId(String type, UUID requestId,
                                        int denominationIndex,
                                        int portionIndex) {
        return UUID.nameUUIDFromBytes(("futureshops foreign atm " + type
                + " v1 " + Objects.requireNonNull(requestId, "requestId")
                + " " + denominationIndex + " " + portionIndex)
                .getBytes(StandardCharsets.UTF_8));
    }

    private record ClaimPortionKey(int denominationIndex,
                                   int portionIndex) {
        private ClaimPortionKey {
            requirePortion(denominationIndex, portionIndex);
        }

        private static ClaimPortionKey from(
                ForeignCashClaimPayload payload) {
            return new ClaimPortionKey(payload.denominationIndex(),
                    payload.portionIndex());
        }
    }

    private record DenominationDefinition(String registryItemId,
                                          long denominationMinorUnits,
                                          int portionCount) {
        private static DenominationDefinition from(
                ForeignCashClaimPayload payload) {
            return new DenominationDefinition(payload.registryItemId(),
                    payload.denominationMinorUnits(), payload.portionCount());
        }
    }

    private record ClaimsValidation(
            Map<ClaimPortionKey, EscrowClaim> claimsByPortion,
            long totalMinorUnits,
            String providerId,
            String configSignature
    ) {
    }
}
