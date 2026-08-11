package com.enviouse.futureshops.block;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBlockIdentityNbtTest {
    @Test
    void identityEvidenceRoundTripsThroughBlockNbt() {
        CompoundTag tag = new CompoundTag();
        UUID shopId = new UUID(1L, 2L);

        ShopBlockEntity.writeRegistryIdentity(tag, shopId, 7L);
        ShopBlockEntity.PersistedRegistryIdentity restored =
                ShopBlockEntity.readRegistryIdentity(tag).orElseThrow();

        assertEquals(shopId, restored.shopId());
        assertEquals(7L, restored.revision());
    }

    @Test
    void legacyBlockWithoutIdentityRemainsMigratable() {
        assertTrue(ShopBlockEntity.readRegistryIdentity(
                new CompoundTag()).isEmpty());
    }

    @Test
    void partialWrongTypeZeroAndNegativeEvidenceFailClosed() {
        CompoundTag partial = new CompoundTag();
        partial.putUUID("RegistryShopUUID", new UUID(1L, 2L));
        CompoundTag wrongType = new CompoundTag();
        wrongType.putUUID("RegistryShopUUID", new UUID(1L, 2L));
        wrongType.putString("RegistryIdentityRevision", "seven");
        CompoundTag zero = new CompoundTag();
        zero.putUUID("RegistryShopUUID", new UUID(0L, 0L));
        zero.putLong("RegistryIdentityRevision", 0L);
        CompoundTag negative = new CompoundTag();
        negative.putUUID("RegistryShopUUID", new UUID(1L, 2L));
        negative.putLong("RegistryIdentityRevision", -1L);

        assertThrows(IllegalArgumentException.class,
                () -> ShopBlockEntity.readRegistryIdentity(partial));
        assertThrows(IllegalArgumentException.class,
                () -> ShopBlockEntity.readRegistryIdentity(wrongType));
        assertThrows(IllegalArgumentException.class,
                () -> ShopBlockEntity.readRegistryIdentity(zero));
        assertThrows(IllegalArgumentException.class,
                () -> ShopBlockEntity.readRegistryIdentity(negative));
    }
}
