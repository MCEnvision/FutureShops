package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.escrow.runtime.PlayerShopEscrowSavedData;
import com.enviouse.futureshops.server.escrow.runtime.ServerShopIntentSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class EscrowCheckpointCodec {
    public static final int FORMAT_VERSION = 8;
    public static final long MAX_FILE_BYTES = 268_435_680L;

    private static final int MAGIC = 0x46534350;
    private static final int FIXED_BYTES = 80;
    private static final int ENTRY_FIXED_BYTES = Integer.BYTES * 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int LEGACY_STORE_COUNT = 7;
    private static final int STOCK_FORMAT_VERSION = 2;
    private static final int STOCK_STORE_COUNT = 8;
    private static final int ITEM_INVENTORY_FORMAT_VERSION = 3;
    private static final int ITEM_INVENTORY_STORE_COUNT = 9;
    private static final int AUCTION_HOUSE_FORMAT_VERSION = 4;
    private static final int AUCTION_HOUSE_STORE_COUNT = 10;
    private static final int SERVER_SHOP_FORMAT_VERSION = 5;
    private static final int SERVER_SHOP_STORE_COUNT = 11;
    private static final int BAZAAR_FORMAT_VERSION = 6;
    private static final int BAZAAR_STORE_COUNT = 12;
    private static final int PLAYER_SHOP_FORMAT_VERSION = 7;
    private static final int PLAYER_SHOP_STORE_COUNT = 13;

    private EscrowCheckpointCodec() {
    }

    public static void write(OutputStream destination, EscrowCheckpoint checkpoint) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(checkpoint, "checkpoint");
        DataOutputStream output = new DataOutputStream(destination);
        output.writeInt(MAGIC);
        output.writeShort(FORMAT_VERSION);
        output.writeShort(0);
        writeUuid(output, checkpoint.checkpointId());
        writeUuid(output, checkpoint.sourceJournalLineageId());
        writeUuid(output, checkpoint.replacementJournalLineageId());
        output.writeLong(checkpoint.baseJournalSequence());
        output.writeLong(checkpoint.createdAt().getEpochSecond());
        output.writeInt(checkpoint.createdAt().getNano());
        output.writeInt(EscrowCheckpointStore.values().length);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            byte[] snapshot = checkpoint.snapshot(store);
            output.writeInt(store.wireId());
            output.writeInt(snapshot.length);
            output.write(snapshot);
        }
        output.flush();
    }

    public static EscrowCheckpoint read(InputStream source, long fileBytes) throws IOException {
        Objects.requireNonNull(source, "source");
        if (fileBytes < minimumFileBytes(LEGACY_STORE_COUNT)
                || fileBytes > MAX_FILE_BYTES) {
            throw new EscrowCheckpointException("Escrow checkpoint file size is invalid");
        }
        try {
            DataInputStream input = new DataInputStream(source);
            if (input.readInt() != MAGIC) {
                throw new EscrowCheckpointException("Escrow checkpoint magic does not match");
            }
            int version = input.readUnsignedShort();
            if (version != LEGACY_FORMAT_VERSION
                    && version != STOCK_FORMAT_VERSION
                    && version != ITEM_INVENTORY_FORMAT_VERSION
                    && version != AUCTION_HOUSE_FORMAT_VERSION
                    && version != SERVER_SHOP_FORMAT_VERSION
                    && version != BAZAAR_FORMAT_VERSION
                    && version != PLAYER_SHOP_FORMAT_VERSION
                    && version != FORMAT_VERSION) {
                throw new EscrowCheckpointException(
                        version > FORMAT_VERSION
                                ? "Escrow checkpoint schema is newer than this build"
                                : "Escrow checkpoint schema is unsupported");
            }
            if (input.readUnsignedShort() != 0) {
                throw new EscrowCheckpointException("Escrow checkpoint flags are unsupported");
            }
            UUID checkpointId = readUuid(input);
            UUID sourceLineage = readUuid(input);
            UUID replacementLineage = readUuid(input);
            long baseSequence = input.readLong();
            Instant createdAt = readInstant(input);
            int expectedStoreCount = switch (version) {
                case LEGACY_FORMAT_VERSION -> LEGACY_STORE_COUNT;
                case STOCK_FORMAT_VERSION -> STOCK_STORE_COUNT;
                case ITEM_INVENTORY_FORMAT_VERSION ->
                        ITEM_INVENTORY_STORE_COUNT;
                case AUCTION_HOUSE_FORMAT_VERSION ->
                        AUCTION_HOUSE_STORE_COUNT;
                case SERVER_SHOP_FORMAT_VERSION ->
                        SERVER_SHOP_STORE_COUNT;
                case BAZAAR_FORMAT_VERSION -> BAZAAR_STORE_COUNT;
                case PLAYER_SHOP_FORMAT_VERSION ->
                        PLAYER_SHOP_STORE_COUNT;
                default -> EscrowCheckpointStore.values().length;
            };
            int storeCount = input.readInt();
            if (storeCount != expectedStoreCount) {
                throw new EscrowCheckpointException("Escrow checkpoint store count is invalid");
            }

            EnumMap<EscrowCheckpointStore, byte[]> snapshots =
                    new EnumMap<>(EscrowCheckpointStore.class);
            long aggregate = 0L;
            for (int index = 0; index < storeCount; index++) {
                EscrowCheckpointStore store;
                try {
                    store = EscrowCheckpointStore.fromWireId(input.readInt());
                } catch (IllegalArgumentException exception) {
                    throw new EscrowCheckpointException("Escrow checkpoint store identity is invalid", exception);
                }
                int length = input.readInt();
                if (length < 0 || length > EscrowCheckpoint.MAX_STORE_BYTES) {
                    throw new EscrowCheckpointException("Escrow checkpoint store size is invalid");
                }
                aggregate = Math.addExact(aggregate, length);
                if (aggregate > EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES) {
                    throw new EscrowCheckpointException("Escrow checkpoint aggregate size is invalid");
                }
                byte[] snapshot = input.readNBytes(length);
                if (snapshot.length != length) {
                    throw new EscrowCheckpointException("Escrow checkpoint store is truncated");
                }
                if (snapshots.put(store, snapshot) != null) {
                    throw new EscrowCheckpointException("Escrow checkpoint contains a duplicate store");
                }
            }
            if (version == LEGACY_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.STOCK,
                        emptyStockSnapshot());
            }
            if (version <= STOCK_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.ITEM_INVENTORY_JOURNAL,
                        emptyItemInventoryJournalSnapshot());
            }
            if (version <= ITEM_INVENTORY_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.AUCTION_HOUSE,
                        emptyAuctionHouseSnapshot());
            }
            if (version <= AUCTION_HOUSE_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.SERVER_SHOP_INTENTS,
                        emptyServerShopIntentSnapshot());
            }
            if (version <= SERVER_SHOP_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.BAZAAR,
                        emptyBazaarSnapshot());
            }
            if (version <= BAZAAR_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.PLAYER_SHOP_ESCROW,
                        emptyPlayerShopEscrowSnapshot());
            }
            if (version <= PLAYER_SHOP_FORMAT_VERSION) {
                snapshots.put(EscrowCheckpointStore.MARKET_CONTROL,
                        emptyMarketControlSnapshot());
            }
            for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
                if (!snapshots.containsKey(store)) {
                    throw new EscrowCheckpointException("Escrow checkpoint store is missing");
                }
            }
            if (input.read() != -1) {
                throw new EscrowCheckpointException("Escrow checkpoint contains trailing data");
            }
            try {
                return new EscrowCheckpoint(checkpointId, sourceLineage, replacementLineage,
                        baseSequence, createdAt, snapshots);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw new EscrowCheckpointException("Escrow checkpoint metadata is invalid", exception);
            }
        } catch (EOFException exception) {
            throw new EscrowCheckpointException("Escrow checkpoint is truncated", exception);
        }
    }

    public static byte[] encode(EscrowCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    Math.toIntExact(encodedSize(checkpoint)));
            write(bytes, checkpoint);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode escrow checkpoint", exception);
        }
    }

    public static EscrowCheckpoint decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return read(new ByteArrayInputStream(encoded), encoded.length);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode escrow checkpoint", exception);
        }
    }

    public static long encodedSize(EscrowCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        return FIXED_BYTES
                + (long) ENTRY_FIXED_BYTES * EscrowCheckpointStore.values().length
                + checkpoint.aggregateSnapshotBytes();
    }

    private static long minimumFileBytes(int storeCount) {
        return FIXED_BYTES + (long) ENTRY_FIXED_BYTES * storeCount;
    }

    private static byte[] emptyStockSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.STOCK,
                new StockSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static byte[] emptyItemInventoryJournalSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.ITEM_INVENTORY_JOURNAL,
                new ItemInventoryJournalSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static byte[] emptyAuctionHouseSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.AUCTION_HOUSE,
                new AuctionHouseSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static byte[] emptyServerShopIntentSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.SERVER_SHOP_INTENTS,
                new ServerShopIntentSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static byte[] emptyBazaarSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.BAZAAR,
                new BazaarSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static byte[] emptyPlayerShopEscrowSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.PLAYER_SHOP_ESCROW,
                new PlayerShopEscrowSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static byte[] emptyMarketControlSnapshot() {
        return EscrowCheckpointComponentCodec.encode(
                EscrowCheckpointStore.MARKET_CONTROL,
                new MarketControlSavedData().save(new CompoundTag()),
                EscrowCheckpoint.MAX_STORE_BYTES);
    }

    private static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new EscrowCheckpointException("Escrow checkpoint time is invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw new EscrowCheckpointException("Escrow checkpoint time is invalid", exception);
        }
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
}
