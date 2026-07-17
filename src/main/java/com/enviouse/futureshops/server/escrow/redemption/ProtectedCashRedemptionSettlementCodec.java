package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.runtime.LedgerJournalCodec;
import com.enviouse.futureshops.server.escrow.runtime.ClaimJournalCodec;
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
import java.util.Optional;
import java.util.UUID;

public final class ProtectedCashRedemptionSettlementCodec {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_ENCODED_BYTES = 16_777_184;

    private static final int MAGIC = 0x46534353;
    private static final int MAX_RESERVATION_BYTES =
            ProtectedCashRedemptionReservationCodec.MAX_ENCODED_BYTES;
    private static final int MAX_TRANSACTION_BYTES = 4_194_304;
    private static final int MAX_INVENTORY_RECEIPT_BYTES = 2_300_000;
    private static final int MAX_CUSTODY_BYTES = 1_200_000;
    private static final int MAX_MINT_EVENT_BYTES = 16_384;
    private static final int MAX_LEDGER_BYTES = 16_384;
    private static final int MAX_CLAIM_BYTES = 16_384;

    private ProtectedCashRedemptionSettlementCodec() {
    }

    public static byte[] encode(ProtectedCashRedemptionSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            ProtectedCashRedemptionSupport.writeComponent(output,
                    ProtectedCashRedemptionReservationCodec.encode(
                            settlement.reservation()),
                    MAX_RESERVATION_BYTES,
                    "Protected cash settlement reservation");
            ProtectedCashRedemptionSupport.writeComponent(output,
                    EscrowTransactionByteCodec.encode(
                            settlement.completedTransaction()),
                    MAX_TRANSACTION_BYTES,
                    "Protected cash completed transaction");
            ProtectedCashRedemptionSupport.writeComponent(output,
                    encodeInventory(settlement.inventoryMutation()),
                    MAX_INVENTORY_RECEIPT_BYTES,
                    "Protected cash inventory receipt");
            output.writeInt(settlement.custodyConsumptions().size());
            for (CustodyMutation mutation : settlement.custodyConsumptions()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        CustodyMutationCodec.encode(mutation),
                        MAX_CUSTODY_BYTES,
                        "Protected cash custody consumption");
            }
            output.writeInt(settlement.mintCommits().size());
            for (ProtectedMintJournalEvent event : settlement.mintCommits()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        ProtectedMintEventCodec.encode(event),
                        MAX_MINT_EVENT_BYTES,
                        "Protected cash mint commit");
            }
            output.writeInt(settlement.destinationAccount().type().ordinal());
            ProtectedCashRedemptionSupport.writeString(output,
                    settlement.destinationAccount().ownerKey(), 512);
            output.writeLong(settlement.walletBalanceBeforeMinorUnits());
            output.writeLong(settlement.walletReservedBeforeMinorUnits());
            output.writeByte(settlement.overflowClaim().isPresent() ? 1 : 0);
            if (settlement.overflowClaim().isPresent()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        ClaimJournalCodec.encodeClaim(
                                settlement.overflowClaim().orElseThrow()),
                        MAX_CLAIM_BYTES,
                        "Protected cash overflow claim");
            }
            ProtectedCashRedemptionSupport.writeComponent(output,
                    LedgerJournalCodec.encode(settlement.ledgerTransaction()),
                    MAX_LEDGER_BYTES,
                    "Protected cash redemption ledger");
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash settlement exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash settlement", exception);
        }
    }

    public static ProtectedCashRedemptionSettlement decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Protected cash settlement size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Protected cash settlement magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Protected cash settlement schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Protected cash settlement schema is unsupported");
            }
            ProtectedCashRedemptionReservation reservation =
                    ProtectedCashRedemptionReservationCodec.decode(
                            ProtectedCashRedemptionSupport.readComponent(
                                    input, bytes, MAX_RESERVATION_BYTES,
                                    "Protected cash settlement reservation"));
            EscrowTransaction completed = EscrowTransactionByteCodec.decode(
                    ProtectedCashRedemptionSupport.readComponent(
                            input, bytes, MAX_TRANSACTION_BYTES,
                            "Protected cash completed transaction"));
            ProtectedCashRedemptionSettlement.InventoryMutationReceipt inventory =
                    decodeInventory(ProtectedCashRedemptionSupport.readComponent(
                            input, bytes, MAX_INVENTORY_RECEIPT_BYTES,
                            "Protected cash inventory receipt"));
            int custodyCount = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_PORTIONS,
                    "Protected cash custody consumption count");
            if (custodyCount == 0) {
                throw new IllegalArgumentException(
                        "Protected cash custody consumption count is invalid");
            }
            List<CustodyMutation> custody = new ArrayList<>(custodyCount);
            for (int index = 0; index < custodyCount; index++) {
                custody.add(CustodyMutationCodec.decode(
                        ProtectedCashRedemptionSupport.readComponent(
                                input, bytes, MAX_CUSTODY_BYTES,
                                "Protected cash custody consumption")));
            }
            int mintCount = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_MINT_BATCHES,
                    "Protected cash mint commit count");
            if (mintCount == 0) {
                throw new IllegalArgumentException(
                        "Protected cash mint commit count is invalid");
            }
            List<ProtectedMintJournalEvent> mints = new ArrayList<>(mintCount);
            for (int index = 0; index < mintCount; index++) {
                mints.add(ProtectedMintEventCodec.decode(
                        ProtectedCashRedemptionSupport.readComponent(
                                input, bytes, MAX_MINT_EVENT_BYTES,
                                "Protected cash mint commit")));
            }
            LedgerAccountType destinationType =
                    ProtectedCashRedemptionSupport.readEnum(
                            input.readInt(), LedgerAccountType.values(),
                            "Protected cash ledger destination type");
            LedgerAccountId destination = new LedgerAccountId(destinationType,
                    ProtectedCashRedemptionSupport.readString(
                            input, bytes, 512,
                            "Protected cash ledger destination owner"));
            long walletBalanceBefore = input.readLong();
            long walletReservedBefore = input.readLong();
            int claimMarker = input.readUnsignedByte();
            if (claimMarker > 1) {
                throw new IllegalArgumentException(
                        "Protected cash overflow claim marker is invalid");
            }
            Optional<EscrowClaim> overflowClaim = claimMarker == 1
                    ? Optional.of(ClaimJournalCodec.decodeClaim(
                    ProtectedCashRedemptionSupport.readComponent(input, bytes,
                            MAX_CLAIM_BYTES,
                            "Protected cash overflow claim")))
                    : Optional.empty();
            LedgerTransaction ledger = LedgerJournalCodec.decode(
                    ProtectedCashRedemptionSupport.readComponent(
                            input, bytes, MAX_LEDGER_BYTES,
                            "Protected cash redemption ledger"));
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash settlement has trailing data");
            }
            ProtectedCashRedemptionSettlement settlement =
                    new ProtectedCashRedemptionSettlement(reservation, completed,
                            inventory, custody, mints, destination,
                            walletBalanceBefore, walletReservedBefore,
                            overflowClaim, ledger);
            if (!custody.equals(settlement.custodyConsumptions())
                    || !mints.equals(settlement.mintCommits())) {
                throw new IllegalArgumentException(
                        "Protected cash settlement ordering is not canonical");
            }
            return settlement;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash settlement is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash settlement is invalid", exception);
        }
    }

    public static String fingerprint(
            ProtectedCashRedemptionSettlement settlement
    ) {
        return ProtectedCashRedemptionSupport.fingerprint(encode(settlement));
    }

    private static byte[] encodeInventory(
            ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            ProtectedCashRedemptionSupport.writeUuid(output, receipt.receiptId());
            ProtectedCashRedemptionSupport.writeUuid(output, receipt.playerId());
            ProtectedCashRedemptionSupport.writeUuid(output,
                    receipt.transactionId());
            ProtectedCashRedemptionSupport.writeUuid(output,
                    receipt.reservationId());
            ProtectedCashRedemptionSupport.writeString(output,
                    receipt.requestKey(),
                    ProtectedCashRedemptionSettlement.InventoryMutationReceipt
                            .MAX_REQUEST_KEY_LENGTH * 4);
            output.writeInt(receipt.mutations().size());
            for (ProtectedCashRedemptionSettlement.SlotMutation mutation
                    : receipt.mutations()) {
                output.writeInt(mutation.slot().container().ordinal());
                output.writeInt(mutation.slot().index());
                output.writeInt(mutation.removedCount());
                ProtectedCashRedemptionSupport.writeBytes(output,
                        mutation.beforeSnapshot(),
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash inventory before snapshot");
                ProtectedCashRedemptionSupport.writeBytes(output,
                        mutation.afterSnapshot(),
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash inventory after snapshot");
            }
            ProtectedCashRedemptionSupport.writeBytes(output,
                    receipt.beforeInventoryHash(),
                    ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash inventory before hash");
            ProtectedCashRedemptionSupport.writeBytes(output,
                    receipt.afterInventoryHash(),
                    ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash inventory after hash");
            ProtectedCashRedemptionSupport.writeBytes(output,
                    receipt.mutationTokenDigest(),
                    ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash inventory mutation token digest");
            ProtectedCashRedemptionSupport.writeInstant(output,
                    receipt.occurredAt());
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0
                    || encoded.length > MAX_INVENTORY_RECEIPT_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash inventory receipt exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash inventory receipt",
                    exception);
        }
    }

    private static ProtectedCashRedemptionSettlement.InventoryMutationReceipt
    decodeInventory(byte[] encoded) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            UUID receiptId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID playerId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID transactionId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID reservationId = ProtectedCashRedemptionSupport.readUuid(input);
            String requestKey = ProtectedCashRedemptionSupport.readString(
                    input, bytes,
                    ProtectedCashRedemptionSettlement.InventoryMutationReceipt
                            .MAX_REQUEST_KEY_LENGTH * 4,
                    "Protected cash inventory request key");
            int count = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_PORTIONS,
                    "Protected cash inventory mutation count");
            if (count == 0) {
                throw new IllegalArgumentException(
                        "Protected cash inventory mutation count is invalid");
            }
            List<ProtectedCashRedemptionSettlement.SlotMutation> mutations =
                    new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                InternalBillInventoryPlanner.Container container =
                        ProtectedCashRedemptionSupport.readEnum(
                                input.readInt(),
                                InternalBillInventoryPlanner.Container.values(),
                                "Protected cash inventory container");
                int slot = input.readInt();
                int removed = input.readInt();
                byte[] before = ProtectedCashRedemptionSupport.readBytes(
                        input, bytes, ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash inventory before snapshot");
                byte[] after = ProtectedCashRedemptionSupport.readBytes(
                        input, bytes, ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash inventory after snapshot");
                mutations.add(new ProtectedCashRedemptionSettlement.SlotMutation(
                        new InternalBillInventoryPlanner.SlotIdentity(
                                container, slot), removed, before, after));
            }
            byte[] beforeHash = ProtectedCashRedemptionSupport.readBytes(
                    input, bytes, ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash inventory before hash");
            byte[] afterHash = ProtectedCashRedemptionSupport.readBytes(
                    input, bytes, ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash inventory after hash");
            byte[] tokenDigest = ProtectedCashRedemptionSupport.readBytes(
                    input, bytes, ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash inventory mutation token digest");
            Instant occurredAt = ProtectedCashRedemptionSupport.readInstant(input);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash inventory receipt has trailing data");
            }
            ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt =
                    new ProtectedCashRedemptionSettlement.InventoryMutationReceipt(
                            receiptId, playerId, transactionId, reservationId,
                            requestKey, mutations, beforeHash, afterHash,
                            tokenDigest, occurredAt);
            if (!mutations.equals(receipt.mutations())) {
                throw new IllegalArgumentException(
                        "Protected cash inventory mutation ordering is not canonical");
            }
            return receipt;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash inventory receipt is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash inventory receipt is invalid", exception);
        }
    }
}
