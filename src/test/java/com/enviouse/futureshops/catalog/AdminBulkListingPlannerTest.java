package com.enviouse.futureshops.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBulkListingPlannerTest {
    private static final UUID REQUEST = UUID.fromString("7c6f0c59-0bd7-4bd2-a865-7be8b5c7e9df");
    private static final Set<String> REGISTRY = Set.of("minecraft:apple", "minecraft:bread");

    @Test
    void previewUsesExactMinorUnitsAndDeterministicRows() {
        AdminBulkListingPlanner.Result result = AdminBulkListingPlanner.preview(
                REQUEST, REGISTRY, root(), List.of(
                        new AdminBulkListingPlanner.Selection("minecraft:bread", "", "Bread"),
                        new AdminBulkListingPlanner.Selection("minecraft:apple", "", "Apple")),
                "Food", "1.00", "10", 2, 512);

        assertTrue(result.valid());
        AdminBulkListingPlanner.Preview preview = result.preview();
        assertEquals(100L, preview.priceMinor());
        assertEquals(10, preview.stock());
        assertEquals("minecraft:apple", preview.rows().get(0).itemId());
        assertEquals(AdminBulkListingPlanner.Action.CREATE, preview.rows().get(0).action());
        assertEquals(AdminBulkListingPlanner.Action.CREATE, preview.rows().get(1).action());
        assertEquals("food", preview.categoryId());
        assertEquals(2, preview.candidate().getAsJsonArray("items").size());
    }

    @Test
    void duplicateAndExistingRowsAreSafeByDefaultAndReplacementPreservesFields() {
        JsonObject root = root();
        JsonObject existing = new JsonObject();
        existing.addProperty("id", "apple_1");
        existing.addProperty("itemId", "minecraft:apple");
        existing.addProperty("buyPrice", 25L);
        existing.addProperty("sellPrice", 4L);
        existing.addProperty("stock", 3);
        existing.addProperty("description", "keep me");
        existing.addProperty("futureField", "keep too");
        root.getAsJsonArray("items").add(existing);

        AdminBulkListingPlanner.Result result = AdminBulkListingPlanner.preview(
                REQUEST, REGISTRY, root, List.of(
                        new AdminBulkListingPlanner.Selection("minecraft:apple", "", "Apple"),
                        new AdminBulkListingPlanner.Selection("minecraft:apple", "", "Apple")),
                "", "2.50", "8", 2, 512);

        assertTrue(result.valid());
        AdminBulkListingPlanner.Preview preview = result.preview();
        assertEquals(2, preview.rows().size());
        assertEquals(AdminBulkListingPlanner.Action.SKIP, preview.rows().get(0).action());
        assertEquals(AdminBulkListingPlanner.Action.SKIP, preview.rows().get(1).action());
        String listingId = preview.rows().stream()
                .filter(AdminBulkListingPlanner.Row::replaceEligible)
                .findFirst().orElseThrow().listingId();
        JsonObject candidate = AdminBulkListingPlanner.apply(preview, Set.of(listingId));
        JsonObject updated = candidate.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(250L, updated.get("buyPrice").getAsLong());
        assertEquals(8, updated.get("stock").getAsInt());
        assertEquals("keep me", updated.get("description").getAsString());
        assertEquals("keep too", updated.get("futureField").getAsString());
        assertEquals(1, root.getAsJsonArray("items").size());
    }

    @Test
    void canonicalNbtOrderCollapsesAndInvalidInputDoesNotCreatePreview() {
        AdminBulkListingIdentity.Result first = AdminBulkListingIdentity.parse(
                "minecraft:apple", "{z:1,a:{b:2,c:3}}");
        AdminBulkListingIdentity.Result second = AdminBulkListingIdentity.parse(
                "minecraft:apple", "{a:{c:3,b:2},z:1}");
        assertTrue(first.valid());
        assertTrue(second.valid());
        assertEquals(first.identity().canonicalNbt(), second.identity().canonicalNbt());
        assertEquals(first.identity().digest(), second.identity().digest());

        AdminBulkListingPlanner.Result invalid = AdminBulkListingPlanner.preview(
                REQUEST, REGISTRY, root(),
                List.of(new AdminBulkListingPlanner.Selection("minecraft:missing", "")),
                "", "1.00", "10", 2, 512);
        assertFalse(invalid.valid());
    }

    @Test
    void replayOutcomesSurviveSavedDataRoundTrip() {
        UUID requestId = UUID.randomUUID();
        AdminBulkListingPlanner.Preview preview = new AdminBulkListingPlanner.Preview(
                requestId, "registry", "catalog", "materials", "1.00", "∞", 100L, -1,
                List.of(new AdminBulkListingPlanner.Row(1, "minecraft:stone", "", "", "digest",
                        "stone_1", "Stone", AdminBulkListingPlanner.Action.CREATE,
                        "new listing", false)),
                "preview", new JsonObject());

        AdminBulkReplaySavedData original = new AdminBulkReplaySavedData();
        original.record(requestId, preview);
        AdminBulkReplaySavedData restored = AdminBulkReplaySavedData.load(original.save(new CompoundTag()));

        AdminBulkListingPlanner.Preview replayed = restored.find(requestId).orElseThrow();
        assertEquals(preview.fingerprint(), replayed.fingerprint());
        assertEquals(AdminBulkListingPlanner.Action.CREATE, replayed.rows().get(0).action());
    }

    @Test
    void duplicateListingIdsBlockUnsafeReplacement() {
        JsonObject root = root();
        JsonObject first = new JsonObject();
        first.addProperty("id", "shared");
        first.addProperty("itemId", "minecraft:apple");
        first.addProperty("buyPrice", 10L);
        first.addProperty("sellPrice", 0L);
        first.addProperty("stock", 1);
        JsonObject second = new JsonObject();
        second.addProperty("id", "shared");
        second.addProperty("itemId", "minecraft:bread");
        second.addProperty("buyPrice", 10L);
        second.addProperty("sellPrice", 0L);
        second.addProperty("stock", 1);
        root.getAsJsonArray("items").add(first);
        root.getAsJsonArray("items").add(second);

        AdminBulkListingPlanner.Result result = AdminBulkListingPlanner.preview(
                REQUEST, REGISTRY, root,
                List.of(new AdminBulkListingPlanner.Selection("minecraft:apple", "")),
                "", "1.00", "1", 2, 512);

        assertTrue(result.valid());
        assertEquals(AdminBulkListingPlanner.Action.BLOCKING, result.preview().rows().get(0).action());
        assertFalse(result.preview().rows().get(0).replaceEligible());
    }

    private static JsonObject root() {
        JsonObject root = new JsonObject();
        root.add("items", new JsonArray());
        root.add("categories", new JsonArray());
        return root;
    }
}
