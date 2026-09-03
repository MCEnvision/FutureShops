package com.enviouse.futureshopsp.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FranchiseSavedDataTest {
    private static final UUID FRANCHISE = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void roundTripsValidFranchise() {
        CompoundTag saved = franchiseTag(FRANCHISE, LEADER, "Traders", memberList(LEADER));

        FranchiseSavedData loaded = FranchiseSavedData.load(root(saved), null);

        assertTrue(loaded.integrityValid());
        assertTrue(loaded.getFranchise(LEADER).getMembers().contains(LEADER));
    }

    @Test
    void wrongFranchiseContainerTypeBlocksRecovery() {
        CompoundTag saved = new CompoundTag();
        saved.putString("Franchises", "not a list");

        FranchiseSavedData loaded = FranchiseSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertNull(loaded.getFranchise(LEADER));
    }

    @Test
    void malformedLaterFranchiseDoesNotPartiallyReconstruct() {
        CompoundTag saved = root(franchiseTag(FRANCHISE, LEADER, "Traders", memberList(LEADER)));
        ListTag franchises = saved.getList("Franchises", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag malformed = new CompoundTag();
        malformed.putUUID("Id", UUID.fromString("00000000-0000-0000-0000-000000000043"));
        malformed.putUUID("Leader", UUID.fromString("00000000-0000-0000-0000-000000000044"));
        malformed.putString("Name", "Broken");
        malformed.putLong("CreatedAt", 1L);
        malformed.putString("Members", "not a list");
        franchises.add(malformed);

        FranchiseSavedData loaded = FranchiseSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertNull(loaded.getFranchise(LEADER));
    }

    @Test
    void duplicateMemberAcrossFranchisesBlocksRecovery() {
        UUID secondFranchise = UUID.fromString("00000000-0000-0000-0000-000000000045");
        UUID secondLeader = UUID.fromString("00000000-0000-0000-0000-000000000046");
        CompoundTag saved = root(franchiseTag(FRANCHISE, LEADER, "One", memberList(LEADER)));
        saved.getList("Franchises", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(franchiseTag(secondFranchise, secondLeader, "Two", memberList(secondLeader, LEADER)));

        FranchiseSavedData loaded = FranchiseSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertNull(loaded.getFranchise(LEADER));
    }

    @Test
    void newerSchemaBlocksRecovery() {
        CompoundTag saved = root(franchiseTag(FRANCHISE, LEADER, "Traders", memberList(LEADER)));
        saved.putInt("schemaVersion", 2);

        FranchiseSavedData loaded = FranchiseSavedData.load(saved, null);

        assertFalse(loaded.integrityValid());
        assertNull(loaded.getFranchise(LEADER));
    }

    private static CompoundTag root(CompoundTag franchise) {
        CompoundTag root = new CompoundTag();
        ListTag franchises = new ListTag();
        franchises.add(franchise);
        root.put("Franchises", franchises);
        return root;
    }

    private static CompoundTag franchiseTag(UUID id, UUID leader, String name, ListTag members) {
        CompoundTag franchise = new CompoundTag();
        franchise.putUUID("Id", id);
        franchise.putUUID("Leader", leader);
        franchise.putString("Name", name);
        franchise.putLong("CreatedAt", 1L);
        franchise.put("Members", members);
        return franchise;
    }

    private static ListTag memberList(UUID... members) {
        ListTag list = new ListTag();
        for (UUID member : members) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", member);
            list.add(entry);
        }
        return list;
    }
}
