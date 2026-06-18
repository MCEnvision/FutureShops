package com.enviouse.futureshopsp.money;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HIGH-BLAST-RADIUS GUARD: the entire legacy-coin rescue branch in {@link MoneyValidationService}
 * forks on the fact that the vanilla item DataFixer routes a 1.20.1 {@code futureshops:coin_data}
 * NBT compound into the {@code minecraft:custom_data} component under the identical key. That is
 * vanilla MC behaviour that could shift in a future patch — this test asserts it against the actual
 * 3465→current DataFixer chain, so a routing change fails at build time instead of silently breaking
 * coin rescue on a live server.
 */
class LegacyCoinDataFixLandingTest {

    @Test
    void legacyCoinDataLandsInCustomDataUnderIdenticalKey() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        CompoundTag coin = new CompoundTag();
        coin.putLong("denomination", 100L);
        coin.putString("mint_id", "11111111-1111-1111-1111-111111111111");
        coin.putLong("mint_timestamp", 1700000000L);
        coin.putString("mint_player", "22222222-2222-2222-2222-222222222222");
        coin.putString("mint_server", "futureshops-dev");
        coin.putInt("authorized_count", 64);
        coin.putString("checksum", "deadbeefcafe");

        CompoundTag tag = new CompoundTag();
        tag.put("futureshops:coin_data", coin);

        CompoundTag stackNbt = new CompoundTag();
        stackNbt.putString("id", "futureshops:money");
        stackNbt.putByte("Count", (byte) 1);
        stackNbt.put("tag", tag);

        int from = 3465; // MC 1.20.1
        int to = SharedConstants.getCurrentVersion().getDataVersion().getVersion();

        CompoundTag out = (CompoundTag) DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, stackNbt),
                from, to).getValue();

        // The whole rescue design depends on this exact routing:
        assertTrue(out.contains("components", Tag.TAG_COMPOUND), "fixed stack must use components");
        CompoundTag components = out.getCompound("components");
        assertTrue(components.contains("minecraft:custom_data", Tag.TAG_COMPOUND),
                "legacy coin_data must land in minecraft:custom_data");
        CompoundTag custom = components.getCompound("minecraft:custom_data");
        assertTrue(custom.contains("futureshops:coin_data", Tag.TAG_COMPOUND),
                "under the identical futureshops:coin_data key");

        // All 7 fields byte-intact:
        CompoundTag landed = custom.getCompound("futureshops:coin_data");
        assertEquals(100L, landed.getLong("denomination"));
        assertEquals("11111111-1111-1111-1111-111111111111", landed.getString("mint_id"));
        assertEquals(1700000000L, landed.getLong("mint_timestamp"));
        assertEquals("22222222-2222-2222-2222-222222222222", landed.getString("mint_player"));
        assertEquals("futureshops-dev", landed.getString("mint_server"));
        assertEquals(64, landed.getInt("authorized_count"));
        assertEquals("deadbeefcafe", landed.getString("checksum"));

        // Old top-level tag must be fully consumed by componentization.
        assertFalse(out.contains("tag"), "legacy tag must not survive");
    }
}
