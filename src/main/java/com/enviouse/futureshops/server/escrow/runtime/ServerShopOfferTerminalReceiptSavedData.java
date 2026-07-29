package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopOfferTerminalReceiptSavedData
        extends SavedData {
    private static final String DATA_ID =
            "futureshops_server_shop_offer_terminal_receipts";
    private static final int CURRENT_VERSION = 2;
    private static final int MAXIMUM_RECEIPTS = 262_144;
    private static final int MAXIMUM_RECEIPTS_PER_PLAYER = 4_096;
    private static final UUID LEGACY_PLAYER = UUID.fromString(
            "fba85ed7-8edf-53cf-8d38-95d6a42b7f7a");
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private final LinkedHashMap<UUID, Receipt> receipts =
            new LinkedHashMap<>();
    private final java.util.Map<UUID, Integer> playerReceiptCounts =
            new java.util.HashMap<>();
    private final int maximumReceipts;

    public ServerShopOfferTerminalReceiptSavedData() {
        this(MAXIMUM_RECEIPTS);
    }

    ServerShopOfferTerminalReceiptSavedData(int maximumReceipts) {
        if (maximumReceipts <= 0
                || maximumReceipts > MAXIMUM_RECEIPTS) {
            throw new IllegalArgumentException(
                    "Server shop terminal receipt limit is invalid");
        }
        this.maximumReceipts = maximumReceipts;
    }

    public static ServerShopOfferTerminalReceiptSavedData get(
            MinecraftServer server
    ) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServerShopOfferTerminalReceiptSavedData::load,
                ServerShopOfferTerminalReceiptSavedData::new,
                DATA_ID);
    }

    public synchronized Optional<Receipt> find(UUID requestId) {
        return Optional.ofNullable(receipts.get(requestId));
    }

    public synchronized boolean canRecord(UUID requestId) {
        return canRecord(requestId, LEGACY_PLAYER);
    }

    public synchronized boolean canRecord(
            UUID requestId,
            UUID playerId
    ) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(playerId, "playerId");
        return receipts.containsKey(requestId)
                || receipts.size() < maximumReceipts
                && playerReceiptCounts.getOrDefault(
                playerId, 0) < MAXIMUM_RECEIPTS_PER_PLAYER;
    }

    public synchronized int size() {
        return receipts.size();
    }

    public synchronized boolean record(Receipt receipt) {
        Receipt existing = receipts.get(receipt.requestId());
        if (existing != null) {
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Server shop terminal receipt identity conflicts");
            }
            return false;
        }
        if (receipts.size() >= maximumReceipts) {
            throw new IllegalStateException(
                    "Server shop terminal receipt repository is full");
        }
        int playerCount = playerReceiptCounts.getOrDefault(
                receipt.playerId(), 0);
        if (playerCount >= MAXIMUM_RECEIPTS_PER_PLAYER) {
            throw new IllegalStateException(
                    "Server shop terminal receipt player limit is exceeded");
        }
        receipts.put(receipt.requestId(), receipt);
        playerReceiptCounts.put(
                receipt.playerId(), Math.addExact(playerCount, 1));
        setDirty();
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag values = new ListTag();
        for (Receipt receipt : receipts.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Request", receipt.requestId());
            value.putUUID("Player", receipt.playerId());
            value.putString("Kind", receipt.kind().name());
            value.putString("Fingerprint",
                    receipt.requestFingerprint());
            value.putString("Status", receipt.status().name());
            values.add(value);
        }
        tag.put("Receipts", values);
        return tag;
    }

    public static ServerShopOfferTerminalReceiptSavedData load(
            CompoundTag tag
    ) {
        int loadedVersion = SavedDataMigrations.readVersion(tag);
        if (loadedVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Server shop terminal receipt version is unsupported");
        }
        SavedDataMigrations.needsMigration(
                DATA_ID, loadedVersion, CURRENT_VERSION);
        ServerShopOfferTerminalReceiptSavedData data =
                new ServerShopOfferTerminalReceiptSavedData();
        ListTag values = tag.getList("Receipts", Tag.TAG_COMPOUND);
        if (values.size() > MAXIMUM_RECEIPTS) {
            throw new IllegalArgumentException(
                    "Server shop terminal receipt limit is exceeded");
        }
        for (int index = 0;
             index < values.size();
             index++) {
            try {
                CompoundTag value = values.getCompound(index);
                Receipt receipt = new Receipt(
                        value.getUUID("Request"),
                        loadedVersion >= 2
                                ? value.getUUID("Player")
                                : LEGACY_PLAYER,
                        Kind.valueOf(value.getString("Kind")),
                        value.getString("Fingerprint"),
                        ServerShopOfferService.Status.valueOf(
                                value.getString("Status")));
                if (data.receipts.putIfAbsent(
                        receipt.requestId(), receipt) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate server shop terminal receipt");
                }
                int playerCount = data.playerReceiptCounts.merge(
                        receipt.playerId(), 1, Math::addExact);
                if (loadedVersion >= 2
                        && playerCount
                        > MAXIMUM_RECEIPTS_PER_PLAYER) {
                    throw new IllegalArgumentException(
                            "Server shop terminal receipt player limit is exceeded");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Server shop terminal receipt row " + index
                                + " is invalid", exception);
            }
        }
        return data;
    }

    public enum Kind {
        SINGLE,
        CART
    }

    public record Receipt(
            UUID requestId,
            UUID playerId,
            Kind kind,
            String requestFingerprint,
            ServerShopOfferService.Status status
    ) {
        public Receipt {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            java.util.Objects.requireNonNull(kind, "kind");
            requestFingerprint = java.util.Objects.requireNonNull(
                    requestFingerprint, "requestFingerprint");
            java.util.Objects.requireNonNull(status, "status");
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || !requestFingerprint.matches("[0-9a-f]{64}")
                    || !ServerShopOfferReplayReceipt
                    .isDurableTerminalFailure(status)) {
                throw new IllegalArgumentException(
                        "Server shop terminal receipt is invalid");
            }
        }
    }
}
