package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyCustodyAndClaimSavedDataTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000030");

    @Test
    void custodyRoundTripAndTransitionsPreserveContentIdentity() {
        RequestId requestId = RequestId.random();
        EconomyCustodySavedData data = new EconomyCustodySavedData();
        data.hold(requestId, OWNER, "minecraft:diamond", 3L, "hash-1");
        data.transition(requestId, CustodyState.HELD, CustodyState.DELIVERED);

        EconomyCustodySavedData loaded = EconomyCustodySavedData.load(data.save(new CompoundTag(), null), null);

        assertTrue(loaded.integrityValid());
        assertEquals(CustodyState.DELIVERED, loaded.find(requestId).orElseThrow().state());
        assertEquals("minecraft:diamond", loaded.find(requestId).orElseThrow().itemKey());
        assertThrows(IllegalStateException.class,
                () -> loaded.transition(requestId, CustodyState.HELD, CustodyState.CLAIMED));
    }

    @Test
    void custodyChecksumAndNewerSchemaFailClosed() {
        EconomyCustodySavedData data = new EconomyCustodySavedData();
        data.hold(RequestId.random(), OWNER, "minecraft:diamond", 1L, "hash-2");
        CompoundTag tampered = data.save(new CompoundTag(), null);
        tampered.getList("records", 10).getCompound(0).putLong("quantity", 2L);

        EconomyCustodySavedData loaded = EconomyCustodySavedData.load(tampered, null);
        assertFalse(loaded.integrityValid());
        assertTrue(loaded.snapshot().isEmpty());

        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion", 99);
        assertFalse(EconomyCustodySavedData.load(newer, null).integrityValid());
    }

    @Test
    void claimRoundTripAndPendingStateRemainDurable() {
        RequestId requestId = RequestId.random();
        EconomyClaimSavedData data = new EconomyClaimSavedData();
        data.create(requestId, OWNER, 250L, "offline proceeds");

        EconomyClaimSavedData loaded = EconomyClaimSavedData.load(data.save(new CompoundTag(), null), null);

        assertTrue(loaded.integrityValid());
        assertTrue(loaded.hasIncompleteRecords());
        assertEquals(ClaimState.PENDING, loaded.find(requestId).orElseThrow().state());
        loaded.transition(requestId, ClaimState.PENDING, ClaimState.DELIVERED);
        loaded.transition(requestId, ClaimState.DELIVERED, ClaimState.RESOLVED);
        assertFalse(loaded.hasIncompleteRecords());
    }

    @Test
    void claimChecksumTamperIsNotInterpreted() {
        EconomyClaimSavedData data = new EconomyClaimSavedData();
        data.create(RequestId.random(), OWNER, 250L, "offline proceeds");
        CompoundTag tampered = data.save(new CompoundTag(), null);
        tampered.getList("records", 10).getCompound(0).putString("description", "changed");

        EconomyClaimSavedData loaded = EconomyClaimSavedData.load(tampered, null);
        assertFalse(loaded.integrityValid());
        assertTrue(loaded.snapshot().isEmpty());
    }
}
