package com.enviouse.futureshopsp.money;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HIGH-BLAST-RADIUS GUARD for the coin legacy-rescue (Decision B). Runs on a booted MinecraftServer
 * so the {@code futureshops:money} item and {@code futureshops:coin_data} component are registered.
 *
 * Invariants locked in:
 *  - a rescued legacy coin is COMPONENT-IDENTICAL to a freshly minted coin of the same batch
 *    (so they stack) — the residual empty custom_data must be removed, not left dangling;
 *  - the checksum integrity gate runs identically on a rescued coin, so a tampered legacy coin
 *    fails exactly like a tampered fresh one;
 *  - unrelated custom_data keys survive the rescue.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class CoinRescueTest {

    @BeforeEach
    void fixSalt() {
        // Deterministic salt for checksum computation in-test (overrides whatever config loaded).
        Config.moneyChecksumSalt = "test-salt";
        Config.moneyMaxAgeDays = 365;
    }

    private static CompoundTag legacyCoinTag(long denom, String mintId, long ts, String player,
                                             String server, int authorized, String checksum) {
        CompoundTag md = new CompoundTag();
        md.putLong("denomination", denom);
        md.putString("mint_id", mintId);
        md.putLong("mint_timestamp", ts);
        md.putString("mint_player", player);
        md.putString("mint_server", server);
        md.putInt("authorized_count", authorized);
        md.putString("checksum", checksum);
        return md;
    }

    @Test
    void rescuedLegacyCoinIsComponentIdenticalToFresh(MinecraftServer server) {
        long ts = Instant.now().getEpochSecond();
        String checksum = MoneyChecksumService.createChecksum(100L, "mintA", ts, "p1", "srv", 8);
        CoinData expected = new CoinData(100L, "mintA", ts, "p1", "srv", 8, checksum);

        // Fresh coin: typed component only.
        ItemStack fresh = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        fresh.set(ModDataComponents.COIN_DATA.get(), expected);

        // Legacy coin: same data sitting in minecraft:custom_data (where the DataFixer puts it).
        CompoundTag root = new CompoundTag();
        root.put("futureshops:coin_data", legacyCoinTag(100L, "mintA", ts, "p1", "srv", 8, checksum));
        ItemStack legacy = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        assertNull(legacy.get(ModDataComponents.COIN_DATA.get()), "precondition: legacy has no typed component");

        MoneyValidationResult result = MoneyValidationService.validate(legacy);
        assertTrue(result.valid(), "rescued legacy coin must validate; got " + result.errorCode());

        // Promoted to the typed component...
        assertEquals(expected, legacy.get(ModDataComponents.COIN_DATA.get()));
        // ...and the now-empty custom_data is removed entirely (not left as a residual component).
        assertNull(legacy.get(DataComponents.CUSTOM_DATA), "empty custom_data must be removed after rescue");

        // THE invariant: rescued coin stacks with a fresh coin of the same batch.
        assertTrue(ItemStack.isSameItemSameComponents(legacy, fresh),
                "rescued coin must be component-identical to a fresh coin of the same batch");
    }

    @Test
    void tamperedLegacyCoinFailsChecksumLikeFresh(MinecraftServer server) {
        long ts = Instant.now().getEpochSecond();
        // Checksum computed over the ORIGINAL denomination (100), but the stored denomination is tampered to 999.
        String checksumForOriginal = MoneyChecksumService.createChecksum(100L, "mintB", ts, "p1", "srv", 8);
        CompoundTag root = new CompoundTag();
        root.put("futureshops:coin_data", legacyCoinTag(999L, "mintB", ts, "p1", "srv", 8, checksumForOriginal));
        ItemStack legacy = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        MoneyValidationResult result = MoneyValidationService.validate(legacy);
        assertFalse(result.valid(), "tampered legacy coin must be rejected");
        assertEquals("BAD_CHECKSUM", result.errorCode());
    }

    @Test
    void rescueKeepsUnrelatedCustomDataKeys(MinecraftServer server) {
        long ts = Instant.now().getEpochSecond();
        String checksum = MoneyChecksumService.createChecksum(100L, "mintC", ts, "p1", "srv", 8);
        CompoundTag root = new CompoundTag();
        root.put("futureshops:coin_data", legacyCoinTag(100L, "mintC", ts, "p1", "srv", 8, checksum));
        root.putString("someothermod:note", "keep-me");
        ItemStack legacy = new ItemStack(ModItems.MONEY_ITEM.get(), 1);
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        MoneyValidationResult result = MoneyValidationService.validate(legacy);
        assertTrue(result.valid(), "valid legacy coin must validate; got " + result.errorCode());

        // coin_data sub-key stripped, but the unrelated key (and the component) survive.
        CustomData remaining = legacy.get(DataComponents.CUSTOM_DATA);
        assertNotNull(remaining, "custom_data with other keys must survive");
        CompoundTag remainingTag = remaining.copyTag();
        assertFalse(remainingTag.contains("futureshops:coin_data"), "coin_data remnant must be stripped");
        assertEquals("keep-me", remainingTag.getString("someothermod:note"));
    }
}
