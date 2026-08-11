package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public record PlayerShopSettlementImportEvidence(
        UUID requestId,
        UUID ownerId,
        UUID registryShopId,
        String legacySettlementKey,
        long legacyRevision,
        long pendingMinorUnits,
        String sourceFingerprint
) {
    public PlayerShopSettlementImportEvidence {
        requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "settlement import request id");
        ownerId = PlayerShopBinarySupport.requireUuid(ownerId,
                "settlement import owner id");
        registryShopId = PlayerShopBinarySupport.requireUuid(registryShopId,
                "settlement import shop id");
        legacySettlementKey = PlayerShopBinarySupport.requireString(
                legacySettlementKey, PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                "legacy settlement key");
        if (legacyRevision < 0L || pendingMinorUnits <= 0L) {
            throw new IllegalArgumentException("Player shop settlement import is invalid");
        }
        sourceFingerprint = PlayerShopBinarySupport.requireString(
                sourceFingerprint, 64, "settlement import fingerprint");
        if (!fingerprintOf(requestId, ownerId, registryShopId,
                legacySettlementKey, legacyRevision, pendingMinorUnits)
                .equals(sourceFingerprint)) {
            throw new IllegalArgumentException("Player shop settlement import fingerprint is invalid");
        }
    }

    public static PlayerShopSettlementImportEvidence capture(
            UUID requestId,
            UUID ownerId,
            UUID registryShopId,
            String legacySettlementKey,
            long legacyRevision,
            long pendingMinorUnits
    ) {
        return new PlayerShopSettlementImportEvidence(requestId, ownerId,
                registryShopId, legacySettlementKey, legacyRevision,
                pendingMinorUnits, fingerprintOf(requestId, ownerId,
                registryShopId, legacySettlementKey, legacyRevision,
                pendingMinorUnits));
    }

    public boolean matches(PlayerShopEscrowIntent intent) {
        if (intent.operation() != PlayerShopOperation.SETTLEMENT_CLAIM
                || !requestId.equals(intent.requestId())
                || !ownerId.equals(intent.ownerId())
                || !registryShopId.equals(
                        intent.shopIdentity().registryShopId())
                || intent.moneyTransfers().size() != 1) {
            return false;
        }
        PlayerShopMoneyTransfer transfer = intent.moneyTransfers().get(0);
        return transfer.amountMinorUnits() == pendingMinorUnits
                && transfer.source().kind()
                == PlayerShopAssetEndpoint.Kind.SETTLEMENT_BALANCE;
    }

    public boolean sameLegacySource(
            UUID expectedOwnerId,
            UUID expectedRegistryShopId,
            String expectedLegacySettlementKey
    ) {
        return ownerId.equals(Objects.requireNonNull(
                expectedOwnerId, "expectedOwnerId"))
                && registryShopId.equals(Objects.requireNonNull(
                expectedRegistryShopId, "expectedRegistryShopId"))
                && legacySettlementKey.equals(Objects.requireNonNull(
                expectedLegacySettlementKey,
                "expectedLegacySettlementKey"));
    }

    private static String fingerprintOf(
            UUID requestId,
            UUID ownerId,
            UUID registryShopId,
            String key,
            long legacyRevision,
            long pendingMinorUnits
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop settlement import v1");
            PlayerShopBinarySupport.writeUuid(output, requestId);
            PlayerShopBinarySupport.writeUuid(output, ownerId);
            PlayerShopBinarySupport.writeUuid(output, registryShopId);
            PlayerShopBinarySupport.writeString(output, key,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
            output.writeLong(legacyRevision);
            output.writeLong(pendingMinorUnits);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop settlement import", exception);
        }
    }
}
