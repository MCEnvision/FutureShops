package com.enviouse.futureshopsp.server.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class PlayerShopSaleEscrowSavedDataTest {
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Test
    void exactStackLifecycleSurvivesUncleanSave(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000402");
        List<ItemStack> stacks = List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.DIAMOND, 1));
        PlayerShopSaleEscrowSavedData data = new PlayerShopSaleEscrowSavedData();

        assertTrue(data.prepare(request, BUYER, 42L, "minecraft:overworld", "minecraft:diamond", 3,
                stacks, provider));
        data.markUnclean();

        CompoundTag saved = data.save(new CompoundTag(), provider);
        PlayerShopSaleEscrowSavedData recovered = PlayerShopSaleEscrowSavedData.load(saved, provider);

        assertFalse(recovered.cleanMarkerValid());
        assertTrue(recovered.integrityValid());
        assertEquals(PlayerShopSaleEscrowSavedData.State.PREPARED, recovered.find(request).orElseThrow().state());
        assertEquals(3, recovered.decodeStacks(request, provider).orElseThrow().stream()
                .mapToInt(ItemStack::getCount).sum());
        assertTrue(recovered.markRemoved(request, stacks, provider));
        assertTrue(recovered.markDelivered(request));
        assertTrue(recovered.markClaimed(request));
        assertFalse(recovered.hasIncompleteRecords());
    }

    @Test
    void checksumTamperingDoesNotReconstructARecord(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000403");
        PlayerShopSaleEscrowSavedData data = new PlayerShopSaleEscrowSavedData();
        assertTrue(data.prepare(request, BUYER, 43L, "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        CompoundTag saved = data.save(new CompoundTag(), provider);
        ListTag records = saved.getList("records", 10);
        ((CompoundTag) records.get(0)).putString("checksum", "tampered");

        PlayerShopSaleEscrowSavedData recovered = PlayerShopSaleEscrowSavedData.load(saved, provider);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }

    @Test
    void malformedRecordDoesNotPartiallyReconstructEscrow(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        PlayerShopSaleEscrowSavedData data = new PlayerShopSaleEscrowSavedData();
        assertTrue(data.prepare(UUID.fromString("00000000-0000-0000-0000-00000000040a"), BUYER, 430L,
                "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));
        assertTrue(data.prepare(UUID.fromString("00000000-0000-0000-0000-00000000040b"), BUYER, 431L,
                "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        CompoundTag saved = data.save(new CompoundTag(), provider);
        ListTag records = saved.getList("records", 10);
        ((CompoundTag) records.get(1)).putString("checksum", "tampered");

        PlayerShopSaleEscrowSavedData recovered = PlayerShopSaleEscrowSavedData.load(saved, provider);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }

    @Test
    void wrongRecordsTagTypeBlocksRecovery(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        CompoundTag saved = new CompoundTag();
        saved.putString("records", "not a list");

        PlayerShopSaleEscrowSavedData recovered = PlayerShopSaleEscrowSavedData.load(saved, provider);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }

    @Test
    void mismatchedRemovedContentsStayPreparedAndCanBeFrozen(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000404");
        PlayerShopSaleEscrowSavedData data = new PlayerShopSaleEscrowSavedData();
        assertTrue(data.prepare(request, BUYER, 44L, "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        assertFalse(data.markRemoved(request, List.of(new ItemStack(Items.EMERALD)), provider));
        assertEquals(PlayerShopSaleEscrowSavedData.State.PREPARED, data.find(request).orElseThrow().state());
        assertTrue(data.markRecoveryRequired(request));
        assertTrue(data.hasIncompleteRecords());
    }

    @Test
    void deliveredSaleCannotBeRefunded(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000405");
        PlayerShopSaleEscrowSavedData data = new PlayerShopSaleEscrowSavedData();
        List<ItemStack> stacks = List.of(new ItemStack(Items.DIAMOND));

        assertTrue(data.prepare(request, BUYER, 45L, "minecraft:overworld", "minecraft:diamond", 1,
                stacks, provider));
        assertTrue(data.markRemoved(request, stacks, provider));
        assertTrue(data.markDelivered(request));
        assertFalse(data.markRefunded(request));
        assertEquals(PlayerShopSaleEscrowSavedData.State.DELIVERED, data.find(request).orElseThrow().state());
        assertTrue(data.markClaimed(request));
        assertFalse(data.markRefunded(request));
    }
}
