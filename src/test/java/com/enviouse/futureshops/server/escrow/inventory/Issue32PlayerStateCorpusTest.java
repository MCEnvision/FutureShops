package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Issue32PlayerStateCorpusTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void boundedTokenCorpusRejectsMalformedAndNewerStates() {
        PlayerInventoryDeliveryToken token = PlayerInventoryDeliveryToken.create(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                UUID.fromString("10000000-0000-0000-0000-000000000004"),
                UUID.fromString("10000000-0000-0000-0000-000000000005"),
                "issue32.corpus.request",
                hash("asset"), hash("before"), hash("after"));

        String encoded = token.encode();
        List<String> malformed = new ArrayList<>();
        malformed.add("not a token");
        malformed.add(encoded.substring(0, encoded.length() - 2));
        malformed.add(mutate(encoded, encoded.length() / 2, '0'));
        malformed.add(version(encoded, 99));
        malformed.add(withTrailingByte(encoded));

        for (String candidate : malformed) {
            assertThrows(IllegalArgumentException.class,
                    () -> PlayerInventoryDeliveryToken.decode(candidate));
        }
        assertEquals(token, PlayerInventoryDeliveryToken.decode(encoded));
    }

    @Test
    void invalidDeliveryEvidenceDoesNotOverwriteUnrelatedInventoryState() {
        Inventory playerInventory = new Inventory(null);
        List<ItemStack> inventory = playerInventory.items;
        for (int index = 0; index < PlayerInventoryHashes.MAIN_SLOT_COUNT;
             index++) {
            inventory.set(index, ItemStack.EMPTY);
        }
        ItemStack unrelated = new ItemStack(Items.STONE, 4);
        unrelated.getOrCreateTag().putString("foreign_mod", "sentinel");
        inventory.set(35, unrelated);
        PlayerInventoryInsertionPlan plan =
                PlayerInventoryInsertionPlan.plan(inventory,
                        new ItemStack(Items.EMERALD, 2));
        assertTrue(plan.matchesBefore(playerInventory));

        List<ItemStack> changed = new ArrayList<>(plan.resultSlots());
        changed.set(plan.changes().get(0).slot(),
                new ItemStack(Items.DIAMOND, 1));

        playerInventory.items.set(plan.changes().get(0).slot(),
                changed.get(plan.changes().get(0).slot()));
        assertFalse(plan.matchesAfter(playerInventory));
        assertEquals(Items.STONE, changed.get(35).getItem());
        assertEquals("sentinel",
                changed.get(35).getTag().getString("foreign_mod"));
    }

    private static byte[] hash(String value) {
        return PlayerInventoryHashes.hashText(value);
    }

    private static String mutate(String value, int index, char replacement) {
        char[] chars = value.toCharArray();
        chars[index] = chars[index] == replacement ? '1' : replacement;
        return new String(chars);
    }

    private static String version(String value, int version) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        ByteBuffer.wrap(bytes).putInt(8, version);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String withTrailingByte(String value) {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        byte[] expanded = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        expanded[expanded.length - 1] = 7;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(expanded);
    }
}
