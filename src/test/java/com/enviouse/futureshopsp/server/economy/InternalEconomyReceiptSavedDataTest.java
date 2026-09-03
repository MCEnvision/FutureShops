package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.RequestId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalEconomyReceiptSavedDataTest {
    private static final UUID REQUEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000030");

    @Test
    void roundTripsReceiptsWithChecksums() {
        InternalEconomyReceiptSavedData data = new InternalEconomyReceiptSavedData();
        MutationReceipt receipt = new MutationReceipt(new RequestId(REQUEST_UUID), MutationKind.DEPOSIT,
                25L, "internal-operation", OptionalLong.of(125L));
        data.put(receipt);

        CompoundTag saved = data.save(new CompoundTag(), null);
        InternalEconomyReceiptSavedData loaded = InternalEconomyReceiptSavedData.load(saved, null);

        assertTrue(loaded.integrityValid());
        assertEquals(receipt, loaded.find(receipt.requestId()).orElseThrow());
    }

    @Test
    void checksumTamperingBlocksReceiptLookup() {
        InternalEconomyReceiptSavedData data = new InternalEconomyReceiptSavedData();
        MutationReceipt receipt = new MutationReceipt(new RequestId(REQUEST_UUID), MutationKind.WITHDRAW,
                25L, "internal-operation", OptionalLong.of(75L));
        data.put(receipt);
        CompoundTag saved = data.save(new CompoundTag(), null);
        saved.getList("receipts", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putLong("amount", 26L);

        InternalEconomyReceiptSavedData loaded = InternalEconomyReceiptSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertTrue(loaded.find(receipt.requestId()).isEmpty());
    }

    @Test
    void cleanMarkerTracksLifecycleBoundary() {
        InternalEconomyReceiptSavedData data = new InternalEconomyReceiptSavedData();
        data.markUnclean();
        assertFalse(data.cleanMarkerValid());
        data.markCleanMarker();
        assertTrue(data.cleanMarkerValid());
    }
}
