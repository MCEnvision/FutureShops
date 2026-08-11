package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockPolicy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CatalogStockSeedCapture {
    private static final byte[] CONFIG_DOMAIN =
            "futureshops.catalog.stock.config.v1"
                    .getBytes(StandardCharsets.UTF_8);

    private CatalogStockSeedCapture() {
    }

    public static CatalogStockSeedSnapshot capture(
            Collection<ShopDefinition> definitions,
            CatalogStockQuantityReader stockReader
    ) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(stockReader, "stockReader");
        List<CatalogStockSeedEntry> entries = new ArrayList<>();
        for (ShopDefinition shop : definitions) {
            Objects.requireNonNull(shop, "shop definition");
            for (ItemDef item : shop.items()) {
                Objects.requireNonNull(item, "shop item definition");
                StockKey key = new StockKey(
                        shop.shopId(), item.resolutionKey());
                if (item.isUnlimited()) {
                    entries.add(new CatalogStockSeedEntry(
                            key, true, 0L, 0L,
                            configFingerprint(key, item)));
                    continue;
                }
                int available = stockReader.currentStock(
                        shop.shopId(), item.resolutionKey());
                if (available < 0) {
                    throw new IllegalStateException(
                            "Finite catalog stock source is invalid");
                }
                entries.add(new CatalogStockSeedEntry(
                        key, false, item.stock(), available,
                        configFingerprint(key, item)));
            }
        }
        return CatalogStockSeedSnapshot.capture(entries);
    }

    public static CatalogStockSeedSnapshot captureConfiguration(
            Collection<ShopDefinition> definitions
    ) {
        Objects.requireNonNull(definitions, "definitions");
        List<CatalogStockSeedEntry> entries = new ArrayList<>();
        for (ShopDefinition shop : definitions) {
            for (ItemDef item : shop.items()) {
                StockKey key = new StockKey(
                        shop.shopId(), item.resolutionKey());
                entries.add(new CatalogStockSeedEntry(key,
                        item.isUnlimited(),
                        item.isUnlimited() ? 0L : item.stock(),
                        item.isUnlimited() ? 0L : item.stock(),
                        configFingerprint(key, item)));
            }
        }
        return CatalogStockSeedSnapshot.capture(entries);
    }

    public static StockDefinition definition(
            String shopId,
            ItemDef item
    ) {
        Objects.requireNonNull(item, "item");
        StockKey key = new StockKey(shopId, item.resolutionKey());
        return new StockDefinition(key,
                item.isUnlimited() ? StockPolicy.unlimitedStock()
                        : StockPolicy.limited(item.stock()),
                configFingerprint(key, item));
    }

    public static boolean configurationMatches(
            Collection<ShopDefinition> definitions,
            CatalogStockSeedSnapshot snapshot
    ) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(snapshot, "snapshot");
        Map<StockKey, CatalogStockSeedEntry> expected = new HashMap<>();
        for (CatalogStockSeedEntry entry : snapshot.entries()) {
            expected.put(entry.key(), entry);
        }
        Set<StockKey> found = new HashSet<>();
        for (ShopDefinition shop : definitions) {
            for (ItemDef item : shop.items()) {
                StockKey key;
                try {
                    key = new StockKey(
                            shop.shopId(), item.resolutionKey());
                } catch (RuntimeException exception) {
                    return false;
                }
                CatalogStockSeedEntry entry = expected.get(key);
                if (entry == null || !found.add(key)
                        || entry.unlimited() != item.isUnlimited()
                        || entry.configuredQuantity()
                        != (item.isUnlimited() ? 0L : item.stock())
                        || !entry.configFingerprint().equals(
                        configFingerprint(key, item))) {
                    return false;
                }
            }
        }
        return found.size() == expected.size();
    }

    static String configFingerprint(StockKey key, ItemDef item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(CONFIG_DOMAIN);
            updateString(digest, key.shopId());
            updateString(digest, key.listingId());
            updateString(digest, item.itemId());
            updateString(digest,
                    item.nbtJson() == null ? "" : item.nbtJson());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length).array());
        digest.update(bytes);
    }
}
