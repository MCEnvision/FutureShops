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
class PlayerShopBarterEscrowSavedDataTest {
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Test
    void exactStackLifecycleSurvivesUncleanSave(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000302");
        List<ItemStack> stacks = List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.DIAMOND, 1));
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();

        assertTrue(data.prepare(request, BUYER, 42L, "minecraft:overworld", "minecraft:diamond", 3,
                stacks, provider));
        data.markUnclean();

        CompoundTag saved = data.save(new CompoundTag(), provider);
        PlayerShopBarterEscrowSavedData recovered = PlayerShopBarterEscrowSavedData.load(saved, provider);

        assertFalse(recovered.cleanMarkerValid());
        assertTrue(recovered.integrityValid());
        assertEquals(PlayerShopBarterEscrowSavedData.State.PREPARED,
                recovered.find(request).state());
        assertTrue(recovered.markRemoved(request, stacks, provider));
        assertFalse(recovered.markComplete(request));
        assertTrue(recovered.markStored(request));

        CompoundTag stored = recovered.save(new CompoundTag(), provider);
        PlayerShopBarterEscrowSavedData storedAgain = PlayerShopBarterEscrowSavedData.load(stored, provider);
        assertEquals(PlayerShopBarterEscrowSavedData.State.STORED,
                storedAgain.find(request).state());
        assertTrue(storedAgain.markComplete(request));
        assertFalse(storedAgain.hasIncompleteRecords());
    }

    @Test
    void checksumTamperingDoesNotReconstructARecord(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000303");
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();
        assertTrue(data.prepare(request, BUYER, 43L, "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        CompoundTag saved = data.save(new CompoundTag(), provider);
        ListTag records = saved.getList("records", 10);
        ((CompoundTag) records.get(0)).putString("checksum", "tampered");

        PlayerShopBarterEscrowSavedData recovered = PlayerShopBarterEscrowSavedData.load(saved, provider);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }

    @Test
    void malformedRecordDoesNotPartiallyReconstructEscrow(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();
        assertTrue(data.prepare(UUID.fromString("00000000-0000-0000-0000-00000000030a"), BUYER, 430L,
                "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));
        assertTrue(data.prepare(UUID.fromString("00000000-0000-0000-0000-00000000030b"), BUYER, 431L,
                "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        CompoundTag saved = data.save(new CompoundTag(), provider);
        ListTag records = saved.getList("records", 10);
        ((CompoundTag) records.get(1)).putString("checksum", "tampered");

        PlayerShopBarterEscrowSavedData recovered = PlayerShopBarterEscrowSavedData.load(saved, provider);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }

    @Test
    void wrongRecordsTagTypeBlocksRecovery(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        CompoundTag saved = new CompoundTag();
        saved.putString("records", "not a list");

        PlayerShopBarterEscrowSavedData recovered = PlayerShopBarterEscrowSavedData.load(saved, provider);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }

    @Test
    void mismatchedRemovedContentsStayPreparedAndCanBeFrozen(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000304");
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();
        assertTrue(data.prepare(request, BUYER, 44L, "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        assertFalse(data.markRemoved(request, List.of(new ItemStack(Items.EMERALD)), provider));
        assertEquals(PlayerShopBarterEscrowSavedData.State.PREPARED, data.find(request).state());
        assertTrue(data.markRecoveryRequired(request));
        assertTrue(data.hasIncompleteRecords());
    }

    @Test
    void storedPaymentCanRefundButCompletedPaymentCannot(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        UUID storedRequest = UUID.fromString("00000000-0000-0000-0000-000000000305");
        UUID completeRequest = UUID.fromString("00000000-0000-0000-0000-000000000306");
        List<ItemStack> stacks = List.of(new ItemStack(Items.DIAMOND));

        PlayerShopBarterEscrowSavedData stored = new PlayerShopBarterEscrowSavedData();
        assertTrue(stored.prepare(storedRequest, BUYER, 45L, "minecraft:overworld", "minecraft:diamond", 1,
                stacks, provider));
        assertTrue(stored.markRemoved(storedRequest, stacks, provider));
        assertTrue(stored.markStored(storedRequest));
        assertTrue(stored.markRefunded(storedRequest));

        PlayerShopBarterEscrowSavedData complete = new PlayerShopBarterEscrowSavedData();
        assertTrue(complete.prepare(completeRequest, BUYER, 46L, "minecraft:overworld", "minecraft:diamond", 1,
                stacks, provider));
        assertTrue(complete.markRemoved(completeRequest, stacks, provider));
        assertTrue(complete.markStored(completeRequest));
        assertTrue(complete.markComplete(completeRequest));
        assertFalse(complete.markRefunded(completeRequest));
    }

    @Test
    void oversizedIdentifiersAreRejectedBeforePersistence(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();
        List<ItemStack> stacks = List.of(new ItemStack(Items.DIAMOND));

        assertFalse(data.prepare(UUID.fromString("00000000-0000-0000-0000-000000000307"), BUYER, 47L,
                "d".repeat(129), "minecraft:diamond", 1, stacks, provider));
        assertFalse(data.prepare(UUID.fromString("00000000-0000-0000-0000-00000000030c"), BUYER, 48L,
                null, "minecraft:diamond", 1, stacks, provider));
        assertFalse(data.prepare(UUID.fromString("00000000-0000-0000-0000-000000000308"), BUYER, 48L,
                "minecraft:overworld", "i".repeat(257), 1, stacks, provider));
        assertTrue(data.snapshot().isEmpty());
    }

    @Test
    void oversizedPersistedIdentifiersAreReadOnly(MinecraftServer server) {
        HolderLookup.Provider provider = server.registryAccess();
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000309");
        assertTrue(data.prepare(request, BUYER, 49L, "minecraft:overworld", "minecraft:diamond", 1,
                List.of(new ItemStack(Items.DIAMOND)), provider));

        CompoundTag saved = data.save(new CompoundTag(), provider);
        CompoundTag record = (CompoundTag) saved.getList("records", 10).get(0);
        record.putString("dimension", "d".repeat(129));
        PlayerShopBarterEscrowSavedData recovered = PlayerShopBarterEscrowSavedData.load(saved, provider);

        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshot().isEmpty());
    }
}
