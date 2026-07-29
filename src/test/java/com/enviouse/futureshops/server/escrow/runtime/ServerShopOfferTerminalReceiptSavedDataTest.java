package com.enviouse.futureshops.server.escrow.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferTerminalReceiptSavedDataTest {
    private static final UUID REQUEST = UUID.fromString(
            "83000000-0000-0000-0000-000000000003");
    private static final UUID CORRUPT_REQUEST = UUID.fromString(
            "83000000-0000-0000-0000-000000000004");
    private static final UUID PLAYER = UUID.fromString(
            "83000000-0000-0000-0000-000000000005");
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void receiptsRoundTripAndReplayWithoutChangingIdentity() {
        ServerShopOfferTerminalReceiptSavedData data =
                new ServerShopOfferTerminalReceiptSavedData();
        ServerShopOfferTerminalReceiptSavedData.Receipt receipt =
                receipt(ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        ServerShopOfferService.Status.OUT_OF_STOCK);

        assertTrue(data.record(receipt));
        assertFalse(data.record(receipt));

        ServerShopOfferTerminalReceiptSavedData loaded =
                ServerShopOfferTerminalReceiptSavedData.load(
                        data.save(new CompoundTag()));

        assertEquals(receipt, loaded.find(REQUEST).orElseThrow());
        assertFalse(loaded.record(receipt));
    }

    @Test
    void conflictingReuseOfRequestIdentityIsRejected() {
        ServerShopOfferTerminalReceiptSavedData data =
                new ServerShopOfferTerminalReceiptSavedData();
        data.record(receipt(
                ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                ServerShopOfferService.Status.OUT_OF_STOCK));

        assertThrows(IllegalStateException.class, () -> data.record(
                receipt(ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        ServerShopOfferService.Status.REJECTED)));
        assertThrows(IllegalStateException.class, () -> data.record(
                receipt(ServerShopOfferTerminalReceiptSavedData.Kind.SINGLE,
                        ServerShopOfferService.Status.OUT_OF_STOCK)));
        assertThrows(IllegalStateException.class, () -> data.record(
                new ServerShopOfferTerminalReceiptSavedData.Receipt(
                        REQUEST,
                        PLAYER,
                        ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        "b".repeat(64),
                        ServerShopOfferService.Status.OUT_OF_STOCK)));
    }

    @Test
    void receiptRejectsUnsafeIdentityAndNonterminalStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerShopOfferTerminalReceiptSavedData.Receipt(
                        new UUID(0L, 0L),
                        PLAYER,
                        ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        FINGERPRINT,
                        ServerShopOfferService.Status.OUT_OF_STOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerShopOfferTerminalReceiptSavedData.Receipt(
                        REQUEST,
                        PLAYER,
                        ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        "not a fingerprint",
                        ServerShopOfferService.Status.OUT_OF_STOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerShopOfferTerminalReceiptSavedData.Receipt(
                        REQUEST,
                        PLAYER,
                        ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        FINGERPRINT,
                        ServerShopOfferService.Status.SUCCESS));
    }

    @Test
    void loadRejectsCorruptAndConflictingDuplicateRows() {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        ListTag rows = new ListTag();
        rows.add(row(
                REQUEST, FINGERPRINT,
                ServerShopOfferService.Status.OUT_OF_STOCK));
        rows.add(row(
                CORRUPT_REQUEST, "not a fingerprint",
                ServerShopOfferService.Status.OUT_OF_STOCK));
        rows.add(row(
                REQUEST, "b".repeat(64),
                ServerShopOfferService.Status.REJECTED));
        root.put("Receipts", rows);

        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferTerminalReceiptSavedData.load(root));
    }

    @Test
    void fullRepositoryFailsClosedWithoutEvictingOldReceipt() {
        ServerShopOfferTerminalReceiptSavedData data =
                new ServerShopOfferTerminalReceiptSavedData(1);
        ServerShopOfferTerminalReceiptSavedData.Receipt first =
                receipt(ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        ServerShopOfferService.Status.OUT_OF_STOCK);
        data.record(first);
        ServerShopOfferTerminalReceiptSavedData.Receipt second =
                new ServerShopOfferTerminalReceiptSavedData.Receipt(
                        CORRUPT_REQUEST,
                        PLAYER,
                        ServerShopOfferTerminalReceiptSavedData.Kind.CART,
                        "b".repeat(64),
                        ServerShopOfferService.Status.REJECTED);

        assertFalse(data.canRecord(CORRUPT_REQUEST));
        assertThrows(IllegalStateException.class,
                () -> data.record(second));
        assertEquals(first, data.find(REQUEST).orElseThrow());
        assertEquals(1, data.size());
    }

    @Test
    void onePlayersFailureFloodCannotFillTheGlobalArchive() {
        ServerShopOfferTerminalReceiptSavedData data =
                new ServerShopOfferTerminalReceiptSavedData();
        for (int index = 1; index <= 4_096; index++) {
            data.record(new ServerShopOfferTerminalReceiptSavedData.Receipt(
                    new UUID(93L, index), PLAYER,
                    ServerShopOfferTerminalReceiptSavedData.Kind.SINGLE,
                    "d".repeat(64),
                    ServerShopOfferService.Status.OUT_OF_STOCK));
        }
        UUID blocked = new UUID(93L, 4_097L);
        UUID otherPlayer = new UUID(94L, 1L);

        assertFalse(data.canRecord(blocked, PLAYER));
        assertTrue(data.canRecord(blocked, otherPlayer));
        assertThrows(IllegalStateException.class, () -> data.record(
                new ServerShopOfferTerminalReceiptSavedData.Receipt(
                        blocked, PLAYER,
                        ServerShopOfferTerminalReceiptSavedData.Kind.SINGLE,
                        "e".repeat(64),
                        ServerShopOfferService.Status.REJECTED)));
        assertEquals(4_096, data.size());

        ServerShopOfferTerminalReceiptSavedData loaded =
                ServerShopOfferTerminalReceiptSavedData.load(
                        data.save(new CompoundTag()));
        assertFalse(loaded.canRecord(blocked, PLAYER));
        assertTrue(loaded.canRecord(blocked, otherPlayer));
        assertEquals(4_096, loaded.size());
    }

    private static CompoundTag row(
            UUID requestId,
            String fingerprint,
            ServerShopOfferService.Status status
    ) {
        CompoundTag row = new CompoundTag();
        row.putUUID("Request", requestId);
        row.putUUID("Player", PLAYER);
        row.putString("Kind",
                ServerShopOfferTerminalReceiptSavedData.Kind.CART.name());
        row.putString("Fingerprint", fingerprint);
        row.putString("Status", status.name());
        return row;
    }

    private static ServerShopOfferTerminalReceiptSavedData.Receipt receipt(
            ServerShopOfferTerminalReceiptSavedData.Kind kind,
            ServerShopOfferService.Status status
    ) {
        return new ServerShopOfferTerminalReceiptSavedData.Receipt(
                REQUEST, PLAYER, kind, FINGERPRINT, status);
    }
}
