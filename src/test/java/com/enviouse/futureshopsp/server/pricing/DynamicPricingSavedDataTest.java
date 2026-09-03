package com.enviouse.futureshopsp.server.pricing;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPricingSavedDataTest {
    @Test
    void recordsRoundTripWithVersionedState() {
        DynamicPricingSavedData data = new DynamicPricingSavedData();
        data.recordBuy("default", "diamond", 3);

        DynamicPricingSavedData loaded = DynamicPricingSavedData.load(
                data.save(new CompoundTag(), null), null);

        assertTrue(loaded.integrityValid());
        assertTrue(loaded.allStates().containsKey("default:diamond"));
        assertTrue(loaded.getState("default", "diamond").buysSinceLastCalc == 3);
    }

    @Test
    void wrongStatesContainerBlocksRecovery() {
        CompoundTag saved = new CompoundTag();
        saved.putString("States", "not a compound");

        DynamicPricingSavedData loaded = DynamicPricingSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertTrue(loaded.allStates().isEmpty());
    }

    @Test
    void malformedStateBlocksRecoveryWithoutPartialData() {
        DynamicPricingSavedData data = new DynamicPricingSavedData();
        data.recordBuy("default", "diamond", 3);
        CompoundTag saved = data.save(new CompoundTag(), null);
        saved.getCompound("States").putString("broken", "not a state");

        DynamicPricingSavedData loaded = DynamicPricingSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertTrue(loaded.allStates().isEmpty());
    }

    @Test
    void invalidActivityIsIgnored() {
        DynamicPricingSavedData data = new DynamicPricingSavedData();
        data.recordBuy("default", "diamond", 0);
        data.recordSell("default", "diamond", -1);

        assertTrue(data.allStates().isEmpty());
    }
}
