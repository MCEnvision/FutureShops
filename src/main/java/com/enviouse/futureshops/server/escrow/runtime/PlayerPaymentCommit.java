package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
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
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record PlayerPaymentCommit(
        UUID requestId,
        UUID payerId,
        UUID recipientId,
        long payerWalletBeforeMinorUnits,
        long payerDebtBeforeMinorUnits,
        long recipientWalletBeforeMinorUnits,
        long recipientDebtBeforeMinorUnits,
        long recipientReservedBeforeMinorUnits,
        long walletBalanceLimitMinorUnits,
        String currencyName,
        int currencyDecimals,
        EscrowTransaction completedTransaction,
        LedgerTransaction ledgerTransaction,
        Optional<EscrowClaim> overflowClaim
) {
    public static final String CURRENCY_ID = "futureshops:wallet";
    public static final String LEDGER_REASON = "Player payment";
    public static final String CLAIM_LABEL = "Player payment overflow";
    public static final int MAX_CURRENCY_NAME_LENGTH = 128;

    static final String ATTRIBUTE_REQUEST_ID = "request_id";
    static final String ATTRIBUTE_PAYER_WALLET = "payer_wallet_before";
    static final String ATTRIBUTE_PAYER_DEBT = "payer_debt_before";
    static final String ATTRIBUTE_RECIPIENT_WALLET =
            "recipient_wallet_before";
    static final String ATTRIBUTE_RECIPIENT_DEBT =
            "recipient_debt_before";
    static final String ATTRIBUTE_RECIPIENT_RESERVED =
            "recipient_reserved_before";
    static final String ATTRIBUTE_WALLET_LIMIT = "wallet_balance_limit";
    static final String ATTRIBUTE_CURRENCY_NAME = "currency_name";
    static final String ATTRIBUTE_CURRENCY_DECIMALS = "currency_decimals";

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public PlayerPaymentCommit(
            UUID requestId,
            UUID payerId,
            UUID recipientId,
            long payerWalletBeforeMinorUnits,
            long payerDebtBeforeMinorUnits,
            long recipientWalletBeforeMinorUnits,
            long recipientDebtBeforeMinorUnits,
            long recipientReservedBeforeMinorUnits,
            long walletBalanceLimitMinorUnits,
            String currencyName,
            int currencyDecimals,
            EscrowTransaction completedTransaction,
            LedgerTransaction ledgerTransaction,
            Optional<EscrowClaim> overflowClaim
    ) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.payerId = Objects.requireNonNull(payerId, "payerId");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.payerWalletBeforeMinorUnits = payerWalletBeforeMinorUnits;
        this.payerDebtBeforeMinorUnits = payerDebtBeforeMinorUnits;
        this.recipientWalletBeforeMinorUnits =
                recipientWalletBeforeMinorUnits;
        this.recipientDebtBeforeMinorUnits = recipientDebtBeforeMinorUnits;
        this.recipientReservedBeforeMinorUnits =
                recipientReservedBeforeMinorUnits;
        this.walletBalanceLimitMinorUnits = walletBalanceLimitMinorUnits;
        this.currencyName = normalizeCurrencyName(currencyName);
        this.currencyDecimals = currencyDecimals;
        this.completedTransaction = Objects.requireNonNull(
                completedTransaction, "completedTransaction");
        this.ledgerTransaction = Objects.requireNonNull(
                ledgerTransaction, "ledgerTransaction");
        this.overflowClaim = Objects.requireNonNull(
                overflowClaim, "overflowClaim");
        PlayerPaymentConservationValidator.validate(this);
    }

    public static PlayerPaymentCommit create(
            UUID requestId,
            UUID payerId,
            UUID recipientId,
            long amountMinorUnits,
            long payerWalletBeforeMinorUnits,
            long payerDebtBeforeMinorUnits,
            long recipientWalletBeforeMinorUnits,
            long recipientDebtBeforeMinorUnits,
            long recipientReservedBeforeMinorUnits,
            long walletBalanceLimitMinorUnits,
            String currencyName,
            int currencyDecimals,
            Instant now
    ) {
        Objects.requireNonNull(now, "now");
        String normalizedCurrencyName = normalizeCurrencyName(currencyName);
        long configRevision = configurationRevision(
                walletBalanceLimitMinorUnits, normalizedCurrencyName,
                currencyDecimals);
        EscrowParty payer = EscrowParty.player(payerId);
        EscrowParty recipient = EscrowParty.player(recipientId);
        Map<String, String> attributes = Map.of(
                ATTRIBUTE_REQUEST_ID, requestId.toString(),
                ATTRIBUTE_PAYER_WALLET,
                Long.toString(payerWalletBeforeMinorUnits),
                ATTRIBUTE_PAYER_DEBT,
                Long.toString(payerDebtBeforeMinorUnits),
                ATTRIBUTE_RECIPIENT_WALLET,
                Long.toString(recipientWalletBeforeMinorUnits),
                ATTRIBUTE_RECIPIENT_DEBT,
                Long.toString(recipientDebtBeforeMinorUnits),
                ATTRIBUTE_RECIPIENT_RESERVED,
                Long.toString(recipientReservedBeforeMinorUnits),
                ATTRIBUTE_WALLET_LIMIT,
                Long.toString(walletBalanceLimitMinorUnits),
                ATTRIBUTE_CURRENCY_NAME, normalizedCurrencyName,
                ATTRIBUTE_CURRENCY_DECIMALS,
                Integer.toString(currencyDecimals));
        EscrowAssetLot asset = new EscrowAssetLot(
                assetLotId(requestId),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                payer,
                recipient,
                1L,
                Optional.of(new MoneyAmount(
                        CURRENCY_ID, amountMinorUnits)),
                new byte[0],
                attributes);
        EscrowTransaction transaction = EscrowTransaction.create(
                        new EscrowTransactionId(requestId),
                        Optional.empty(),
                        new EscrowRequestKey(requestKey(
                                requestId, payerId, recipientId,
                                amountMinorUnits,
                                walletBalanceLimitMinorUnits,
                                normalizedCurrencyName, currencyDecimals,
                                configRevision)),
                        EscrowOperation.PLAYER_PAYMENT,
                        Set.of(
                                new EscrowParticipant(
                                        payer, Set.of(
                                        EscrowParticipantRole.INITIATOR,
                                        EscrowParticipantRole.PAYER)),
                                new EscrowParticipant(
                                        recipient, Set.of(
                                        EscrowParticipantRole.BENEFICIARY,
                                        EscrowParticipantRole.RECIPIENT))),
                        List.of(asset),
                        now,
                        configRevision,
                        Optional.empty())
                .transitionTo(EscrowState.VALIDATED, now)
                .transitionTo(EscrowState.HOLDING, now)
                .transitionTo(EscrowState.HELD, now)
                .transitionTo(EscrowState.COMMIT_DECIDED, now)
                .transitionTo(EscrowState.COMMITTED, now)
                .transitionTo(EscrowState.CLAIMS_CREATED, now)
                .transitionTo(EscrowState.COMPLETED, now);
        long accepted = acceptedMinorUnits(
                amountMinorUnits, recipientWalletBeforeMinorUnits,
                recipientDebtBeforeMinorUnits,
                recipientReservedBeforeMinorUnits,
                walletBalanceLimitMinorUnits);
        long debtCredit = debtCreditMinorUnits(
                accepted, recipientDebtBeforeMinorUnits);
        long walletCredit = Math.subtractExact(accepted, debtCredit);
        long overflow = Math.subtractExact(amountMinorUnits, accepted);
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(walletAccount(payerId),
                Math.negateExact(amountMinorUnits)));
        if (debtCredit > 0L) {
            legs.add(new LedgerLeg(debtAccount(recipientId), debtCredit));
        }
        if (walletCredit > 0L) {
            legs.add(new LedgerLeg(walletAccount(recipientId),
                    walletCredit));
        }
        Optional<EscrowClaim> claim = Optional.empty();
        if (overflow > 0L) {
            UUID claimId = overflowClaimId(requestId);
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM,
                    claimId.toString()), overflow));
            claim = Optional.of(new EscrowClaim(
                    claimId,
                    requestId,
                    recipientId,
                    overflowClaimSourceKey(requestId),
                    ClaimKind.MONEY,
                    overflow,
                    overflow,
                    new byte[0],
                    ClaimStatus.PENDING,
                    CLAIM_LABEL,
                    now,
                    now));
        }
        LedgerTransaction ledger = new LedgerTransaction(
                requestId,
                ledgerIdempotencyKey(requestId),
                LEDGER_REASON,
                legs);
        return new PlayerPaymentCommit(
                requestId, payerId, recipientId,
                payerWalletBeforeMinorUnits,
                payerDebtBeforeMinorUnits,
                recipientWalletBeforeMinorUnits,
                recipientDebtBeforeMinorUnits,
                recipientReservedBeforeMinorUnits,
                walletBalanceLimitMinorUnits,
                normalizedCurrencyName, currencyDecimals,
                transaction, ledger, claim);
    }

    public static PlayerPaymentCommit fromEvidence(
            EscrowTransaction transaction,
            LedgerTransaction ledger,
            List<EscrowClaim> claims
    ) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(ledger, "ledger");
        List<EscrowClaim> exactClaims = List.copyOf(
                Objects.requireNonNull(claims, "claims"));
        if (transaction.assetLots().size() != 1) {
            throw new IllegalArgumentException(
                    "Player payment asset evidence is invalid");
        }
        EscrowAssetLot asset = transaction.assetLots().get(0);
        UUID requestId = transaction.transactionId().value();
        UUID payerId = UUID.fromString(asset.source().id());
        UUID recipientId = UUID.fromString(asset.destination().id());
        Map<String, String> attributes = asset.attributes();
        Optional<EscrowClaim> claim;
        if (exactClaims.isEmpty()) {
            claim = Optional.empty();
        } else if (exactClaims.size() == 1) {
            EscrowClaim evidence = exactClaims.get(0);
            Instant createdAt = transaction.timestamps().terminalAt()
                    .orElseThrow();
            if (!evidence.claimId().equals(overflowClaimId(requestId))
                    || !evidence.transactionId().equals(requestId)
                    || !evidence.ownerId().equals(recipientId)
                    || !evidence.sourceKey().equals(
                    overflowClaimSourceKey(requestId))
                    || evidence.kind() != ClaimKind.MONEY
                    || evidence.originalUnits() <= 0L
                    || evidence.payload().length != 0
                    || evidence.status() == ClaimStatus.QUARANTINED
                    || !evidence.label().equals(CLAIM_LABEL)
                    || !evidence.createdAt().equals(createdAt)) {
                throw new IllegalArgumentException(
                        "Player payment claim evidence is invalid");
            }
            claim = Optional.of(new EscrowClaim(
                    evidence.claimId(), evidence.transactionId(),
                    evidence.ownerId(), evidence.sourceKey(),
                    ClaimKind.MONEY, evidence.originalUnits(),
                    evidence.originalUnits(), new byte[0],
                    ClaimStatus.PENDING, evidence.label(),
                    createdAt, createdAt));
        } else {
            throw new IllegalArgumentException(
                    "Player payment claim evidence is invalid");
        }
        return new PlayerPaymentCommit(
                requestId,
                payerId,
                recipientId,
                parseLong(attributes, ATTRIBUTE_PAYER_WALLET),
                parseLong(attributes, ATTRIBUTE_PAYER_DEBT),
                parseLong(attributes, ATTRIBUTE_RECIPIENT_WALLET),
                parseLong(attributes, ATTRIBUTE_RECIPIENT_DEBT),
                parseLong(attributes, ATTRIBUTE_RECIPIENT_RESERVED),
                parseLong(attributes, ATTRIBUTE_WALLET_LIMIT),
                requireAttribute(attributes, ATTRIBUTE_CURRENCY_NAME),
                parseInt(attributes, ATTRIBUTE_CURRENCY_DECIMALS),
                transaction,
                ledger,
                claim);
    }

    static PlayerPaymentIdentity identityFromTransaction(
            EscrowTransaction transaction
    ) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.operation() != EscrowOperation.PLAYER_PAYMENT
                || transaction.assetLots().size() != 1) {
            throw new IllegalArgumentException(
                    "Player payment immutable identity is invalid");
        }
        EscrowAssetLot asset = transaction.assetLots().get(0);
        Map<String, String> attributes = asset.attributes();
        UUID requestId = UUID.fromString(requireAttribute(
                attributes, ATTRIBUTE_REQUEST_ID));
        UUID payerId = UUID.fromString(asset.source().id());
        UUID recipientId = UUID.fromString(asset.destination().id());
        long amount = asset.money().orElseThrow().minorUnits();
        PlayerPaymentCommit expected = create(
                requestId, payerId, recipientId, amount,
                parseLong(attributes, ATTRIBUTE_PAYER_WALLET),
                parseLong(attributes, ATTRIBUTE_PAYER_DEBT),
                parseLong(attributes, ATTRIBUTE_RECIPIENT_WALLET),
                parseLong(attributes, ATTRIBUTE_RECIPIENT_DEBT),
                parseLong(attributes, ATTRIBUTE_RECIPIENT_RESERVED),
                parseLong(attributes, ATTRIBUTE_WALLET_LIMIT),
                requireAttribute(attributes, ATTRIBUTE_CURRENCY_NAME),
                parseInt(attributes, ATTRIBUTE_CURRENCY_DECIMALS),
                transaction.timestamps().createdAt());
        EscrowTransaction expectedTransaction =
                expected.completedTransaction();
        if (!requestId.equals(transaction.transactionId().value())
                || !transaction.parentTransactionId().equals(
                expectedTransaction.parentTransactionId())
                || !transaction.requestKey().equals(
                expectedTransaction.requestKey())
                || !transaction.participants().equals(
                expectedTransaction.participants())
                || !transaction.assetLots().equals(
                expectedTransaction.assetLots())
                || transaction.configRevision()
                != expectedTransaction.configRevision()
                || !transaction.shopReference().equals(
                expectedTransaction.shopReference())) {
            throw new IllegalArgumentException(
                    "Player payment immutable evidence is invalid");
        }
        return new PlayerPaymentIdentity(
                requestId, payerId, recipientId, amount);
    }

    public UUID transactionId() {
        return completedTransaction.transactionId().value();
    }

    public long amountMinorUnits() {
        return completedTransaction.assetLots().get(0)
                .money().orElseThrow().minorUnits();
    }

    public long acceptedMinorUnits() {
        return acceptedMinorUnits(amountMinorUnits(),
                recipientWalletBeforeMinorUnits,
                recipientDebtBeforeMinorUnits,
                recipientReservedBeforeMinorUnits,
                walletBalanceLimitMinorUnits);
    }

    public long recipientDebtCreditMinorUnits() {
        return debtCreditMinorUnits(
                acceptedMinorUnits(), recipientDebtBeforeMinorUnits);
    }

    public long recipientWalletCreditMinorUnits() {
        return Math.subtractExact(acceptedMinorUnits(),
                recipientDebtCreditMinorUnits());
    }

    public long overflowClaimMinorUnits() {
        return Math.subtractExact(amountMinorUnits(),
                acceptedMinorUnits());
    }

    public long payerBalanceAfterMinorUnits() {
        return Math.subtractExact(payerWalletBeforeMinorUnits,
                amountMinorUnits());
    }

    public long recipientBalanceBeforeMinorUnits() {
        return Math.addExact(recipientWalletBeforeMinorUnits,
                recipientDebtBeforeMinorUnits);
    }

    public long recipientBalanceAfterMinorUnits() {
        return Math.addExact(recipientBalanceBeforeMinorUnits(),
                acceptedMinorUnits());
    }

    public boolean matches(
            UUID expectedPayer,
            UUID expectedRecipient,
            long expectedAmount
    ) {
        return payerId.equals(expectedPayer)
                && recipientId.equals(expectedRecipient)
                && amountMinorUnits() == expectedAmount;
    }

    public String fingerprint() {
        return PlayerPaymentCommitCodec.fingerprint(this);
    }

    public static UUID assetLotId(UUID requestId) {
        return deterministicUuid(
                "futureshops player payment asset v1 ", requestId);
    }

    public static UUID overflowClaimId(UUID requestId) {
        return deterministicUuid(
                "futureshops player payment overflow claim v1 ",
                requestId);
    }

    public static String overflowClaimSourceKey(UUID requestId) {
        return "player.payment." + Objects.requireNonNull(
                requestId, "requestId") + ".overflow";
    }

    public static String ledgerIdempotencyKey(UUID requestId) {
        return "player.payment." + Objects.requireNonNull(
                requestId, "requestId") + ".ledger";
    }

    public static String requestKey(
            UUID requestId,
            UUID payerId,
            UUID recipientId,
            long amountMinorUnits,
            long walletBalanceLimitMinorUnits,
            String currencyName,
            int currencyDecimals,
            long configRevision
    ) {
        String material = "v1," + requestId + "," + payerId + ","
                + recipientId + "," + amountMinorUnits + ","
                + walletBalanceLimitMinorUnits + "," + currencyName + ","
                + currencyDecimals + "," + configRevision;
        return "player.payment." + requestId + "." + sha256(material);
    }

    public static long configurationRevision(
            long walletBalanceLimitMinorUnits,
            String currencyName,
            int currencyDecimals
    ) {
        String material = "v1," + walletBalanceLimitMinorUnits + ","
                + Objects.requireNonNull(currencyName, "currencyName")
                + "," + currencyDecimals;
        byte[] digest = HexFormat.of().parseHex(sha256(material));
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = value << 8 | digest[index] & 255L;
        }
        return value & Long.MAX_VALUE;
    }

    static long acceptedMinorUnits(
            long amountMinorUnits,
            long recipientWalletMinorUnits,
            long recipientDebtMinorUnits,
            long recipientReservedMinorUnits,
            long walletBalanceLimitMinorUnits
    ) {
        long recipientBalance = Math.addExact(
                recipientWalletMinorUnits, recipientDebtMinorUnits);
        long exposure = Math.addExact(
                recipientBalance, recipientReservedMinorUnits);
        if (exposure >= walletBalanceLimitMinorUnits) {
            return 0L;
        }
        long capacity;
        try {
            capacity = Math.subtractExact(
                    walletBalanceLimitMinorUnits, exposure);
        } catch (ArithmeticException exception) {
            capacity = Long.MAX_VALUE;
        }
        return Math.min(amountMinorUnits, capacity);
    }

    static long debtCreditMinorUnits(
            long acceptedMinorUnits,
            long recipientDebtMinorUnits
    ) {
        if (acceptedMinorUnits <= 0L || recipientDebtMinorUnits >= 0L) {
            return 0L;
        }
        long nextDebt = Math.addExact(
                recipientDebtMinorUnits, acceptedMinorUnits);
        return nextDebt <= 0L
                ? acceptedMinorUnits
                : Math.negateExact(recipientDebtMinorUnits);
    }

    static LedgerAccountId walletAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_WALLET,
                Objects.requireNonNull(playerId, "playerId").toString());
    }

    static LedgerAccountId debtAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_DEBT,
                Objects.requireNonNull(playerId, "playerId").toString());
    }

    static LedgerAccountId reservedAccount(UUID playerId) {
        return new LedgerAccountId(LedgerAccountType.PLAYER_RESERVED,
                Objects.requireNonNull(playerId, "playerId").toString());
    }

    static String normalizeCurrencyName(String currencyName) {
        String normalized = Objects.requireNonNull(
                currencyName, "currencyName").trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CURRENCY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Player payment currency name is invalid");
        }
        return normalized;
    }

    static void requireNonzeroUuid(UUID value, String label) {
        if (ZERO_UUID.equals(value)) {
            throw new IllegalArgumentException(label + " cannot be zero");
        }
    }

    private static UUID deterministicUuid(String prefix, UUID requestId) {
        return UUID.nameUUIDFromBytes((prefix + Objects.requireNonNull(
                requestId, "requestId")).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(value.getBytes(
                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static String requireAttribute(
            Map<String, String> attributes,
            String key
    ) {
        String value = attributes.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Player payment evidence attribute is missing");
        }
        return value;
    }

    private static long parseLong(Map<String, String> attributes,
                                  String key) {
        return Long.parseLong(requireAttribute(attributes, key));
    }

    private static int parseInt(Map<String, String> attributes,
                                String key) {
        return Integer.parseInt(requireAttribute(attributes, key));
    }

    record PlayerPaymentIdentity(
            UUID requestId,
            UUID payerId,
            UUID recipientId,
            long amountMinorUnits
    ) {
        PlayerPaymentIdentity {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(payerId, "payerId");
            Objects.requireNonNull(recipientId, "recipientId");
            if (amountMinorUnits <= 0L) {
                throw new IllegalArgumentException(
                        "Player payment identity amount is invalid");
            }
        }

        boolean matches(
                UUID expectedPayer,
                UUID expectedRecipient,
                long expectedAmount
        ) {
            return payerId.equals(expectedPayer)
                    && recipientId.equals(expectedRecipient)
                    && amountMinorUnits == expectedAmount;
        }
    }
}
