package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlayerShopAtomicCommit(
        UUID commitId,
        PlayerShopEscrowIntent committedIntent,
        Instant committedAt,
        List<PlayerShopMoneyMutationReceipt> moneyReceipts,
        List<PlayerShopItemMutationReceipt> itemReceipts,
        List<PlayerShopStorageCustodyReceipt> storageReceipts,
        List<PlayerShopClaimPlan> createdClaims,
        String commitFingerprint
) {
    public PlayerShopAtomicCommit {
        commitId = PlayerShopBinarySupport.requireUuid(commitId, "commit id");
        committedIntent = Objects.requireNonNull(committedIntent,
                "committedIntent");
        if (!commitId.equals(committedIntent.requestId())
                || committedIntent.status()
                        != PlayerShopEscrowIntent.Status.COMMITTED) {
            throw new IllegalArgumentException("Player shop committed intent is invalid");
        }
        committedAt = Objects.requireNonNull(committedAt, "committedAt");
        if (committedAt.isBefore(committedIntent.quoteCreatedAt())) {
            throw new IllegalArgumentException("Player shop commit time is invalid");
        }
        moneyReceipts = List.copyOf(Objects.requireNonNull(moneyReceipts,
                "moneyReceipts"));
        itemReceipts = List.copyOf(Objects.requireNonNull(itemReceipts,
                "itemReceipts"));
        storageReceipts = List.copyOf(Objects.requireNonNull(storageReceipts,
                "storageReceipts"));
        createdClaims = List.copyOf(Objects.requireNonNull(createdClaims,
                "createdClaims"));
        commitFingerprint = PlayerShopBinarySupport.requireString(
                commitFingerprint, 64, "commit fingerprint");
        validateReceipts(committedIntent, moneyReceipts, itemReceipts,
                storageReceipts, createdClaims);
        if (!computedFingerprint(commitId, committedIntent, committedAt,
                moneyReceipts, itemReceipts, storageReceipts,
                createdClaims).equals(commitFingerprint)) {
            throw new IllegalArgumentException("Player shop commit fingerprint is invalid");
        }
        PlayerShopConservationValidator.requireConserved(committedIntent);
    }

    public static PlayerShopAtomicCommit create(
            PlayerShopEscrowIntent preparedIntent,
            Instant committedAt,
            List<PlayerShopMoneyMutationReceipt> moneyReceipts,
            List<PlayerShopItemMutationReceipt> itemReceipts,
            List<PlayerShopStorageCustodyReceipt> storageReceipts
    ) {
        Objects.requireNonNull(preparedIntent, "preparedIntent");
        PlayerShopEscrowIntent committed = preparedIntent.status()
                == PlayerShopEscrowIntent.Status.COMMITTED
                ? preparedIntent : preparedIntent.complete();
        List<PlayerShopClaimPlan> claims = committed.claims();
        String fingerprint = computedFingerprint(committed.requestId(),
                committed, committedAt, moneyReceipts, itemReceipts,
                storageReceipts, claims);
        return new PlayerShopAtomicCommit(committed.requestId(), committed,
                committedAt, moneyReceipts, itemReceipts, storageReceipts,
                claims, fingerprint);
    }

    private static void validateReceipts(
            PlayerShopEscrowIntent intent,
            List<PlayerShopMoneyMutationReceipt> moneyReceipts,
            List<PlayerShopItemMutationReceipt> itemReceipts,
            List<PlayerShopStorageCustodyReceipt> storageReceipts,
            List<PlayerShopClaimPlan> claims
    ) {
        if (!claims.equals(intent.claims())
                || moneyReceipts.size() != intent.moneyTransfers().size()
                || itemReceipts.size() != intent.itemTransfers().size()
                || storageReceipts.size()
                        != intent.storageMutations().size()) {
            throw new IllegalArgumentException("Player shop commit evidence count is invalid");
        }
        for (int index = 0; index < moneyReceipts.size(); index++) {
            PlayerShopMoneyMutationReceipt receipt = moneyReceipts.get(index);
            if (!receipt.requestId().equals(intent.requestId())
                    || !receipt.transfer().equals(
                            intent.moneyTransfers().get(index))) {
                throw new IllegalArgumentException("Player shop money receipt order is invalid");
            }
        }
        for (int index = 0; index < itemReceipts.size(); index++) {
            PlayerShopItemMutationReceipt receipt = itemReceipts.get(index);
            if (!receipt.requestId().equals(intent.requestId())
                    || !receipt.transfer().equals(
                            intent.itemTransfers().get(index))) {
                throw new IllegalArgumentException("Player shop item receipt order is invalid");
            }
        }
        for (int index = 0; index < storageReceipts.size(); index++) {
            PlayerShopStorageCustodyReceipt receipt = storageReceipts.get(index);
            PlayerShopStorageMutationPlan plan =
                    intent.storageMutations().get(index);
            if (!receipt.requestId().equals(intent.requestId())
                    || !receipt.plan().equals(plan)
                    || receipt.state()
                            == PlayerShopStorageCustodyReceipt.RecoveryState.RECOVERY_REQUIRED
                    || receipt.state()
                            == PlayerShopStorageCustodyReceipt.RecoveryState.QUARANTINED
                    || plan.direction()
                            == PlayerShopStorageMutationPlan.Direction.EXTRACT
                    && receipt.state()
                            != PlayerShopStorageCustodyReceipt.RecoveryState.APPLIED
                    || plan.direction()
                            == PlayerShopStorageMutationPlan.Direction.INSERT
                    && receipt.state()
                            != PlayerShopStorageCustodyReceipt.RecoveryState.PREPARED
                    && receipt.state()
                            != PlayerShopStorageCustodyReceipt.RecoveryState.APPLIED
                    && receipt.state()
                            != PlayerShopStorageCustodyReceipt.RecoveryState.CLAIM_PRESERVED) {
                throw new IllegalArgumentException("Player shop storage receipt order is invalid");
            }
        }
        requireUnique(moneyReceipts.stream().map(value ->
                value.transfer().transferId()).toList(), "money receipts");
        requireUnique(itemReceipts.stream().map(value ->
                value.transfer().transferId()).toList(), "item receipts");
        requireUnique(storageReceipts.stream().map(value ->
                value.plan().mutationId()).toList(), "storage receipts");
    }

    private static void requireUnique(List<UUID> ids, String label) {
        Set<UUID> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new IllegalArgumentException("Player shop " + label + " are duplicated");
        }
    }

    private static String computedFingerprint(
            UUID commitId,
            PlayerShopEscrowIntent intent,
            Instant committedAt,
            List<PlayerShopMoneyMutationReceipt> moneyReceipts,
            List<PlayerShopItemMutationReceipt> itemReceipts,
            List<PlayerShopStorageCustodyReceipt> storageReceipts,
            List<PlayerShopClaimPlan> claims
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop atomic commit v1");
            PlayerShopBinarySupport.writeUuid(output, commitId);
            PlayerShopBinarySupport.writeBytes(output,
                    PlayerShopIntentCodec.encode(intent),
                    PlayerShopIntentCodec.MAX_ENCODED_BYTES);
            output.writeLong(committedAt.getEpochSecond());
            output.writeInt(committedAt.getNano());
            PlayerShopAtomicCommitCodec.writeEvidence(output, moneyReceipts,
                    itemReceipts, storageReceipts, claims);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop commit", exception);
        }
    }
}
