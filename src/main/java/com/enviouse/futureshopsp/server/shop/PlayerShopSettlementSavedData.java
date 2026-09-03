package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.util.PageBounds;

import com.enviouse.futureshopsp.data.SettlementHistoryRow;
import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerShopSettlementSavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_player_shop_settlements";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_ROWS_PER_OWNER = 40;

    private final Map<Long, ShopSettlement> settlementsByShopPos = new HashMap<>();
    private final Map<UUID, List<RevenueRow>> rowsByOwner = new HashMap<>();

    public static PlayerShopSettlementSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);

        ListTag settlementList = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (Tag value : settlementList) {
            CompoundTag row = (CompoundTag) value;
            long shopPos = row.getLong("shopPos");
            if (!row.hasUUID("owner")) {
                continue;
            }
            UUID owner = row.getUUID("owner");
            long pending = row.getLong("pending");
            long lifetime = row.getLong("lifetime");
            UUID claimRequest = row.hasUUID("claimRequest") ? row.getUUID("claimRequest") : null;
            long claimAmount = Math.max(0L, row.getLong("claimAmount"));
            data.settlementsByShopPos.put(shopPos,
                    new ShopSettlement(owner, pending, lifetime, claimRequest, claimAmount));
        }

        ListTag ownerRows = tag.getList("ownerRows", Tag.TAG_COMPOUND);
        for (Tag ownerTag : ownerRows) {
            CompoundTag ownerCompound = (CompoundTag) ownerTag;
            if (!ownerCompound.hasUUID("owner")) {
                continue;
            }
            UUID owner = ownerCompound.getUUID("owner");
            ListTag rowsTag = ownerCompound.getList("rows", Tag.TAG_COMPOUND);
            List<RevenueRow> rows = new ArrayList<>();
            for (Tag rowTag : rowsTag) {
                CompoundTag row = (CompoundTag) rowTag;
                rows.add(new RevenueRow(
                        row.getLong("ts"),
                        row.getLong("shopPos"),
                        row.getLong("amount"),
                        row.getString("type"),
                        row.getString("itemId"),
                        row.getInt("quantity")
                ));
            }
            data.rowsByOwner.put(owner, rows);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag settlementList = new ListTag();
        for (Map.Entry<Long, ShopSettlement> entry : settlementsByShopPos.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putLong("shopPos", entry.getKey());
            row.putUUID("owner", entry.getValue().owner());
            row.putLong("pending", entry.getValue().pendingMinor());
            row.putLong("lifetime", entry.getValue().lifetimeMinor());
            if (entry.getValue().claimRequest() != null) {
                row.putUUID("claimRequest", entry.getValue().claimRequest());
                row.putLong("claimAmount", entry.getValue().claimAmount());
            }
            settlementList.add(row);
        }
        tag.put("settlements", settlementList);

        ListTag ownerRows = new ListTag();
        for (Map.Entry<UUID, List<RevenueRow>> entry : rowsByOwner.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("owner", entry.getKey());
            ListTag rowsTag = new ListTag();
            for (RevenueRow row : entry.getValue()) {
                CompoundTag rowTag = new CompoundTag();
                rowTag.putLong("ts", row.timestampEpochSeconds());
                rowTag.putLong("shopPos", row.shopPosLong());
                rowTag.putLong("amount", row.amountMinor());
                rowTag.putString("type", row.type());
                rowTag.putString("itemId", row.itemId());
                rowTag.putInt("quantity", row.quantity());
                rowsTag.add(rowTag);
            }
            ownerTag.put("rows", rowsTag);
            ownerRows.add(ownerTag);
        }
        tag.put("ownerRows", ownerRows);

        return tag;
    }

    public static PlayerShopSettlementSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlayerShopSettlementSavedData::new, PlayerShopSettlementSavedData::load, null), DATA_NAME);
    }

    public synchronized void recordSale(UUID owner, long shopPosLong, long amountMinor, String itemId, int quantity) {
        ShopSettlement current = settlementsByShopPos.get(shopPosLong);
        if (current == null || !current.owner().equals(owner)) {
            current = new ShopSettlement(owner, 0L, 0L, null, 0L);
        }
        settlementsByShopPos.put(shopPosLong, new ShopSettlement(
                owner,
                Math.addExact(current.pendingMinor(), Math.max(0L, amountMinor)),
                Math.addExact(current.lifetimeMinor(), Math.max(0L, amountMinor)),
                current.claimRequest(),
                current.claimAmount()
        ));

        appendRow(owner, new RevenueRow(Instant.now().getEpochSecond(), shopPosLong, amountMinor, "SALE", itemId == null ? "" : itemId, Math.max(0, quantity)));
        setDirty();
    }

    public synchronized SettlementClaim beginClaim(UUID owner, long shopPosLong) {
        ShopSettlement settlement = settlementsByShopPos.get(shopPosLong);
        if (settlement == null || !settlement.owner().equals(owner)) {
            return null;
        }
        long pending = Math.max(0L, settlement.pendingMinor());
        if (pending <= 0L) {
            return null;
        }
        UUID request = settlement.claimRequest();
        long amount = settlement.claimAmount();
        if (request == null || amount <= 0L) {
            request = UUID.randomUUID();
            amount = pending;
            settlementsByShopPos.put(shopPosLong,
                    new ShopSettlement(owner, pending, settlement.lifetimeMinor(), request, amount));
            setDirty();
        }
        return new SettlementClaim(request, amount);
    }

    public synchronized boolean completeClaim(UUID owner, long shopPosLong,
                                               UUID requestId, long amountMinor) {
        if (requestId == null || amountMinor <= 0L) {
            return false;
        }
        ShopSettlement settlement = settlementsByShopPos.get(shopPosLong);
        if (settlement == null || !settlement.owner().equals(owner)
                || !requestId.equals(settlement.claimRequest())
                || settlement.claimAmount() != amountMinor) {
            return false;
        }
        long pending = Math.max(0L, settlement.pendingMinor());
        if (pending < amountMinor) {
            return false;
        }
        settlementsByShopPos.put(shopPosLong,
                new ShopSettlement(owner, pending - amountMinor, settlement.lifetimeMinor(), null, 0L));
        appendRow(owner, new RevenueRow(Instant.now().getEpochSecond(), shopPosLong,
                amountMinor, "CLAIM", "", 0));
        setDirty();
        return true;
    }

    public synchronized boolean rollbackPending(UUID owner, long shopPosLong, long amountMinor) {
        if (amountMinor <= 0L) {
            return true;
        }
        ShopSettlement settlement = settlementsByShopPos.get(shopPosLong);
        if (settlement == null || !settlement.owner().equals(owner)) {
            return false;
        }
        long currentPending = Math.max(0L, settlement.pendingMinor());
        if (currentPending < amountMinor) {
            return false;
        }
        settlementsByShopPos.put(shopPosLong, new ShopSettlement(owner, currentPending - amountMinor,
                settlement.lifetimeMinor(), settlement.claimRequest(), settlement.claimAmount()));
        appendRow(owner, new RevenueRow(Instant.now().getEpochSecond(), shopPosLong, amountMinor, "ROLLBACK", "", 0));
        setDirty();
        return true;
    }

    public synchronized Snapshot snapshot(UUID owner, long shopPosLong, int maxRows) {
        ShopSettlement settlement = settlementsByShopPos.get(shopPosLong);
        long pending = 0L;
        long lifetime = 0L;
        if (settlement != null && settlement.owner().equals(owner)) {
            pending = settlement.pendingMinor();
            lifetime = settlement.lifetimeMinor();
        }

        List<String> rows = new ArrayList<>();
        List<RevenueRow> ownerRows = rowsByOwner.getOrDefault(owner, List.of());
        int count = Math.min(Math.max(1, maxRows), ownerRows.size());
        for (int i = 0; i < count; i++) {
            RevenueRow row = ownerRows.get(i);
            if (row.shopPosLong() != shopPosLong) {
                continue;
            }
            rows.add(row.type() + " " + row.amountMinor() + " @ " + row.timestampEpochSeconds());
        }
        return new Snapshot(pending, lifetime, rows);
    }

    public synchronized int getTotalPages(UUID owner, long shopPosLong, int pageSize) {
        return getTotalPages(owner, shopPosLong, pageSize, SettlementHistoryRow.SettlementFilter.ALL, 0L, 0L);
    }

    public synchronized int getTotalPages(UUID owner, long shopPosLong, int pageSize,
                                          SettlementHistoryRow.SettlementFilter filter,
                                          long fromEpochSeconds,
                                          long toEpochSeconds) {
        int safePageSize = Math.max(1, pageSize);
        long count = rowsByOwner.getOrDefault(owner, List.of()).stream()
                .filter(row -> row.shopPosLong() == shopPosLong)
                .filter(row -> (filter == null ? SettlementHistoryRow.SettlementFilter.ALL : filter).matches(row.type()))
                .filter(row -> isInRange(row.timestampEpochSeconds(), fromEpochSeconds, toEpochSeconds))
                .count();
        return Math.max(1, (int) Math.ceil((double) count / safePageSize));
    }

    public synchronized List<SettlementHistoryRow> getPage(UUID owner, long shopPosLong, int page, int pageSize) {
        return getPage(owner, shopPosLong, page, pageSize, SettlementHistoryRow.SettlementFilter.ALL, 0L, 0L);
    }

    public synchronized List<SettlementHistoryRow> getPage(UUID owner, long shopPosLong, int page, int pageSize,
                                                           SettlementHistoryRow.SettlementFilter filter,
                                                           long fromEpochSeconds,
                                                           long toEpochSeconds) {
        int safePage = PageBounds.normalizePage(page);
        int safePageSize = PageBounds.normalizePageSize(pageSize);
        List<RevenueRow> filtered = rowsByOwner.getOrDefault(owner, List.of()).stream()
                .filter(row -> row.shopPosLong() == shopPosLong)
                .filter(row -> (filter == null ? SettlementHistoryRow.SettlementFilter.ALL : filter).matches(row.type()))
                .filter(row -> isInRange(row.timestampEpochSeconds(), fromEpochSeconds, toEpochSeconds))
                .toList();
        long fromLong = PageBounds.offset(safePage, safePageSize);
        if (fromLong >= filtered.size()) {
            return List.of();
        }
        int from = (int) fromLong;
        int to = (int) Math.min((long) filtered.size(), fromLong + safePageSize);
        return filtered.subList(from, to).stream()
                .map(row -> new SettlementHistoryRow(row.timestampEpochSeconds(), row.amountMinor(), row.type(), row.itemId(), row.quantity()))
                .toList();
    }

    private static boolean isInRange(long value, long fromEpochSeconds, long toEpochSeconds) {
        long min = Math.min(fromEpochSeconds, toEpochSeconds);
        long max = Math.max(fromEpochSeconds, toEpochSeconds);
        boolean fromEnabled = fromEpochSeconds > 0L;
        boolean toEnabled = toEpochSeconds > 0L;
        if (fromEnabled && value < min) {
            return false;
        }
        if (toEnabled && value > max) {
            return false;
        }
        return true;
    }

    private void appendRow(UUID owner, RevenueRow row) {
        List<RevenueRow> rows = rowsByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>());
        rows.add(0, row);
        if (rows.size() > MAX_ROWS_PER_OWNER) {
            rows.subList(MAX_ROWS_PER_OWNER, rows.size()).clear();
        }
    }

    public synchronized Map<UUID, Long> snapshotLifetimeMinorByOwner() {
        Map<UUID, Long> totals = new HashMap<>();
        for (ShopSettlement settlement : settlementsByShopPos.values()) {
            totals.merge(settlement.owner(), Math.max(0L, settlement.lifetimeMinor()), Long::sum);
        }
        return totals;
    }

    public synchronized Map<UUID, Integer> snapshotSaleCountByOwner() {
        Map<UUID, Integer> counts = new HashMap<>();
        rowsByOwner.forEach((owner, rows) -> {
            int total = (int) rows.stream().filter(row -> "SALE".equalsIgnoreCase(row.type())).count();
            if (total > 0) {
                counts.put(owner, total);
            }
        });
        return counts;
    }

    public synchronized Map<Long, Snapshot> snapshotByShop() {
        Map<Long, Snapshot> snapshot = new HashMap<>();
        settlementsByShopPos.forEach((shopPos, settlement) -> snapshot.put(
                shopPos,
                new Snapshot(settlement.pendingMinor(), settlement.lifetimeMinor(), List.of())));
        return snapshot;
    }

    public record SettlementClaim(UUID requestId, long amountMinor) {
    }

    private record ShopSettlement(UUID owner, long pendingMinor, long lifetimeMinor,
                                  UUID claimRequest, long claimAmount) {
    }

    private record RevenueRow(long timestampEpochSeconds, long shopPosLong, long amountMinor, String type, String itemId, int quantity) {
    }

    public record Snapshot(long pendingMinor, long lifetimeMinor, List<String> rows) {
    }
}
