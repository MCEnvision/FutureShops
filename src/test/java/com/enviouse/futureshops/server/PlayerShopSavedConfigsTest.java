package com.enviouse.futureshops.server;

import com.enviouse.futureshops.server.shop.PlayerShopSavedConfigs;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for the named saved-config store (no server bootstrap needed). */
public class PlayerShopSavedConfigsTest {

    private static CompoundTag snap(String marker) {
        CompoundTag t = new CompoundTag();
        t.putString("ShopName", marker);
        return t;
    }

    @Test
    void saveGetDeleteRoundTrip() {
        PlayerShopSavedConfigs store = new PlayerShopSavedConfigs();
        UUID p = new UUID(1L, 2L);
        assertTrue(store.save(p, "alpha", snap("A")));
        assertEquals("A", store.get(p, "alpha").getString("ShopName"));
        assertEquals(java.util.List.of("alpha"), store.names(p));
        assertTrue(store.delete(p, "alpha"));
        assertNull(store.get(p, "alpha"));
        assertTrue(store.names(p).isEmpty());
    }

    @Test
    void overlongNameNormalizesSymmetrically() {
        PlayerShopSavedConfigs store = new PlayerShopSavedConfigs();
        UUID p = new UUID(9L, 10L);
        String longName = "x".repeat(PlayerShopSavedConfigs.MAX_NAME_LENGTH + 10);
        assertTrue(store.save(p, longName, snap("L")));
        // get/delete with the SAME over-length name must resolve to the clamped key.
        assertNotNull(store.get(p, longName));
        assertEquals("L", store.get(p, longName).getString("ShopName"));
        assertTrue(store.delete(p, longName));
        assertTrue(store.names(p).isEmpty());
    }

    @Test
    void blankNameRejected() {
        PlayerShopSavedConfigs store = new PlayerShopSavedConfigs();
        UUID p = new UUID(3L, 4L);
        assertFalse(store.save(p, "   ", snap("X")));
        assertFalse(store.save(p, null, snap("X")));
        assertTrue(store.names(p).isEmpty());
    }

    @Test
    void overwriteKeepsUnderCap() {
        PlayerShopSavedConfigs store = new PlayerShopSavedConfigs();
        UUID p = new UUID(5L, 6L);
        for (int i = 0; i < PlayerShopSavedConfigs.MAX_PER_PLAYER; i++) {
            assertTrue(store.save(p, "cfg" + i, snap("v" + i)));
        }
        // Overwriting an existing name must succeed even at the cap.
        assertTrue(store.save(p, "cfg0", snap("updated")));
        assertEquals("updated", store.get(p, "cfg0").getString("ShopName"));
        // A NEW name beyond the cap is rejected.
        assertFalse(store.save(p, "one-too-many", snap("nope")));
        assertNull(store.get(p, "one-too-many"));
        assertEquals(PlayerShopSavedConfigs.MAX_PER_PLAYER, store.names(p).size());
    }

    @Test
    void namesPreserveInsertionOrderAcrossSaveLoad() {
        PlayerShopSavedConfigs store = new PlayerShopSavedConfigs();
        UUID p = new UUID(7L, 8L);
        store.save(p, "first", snap("1"));
        store.save(p, "second", snap("2"));
        store.save(p, "third", snap("3"));
        CompoundTag saved = store.save(new CompoundTag());
        PlayerShopSavedConfigs reloaded = PlayerShopSavedConfigs.load(saved);
        assertEquals(java.util.List.of("first", "second", "third"), reloaded.names(p));
        assertNotNull(reloaded.get(p, "second"));
        assertEquals("2", reloaded.get(p, "second").getString("ShopName"));
    }
}
