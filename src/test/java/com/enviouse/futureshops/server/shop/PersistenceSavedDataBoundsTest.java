package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.AdminBulkReplaySavedData;
import com.enviouse.futureshops.money.SpentMintsSavedData;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import com.enviouse.futureshops.server.pricing.DynamicPricingSavedData;
import com.enviouse.futureshops.server.transaction.TransactionHistorySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistenceSavedDataBoundsTest {
    @Test
    void spentMintsRejectsDuplicateIdentities() {
        CompoundTag root = new CompoundTag();
        ListTag mints = new ListTag();
        mints.add(mint("mint", 1));
        mints.add(mint("mint", 1));
        root.put("mints", mints);
        assertThrows(IllegalArgumentException.class,
                () -> SpentMintsSavedData.load(root));
    }

    @Test
    void departmentsRejectOversizedCollection() {
        CompoundTag root = new CompoundTag();
        ListTag departments = new ListTag();
        for (int index = 0; index <= 512; index++) {
            departments.add(StringTag.valueOf("department" + index));
        }
        root.put("Departments", departments);
        assertThrows(IllegalArgumentException.class,
                () -> DepartmentSavedData.load(root));
    }

    @Test
    void franchisesRejectOversizedMemberCollection() {
        CompoundTag root = new CompoundTag();
        ListTag franchises = new ListTag();
        CompoundTag franchise = new CompoundTag();
        UUID leader = UUID.randomUUID();
        franchise.putUUID("Id", UUID.randomUUID());
        franchise.putString("Name", "friends");
        franchise.putUUID("Leader", leader);
        ListTag members = new ListTag();
        for (int index = 0; index < 21; index++) {
            CompoundTag member = new CompoundTag();
            member.putUUID("UUID", index == 0 ? leader : UUID.randomUUID());
            members.add(member);
        }
        franchise.put("Members", members);
        franchises.add(franchise);
        root.put("Franchises", franchises);
        assertThrows(IllegalArgumentException.class,
                () -> FranchiseSavedData.load(root));
    }

    @Test
    void transactionHistoryRejectsOversizedPlayerHistory() {
        CompoundTag root = new CompoundTag();
        ListTag players = new ListTag();
        CompoundTag player = new CompoundTag();
        player.putUUID("uuid", UUID.randomUUID());
        ListTag entries = new ListTag();
        for (int index = 0; index < 201; index++) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("ts", index);
            entry.putString("type", "BUY");
            entry.putString("item", "minecraft:stone");
            entry.putInt("qty", 1);
            entry.putLong("total", 1);
            entry.putString("note", "");
            entry.putString("nbt", "");
            entries.add(entry);
        }
        player.put("entries", entries);
        players.add(player);
        root.put("players", players);
        assertThrows(IllegalArgumentException.class,
                () -> TransactionHistorySavedData.load(root));
    }

    @Test
    void transactionHistoryRejectsWrongListElementTypes() {
        CompoundTag root = new CompoundTag();
        ListTag players = new ListTag();
        players.add(StringTag.valueOf("invalid"));
        root.put("players", players);
        assertThrows(IllegalArgumentException.class,
                () -> TransactionHistorySavedData.load(root));

        CompoundTag player = new CompoundTag();
        player.putUUID("uuid", UUID.randomUUID());
        ListTag entries = new ListTag();
        entries.add(StringTag.valueOf("invalid"));
        player.put("entries", entries);
        players = new ListTag();
        players.add(player);
        root.put("players", players);
        assertThrows(IllegalArgumentException.class,
                () -> TransactionHistorySavedData.load(root));
    }

    @Test
    void playerSettlementsRejectOversizedHistory() {
        CompoundTag root = new CompoundTag();
        ListTag owners = new ListTag();
        CompoundTag owner = new CompoundTag();
        owner.putUUID("owner", UUID.randomUUID());
        ListTag rows = new ListTag();
        for (int index = 0; index < 41; index++) {
            CompoundTag row = new CompoundTag();
            row.putLong("ts", index);
            row.putLong("shopPos", index);
            row.putLong("amount", 1);
            row.putString("type", "SALE");
            row.putString("itemId", "minecraft:stone");
            row.putInt("quantity", 1);
            row.putString("nbt", "");
            rows.add(row);
        }
        owner.put("rows", rows);
        owners.add(owner);
        root.put("ownerRows", owners);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopSettlementSavedData.load(root));
    }

    @Test
    void playerSettlementsRejectWrongListElementTypes() {
        CompoundTag root = new CompoundTag();
        ListTag owners = new ListTag();
        owners.add(StringTag.valueOf("invalid"));
        root.put("ownerRows", owners);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopSettlementSavedData.load(root));

        CompoundTag owner = new CompoundTag();
        owner.putUUID("owner", UUID.randomUUID());
        ListTag rows = new ListTag();
        rows.add(StringTag.valueOf("invalid"));
        owner.put("rows", rows);
        owners = new ListTag();
        owners.add(owner);
        root.put("ownerRows", owners);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopSettlementSavedData.load(root));
    }

    @Test
    void savedShopConfigurationsRejectOversizedSnapshot() {
        CompoundTag root = new CompoundTag();
        CompoundTag players = new CompoundTag();
        CompoundTag named = new CompoundTag();
        ListTag order = new ListTag();
        CompoundTag entries = new CompoundTag();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("payload", "x".repeat(262_145));
        order.add(StringTag.valueOf("default"));
        entries.put("default", snapshot);
        named.put("Order", order);
        named.put("Entries", entries);
        players.put(UUID.randomUUID().toString(), named);
        root.put("Players", players);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopSavedConfigs.load(root));
    }

    @Test
    void shopLimitsRejectInvalidValueType() {
        CompoundTag root = new CompoundTag();
        CompoundTag limits = new CompoundTag();
        limits.putString(UUID.randomUUID().toString(), "unlimited");
        root.put("MaxShopBlocks", limits);
        assertThrows(IllegalArgumentException.class,
                () -> ShopLimitsSavedData.load(root));
    }

    @Test
    void stockRefreshRejectsOversizedKey() {
        CompoundTag root = new CompoundTag();
        CompoundTag refreshes = new CompoundTag();
        refreshes.putLong("x".repeat(513), 1L);
        root.put("Refreshes", refreshes);
        assertThrows(IllegalArgumentException.class,
                () -> StockRefreshSavedData.load(root));
    }

    @Test
    void dynamicPricingRejectsNegativeState() {
        CompoundTag root = new CompoundTag();
        CompoundTag states = new CompoundTag();
        CompoundTag state = new CompoundTag();
        state.putInt("buys", -1);
        state.putInt("sells", 0);
        state.putLong("currentPrice", 0L);
        states.put("shop:item", state);
        root.put("States", states);
        assertThrows(IllegalArgumentException.class,
                () -> DynamicPricingSavedData.load(root));
    }

    @Test
    void dynamicPricingRejectsWrongStatesType() {
        CompoundTag root = new CompoundTag();
        root.putString("States", "invalid");
        assertThrows(IllegalArgumentException.class,
                () -> DynamicPricingSavedData.load(root));
    }

    @Test
    void stockRefreshRejectsWrongRefreshesType() {
        CompoundTag root = new CompoundTag();
        root.putString("Refreshes", "invalid");
        assertThrows(IllegalArgumentException.class,
                () -> StockRefreshSavedData.load(root));
    }

    @Test
    void adminBulkReplayRejectsWrongListElementTypes() {
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        entries.add(StringTag.valueOf("invalid"));
        root.put("entries", entries);
        assertThrows(IllegalArgumentException.class,
                () -> AdminBulkReplaySavedData.load(root));

        CompoundTag entry = new CompoundTag();
        entry.putString("requestId", UUID.randomUUID().toString());
        ListTag rows = new ListTag();
        rows.add(StringTag.valueOf("invalid"));
        entry.put("rows", rows);
        entries = new ListTag();
        entries.add(entry);
        root.put("entries", entries);
        assertThrows(IllegalArgumentException.class,
                () -> AdminBulkReplaySavedData.load(root));
    }

    @Test
    void marketProfilesRejectWrongListElementTypes() {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", MarketProfileSavedData.CURRENT_VERSION);
        ListTag players = new ListTag();
        players.add(StringTag.valueOf("invalid"));
        root.put("players", players);
        assertThrows(IllegalArgumentException.class,
                () -> MarketProfileSavedData.load(root));

        CompoundTag player = new CompoundTag();
        player.putUUID("player", UUID.randomUUID());
        player.putLong("revision", 0L);
        player.putLong("mutationReplayEpoch", 0L);
        for (String key : new String[]{"favoriteProducts", "recentProducts",
                "priceAlerts", "notifications", "mutationReceipts",
                "mutationTombstones"}) {
            player.put(key, new ListTag());
        }
        ListTag malformedWatched = new ListTag();
        malformedWatched.add(StringTag.valueOf("invalid"));
        player.put("watchedAuctions", malformedWatched);
        players = new ListTag();
        players.add(player);
        root.put("players", players);
        assertThrows(IllegalArgumentException.class,
                () -> MarketProfileSavedData.load(root));
    }

    private static CompoundTag mint(String id, int count) {
        CompoundTag mint = new CompoundTag();
        mint.putString("id", id);
        mint.putUUID("player", UUID.randomUUID());
        mint.putLong("denomination", 1L);
        mint.putInt("authorized_count", count);
        mint.putInt("remaining_count", count);
        mint.putLong("minted_at", 1L);
        mint.putLong("consumed_at", 0L);
        mint.putString("server", "test");
        return mint;
    }
}
