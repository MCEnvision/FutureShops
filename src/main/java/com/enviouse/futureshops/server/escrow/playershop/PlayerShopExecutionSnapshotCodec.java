package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopExecutionSnapshotCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            PlayerShopEscrowConstants.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x46535058;

    private PlayerShopExecutionSnapshotCodec() {
    }

    public static byte[] encode(PlayerShopExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeRequest(output, snapshot.requestIdentity());
            PlayerShopBinarySupport.writeBytes(output,
                    PlayerShopIntentCodec.encode(snapshot.intent()),
                    PlayerShopIntentCodec.MAX_ENCODED_BYTES);
            output.writeBoolean(snapshot.settlementImport() != null);
            if (snapshot.settlementImport() != null) {
                writeSettlement(output, snapshot.settlementImport());
            }
            output.writeBoolean(snapshot.preparation() != null);
            if (snapshot.preparation() != null) {
                writePreparation(output, snapshot.preparation());
            }
            output.writeBoolean(snapshot.funding() != null);
            if (snapshot.funding() != null) {
                writeFunding(output, snapshot.funding());
            }
            output.writeBoolean(snapshot.claimCreation() != null);
            if (snapshot.claimCreation() != null) {
                writeClaims(output, snapshot.claimCreation());
            }
            output.writeBoolean(snapshot.commit() != null);
            if (snapshot.commit() != null) {
                PlayerShopBinarySupport.writeBytes(output,
                        PlayerShopAtomicCommitCodec.encode(snapshot.commit()),
                        PlayerShopAtomicCommitCodec.MAX_ENCODED_BYTES);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode player shop execution snapshot", exception);
        }
    }

    public static PlayerShopExecutionSnapshot decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Player shop execution snapshot magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Player shop execution snapshot schema is unsupported");
            }
            PlayerShopRequestIdentity request = readRequest(input);
            PlayerShopEscrowIntent intent = PlayerShopIntentCodec.decode(
                    PlayerShopBinarySupport.readBytes(input,
                            PlayerShopIntentCodec.MAX_ENCODED_BYTES,
                            "execution intent"));
            PlayerShopSettlementImportEvidence settlement =
                    input.readBoolean() ? readSettlement(input) : null;
            PlayerShopPreparedExecution preparation = input.readBoolean()
                    ? readPreparation(input, request, intent) : null;
            PlayerShopFundingEvidence funding = input.readBoolean()
                    ? readFunding(input) : null;
            PlayerShopClaimCreationEvidence claims = input.readBoolean()
                    ? readClaims(input) : null;
            PlayerShopAtomicCommit commit = input.readBoolean()
                    ? PlayerShopAtomicCommitCodec.decode(
                    PlayerShopBinarySupport.readBytes(input,
                            PlayerShopAtomicCommitCodec.MAX_ENCODED_BYTES,
                            "execution commit")) : null;
            PlayerShopBinarySupport.requireFinished(input,
                    "execution snapshot");
            PlayerShopExecutionSnapshot snapshot =
                    new PlayerShopExecutionSnapshot(request, intent,
                            settlement, preparation, funding, claims, commit);
            if (!Arrays.equals(copy, encode(snapshot))) {
                throw new IllegalArgumentException("Player shop execution snapshot is not canonical");
            }
            return snapshot;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Player shop execution snapshot is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Player shop execution snapshot is invalid", exception);
        }
    }

    private static void writeRequest(DataOutputStream output,
                                     PlayerShopRequestIdentity request) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, request.requestId());
        output.writeInt(request.responseToken());
        PlayerShopBinarySupport.writeUuid(output, request.actorId());
        PlayerShopBinarySupport.writeUuid(output, request.registryShopId());
        output.writeLong(request.shopIdentityRevision());
        output.writeByte(request.operation().ordinal());
        output.writeByte(request.paymentSource().ordinal());
        output.writeInt(request.requestedUnits());
        PlayerShopBinarySupport.writeString(output,
                request.listingRevision(), 64);
        PlayerShopBinarySupport.writeString(output,
                request.requestFingerprint(), 64);
    }

    private static PlayerShopRequestIdentity readRequest(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopRequestIdentity(
                PlayerShopBinarySupport.readUuid(input, "request id"),
                input.readInt(),
                PlayerShopBinarySupport.readUuid(input, "request actor id"),
                PlayerShopBinarySupport.readUuid(input, "request shop id"),
                input.readLong(),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopOperation.values(), "request operation"),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopPaymentSource.values(), "request payment source"),
                input.readInt(),
                PlayerShopBinarySupport.readString(input, 64,
                        "request listing revision"),
                PlayerShopBinarySupport.readString(input, 64,
                        "request fingerprint"));
    }

    private static void writeSettlement(
            DataOutputStream output,
            PlayerShopSettlementImportEvidence settlement
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, settlement.requestId());
        PlayerShopBinarySupport.writeUuid(output, settlement.ownerId());
        PlayerShopBinarySupport.writeUuid(output, settlement.registryShopId());
        PlayerShopBinarySupport.writeString(output,
                settlement.legacySettlementKey(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
        output.writeLong(settlement.legacyRevision());
        output.writeLong(settlement.pendingMinorUnits());
        PlayerShopBinarySupport.writeString(output,
                settlement.sourceFingerprint(), 64);
    }

    private static PlayerShopSettlementImportEvidence readSettlement(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopSettlementImportEvidence(
                PlayerShopBinarySupport.readUuid(input,
                        "settlement request id"),
                PlayerShopBinarySupport.readUuid(input,
                        "settlement owner id"),
                PlayerShopBinarySupport.readUuid(input,
                        "settlement shop id"),
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                        "legacy settlement key"),
                input.readLong(), input.readLong(),
                PlayerShopBinarySupport.readString(input, 64,
                        "settlement source fingerprint"));
    }

    private static void writePreparation(
            DataOutputStream output,
            PlayerShopPreparedExecution preparation
    ) throws IOException {
        output.writeLong(preparation.preparedAt().getEpochSecond());
        output.writeInt(preparation.preparedAt().getNano());
        output.writeInt(preparation.mutations().size());
        for (PlayerShopMutationPreparation mutation
                : preparation.mutations()) {
            output.writeByte(mutation.kind().ordinal());
            PlayerShopBinarySupport.writeUuid(output, mutation.mutationId());
            PlayerShopBinarySupport.writeString(output,
                    mutation.subjectFingerprint(), 64);
            PlayerShopBinarySupport.writeBytes(output,
                    mutation.backendToken(),
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            PlayerShopBinarySupport.writeString(output,
                    mutation.preparationFingerprint(), 64);
        }
        PlayerShopBinarySupport.writeString(output,
                preparation.preparationFingerprint(), 64);
    }

    private static PlayerShopPreparedExecution readPreparation(
            DataInputStream input,
            PlayerShopRequestIdentity request,
            PlayerShopEscrowIntent intent
    ) throws IOException {
        Instant preparedAt = readInstant(input, "preparation instant");
        int size = readCount(input,
                PlayerShopEscrowConstants.MAX_TRANSFERS * 3,
                "preparation mutations");
        List<PlayerShopMutationPreparation> mutations =
                new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            mutations.add(new PlayerShopMutationPreparation(
                    PlayerShopBinarySupport.readEnum(input,
                            PlayerShopMutationPreparation.Kind.values(),
                            "preparation kind"),
                    PlayerShopBinarySupport.readUuid(input,
                            "prepared mutation id"),
                    PlayerShopBinarySupport.readString(input, 64,
                            "prepared subject fingerprint"),
                    PlayerShopBinarySupport.readBytes(input,
                            PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                            "prepared backend token"),
                    PlayerShopBinarySupport.readString(input, 64,
                            "mutation preparation fingerprint")));
        }
        return new PlayerShopPreparedExecution(request, intent, preparedAt,
                mutations, PlayerShopBinarySupport.readString(input, 64,
                "execution preparation fingerprint"));
    }

    private static void writeFunding(
            DataOutputStream output,
            PlayerShopFundingEvidence funding
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, funding.requestId());
        output.writeByte(funding.status().ordinal());
        output.writeInt(funding.moneyReceipts().size());
        for (PlayerShopMoneyMutationReceipt receipt
                : funding.moneyReceipts()) {
            PlayerShopAtomicCommitCodec.writeMoneyReceipt(output, receipt);
        }
        output.writeInt(funding.itemReceipts().size());
        for (PlayerShopItemMutationReceipt receipt : funding.itemReceipts()) {
            PlayerShopAtomicCommitCodec.writeItemReceipt(output, receipt);
        }
        output.writeInt(funding.storageReceipts().size());
        for (PlayerShopStorageCustodyReceipt receipt
                : funding.storageReceipts()) {
            PlayerShopStorageCustodyReceiptCodec.writeBody(output, receipt);
        }
        PlayerShopBinarySupport.writeOptionalString(output, funding.detail(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
    }

    private static PlayerShopFundingEvidence readFunding(
            DataInputStream input
    ) throws IOException {
        UUID requestId = PlayerShopBinarySupport.readUuid(input,
                "funding request id");
        PlayerShopFundingEvidence.Status status =
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopFundingEvidence.Status.values(),
                        "funding status");
        int moneySize = readCount(input,
                PlayerShopEscrowConstants.MAX_TRANSFERS,
                "funding money receipts");
        List<PlayerShopMoneyMutationReceipt> money =
                new ArrayList<>(moneySize);
        for (int index = 0; index < moneySize; index++) {
            money.add(PlayerShopAtomicCommitCodec.readMoneyReceipt(input));
        }
        int itemSize = readCount(input,
                PlayerShopEscrowConstants.MAX_TRANSFERS,
                "funding item receipts");
        List<PlayerShopItemMutationReceipt> items =
                new ArrayList<>(itemSize);
        for (int index = 0; index < itemSize; index++) {
            items.add(PlayerShopAtomicCommitCodec.readItemReceipt(input));
        }
        int storageSize = readCount(input,
                PlayerShopEscrowConstants.MAX_STORAGE_MUTATIONS,
                "funding storage receipts");
        List<PlayerShopStorageCustodyReceipt> storage =
                new ArrayList<>(storageSize);
        for (int index = 0; index < storageSize; index++) {
            storage.add(PlayerShopStorageCustodyReceiptCodec.readBody(input));
        }
        return new PlayerShopFundingEvidence(requestId, status, money, items,
                storage, PlayerShopBinarySupport.readOptionalString(input,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                "funding detail"));
    }

    private static void writeClaims(
            DataOutputStream output,
            PlayerShopClaimCreationEvidence claims
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, claims.requestId());
        output.writeByte(claims.status().ordinal());
        output.writeInt(claims.claims().size());
        for (PlayerShopClaimPlan claim : claims.claims()) {
            PlayerShopIntentCodec.writeClaim(output, claim);
        }
        PlayerShopBinarySupport.writeString(output,
                claims.backendEvidence(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
        PlayerShopBinarySupport.writeOptionalString(output, claims.detail(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
    }

    private static PlayerShopClaimCreationEvidence readClaims(
            DataInputStream input
    ) throws IOException {
        UUID requestId = PlayerShopBinarySupport.readUuid(input,
                "claim creation request id");
        PlayerShopClaimCreationEvidence.Status status =
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopClaimCreationEvidence.Status.values(),
                        "claim creation status");
        int size = readCount(input, PlayerShopEscrowConstants.MAX_CLAIMS,
                "created claims");
        List<PlayerShopClaimPlan> claims = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            claims.add(PlayerShopIntentCodec.readClaim(input));
        }
        return new PlayerShopClaimCreationEvidence(requestId, status, claims,
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                        "claim creation evidence"),
                PlayerShopBinarySupport.readOptionalString(input,
                        PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                        "claim creation detail"));
    }

    private static Instant readInstant(DataInputStream input, String label)
            throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static int readCount(DataInputStream input, int maximum,
                                 String label) throws IOException {
        int value = input.readInt();
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        return value;
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Player shop execution snapshot size is invalid");
        }
    }
}
