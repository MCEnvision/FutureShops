package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProtectedCashRedemptionCancellationCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 16_777_184;

    private static final int MAGIC = 0x46534343;
    private static final int MAX_RESERVATION_BYTES =
            ProtectedCashRedemptionReservationCodec.MAX_ENCODED_BYTES;
    private static final int MAX_TRANSACTION_BYTES = 4_194_304;
    private static final int MAX_PROOF_BYTES = 2_300_000;
    private static final int MAX_CUSTODY_BYTES = 1_200_000;
    private static final int MAX_MINT_EVENT_BYTES = 16_384;

    private ProtectedCashRedemptionCancellationCodec() {
    }

    public static byte[] encode(
            ProtectedCashRedemptionCancellation cancellation
    ) {
        Objects.requireNonNull(cancellation, "cancellation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            ProtectedCashRedemptionSupport.writeComponent(output,
                    ProtectedCashRedemptionReservationCodec.encode(
                            cancellation.reservation()),
                    MAX_RESERVATION_BYTES,
                    "Protected cash cancellation reservation");
            ProtectedCashRedemptionSupport.writeComponent(output,
                    EscrowTransactionByteCodec.encode(
                            cancellation.refundedTransaction()),
                    MAX_TRANSACTION_BYTES,
                    "Protected cash refunded transaction");
            ProtectedCashRedemptionSupport.writeComponent(output,
                    encodeProof(cancellation.inventoryProof()),
                    MAX_PROOF_BYTES,
                    "Protected cash no mutation proof");
            output.writeInt(cancellation.custodyReleases().size());
            for (CustodyMutation mutation :
                    cancellation.custodyReleases()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        CustodyMutationCodec.encode(mutation),
                        MAX_CUSTODY_BYTES,
                        "Protected cash custody release");
            }
            output.writeInt(cancellation.mintReleases().size());
            for (ProtectedMintJournalEvent event :
                    cancellation.mintReleases()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        ProtectedMintEventCodec.encode(event),
                        MAX_MINT_EVENT_BYTES,
                        "Protected cash mint release");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0
                    || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash cancellation", exception);
        }
    }

    public static ProtectedCashRedemptionCancellation decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Protected cash cancellation size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Protected cash cancellation schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation schema is unsupported");
            }
            ProtectedCashRedemptionReservation reservation =
                    ProtectedCashRedemptionReservationCodec.decode(
                            ProtectedCashRedemptionSupport.readComponent(
                                    input, bytes, MAX_RESERVATION_BYTES,
                                    "Protected cash cancellation reservation"));
            EscrowTransaction refunded = EscrowTransactionByteCodec.decode(
                    ProtectedCashRedemptionSupport.readComponent(
                            input, bytes, MAX_TRANSACTION_BYTES,
                            "Protected cash refunded transaction"));
            ProtectedCashRedemptionCancellation.InventoryNoMutationProof proof =
                    decodeProof(ProtectedCashRedemptionSupport.readComponent(
                            input, bytes, MAX_PROOF_BYTES,
                            "Protected cash no mutation proof"));
            int custodyCount = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_PORTIONS,
                    "Protected cash custody release count");
            if (custodyCount == 0) {
                throw new IllegalArgumentException(
                        "Protected cash custody release count is invalid");
            }
            List<CustodyMutation> custody = new ArrayList<>(custodyCount);
            for (int index = 0; index < custodyCount; index++) {
                custody.add(CustodyMutationCodec.decode(
                        ProtectedCashRedemptionSupport.readComponent(
                                input, bytes, MAX_CUSTODY_BYTES,
                                "Protected cash custody release")));
            }
            int mintCount = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_MINT_BATCHES,
                    "Protected cash mint release count");
            if (mintCount == 0) {
                throw new IllegalArgumentException(
                        "Protected cash mint release count is invalid");
            }
            List<ProtectedMintJournalEvent> mints = new ArrayList<>(mintCount);
            for (int index = 0; index < mintCount; index++) {
                mints.add(ProtectedMintEventCodec.decode(
                        ProtectedCashRedemptionSupport.readComponent(
                                input, bytes, MAX_MINT_EVENT_BYTES,
                                "Protected cash mint release")));
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation has trailing data");
            }
            ProtectedCashRedemptionCancellation cancellation =
                    new ProtectedCashRedemptionCancellation(reservation,
                            refunded, proof, custody, mints);
            if (!custody.equals(cancellation.custodyReleases())
                    || !mints.equals(cancellation.mintReleases())) {
                throw new IllegalArgumentException(
                        "Protected cash cancellation ordering is not canonical");
            }
            return cancellation;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash cancellation is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash cancellation is invalid", exception);
        }
    }

    public static String fingerprint(
            ProtectedCashRedemptionCancellation cancellation
    ) {
        return ProtectedCashRedemptionSupport.fingerprint(
                encode(cancellation));
    }

    private static byte[] encodeProof(
            ProtectedCashRedemptionCancellation.InventoryNoMutationProof proof
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            ProtectedCashRedemptionSupport.writeUuid(output, proof.proofId());
            ProtectedCashRedemptionSupport.writeUuid(output, proof.playerId());
            ProtectedCashRedemptionSupport.writeUuid(output,
                    proof.transactionId());
            ProtectedCashRedemptionSupport.writeUuid(output,
                    proof.reservationId());
            ProtectedCashRedemptionSupport.writeString(output,
                    proof.requestKey(),
                    ProtectedCashRedemptionCancellation
                            .InventoryNoMutationProof.MAX_REQUEST_KEY_LENGTH * 4);
            output.writeInt(proof.observations().size());
            for (ProtectedCashRedemptionCancellation.SlotObservation observation
                    : proof.observations()) {
                output.writeInt(observation.slot().container().ordinal());
                output.writeInt(observation.slot().index());
                ProtectedCashRedemptionSupport.writeBytes(output,
                        observation.exactSnapshot(),
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash no mutation snapshot");
            }
            ProtectedCashRedemptionSupport.writeBytes(output,
                    proof.inventoryHash(),
                    ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash no mutation inventory hash");
            ProtectedCashRedemptionSupport.writeBytes(output,
                    proof.proofDigest(),
                    ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash no mutation proof digest");
            ProtectedCashRedemptionSupport.writeInstant(output,
                    proof.inspectedAt());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_PROOF_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation proof is too large");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash no mutation proof",
                    exception);
        }
    }

    private static ProtectedCashRedemptionCancellation.InventoryNoMutationProof
    decodeProof(byte[] encoded) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            UUID proofId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID playerId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID transactionId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID reservationId = ProtectedCashRedemptionSupport.readUuid(input);
            String requestKey = ProtectedCashRedemptionSupport.readString(
                    input, bytes,
                    ProtectedCashRedemptionCancellation
                            .InventoryNoMutationProof.MAX_REQUEST_KEY_LENGTH * 4,
                    "Protected cash no mutation request key");
            int count = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_PORTIONS,
                    "Protected cash no mutation observation count");
            if (count == 0) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation observation count is invalid");
            }
            List<ProtectedCashRedemptionCancellation.SlotObservation>
                    observations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                InternalBillInventoryPlanner.Container container =
                        ProtectedCashRedemptionSupport.readEnum(
                                input.readInt(),
                                InternalBillInventoryPlanner.Container.values(),
                                "Protected cash no mutation container");
                int slot = input.readInt();
                byte[] snapshot = ProtectedCashRedemptionSupport.readBytes(
                        input, bytes, ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash no mutation snapshot");
                observations.add(new ProtectedCashRedemptionCancellation
                        .SlotObservation(
                        new InternalBillInventoryPlanner.SlotIdentity(
                                container, slot), snapshot));
            }
            byte[] inventoryHash = ProtectedCashRedemptionSupport.readBytes(
                    input, bytes, ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash no mutation inventory hash");
            byte[] proofDigest = ProtectedCashRedemptionSupport.readBytes(
                    input, bytes, ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash no mutation proof digest");
            Instant inspectedAt = ProtectedCashRedemptionSupport.readInstant(
                    input);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation proof has trailing data");
            }
            ProtectedCashRedemptionCancellation.InventoryNoMutationProof proof =
                    new ProtectedCashRedemptionCancellation
                            .InventoryNoMutationProof(proofId, playerId,
                            transactionId, reservationId, requestKey,
                            observations, inventoryHash, proofDigest,
                            inspectedAt);
            if (!observations.equals(proof.observations())) {
                throw new IllegalArgumentException(
                        "Protected cash no mutation observations are not canonical");
            }
            return proof;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash no mutation proof is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash no mutation proof is invalid", exception);
        }
    }
}
