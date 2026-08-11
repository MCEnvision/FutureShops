package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation.InventoryNoMutationProof;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionCancellation.SlotObservation;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement.InventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.redemption.ProtectedCashRedemptionSettlement.SlotMutation;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ForeignCashDepositCodec {
    public static final int MAX_RESERVATION_BYTES = 8_388_608;
    public static final int MAX_TERMINAL_BYTES = 16_777_184;

    private static final int RESERVATION_MAGIC = 0x46434452;
    private static final int SETTLEMENT_MAGIC = 0x46434453;
    private static final int CANCELLATION_MAGIC = 0x46434443;
    private static final int PLAN_MAGIC = 0x46434450;
    private static final int SCHEMA = 2;
    private static final int MAX_STRING_BYTES = 8192;

    private ForeignCashDepositCodec() {
    }

    public static byte[] encodeReservation(
            ForeignCashDepositReservation reservation
    ) {
        return encode(output -> {
            output.writeInt(RESERVATION_MAGIC);
            output.writeInt(SCHEMA);
            writeUuid(output, reservation.reservationId());
            writeUuid(output, reservation.requestId());
            writeUuid(output, reservation.playerId());
            output.writeInt(reservation.destinationAccount().type().ordinal());
            writeString(output,
                    reservation.destinationAccount().ownerKey());
            output.writeLong(
                    reservation.walletBalanceLimitMinorUnits());
            output.writeInt(reservation.depositMode().ordinal());
            writeBytes(output, reservation.inventoryBeforeHash(), 32);
            writeBytes(output, encodePlan(reservation.plan()),
                    ForeignCashDepositPlan.MAX_TOTAL_SNAPSHOT_BYTES
                            + 65_536);
            writeBytes(output, EscrowTransactionByteCodec.encode(
                    reservation.heldTransaction()), MAX_RESERVATION_BYTES);
            writeMutations(output, reservation.custodyReservations());
        }, MAX_RESERVATION_BYTES);
    }

    public static ForeignCashDepositReservation decodeReservation(
            byte[] encoded
    ) {
        return decode(encoded, MAX_RESERVATION_BYTES, input -> {
            int schema = requireHeader(input, RESERVATION_MAGIC);
            UUID reservationId = readUuid(input);
            UUID requestId = readUuid(input);
            UUID playerId = readUuid(input);
            LedgerAccountType type = readEnum(input.readInt(),
                    LedgerAccountType.values());
            LedgerAccountId destination = new LedgerAccountId(type,
                    readString(input));
            long walletLimit = input.readLong();
            CashDepositMode depositMode = schema >= 2
                    ? readEnum(input.readInt(), CashDepositMode.values())
                    : CashDepositMode.PUBLIC_WALLET;
            byte[] inventoryHash = readBytes(input, 32);
            ForeignCashDepositPlan plan = decodePlan(readBytes(input,
                    ForeignCashDepositPlan.MAX_TOTAL_SNAPSHOT_BYTES
                            + 65_536));
            EscrowTransaction held = EscrowTransactionByteCodec.decode(
                    readBytes(input, MAX_RESERVATION_BYTES));
            List<CustodyMutation> custody = readMutations(input);
            return new ForeignCashDepositReservation(reservationId,
                    requestId, playerId, destination, walletLimit,
                    depositMode,
                    inventoryHash, plan, held, custody);
        }, ForeignCashDepositCodec::encodeReservation);
    }

    public static byte[] encodeSettlement(
            ForeignCashDepositSettlement settlement
    ) {
        return encode(output -> {
            output.writeInt(SETTLEMENT_MAGIC);
            output.writeInt(SCHEMA);
            writeBytes(output, encodeReservation(settlement.reservation()),
                    MAX_RESERVATION_BYTES);
            writeBytes(output, EscrowTransactionByteCodec.encode(
                    settlement.completedTransaction()),
                    MAX_RESERVATION_BYTES);
            writeReceipt(output, settlement.inventoryMutation());
            writeMutations(output, settlement.custodyConsumptions());
            output.writeLong(
                    settlement.walletBalanceBeforeMinorUnits());
            output.writeLong(
                    settlement.walletReservedBeforeMinorUnits());
            output.writeBoolean(settlement.overflowClaim().isPresent());
            if (settlement.overflowClaim().isPresent()) {
                writeBytes(output, ClaimJournalCodec.encodeClaim(
                        settlement.overflowClaim().orElseThrow()),
                        MAX_RESERVATION_BYTES);
            }
            writeBytes(output, LedgerJournalCodec.encode(
                    settlement.ledgerTransaction()),
                    MAX_RESERVATION_BYTES);
        }, MAX_TERMINAL_BYTES);
    }

    public static ForeignCashDepositSettlement decodeSettlement(
            byte[] encoded
    ) {
        return decode(encoded, MAX_TERMINAL_BYTES, input -> {
            int schema = requireHeader(input, SETTLEMENT_MAGIC);
            ForeignCashDepositReservation reservation =
                    decodeReservation(readBytes(input,
                            MAX_RESERVATION_BYTES));
            requireLegacyPublicMode(schema, reservation.depositMode());
            EscrowTransaction completed =
                    EscrowTransactionByteCodec.decode(readBytes(input,
                            MAX_RESERVATION_BYTES));
            InventoryMutationReceipt receipt = readReceipt(input);
            List<CustodyMutation> custody = readMutations(input);
            long walletBefore = input.readLong();
            long reservedBefore = input.readLong();
            Optional<EscrowClaim> claim = input.readBoolean()
                    ? Optional.of(ClaimJournalCodec.decodeClaim(
                    readBytes(input, MAX_RESERVATION_BYTES)))
                    : Optional.empty();
            LedgerTransaction ledger = LedgerJournalCodec.decode(
                    readBytes(input, MAX_RESERVATION_BYTES));
            return new ForeignCashDepositSettlement(reservation,
                    completed, receipt, custody, walletBefore,
                    reservedBefore, claim, ledger);
        }, ForeignCashDepositCodec::encodeSettlement);
    }

    public static byte[] encodeCancellation(
            ForeignCashDepositCancellation cancellation
    ) {
        return encode(output -> {
            output.writeInt(CANCELLATION_MAGIC);
            output.writeInt(SCHEMA);
            writeBytes(output, encodeReservation(
                    cancellation.reservation()), MAX_RESERVATION_BYTES);
            writeBytes(output, EscrowTransactionByteCodec.encode(
                    cancellation.refundedTransaction()),
                    MAX_RESERVATION_BYTES);
            writeProof(output, cancellation.inventoryProof());
            writeMutations(output, cancellation.custodyReleases());
        }, MAX_TERMINAL_BYTES);
    }

    public static ForeignCashDepositCancellation decodeCancellation(
            byte[] encoded
    ) {
        return decode(encoded, MAX_TERMINAL_BYTES, input -> {
            int schema = requireHeader(input, CANCELLATION_MAGIC);
            ForeignCashDepositReservation reservation =
                    decodeReservation(readBytes(input,
                            MAX_RESERVATION_BYTES));
            requireLegacyPublicMode(schema, reservation.depositMode());
            EscrowTransaction refunded = EscrowTransactionByteCodec.decode(
                    readBytes(input, MAX_RESERVATION_BYTES));
            InventoryNoMutationProof proof = readProof(input);
            List<CustodyMutation> releases = readMutations(input);
            return new ForeignCashDepositCancellation(reservation,
                    refunded, proof, releases);
        }, ForeignCashDepositCodec::encodeCancellation);
    }

    static byte[] encodePlan(ForeignCashDepositPlan plan) {
        return encode(output -> {
            output.writeInt(PLAN_MAGIC);
            output.writeInt(SCHEMA);
            writeString(output, plan.providerId());
            writeString(output, plan.providerSignature());
            output.writeLong(plan.amountMinorUnits());
            output.writeInt(plan.portions().size());
            for (ForeignCashDepositPlan.Portion portion :
                    plan.portions()) {
                output.writeInt(portion.slot().container().ordinal());
                output.writeInt(portion.slot().index());
                writeString(output, portion.registryId());
                output.writeLong(portion.unitValueMinorUnits());
                output.writeInt(portion.originalStackCount());
                output.writeInt(portion.selectedCount());
                writeBytes(output, portion.exactStackSnapshot(),
                        ItemStackSnapshotCodec.MAXIMUM_BYTES);
            }
        }, ForeignCashDepositPlan.MAX_TOTAL_SNAPSHOT_BYTES + 65_536);
    }

    static byte[] encodeLegacyPlanIdentity(ForeignCashDepositPlan plan) {
        byte[] encoded = encodePlan(plan);
        ByteBuffer.wrap(encoded).putInt(Integer.BYTES, 1);
        return encoded;
    }

    static ForeignCashDepositPlan decodePlan(byte[] encoded) {
        return decode(encoded,
                ForeignCashDepositPlan.MAX_TOTAL_SNAPSHOT_BYTES + 65_536,
                input -> {
                    requireHeader(input, PLAN_MAGIC);
                    String provider = readString(input);
                    String signature = readString(input);
                    long amount = input.readLong();
                    int count = readCount(input,
                            ForeignCashDepositPlan.MAX_PORTIONS);
                    List<ForeignCashDepositPlan.Portion> portions =
                            new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        var container = readEnum(input.readInt(),
                                InternalBillInventoryPlanner.Container
                                        .values());
                        int slot = input.readInt();
                        String registryId = readString(input);
                        long unit = input.readLong();
                        int original = input.readInt();
                        int selected = input.readInt();
                        byte[] snapshot = readBytes(input,
                                ItemStackSnapshotCodec.MAXIMUM_BYTES);
                        portions.add(new ForeignCashDepositPlan.Portion(
                                new InternalBillInventoryPlanner.SlotIdentity(
                                        container, slot), registryId, unit,
                                original, selected, snapshot));
                    }
                    return new ForeignCashDepositPlan(provider, signature,
                            amount, portions);
                }, ForeignCashDepositCodec::encodePlan);
    }

    private static void writeReceipt(DataOutputStream output,
                                     InventoryMutationReceipt receipt)
            throws IOException {
        writeUuid(output, receipt.receiptId());
        writeUuid(output, receipt.playerId());
        writeUuid(output, receipt.transactionId());
        writeUuid(output, receipt.reservationId());
        writeString(output, receipt.requestKey());
        output.writeInt(receipt.mutations().size());
        for (SlotMutation mutation : receipt.mutations()) {
            writeSlot(output, mutation.slot());
            output.writeInt(mutation.removedCount());
            writeBytes(output, mutation.beforeSnapshot(),
                    ItemStackSnapshotCodec.MAXIMUM_BYTES);
            writeOptionalBytes(output, mutation.afterSnapshot(),
                    ItemStackSnapshotCodec.MAXIMUM_BYTES);
        }
        writeBytes(output, receipt.beforeInventoryHash(), 32);
        writeBytes(output, receipt.afterInventoryHash(), 32);
        writeBytes(output, receipt.mutationTokenDigest(), 32);
        writeInstant(output, receipt.occurredAt());
    }

    private static InventoryMutationReceipt readReceipt(
            DataInputStream input
    ) throws IOException {
        UUID receiptId = readUuid(input);
        UUID playerId = readUuid(input);
        UUID transactionId = readUuid(input);
        UUID reservationId = readUuid(input);
        String requestKey = readString(input);
        int count = readCount(input, ForeignCashDepositPlan.MAX_PORTIONS);
        List<SlotMutation> mutations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            var slot = readSlot(input);
            int removed = input.readInt();
            byte[] before = readBytes(input,
                    ItemStackSnapshotCodec.MAXIMUM_BYTES);
            byte[] after = readOptionalBytes(input,
                    ItemStackSnapshotCodec.MAXIMUM_BYTES);
            mutations.add(new SlotMutation(slot, removed, before, after));
        }
        return new InventoryMutationReceipt(receiptId, playerId,
                transactionId, reservationId, requestKey, mutations,
                readBytes(input, 32), readBytes(input, 32),
                readBytes(input, 32), readInstant(input));
    }

    private static void writeProof(DataOutputStream output,
                                   InventoryNoMutationProof proof)
            throws IOException {
        writeUuid(output, proof.proofId());
        writeUuid(output, proof.playerId());
        writeUuid(output, proof.transactionId());
        writeUuid(output, proof.reservationId());
        writeString(output, proof.requestKey());
        output.writeInt(proof.observations().size());
        for (SlotObservation observation : proof.observations()) {
            writeSlot(output, observation.slot());
            writeBytes(output, observation.exactSnapshot(),
                    ItemStackSnapshotCodec.MAXIMUM_BYTES);
        }
        writeBytes(output, proof.inventoryHash(), 32);
        writeBytes(output, proof.proofDigest(), 32);
        writeInstant(output, proof.inspectedAt());
    }

    private static InventoryNoMutationProof readProof(
            DataInputStream input
    ) throws IOException {
        UUID proofId = readUuid(input);
        UUID playerId = readUuid(input);
        UUID transactionId = readUuid(input);
        UUID reservationId = readUuid(input);
        String requestKey = readString(input);
        int count = readCount(input, ForeignCashDepositPlan.MAX_PORTIONS);
        List<SlotObservation> observations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            observations.add(new SlotObservation(readSlot(input),
                    readBytes(input,
                            ItemStackSnapshotCodec.MAXIMUM_BYTES)));
        }
        return new InventoryNoMutationProof(proofId, playerId,
                transactionId, reservationId, requestKey, observations,
                readBytes(input, 32), readBytes(input, 32),
                readInstant(input));
    }

    private static void writeMutations(DataOutputStream output,
                                       List<CustodyMutation> mutations)
            throws IOException {
        output.writeInt(mutations.size());
        for (CustodyMutation mutation : mutations) {
            writeBytes(output, CustodyMutationCodec.encode(mutation),
                    CustodyMutationCodec.MAX_ENCODED_BYTES);
        }
    }

    private static List<CustodyMutation> readMutations(
            DataInputStream input
    ) throws IOException {
        int count = readCount(input, ForeignCashDepositPlan.MAX_PORTIONS);
        List<CustodyMutation> mutations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            mutations.add(CustodyMutationCodec.decode(readBytes(input,
                    CustodyMutationCodec.MAX_ENCODED_BYTES)));
        }
        return List.copyOf(mutations);
    }

    private static void writeSlot(DataOutputStream output,
                                  InternalBillInventoryPlanner.SlotIdentity slot)
            throws IOException {
        output.writeInt(slot.container().ordinal());
        output.writeInt(slot.index());
    }

    private static InternalBillInventoryPlanner.SlotIdentity readSlot(
            DataInputStream input
    ) throws IOException {
        return new InternalBillInventoryPlanner.SlotIdentity(
                readEnum(input.readInt(),
                        InternalBillInventoryPlanner.Container.values()),
                input.readInt());
    }

    private static byte[] encode(Writer writer, int maximum) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > maximum) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit encoding exceeds its bound");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode foreign cash deposit", exception);
        }
    }

    private static <T> T decode(byte[] encoded, int maximum,
                                Reader<T> reader,
                                java.util.function.Function<T, byte[]> encoder) {
        if (encoded == null || encoded.length == 0
                || encoded.length > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit encoding size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            T result = reader.read(input);
            boolean currentSchema = encoded.length >= Integer.BYTES * 2
                    && ByteBuffer.wrap(encoded, Integer.BYTES,
                    Integer.BYTES).getInt() == SCHEMA;
            if (input.read() != -1
                    || currentSchema
                    && !Arrays.equals(encoded, encoder.apply(result))) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit encoding is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            if (exception instanceof IllegalStateException newer) {
                throw newer;
            }
            throw new IllegalArgumentException(
                    "Foreign cash deposit encoding is invalid", exception);
        }
    }

    private static int requireHeader(DataInputStream input, int magic)
            throws IOException {
        if (input.readInt() != magic) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit encoding header is invalid");
        }
        int schema = input.readInt();
        if (schema > SCHEMA) {
            throw new IllegalStateException(
                    "Foreign cash deposit encoding schema is newer than this build");
        }
        if (schema < 1) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit encoding schema is unsupported");
        }
        return schema;
    }

    private static void requireLegacyPublicMode(
            int schema,
            CashDepositMode depositMode
    ) {
        if (schema == 1 && depositMode != CashDepositMode.PUBLIC_WALLET) {
            throw new IllegalArgumentException(
                    "Legacy foreign cash deposit mode is invalid");
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value)
            throws IOException {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit text is invalid");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input)
            throws IOException {
        byte[] encoded = readBytes(input, MAX_STRING_BYTES);
        String value = new String(encoded,
                java.nio.charset.StandardCharsets.UTF_8);
        if (!Arrays.equals(encoded, value.getBytes(
                java.nio.charset.StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit text is invalid");
        }
        return value;
    }

    private static void writeBytes(DataOutputStream output, byte[] value,
                                   int maximum) throws IOException {
        if (value.length == 0 || value.length > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit binary value is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input, int maximum)
            throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit binary length is invalid");
        }
        byte[] value = input.readNBytes(count);
        if (value.length != count) {
            throw new EOFException();
        }
        return value;
    }

    private static void writeOptionalBytes(DataOutputStream output,
                                           byte[] value,
                                           int maximum) throws IOException {
        if (value.length > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit binary value is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readOptionalBytes(DataInputStream input,
                                            int maximum)
            throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit binary length is invalid");
        }
        byte[] value = input.readNBytes(count);
        if (value.length != count) {
            throw new EOFException();
        }
        return value;
    }

    private static int readCount(DataInputStream input, int maximum)
            throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit count is invalid");
        }
        return count;
    }

    private static void writeInstant(DataOutputStream output, Instant value)
            throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream input)
            throws IOException {
        return Instant.ofEpochSecond(input.readLong(), input.readInt());
    }

    private static <T> T readEnum(int ordinal, T[] values) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit enum is invalid");
        }
        return values[ordinal];
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream input) throws IOException;
    }
}
