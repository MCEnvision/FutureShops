package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProtectedCashRedemptionReservationCodec {
    public static final int CURRENT_SCHEMA = 4;
    public static final int MAX_ENCODED_BYTES = 8_388_608;

    private static final int MAGIC = 0x46534352;
    private static final int MAX_TRANSACTION_BYTES = 4_194_304;
    private static final int MAX_CUSTODY_BYTES = 1_200_000;
    private static final int MAX_MINT_EVENT_BYTES = 16_384;

    private ProtectedCashRedemptionReservationCodec() {
    }

    public static byte[] encode(ProtectedCashRedemptionReservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            ProtectedCashRedemptionSupport.writeUuid(
                    output, reservation.reservationId());
            ProtectedCashRedemptionSupport.writeUuid(
                    output, reservation.playerId());
            output.writeInt(reservation.destinationAccount().type().ordinal());
            ProtectedCashRedemptionSupport.writeString(output,
                    reservation.destinationAccount().ownerKey(), 512);
            output.writeLong(reservation.walletBalanceLimitMinorUnits());
            output.writeInt(reservation.depositMode().ordinal());
            ProtectedCashRedemptionSupport.writeBytes(output,
                    reservation.inventoryBeforeHash(),
                    ProtectedCashRedemptionSupport.HASH_BYTES,
                    "Protected cash reservation inventory hash");
            ProtectedCashRedemptionSupport.writeComponent(output,
                    ProtectedCashRedemptionSupport.encodePlan(reservation.plan()),
                    ProtectedCashRedemptionSupport.MAX_PLAN_ENCODED_BYTES,
                    "Protected cash reservation plan");
            ProtectedCashRedemptionSupport.writeComponent(output,
                    EscrowTransactionByteCodec.encode(
                            reservation.heldTransaction()),
                    MAX_TRANSACTION_BYTES,
                    "Protected cash held transaction");
            output.writeInt(reservation.custodyReservations().size());
            for (CustodyMutation mutation :
                    reservation.custodyReservations()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        CustodyMutationCodec.encode(mutation),
                        MAX_CUSTODY_BYTES,
                        "Protected cash custody reservation");
            }
            output.writeInt(reservation.mintReservations().size());
            for (ProtectedMintJournalEvent event :
                    reservation.mintReservations()) {
                ProtectedCashRedemptionSupport.writeComponent(output,
                        ProtectedMintEventCodec.encode(event),
                        MAX_MINT_EVENT_BYTES,
                        "Protected cash mint reservation");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash reservation exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash reservation", exception);
        }
    }

    public static ProtectedCashRedemptionReservation decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Protected cash reservation size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Protected cash reservation magic is invalid");
            }
            int schema = input.readInt();
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException(
                        "Protected cash reservation schema is newer than this build");
            }
            if (schema < 3) {
                throw new IllegalArgumentException(
                        "Protected cash reservation schema is unsupported");
            }
            UUID reservationId = ProtectedCashRedemptionSupport.readUuid(input);
            UUID playerId = ProtectedCashRedemptionSupport.readUuid(input);
            LedgerAccountType destinationType =
                    ProtectedCashRedemptionSupport.readEnum(
                            input.readInt(), LedgerAccountType.values(),
                            "Protected cash destination type");
            LedgerAccountId destination = new LedgerAccountId(destinationType,
                    ProtectedCashRedemptionSupport.readString(
                            input, bytes, 512,
                            "Protected cash destination owner"));
            long walletBalanceLimitMinorUnits = input.readLong();
            CashDepositMode depositMode = schema >= 4
                    ? ProtectedCashRedemptionSupport.readEnum(
                            input.readInt(), CashDepositMode.values(),
                            "Protected cash deposit mode")
                    : CashDepositMode.PUBLIC_WALLET;
            byte[] inventoryBeforeHash =
                    ProtectedCashRedemptionSupport.readBytes(input, bytes,
                            ProtectedCashRedemptionSupport.HASH_BYTES,
                            "Protected cash reservation inventory hash");
            InternalBillInventoryPlanner.ExactPlan plan =
                    ProtectedCashRedemptionSupport.decodePlan(
                            ProtectedCashRedemptionSupport.readComponent(
                                    input, bytes,
                                    ProtectedCashRedemptionSupport.MAX_PLAN_ENCODED_BYTES,
                                    "Protected cash reservation plan"));
            EscrowTransaction transaction = EscrowTransactionByteCodec.decode(
                    ProtectedCashRedemptionSupport.readComponent(
                            input, bytes, MAX_TRANSACTION_BYTES,
                            "Protected cash held transaction"));
            int custodyCount = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_PORTIONS,
                    "Protected cash custody reservation count");
            if (custodyCount == 0) {
                throw new IllegalArgumentException(
                        "Protected cash custody reservation count is invalid");
            }
            List<CustodyMutation> custody = new ArrayList<>(custodyCount);
            for (int index = 0; index < custodyCount; index++) {
                custody.add(CustodyMutationCodec.decode(
                        ProtectedCashRedemptionSupport.readComponent(
                                input, bytes, MAX_CUSTODY_BYTES,
                                "Protected cash custody reservation")));
            }
            int mintCount = ProtectedCashRedemptionSupport.readCount(
                    input, ProtectedCashRedemptionReservation.MAX_MINT_BATCHES,
                    "Protected cash mint reservation count");
            if (mintCount == 0) {
                throw new IllegalArgumentException(
                        "Protected cash mint reservation count is invalid");
            }
            List<ProtectedMintJournalEvent> mints = new ArrayList<>(mintCount);
            for (int index = 0; index < mintCount; index++) {
                mints.add(ProtectedMintEventCodec.decode(
                        ProtectedCashRedemptionSupport.readComponent(
                                input, bytes, MAX_MINT_EVENT_BYTES,
                                "Protected cash mint reservation")));
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash reservation has trailing data");
            }
            ProtectedCashRedemptionReservation reservation =
                    new ProtectedCashRedemptionReservation(
                            reservationId, playerId, destination,
                            walletBalanceLimitMinorUnits, depositMode,
                            inventoryBeforeHash, plan, transaction, custody,
                            mints);
            if (!plan.equals(reservation.plan())
                    || !custody.equals(reservation.custodyReservations())
                    || !mints.equals(reservation.mintReservations())) {
                throw new IllegalArgumentException(
                        "Protected cash reservation ordering is not canonical");
            }
            return reservation;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash reservation is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash reservation is invalid", exception);
        }
    }

    public static String fingerprint(
            ProtectedCashRedemptionReservation reservation
    ) {
        return ProtectedCashRedemptionSupport.fingerprint(encode(reservation));
    }
}
