package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.catalog.BarterRecipeDef;
import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.catalog.ShopDefinition;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.S2CInventorySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Builds and sends authoritative owned-item counts for shop-relevant items. */
public final class InventorySyncService {
    private InventorySyncService() {
    }

    public static void sendOwnedCounts(ServerPlayer player, String requestedShopId) {
        String shopId = ShopDataService.resolveShopId(requestedShopId);
        Optional<ShopDefinition> defOpt = ShopCatalog.getOrDefault(shopId);
        if (defOpt.isEmpty()) {
            ShopPackets.sendToPlayer(player, new S2CInventorySyncPacket(shopId, Map.of()));
            return;
        }
        ShopDefinition def = defOpt.get();

        // Resolve each tracked itemId → Item once (O(N) total instead of per-inventory-walk).
        // LinkedHashMap preserves insertion order so the wire format stays stable for clients.
        Map<String, Item> trackedItems = new LinkedHashMap<>();
        for (ItemDef item : def.items()) {
            trackedItems.putIfAbsent(item.itemId(), null);
        }
        for (BarterRecipeDef recipe : def.barterRecipes()) {
            trackedItems.putIfAbsent(recipe.targetItemId(), null);
            recipe.ingredients().forEach(ingredient -> trackedItems.putIfAbsent(ingredient.itemId(), null));
        }
        // Resolve registry once; also build reverse lookup for the single-walk pass.
        Map<Item, String> itemToId = new HashMap<>(trackedItems.size() * 2);
        for (Map.Entry<String, Item> entry : trackedItems.entrySet()) {
            Item resolved = BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(entry.getKey())).orElse(null);
            if (resolved != null) {
                entry.setValue(resolved);
                itemToId.put(resolved, entry.getKey());
            }
        }

        // Walk the inventory exactly once, counting all tracked items.
        Map<String, Integer> counts = new LinkedHashMap<>();
        trackedItems.forEach((id, item) -> {
            if (item != null) counts.put(id, 0);
        });
        accumulate(counts, itemToId, player.getInventory().items);
        accumulate(counts, itemToId, player.getInventory().offhand);

        ShopPackets.sendToPlayer(player, new S2CInventorySyncPacket(shopId, counts));
    }

    private static void accumulate(Map<String, Integer> counts, Map<Item, String> itemToId, java.util.List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            String id = itemToId.get(stack.getItem());
            if (id != null) {
                counts.merge(id, stack.getCount(), Integer::sum);
            }
        }
    }
}

