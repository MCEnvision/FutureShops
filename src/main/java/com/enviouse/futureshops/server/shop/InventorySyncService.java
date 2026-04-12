package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CInventorySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Builds and sends authoritative owned-item counts for shop-relevant items. */
public final class InventorySyncService {
    private InventorySyncService() {
    }

    public static void sendOwnedCounts(ServerPlayer player, String requestedShopId) {
        String shopId = ShopDataService.resolveShopId(requestedShopId);
        Set<String> trackedItemIds = new LinkedHashSet<>();

        ShopCatalog.buildItems(shopId).forEach(item -> trackedItemIds.add(item.itemId()));
        for (BarterRecipeDef recipe : ShopCatalog.getOrDefault(shopId).map(def -> def.barterRecipes()).orElse(java.util.List.of())) {
            trackedItemIds.add(recipe.targetItemId());
            recipe.ingredients().forEach(ingredient -> trackedItemIds.add(ingredient.itemId()));
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String itemId : trackedItemIds) {
            Item target = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(itemId));
            if (target == null) {
                continue;
            }
            int total = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() == target) {
                    total += stack.getCount();
                }
            }
            for (ItemStack stack : player.getInventory().offhand) {
                if (stack.getItem() == target) {
                    total += stack.getCount();
                }
            }
            counts.put(itemId, total);
        }

        ShopPackets.sendToPlayer(player, new S2CInventorySyncPacket(shopId, counts));
    }
}

