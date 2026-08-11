package com.enviouse.futureshops.server.escrow.playershop;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlayerShopFundingEvidence(
        UUID requestId,
        Status status,
        List<PlayerShopMoneyMutationReceipt> moneyReceipts,
        List<PlayerShopItemMutationReceipt> itemReceipts,
        List<PlayerShopStorageCustodyReceipt> storageReceipts,
        String detail
) {
    public PlayerShopFundingEvidence {
        requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "funding request id");
        status = Objects.requireNonNull(status, "status");
        moneyReceipts = boundedCopy(moneyReceipts,
                PlayerShopEscrowConstants.MAX_TRANSFERS,
                "funding money receipts");
        itemReceipts = boundedCopy(itemReceipts,
                PlayerShopEscrowConstants.MAX_TRANSFERS,
                "funding item receipts");
        storageReceipts = boundedCopy(storageReceipts,
                PlayerShopEscrowConstants.MAX_STORAGE_MUTATIONS,
                "funding storage receipts");
        detail = PlayerShopBinarySupport.optionalString(detail,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "funding detail");
        if (status == Status.COMPLETE && !detail.isEmpty()
                || status != Status.COMPLETE && detail.isEmpty()) {
            throw new IllegalArgumentException("Player shop funding detail is invalid");
        }
        requireUnique(moneyReceipts.stream().map(value ->
                value.transfer().transferId()).toList(), "money receipt");
        requireUnique(itemReceipts.stream().map(value ->
                value.transfer().transferId()).toList(), "item receipt");
        requireUnique(storageReceipts.stream().map(value ->
                value.plan().mutationId()).toList(), "storage receipt");
    }

    public static PlayerShopFundingEvidence complete(
            UUID requestId,
            List<PlayerShopMoneyMutationReceipt> moneyReceipts,
            List<PlayerShopItemMutationReceipt> itemReceipts,
            List<PlayerShopStorageCustodyReceipt> storageReceipts
    ) {
        return new PlayerShopFundingEvidence(requestId, Status.COMPLETE,
                moneyReceipts, itemReceipts, storageReceipts, "");
    }

    public boolean completeFor(PlayerShopPreparedExecution preparation) {
        if (status != Status.COMPLETE
                || !requestId.equals(preparation.intent().requestId())) {
            return false;
        }
        PlayerShopEscrowIntent intent = preparation.intent();
        try {
            PlayerShopAtomicCommit.create(intent,
                    preparation.preparedAt(), moneyReceipts, itemReceipts,
                    storageReceipts);
            return true;
        } catch (RuntimeException exception) {
            return false;
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

    private static void requireUnique(List<UUID> values, String label) {
        Set<UUID> unique = new HashSet<>(values);
        if (unique.size() != values.size()) {
            throw new IllegalArgumentException("Player shop " + label + " is duplicated");
        }
    }

    public enum Status {
        COMPLETE,
        RECOVERY_REQUIRED,
        QUARANTINED
    }
}
