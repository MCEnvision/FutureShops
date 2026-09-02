package com.enviouse.futureshops.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCategorySavedDataBoundsTest {
    @Test
    void validStateRoundTripsWithinDefinedBounds() {
        AdminCategorySavedData data = new AdminCategorySavedData();
        assertTrue(data.addCategory("Materials"));
        assertTrue(data.assignItem("minecraft:iron_ingot", "Materials"));
        assertTrue(data.hideBaseCategory("decorations"));

        AdminCategorySavedData restored = AdminCategorySavedData.load(data.save(new CompoundTag()));
        assertEquals(data.getAllSorted(), restored.getAllSorted());
        assertEquals(data.getAllAssignments(), restored.getAllAssignments());
        assertEquals(data.getHiddenBaseCategoryIds(), restored.getHiddenBaseCategoryIds());
    }

    @Test
    void oversizedAndMalformedStateFailsClosed() {
        CompoundTag oversizedCategories = new CompoundTag();
        ListTag categories = new ListTag();
        for (int index = 0; index <= AdminCategorySavedData.MAXIMUM_CATEGORIES; index++) {
            categories.add(StringTag.valueOf("category" + index));
        }
        oversizedCategories.put("Categories", categories);
        assertThrows(RuntimeException.class,
                () -> AdminCategorySavedData.load(oversizedCategories));

        CompoundTag invalidAssignment = new CompoundTag();
        ListTag validCategories = new ListTag();
        validCategories.add(StringTag.valueOf("Materials"));
        invalidAssignment.put("Categories", validCategories);
        CompoundTag assignments = new CompoundTag();
        assignments.putInt("minecraft:iron_ingot", 1);
        invalidAssignment.put("ItemAssignments", assignments);
        assertThrows(IllegalStateException.class,
                () -> AdminCategorySavedData.load(invalidAssignment));
    }
}
