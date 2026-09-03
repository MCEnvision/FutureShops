package com.enviouse.futureshopsp.server.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalBalanceSavedDataTest {
    @Test
    void malformedBalanceRecordBlocksPartialLoad() {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000602");
        data.setBalance(first, 10L);
        data.setBalance(second, 20L);

        CompoundTag saved = data.save(new CompoundTag(), null);
        ListTag balances = saved.getList("balances", 10);
        ((CompoundTag) balances.get(1)).putString("balance", "not a long");

        InternalBalanceSavedData recovered = InternalBalanceSavedData.load(saved, null);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshotBalances().isEmpty());
    }

    @Test
    void wrongBalanceTagTypeBlocksLoad() {
        CompoundTag saved = new CompoundTag();
        saved.putString("balances", "not a list");

        InternalBalanceSavedData recovered = InternalBalanceSavedData.load(saved, null);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshotBalances().isEmpty());
    }

    @Test
    void duplicatePlayerRecordBlocksLoad() {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000603");
        data.setBalance(player, 10L);

        CompoundTag saved = data.save(new CompoundTag(), null);
        ListTag balances = saved.getList("balances", 10);
        balances.add(((CompoundTag) balances.get(0)).copy());

        InternalBalanceSavedData recovered = InternalBalanceSavedData.load(saved, null);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshotBalances().isEmpty());
    }
}
