package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlayerShopEscrowIntent(
        UUID requestId,
        UUID actorId,
        UUID ownerId,
        PlayerShopIdentity shopIdentity,
        PlayerShopOperation operation,
        PlayerShopTradeMethod tradeMethod,
        PlayerShopPaymentSource paymentSource,
        int requestedUnits,
        Instant quoteCreatedAt,
        PlayerShopListingSnapshot listing,
        List<PlayerShopMoneyTransfer> moneyTransfers,
        List<PlayerShopItemTransfer> itemTransfers,
        List<PlayerShopClaimPlan> claims,
        List<PlayerShopStorageMutationPlan> storageMutations,
        Status status,
        long revision,
        String intentFingerprint
) {
    public PlayerShopEscrowIntent {
        requestId = PlayerShopBinarySupport.requireUuid(requestId, "request id");
        actorId = PlayerShopBinarySupport.requireUuid(actorId, "actor id");
        ownerId = PlayerShopBinarySupport.requireUuid(ownerId, "owner id");
        shopIdentity = Objects.requireNonNull(shopIdentity, "shopIdentity");
        if (!ownerId.equals(shopIdentity.ownerId())) {
            throw new IllegalArgumentException("Player shop intent owner is invalid");
        }
        operation = Objects.requireNonNull(operation, "operation");
        tradeMethod = Objects.requireNonNull(tradeMethod, "tradeMethod");
        paymentSource = Objects.requireNonNull(paymentSource, "paymentSource");
        if (requestedUnits <= 0) {
            throw new IllegalArgumentException("Player shop requested units are invalid");
        }
        quoteCreatedAt = Objects.requireNonNull(quoteCreatedAt, "quoteCreatedAt");
        moneyTransfers = boundedCopy(moneyTransfers,
                PlayerShopEscrowConstants.MAX_TRANSFERS, "money transfers");
        itemTransfers = boundedCopy(itemTransfers,
                PlayerShopEscrowConstants.MAX_TRANSFERS, "item transfers");
        claims = boundedCopy(claims, PlayerShopEscrowConstants.MAX_CLAIMS,
                "claims");
        storageMutations = boundedCopy(storageMutations,
                PlayerShopEscrowConstants.MAX_STORAGE_MUTATIONS,
                "storage mutations");
        status = Objects.requireNonNull(status, "status");
        intentFingerprint = PlayerShopBinarySupport.requireString(
                intentFingerprint, 64, "intent fingerprint");
        validateOperation(operation, tradeMethod, paymentSource, listing);
        validateState(status, revision);
        validateReferences(requestId, moneyTransfers, itemTransfers, claims,
                storageMutations);
        if (!computedFingerprint(requestId, actorId, ownerId, shopIdentity,
                operation, tradeMethod, paymentSource, requestedUnits,
                quoteCreatedAt, listing, moneyTransfers, itemTransfers,
                claims, storageMutations).equals(intentFingerprint)) {
            throw new IllegalArgumentException("Player shop intent fingerprint is invalid");
        }
    }

    public static PlayerShopEscrowIntent prepared(
            UUID requestId,
            UUID actorId,
            UUID ownerId,
            PlayerShopIdentity shopIdentity,
            PlayerShopOperation operation,
            PlayerShopTradeMethod tradeMethod,
            PlayerShopPaymentSource paymentSource,
            int requestedUnits,
            Instant quoteCreatedAt,
            PlayerShopListingSnapshot listing,
            List<PlayerShopMoneyTransfer> moneyTransfers,
            List<PlayerShopItemTransfer> itemTransfers,
            List<PlayerShopClaimPlan> claims,
            List<PlayerShopStorageMutationPlan> storageMutations
    ) {
        String fingerprint = computedFingerprint(requestId, actorId, ownerId,
                shopIdentity, operation, tradeMethod, paymentSource,
                requestedUnits, quoteCreatedAt, listing, moneyTransfers,
                itemTransfers, claims, storageMutations);
        return new PlayerShopEscrowIntent(requestId, actorId, ownerId,
                shopIdentity, operation, tradeMethod, paymentSource,
                requestedUnits, quoteCreatedAt, listing, moneyTransfers,
                itemTransfers, claims, storageMutations, Status.PREPARED, 0L,
                fingerprint);
    }

    public PlayerShopEscrowIntent complete() {
        return transition(Status.COMMITTED);
    }

    public PlayerShopEscrowIntent abort(Status terminalStatus) {
        if (!terminalStatus.isAbort()) {
            throw new IllegalArgumentException("Player shop abort status is invalid");
        }
        return transition(terminalStatus);
    }

    public PlayerShopEscrowIntent quarantine() {
        return transition(Status.QUARANTINED);
    }

    private PlayerShopEscrowIntent transition(Status next) {
        if (status == next) {
            return this;
        }
        if (status != Status.PREPARED) {
            throw new IllegalStateException("Player shop intent is terminal");
        }
        return new PlayerShopEscrowIntent(requestId, actorId, ownerId,
                shopIdentity, operation, tradeMethod, paymentSource,
                requestedUnits, quoteCreatedAt, listing, moneyTransfers,
                itemTransfers, claims, storageMutations, next, 1L,
                intentFingerprint);
    }

    private static void validateOperation(
            PlayerShopOperation operation,
            PlayerShopTradeMethod method,
            PlayerShopPaymentSource source,
            PlayerShopListingSnapshot listing
    ) {
        switch (operation) {
            case PURCHASE, ADMIN_PURCHASE_SINK -> {
                if (listing == null || method == PlayerShopTradeMethod.BUYBACK
                        || method == PlayerShopTradeMethod.SETTLEMENT) {
                    throw new IllegalArgumentException("Player shop purchase mode is invalid");
                }
                if (method != PlayerShopTradeMethod.BARTER
                        && source == PlayerShopPaymentSource.NONE) {
                    throw new IllegalArgumentException("Player shop purchase source is invalid");
                }
            }
            case BUYBACK, ADMIN_BUYBACK -> {
                if (listing == null || method != PlayerShopTradeMethod.BUYBACK
                        || source != PlayerShopPaymentSource.NONE) {
                    throw new IllegalArgumentException("Player shop buyback mode is invalid");
                }
            }
            case SETTLEMENT_CLAIM -> {
                if (listing != null || method != PlayerShopTradeMethod.SETTLEMENT
                        || source != PlayerShopPaymentSource.NONE) {
                    throw new IllegalArgumentException("Player shop settlement mode is invalid");
                }
            }
        }
    }

    private static void validateState(Status status, long revision) {
        if (revision < 0L || revision > 1L
                || status == Status.PREPARED && revision != 0L
                || status != Status.PREPARED && revision != 1L) {
            throw new IllegalArgumentException("Player shop intent state is invalid");
        }
    }

    private static void validateReferences(
            UUID requestId,
            List<PlayerShopMoneyTransfer> moneyTransfers,
            List<PlayerShopItemTransfer> itemTransfers,
            List<PlayerShopClaimPlan> claims,
            List<PlayerShopStorageMutationPlan> storageMutations
    ) {
        requireUnique(moneyTransfers.stream().map(
                PlayerShopMoneyTransfer::transferId).toList(), "money transfer");
        requireUnique(itemTransfers.stream().map(
                PlayerShopItemTransfer::transferId).toList(), "item transfer");
        requireUnique(claims.stream().map(PlayerShopClaimPlan::claimId).toList(),
                "claim");
        requireUnique(storageMutations.stream().map(
                PlayerShopStorageMutationPlan::mutationId).toList(),
                "storage mutation");
        Map<UUID, PlayerShopClaimPlan> claimsById = new HashMap<>();
        for (PlayerShopClaimPlan claim : claims) {
            claimsById.put(claim.claimId(), claim);
        }
        Set<UUID> transferIds = new HashSet<>();
        for (PlayerShopItemTransfer transfer : itemTransfers) {
            transferIds.add(transfer.transferId());
            if (!requestId.equals(transfer.lot().sourceTransactionId())) {
                throw new IllegalArgumentException("Player shop item source is invalid");
            }
            if (transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.ITEM_CLAIM) {
                UUID claimId = parseClaimId(transfer.destination().reference());
                PlayerShopClaimPlan claim = claimsById.get(claimId);
                if (claim == null || claim.kind() != PlayerShopClaimPlan.Kind.EXACT_ITEM
                        || !claim.beneficiaryId().equals(
                                transfer.destination().participantId())
                        || !claim.itemLot().equals(transfer.lot())) {
                    throw new IllegalArgumentException("Player shop item claim link is invalid");
                }
            }
        }
        for (PlayerShopMoneyTransfer transfer : moneyTransfers) {
            if (transfer.destination().kind()
                    == PlayerShopAssetEndpoint.Kind.MONEY_CLAIM) {
                UUID claimId = parseClaimId(transfer.destination().reference());
                PlayerShopClaimPlan claim = claimsById.get(claimId);
                if (claim == null || claim.kind() != PlayerShopClaimPlan.Kind.MONEY
                        || !claim.beneficiaryId().equals(
                                transfer.destination().participantId())
                        || claim.moneyAmountMinorUnits()
                                != transfer.amountMinorUnits()) {
                    throw new IllegalArgumentException("Player shop money claim link is invalid");
                }
            }
        }
        for (int index = 0; index < storageMutations.size(); index++) {
            PlayerShopStorageMutationPlan mutation = storageMutations.get(index);
            if (mutation.sequence() != index
                    || !transferIds.contains(mutation.itemTransferId())) {
                throw new IllegalArgumentException("Player shop storage plan is not canonical");
            }
            PlayerShopItemTransfer transfer = itemTransfers.stream()
                    .filter(value -> value.transferId().equals(
                            mutation.itemTransferId())).findFirst().orElseThrow();
            if (!transfer.lot().equals(mutation.lot())) {
                throw new IllegalArgumentException("Player shop storage lot is invalid");
            }
            if (mutation.direction()
                    == PlayerShopStorageMutationPlan.Direction.INSERT) {
                PlayerShopClaimPlan claim = claimsById.get(mutation.claimId());
                if (claim == null || claim.kind()
                        != PlayerShopClaimPlan.Kind.EXACT_ITEM
                        || !claim.itemLot().equals(mutation.lot())) {
                    throw new IllegalArgumentException("Player shop insertion claim is invalid");
                }
            } else if (transfer.source().kind()
                    != PlayerShopAssetEndpoint.Kind.LINKED_STOCK) {
                throw new IllegalArgumentException("Player shop extraction source is invalid");
            }
        }
        for (PlayerShopClaimPlan claim : claims) {
            long incoming = claim.kind() == PlayerShopClaimPlan.Kind.MONEY
                    ? moneyTransfers.stream().filter(value ->
                            isClaimDestination(value.destination(), claim)).count()
                    : itemTransfers.stream().filter(value ->
                            isClaimDestination(value.destination(), claim)).count();
            if (incoming != 1L) {
                throw new IllegalArgumentException("Player shop claim funding is invalid");
            }
        }
    }

    private static boolean isClaimDestination(
            PlayerShopAssetEndpoint endpoint,
            PlayerShopClaimPlan claim
    ) {
        PlayerShopAssetEndpoint.Kind expected = claim.kind()
                == PlayerShopClaimPlan.Kind.MONEY
                ? PlayerShopAssetEndpoint.Kind.MONEY_CLAIM
                : PlayerShopAssetEndpoint.Kind.ITEM_CLAIM;
        return endpoint.kind() == expected
                && endpoint.participantId().equals(claim.beneficiaryId())
                && endpoint.reference().equals(claim.claimId().toString());
    }

    private static UUID parseClaimId(String value) {
        try {
            return PlayerShopBinarySupport.requireUuid(UUID.fromString(value),
                    "claim reference");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Player shop claim reference is invalid", exception);
        }
    }

    private static void requireUnique(List<UUID> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("Player shop " + label + " identity is duplicated");
        }
    }

    private static <T> List<T> boundedCopy(List<T> values, int maximum,
                                           String label) {
        List<T> copy = List.copyOf(Objects.requireNonNull(values, label));
        if (copy.size() > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " are too large");
        }
        return copy;
    }

    private static String computedFingerprint(
            UUID requestId,
            UUID actorId,
            UUID ownerId,
            PlayerShopIdentity shopIdentity,
            PlayerShopOperation operation,
            PlayerShopTradeMethod tradeMethod,
            PlayerShopPaymentSource paymentSource,
            int requestedUnits,
            Instant quoteCreatedAt,
            PlayerShopListingSnapshot listing,
            List<PlayerShopMoneyTransfer> moneyTransfers,
            List<PlayerShopItemTransfer> itemTransfers,
            List<PlayerShopClaimPlan> claims,
            List<PlayerShopStorageMutationPlan> storageMutations
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop intent v1");
            PlayerShopIntentCodec.writeCore(output, requestId, actorId,
                    ownerId, shopIdentity, operation, tradeMethod,
                    paymentSource, requestedUnits, quoteCreatedAt, listing,
                    moneyTransfers, itemTransfers, claims, storageMutations);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop intent", exception);
        }
    }

    public enum Status {
        PREPARED,
        ABORTED_VALIDATION,
        ABORTED_FUNDS,
        ABORTED_ITEMS,
        ABORTED_STORAGE,
        QUARANTINED,
        COMMITTED;

        public boolean isAbort() {
            return this == ABORTED_VALIDATION || this == ABORTED_FUNDS
                    || this == ABORTED_ITEMS || this == ABORTED_STORAGE;
        }
    }
}
