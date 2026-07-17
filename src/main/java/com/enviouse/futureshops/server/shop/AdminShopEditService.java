package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.AdminShopConfigWriter;
import com.enviouse.futureshops.catalog.AdminShopItemSpec;
import com.enviouse.futureshops.catalog.CategoryDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SAdminShopAddItemsPacket;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import com.enviouse.futureshops.network.packets.S2CAdminEditAckPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-side handler for the in-GUI admin shop editor
 * ({@link C2SAdminShopEditPacket} / {@link C2SAdminShopAddItemsPacket}).
 *
 * <p>THIS is the security gate — the client edit button is convenience only. Every handler
 * validates {@code player.hasPermissions(2)} first and re-validates all inputs (string lengths,
 * price/stock ranges, registry-id resolution, category-id normalization) before touching either
 * store. Category operations orchestrate BOTH stores (admin.json via {@link AdminShopConfigWriter}
 * + {@link AdminCategorySavedData}), mirroring ShopAdminCommand's dual-store discipline.
 *
 * <p>Every result is acked with an {@link S2CAdminEditAckPacket} string code the client localizes;
 * successful mutations additionally refresh all live sessions (without the nearby-shop scan —
 * catalog edits can't change nearby player shops).
 */
public final class AdminShopEditService {

    private static final int NAME_CAP = 48;

    private AdminShopEditService() {
    }

    public static void handleEdit(ServerPlayer player, C2SAdminShopEditPacket packet) {
        if (player == null || !player.hasPermissions(2)) {
            ack(player, false, "DENIED", "");
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        String action = packet.action() == null ? "" : packet.action().trim();
        switch (action) {
            case "SAVE_LISTING" -> saveListing(player, server, packet);
            case "REMOVE_LISTING" -> removeListing(player, server, packet);
            case "BARTER_OFF" -> barterOff(player, server, packet);
            case "ADD_HELD_ITEM" -> addHeldItem(player, server, packet);
            case "ADD_HELD_ITEM_BARTER" -> addHeldItemBarter(player, server, packet);
            case "ADD_BARTER_TARGET_HELD" -> addBarterTargetHeld(player, server, packet);
            case "ADD_BARTER_INGREDIENT_HELD" -> addBarterIngredientHeld(player, server, packet);
            case "ADD_BARTER_INGREDIENT" -> addBarterIngredient(player, server, packet);
            case "REMOVE_BARTER_INGREDIENT" -> removeBarterIngredient(player, server, packet);
            case "SET_BARTER_INGREDIENT_COUNT" -> setBarterIngredientCount(player, server, packet);
            case "SET_BARTER_OUTPUT" -> setBarterOutput(player, server, packet);
            case "ADD_CATEGORY" -> addCategory(player, server, packet);
            case "RENAME_CATEGORY" -> renameCategory(player, server, packet);
            case "MOVE_CATEGORY" -> moveCategory(player, server, packet);
            case "REMOVE_CATEGORY" -> removeCategory(player, server, packet);
            default -> ack(player, false, "INVALID", "");
        }
    }

    public static void handleAddItems(ServerPlayer player, C2SAdminShopAddItemsPacket packet) {
        if (player == null || !player.hasPermissions(2)) {
            ack(player, false, "DENIED", "");
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        String categoryId = normalizeId(packet.categoryId());
        long buyMinor = Math.max(0L, packet.buyMinor());
        long sellMinor = Math.max(0L, packet.sellMinor());
        int stock = clampStock(packet.stock());

        // Re-resolve every id against the registry — the picker is client-side and untrusted.
        LinkedHashSet<String> validIds = new LinkedHashSet<>();
        for (String raw : packet.itemIds()) {
            if (raw == null) continue;
            ResourceLocation rl = ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) continue;
            validIds.add(rl.toString());
        }
        if (validIds.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }

        // Barter picker: create target listings with empty recipes, then let the client walk through
        // the searchable ingredient editor for each one. Empty recipes are rejected by the trade
        // service, so abandoning the editor cannot create a free trade.
        if (packet.barter()) {
            List<AdminShopItemSpec> barterSpecs = new ArrayList<>(validIds.size());
            for (String itemId : validIds) {
                barterSpecs.add(new AdminShopItemSpec("", itemId, registryDisplayName(itemId), 0L, 0L,
                        stock, 0, categoryId, "", 0L));
            }
            List<String> listingIds = AdminShopConfigWriter.addBarterTargets(
                    server, barterSpecs, packet.outputCount());
            if (listingIds.isEmpty()) {
                ack(player, false, "IO_ERROR", "");
                return;
            }
            ShopDataService.resendActiveSessions(server, false);
            ack(player, true, "BARTER_TARGETS_CREATED", String.join(",", listingIds));
            return;
        }

        List<AdminShopItemSpec> specs = new ArrayList<>(validIds.size());
        for (String itemId : validIds) {
            specs.add(new AdminShopItemSpec("", itemId, registryDisplayName(itemId), buyMinor, sellMinor,
                    stock, 0, categoryId, "", 0L));
        }
        int added = AdminShopConfigWriter.addItems(server, specs);
        if (added <= 0) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "ADDED", Integer.toString(added));
    }

    // ─── Listing actions ─────────────────────────────────────────────────────

    private static void saveListing(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        if (listingId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        String displayName = cap(trimmed(packet.stringA()));
        String categoryId = normalizeId(packet.stringB());
        long buyMinor = Math.max(0L, packet.longA());
        long sellMinor = Math.max(0L, packet.longB());
        int stock = clampStock(packet.longC());

        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        if (existing == null) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        if (!AdminShopConfigWriter.updateListingFields(server, listingId, displayName, categoryId,
                buyMinor, sellMinor, stock)) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        // A SavedData assignment (keyed by registry itemId, so SHARED by every listing of that item)
        // shadows the "all" JSON fallback. Only clear it when the effective category genuinely
        // changed AND no OTHER listing of the same registry item still relies on it — otherwise a
        // no-op save, or moving one NBT variant, would silently recategorize its siblings to All.
        AdminCategorySavedData catData = AdminCategorySavedData.get(server);
        String existingEffective = existing.categoryId();
        if ("all".equalsIgnoreCase(effectiveCategory(existingEffective))) {
            String assigned = catData.getItemCategory(existing.itemId());
            if (assigned != null && !assigned.isBlank()) {
                existingEffective = assigned;
            }
        }
        boolean categoryChanged = !effectiveCategory(existingEffective).equals(effectiveCategory(categoryId));
        if (categoryChanged) {
            boolean siblingRelies = AdminShopConfigWriter.listItems(server, null).stream()
                    .anyMatch(v -> !v.listingId().equalsIgnoreCase(listingId)
                            && v.itemId().equalsIgnoreCase(existing.itemId())
                            && "all".equalsIgnoreCase(effectiveCategory(v.categoryId())));
            if (!siblingRelies) {
                catData.unassignItem(existing.itemId());
            }
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void removeListing(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        if (listingId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        if (!AdminShopConfigWriter.removeById(server, listingId)) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void barterOff(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        if (listingId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        if (existing == null) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        int removed = AdminShopConfigWriter.removeBarterRecipesTargeting(server, existing.listingId(), existing.itemId());
        if (removed <= 0) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void addHeldItem(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ack(player, false, "EMPTY_HAND", "");
            return;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (key == null) {
            ack(player, false, "INVALID", "");
            return;
        }
        String categoryId = normalizeId(packet.stringB());
        long buyMinor = Math.max(0L, packet.longA());
        long sellMinor = Math.max(0L, packet.longB());
        int stock = clampStock(packet.longC());
        String nbtJson = held.getTag() != null ? held.getTag().toString() : "";
        String displayName = cap(held.getHoverName().getString());

        AdminShopItemSpec spec = new AdminShopItemSpec("", key.toString(), displayName, buyMinor, sellMinor,
                stock, 0, categoryId, nbtJson, 0L);
        String assignedId = AdminShopConfigWriter.addWithGeneratedId(server, spec);
        if (assignedId == null || assignedId.isBlank()) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "ADDED", "1");
    }

    /**
     * Barter add: the MAIN-hand item becomes the barter output listing, the OFF-hand item its payment
     * ingredient. Money buy/sell are 0 (barter-only). {@code longA} = output count, {@code longB} =
     * ingredient count, {@code longC} = stock, {@code stringB} = category. Creates the listing AND its
     * barter recipe atomically so the item shows in the Barter tab.
     */
    private static void addHeldItemBarter(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        ItemStack output = player.getMainHandItem();
        if (output.isEmpty()) {
            ack(player, false, "EMPTY_HAND", "");
            return;
        }
        ItemStack ingredient = player.getOffhandItem();
        if (ingredient.isEmpty()) {
            ack(player, false, "EMPTY_OFFHAND", "");
            return;
        }
        ResourceLocation outKey = ForgeRegistries.ITEMS.getKey(output.getItem());
        ResourceLocation ingKey = ForgeRegistries.ITEMS.getKey(ingredient.getItem());
        if (outKey == null || ingKey == null) {
            ack(player, false, "INVALID", "");
            return;
        }
        String categoryId = normalizeId(packet.stringB());
        int outputCount = (int) Math.max(1L, packet.longA());
        int ingredientCount = (int) Math.max(1L, packet.longB());
        int stock = clampStock(packet.longC());
        String nbtJson = output.getTag() != null ? output.getTag().toString() : "";
        String displayName = cap(output.getHoverName().getString());

        AdminShopItemSpec spec = new AdminShopItemSpec("", outKey.toString(), displayName, 0L, 0L,
                stock, 0, categoryId, nbtJson, 0L);
        int added = AdminShopConfigWriter.addItemsWithBarter(server, java.util.List.of(spec),
                outputCount, ingKey.toString(), ingredientCount);
        if (added <= 0) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "ADDED", "1");
    }

    // ─── Multi-ingredient barter editor ──────────────────────────────────────
    // The OP creates a barter target from the held item, then adds ingredients one held item at a
    // time (no off-hand). Money prices are 0 (barter-only). The client opens the barter editor on the
    // BARTER_TARGET_CREATED ack, keyed by the returned listingId.

    private static void addBarterTargetHeld(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ack(player, false, "EMPTY_HAND", "");
            return;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (key == null) {
            ack(player, false, "INVALID", "");
            return;
        }
        String categoryId = normalizeId(packet.stringB());
        int outputCount = (int) Math.max(1L, packet.longA());
        String nbtJson = held.getTag() != null ? held.getTag().toString() : "";
        // Barter targets default to unlimited stock; the OP can cap it later via Edit Listing.
        AdminShopItemSpec spec = new AdminShopItemSpec("", key.toString(), cap(held.getHoverName().getString()),
                0L, 0L, -1, 0, categoryId, nbtJson, 0L);
        String listingId = AdminShopConfigWriter.addBarterTargetHeld(server, spec, outputCount);
        if (listingId == null || listingId.isBlank()) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "BARTER_TARGET_CREATED", listingId);
    }

    private static void addBarterIngredientHeld(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        if (listingId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        if (existing == null) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ack(player, false, "EMPTY_HAND", "");
            return;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (key == null) {
            ack(player, false, "INVALID", "");
            return;
        }
        int count = (int) Math.max(1L, packet.longA());
        String nbtJson = held.getTag() != null ? held.getTag().toString() : "";
        if (!AdminShopConfigWriter.addBarterIngredient(server, existing.listingId(), key.toString(), nbtJson, count)) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    /** Registry-picker ingredient add. Pristine creative-menu selections intentionally carry no NBT. */
    private static void addBarterIngredient(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        ResourceLocation ingredientId = ResourceLocation.tryParse(trimmed(packet.stringA()).toLowerCase(Locale.ROOT));
        if (existing == null || ingredientId == null || !ForgeRegistries.ITEMS.containsKey(ingredientId)) {
            ack(player, false, "INVALID", "");
            return;
        }
        int count = (int) Math.max(1L, packet.longA());
        if (!AdminShopConfigWriter.addBarterIngredient(
                server, existing.listingId(), ingredientId.toString(), "", count)) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void removeBarterIngredient(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        if (listingId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        if (existing == null) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        if (!AdminShopConfigWriter.removeBarterIngredient(server, existing.listingId(), (int) packet.longA())) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void setBarterOutput(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        if (listingId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        if (existing == null) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        if (!AdminShopConfigWriter.setBarterOutputCount(server, existing.listingId(), (int) Math.max(1L, packet.longA()))) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void setBarterIngredientCount(ServerPlayer player, MinecraftServer server,
                                                 C2SAdminShopEditPacket packet) {
        String listingId = trimmed(packet.targetId());
        AdminShopConfigWriter.AdminListingView existing = findListing(server, listingId);
        int index = (int) packet.longA();
        int count = (int) Math.max(1L, packet.longB());
        if (existing == null || !AdminShopConfigWriter.setBarterIngredientCount(
                server, existing.listingId(), index, count)) {
            ack(player, false, "NOT_FOUND", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    // ─── Category actions ────────────────────────────────────────────────────

    private static void addCategory(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String name = cap(trimmed(packet.stringA()));
        if (name.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        boolean addedSaved = AdminCategorySavedData.get(server).addCategory(name);
        boolean addedJson = AdminShopConfigWriter.addCategory(server, name);
        if (!addedSaved && !addedJson) {
            ack(player, false, "DUPLICATE", "");
            return;
        }
        // Mirror the command: register in the player-shop department registry for autocomplete.
        DepartmentSavedData.get(server).addDepartment(name);
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void renameCategory(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String categoryId = normalizeId(packet.targetId());
        String newName = cap(trimmed(packet.stringA()));
        if (categoryId.isEmpty() || newName.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        String newId = normalizeId(newName);
        if (!newId.equals(categoryId) && categoryExists(server, newId)) {
            ack(player, false, "DUPLICATE", "");
            return;
        }
        // JSON store owns the tab when present: rewrite ONLY its displayName (id + sortOrder stay
        // stable, so item categoryIds and ordering survive). The SavedData twin entry is left as-is
        // on purpose — its name still normalizes to the stable JSON id, keeping assignments routed
        // to the renamed tab; migrating it would mint a second tab under normalize(newName).
        if (AdminShopConfigWriter.renameCategory(server, categoryId, newName)) {
            ShopDataService.resendActiveSessions(server, false);
            ack(player, true, "OK", "");
            return;
        }
        // SavedData-only category: the name IS the identity, so rename it and migrate every
        // itemAssignment value old→new in one step.
        String savedName = findSavedDataName(server, categoryId);
        if (savedName != null && AdminCategorySavedData.get(server).renameCategory(savedName, newName)) {
            ShopDataService.resendActiveSessions(server, false);
            ack(player, true, "OK", "");
            return;
        }
        ack(player, false, "NOT_FOUND", "");
    }

    private static void moveCategory(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String categoryId = normalizeId(packet.targetId());
        long delta = packet.longA();
        if (categoryId.isEmpty() || (delta != -1L && delta != 1L)) {
            ack(player, false, "INVALID", "");
            return;
        }
        List<String> ordered = orderedVisibleCategoryIds(server);
        int idx = indexOfIgnoreCase(ordered, categoryId);
        if (idx < 0) {
            // SavedData-only categories have no sortOrder; promote into admin.json so they can be
            // ordered, then reorder like any other.
            String savedName = findSavedDataName(server, categoryId);
            if (savedName == null || !AdminShopConfigWriter.addCategory(server, savedName)) {
                ack(player, false, "NOT_FOUND", "");
                return;
            }
            ordered = orderedVisibleCategoryIds(server);
            idx = indexOfIgnoreCase(ordered, categoryId);
            if (idx < 0) {
                ack(player, false, "IO_ERROR", "");
                return;
            }
        }
        int swapWith = idx + (int) delta;
        if (swapWith >= 0 && swapWith < ordered.size()) {
            java.util.Collections.swap(ordered, idx, swapWith);
        }
        if (!AdminShopConfigWriter.setCategorySortOrders(server, ordered)) {
            ack(player, false, "IO_ERROR", "");
            return;
        }
        ShopDataService.resendActiveSessions(server, false);
        ack(player, true, "OK", "");
    }

    private static void removeCategory(ServerPlayer player, MinecraftServer server, C2SAdminShopEditPacket packet) {
        String categoryId = normalizeId(packet.targetId());
        if (categoryId.isEmpty()) {
            ack(player, false, "INVALID", "");
            return;
        }
        AdminCategorySavedData data = AdminCategorySavedData.get(server);
        String savedName = findSavedDataName(server, categoryId);
        boolean removedSaved = savedName != null && data.removeCategory(savedName);
        boolean removedJson = AdminShopConfigWriter.removeCategory(server, categoryId);
        if (removedSaved || removedJson) {
            ShopDataService.resendActiveSessions(server, false);
            ack(player, true, "OK", "");
            return;
        }
        // Mirror the command fallback: tombstone a bundled category that isn't physically removable.
        Optional<CategoryDef> bundled = ShopCatalog.findBundledCategory(categoryId);
        if (bundled.isPresent() && data.hideBaseCategory(bundled.get().id())) {
            ShopDataService.resendActiveSessions(server, false);
            ack(player, true, "OK", "");
            return;
        }
        ack(player, false, "NOT_FOUND", "");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static void ack(ServerPlayer player, boolean success, String code, String arg) {
        if (player == null) {
            return;
        }
        ShopPackets.sendToPlayer(player, new S2CAdminEditAckPacket(success, code, arg == null ? "" : arg));
    }

    private static AdminShopConfigWriter.AdminListingView findListing(MinecraftServer server, String listingId) {
        for (AdminShopConfigWriter.AdminListingView view : AdminShopConfigWriter.listItems(server, null)) {
            if (view.listingId().equalsIgnoreCase(listingId)) {
                return view;
            }
        }
        return null;
    }

    /** admin.json category ids in display order, minus the synthetic "all" pseudo-tab. */
    private static List<String> orderedVisibleCategoryIds(MinecraftServer server) {
        List<String> ordered = new ArrayList<>(AdminShopConfigWriter.listCategoryIdsSorted(server));
        ordered.removeIf(id -> "all".equalsIgnoreCase(id));
        return ordered;
    }

    /** The SavedData category name whose normalized id matches, or null. */
    private static String findSavedDataName(MinecraftServer server, String categoryId) {
        for (String name : AdminCategorySavedData.get(server).getAllSorted()) {
            if (normalizeId(name).equals(categoryId)) {
                return name;
            }
        }
        return null;
    }

    private static boolean categoryExists(MinecraftServer server, String categoryId) {
        return indexOfIgnoreCase(AdminShopConfigWriter.listCategoryIdsSorted(server), categoryId) >= 0
                || findSavedDataName(server, categoryId) != null;
    }

    private static int indexOfIgnoreCase(List<String> ids, String needle) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equalsIgnoreCase(needle)) {
                return i;
            }
        }
        return -1;
    }

    /** Server-locale hover name so batch-added listings don't display raw registry ids. */
    private static String registryDisplayName(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (item == null) {
            return "";
        }
        return cap(new ItemStack(item).getHoverName().getString());
    }

    /** Blank and "all" both mean the pseudo All tab. */
    private static String effectiveCategory(String categoryId) {
        String normalized = normalizeId(categoryId);
        return "all".equals(normalized) ? "" : normalized;
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cap(String s) {
        return s.length() > NAME_CAP ? s.substring(0, NAME_CAP) : s;
    }

    /** Matches AdminShopConfigWriter's normalizeId so ids agree across both stores. */
    private static String normalizeId(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static int clampStock(long stock) {
        if (stock < 0L) {
            return -1;
        }
        return (int) Math.min(stock, Integer.MAX_VALUE);
    }
}
