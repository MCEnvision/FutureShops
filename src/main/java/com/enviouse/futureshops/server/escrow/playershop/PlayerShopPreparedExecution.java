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

public record PlayerShopPreparedExecution(
        PlayerShopRequestIdentity requestIdentity,
        PlayerShopEscrowIntent intent,
        Instant preparedAt,
        List<PlayerShopMutationPreparation> mutations,
        String preparationFingerprint
) {
    public PlayerShopPreparedExecution {
        requestIdentity = Objects.requireNonNull(requestIdentity,
                "requestIdentity");
        intent = Objects.requireNonNull(intent, "intent");
        preparedAt = Objects.requireNonNull(preparedAt, "preparedAt");
        mutations = List.copyOf(Objects.requireNonNull(mutations,
                "mutations"));
        preparationFingerprint = PlayerShopBinarySupport.requireString(
                preparationFingerprint, 64, "execution preparation fingerprint");
        if (intent.status() != PlayerShopEscrowIntent.Status.PREPARED
                || !requestIdentity.matches(intent)
                || preparedAt.isBefore(intent.quoteCreatedAt())
                || mutations.size() > PlayerShopEscrowConstants.MAX_TRANSFERS
                * 3) {
            throw new IllegalArgumentException("Player shop prepared execution is invalid");
        }
        validateCoverage(intent, mutations);
        if (!fingerprintOf(requestIdentity, intent, preparedAt, mutations)
                .equals(preparationFingerprint)) {
            throw new IllegalArgumentException("Player shop preparation fingerprint is invalid");
        }
    }

    public static PlayerShopPreparedExecution create(
            PlayerShopRequestIdentity requestIdentity,
            PlayerShopEscrowIntent intent,
            Instant preparedAt,
            List<PlayerShopMutationPreparation> mutations
    ) {
        return new PlayerShopPreparedExecution(requestIdentity, intent,
                preparedAt, mutations, fingerprintOf(requestIdentity, intent,
                preparedAt, mutations));
    }

    private static void validateCoverage(
            PlayerShopEscrowIntent intent,
            List<PlayerShopMutationPreparation> mutations
    ) {
        int expected = intent.moneyTransfers().size()
                + intent.itemTransfers().size()
                + intent.storageMutations().size();
        if (mutations.size() != expected) {
            throw new IllegalArgumentException("Player shop preparation coverage is invalid");
        }
        Set<String> identities = new HashSet<>();
        for (PlayerShopMutationPreparation mutation : mutations) {
            String identity = mutation.kind() + "." + mutation.mutationId();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("Player shop preparation is duplicated");
            }
        }
        for (PlayerShopMoneyTransfer transfer : intent.moneyTransfers()) {
            if (mutations.stream().noneMatch(value -> value.matches(transfer))) {
                throw new IllegalArgumentException("Player shop money preparation is missing");
            }
        }
        for (PlayerShopItemTransfer transfer : intent.itemTransfers()) {
            if (mutations.stream().noneMatch(value -> value.matches(transfer))) {
                throw new IllegalArgumentException("Player shop item preparation is missing");
            }
        }
        for (PlayerShopStorageMutationPlan mutation
                : intent.storageMutations()) {
            if (mutations.stream().noneMatch(value -> value.matches(mutation))) {
                throw new IllegalArgumentException("Player shop storage preparation is missing");
            }
        }
    }

    private static String fingerprintOf(
            PlayerShopRequestIdentity identity,
            PlayerShopEscrowIntent intent,
            Instant preparedAt,
            List<PlayerShopMutationPreparation> mutations
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop prepared execution v1");
            PlayerShopBinarySupport.writeString(output,
                    identity.requestFingerprint(), 64);
            PlayerShopBinarySupport.writeBytes(output,
                    PlayerShopIntentCodec.encode(intent),
                    PlayerShopIntentCodec.MAX_ENCODED_BYTES);
            output.writeLong(preparedAt.getEpochSecond());
            output.writeInt(preparedAt.getNano());
            output.writeInt(mutations.size());
            for (PlayerShopMutationPreparation mutation : mutations) {
                output.writeByte(mutation.kind().ordinal());
                PlayerShopBinarySupport.writeUuid(output,
                        mutation.mutationId());
                PlayerShopBinarySupport.writeString(output,
                        mutation.preparationFingerprint(), 64);
            }
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop execution", exception);
        }
    }
}
