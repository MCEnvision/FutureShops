package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStore;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscrowGlobalStateVerifierTest {
    @Test
    void allSevenStoresAndTheCursorSequenceBindTheFingerprint() {
        EnumMap<EscrowCheckpointStore, byte[]> snapshots = snapshots();

        EscrowGlobalVerificationSnapshot first =
                EscrowGlobalStateVerifier.verify(11L, snapshots);
        EscrowGlobalVerificationSnapshot repeated =
                EscrowGlobalStateVerifier.verify(11L, snapshots);
        assertEquals(first, repeated);

        snapshots.get(EscrowCheckpointStore.CLAIMS)[0] ^= 1;
        assertNotEquals(first, EscrowGlobalStateVerifier.verify(11L, snapshots));
        assertNotEquals(first, EscrowGlobalStateVerifier.verify(12L, snapshots));
    }

    @Test
    void missingNullAndNegativeInputsFailClosed() {
        EnumMap<EscrowCheckpointStore, byte[]> missing = snapshots();
        missing.remove(EscrowCheckpointStore.CUSTODY);
        assertThrows(EscrowRuntimeException.class,
                () -> EscrowGlobalStateVerifier.verify(1L, missing));

        EnumMap<EscrowCheckpointStore, byte[]> nullStore = snapshots();
        nullStore.put(EscrowCheckpointStore.CUSTODY, null);
        assertThrows(NullPointerException.class,
                () -> EscrowGlobalStateVerifier.verify(1L, nullStore));
        assertThrows(EscrowRuntimeException.class,
                () -> EscrowGlobalStateVerifier.verify(-1L, snapshots()));
    }

    private static EnumMap<EscrowCheckpointStore, byte[]> snapshots() {
        EnumMap<EscrowCheckpointStore, byte[]> values =
                new EnumMap<>(EscrowCheckpointStore.class);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            values.put(store, new byte[]{(byte) store.wireId(), 2, 3});
        }
        return values;
    }
}
