package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerShopRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_player_shop_registry";
    private static final int CURRENT_VERSION = 2;
    static final int MAXIMUM_OWNERS = 32_768;
    static final int MAXIMUM_SHOPS_PER_OWNER = 4_096;
    static final int MAXIMUM_SHOPS = 131_072;
    static final int MAXIMUM_DIMENSION_ID_LENGTH = 160;
    private static final UUID ZERO = new UUID(0L, 0L);

    private final Map<UUID, ShopRecord> recordsById = new LinkedHashMap<>();
    private final Map<ShopLocation, UUID> activeIdsByLocation = new LinkedHashMap<>();

    public static PlayerShopRegistrySavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Player shop registry version type is invalid");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version < 0 || version > CURRENT_VERSION) {
            throw new IllegalArgumentException("Player shop registry version is unsupported");
        }
        boolean rewrite = SavedDataMigrations.needsMigration(
                DATA_NAME, version, CURRENT_VERSION);
        if (!tag.contains("owners", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Player shop registry owner list is invalid");
        }
        ListTag owners = (ListTag) tag.get("owners");
        if (!owners.isEmpty() && owners.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Player shop registry owner entries are invalid");
        }
        if (owners.size() > MAXIMUM_OWNERS) {
            throw new IllegalArgumentException("Player shop registry owner limit is exceeded");
        }
        Set<UUID> ownerGroups = new HashSet<>();
        int shopCount = 0;
        for (Tag ownerTag : owners) {
            CompoundTag ownerCompound = (CompoundTag) ownerTag;
            if (!ownerCompound.hasUUID("owner")) {
                throw new IllegalArgumentException("Player shop registry owner is missing");
            }
            UUID owner = ownerCompound.getUUID("owner");
            requireId(owner, "owner");
            if (!ownerGroups.add(owner)) {
                throw new IllegalArgumentException("Player shop registry owner is duplicated");
            }
            if (!ownerCompound.contains("shops", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Player shop registry shop list is missing");
            }
            ListTag shops = (ListTag) ownerCompound.get("shops");
            if (!shops.isEmpty() && shops.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Player shop registry shop entries are invalid");
            }
            if (shops.size() > MAXIMUM_SHOPS_PER_OWNER) {
                throw new IllegalArgumentException("Player shop registry owner shop limit is exceeded");
            }
            shopCount = Math.addExact(shopCount, shops.size());
            if (shopCount > MAXIMUM_SHOPS) {
                throw new IllegalArgumentException("Player shop registry shop limit is exceeded");
            }
            for (Tag shopTag : shops) {
                CompoundTag shopCompound = (CompoundTag) shopTag;
                if (!shopCompound.contains("dimension", Tag.TAG_STRING)
                        || !shopCompound.contains("pos", Tag.TAG_LONG)
                        || version >= 2 && (!shopCompound.hasUUID("shop_id")
                        || !shopCompound.contains("revision", Tag.TAG_LONG))) {
                    throw new IllegalArgumentException("Player shop registry entry is incomplete");
                }
                String dimensionValue = shopCompound.getString("dimension");
                ResourceLocation dimension = parseDimension(dimensionValue);
                long position = shopCompound.getLong("pos");
                ShopLocation location = new ShopLocation(dimension, position);
                UUID shopId = version >= 2
                        ? shopCompound.getUUID("shop_id") : legacyId(location);
                requireId(shopId, "shop");
                long revision = version >= 2 ? shopCompound.getLong("revision") : 0L;
                if (revision < 0L) {
                    throw new IllegalArgumentException("Player shop registry revision is invalid");
                }
                boolean active = true;
                if (version >= 2 && shopCompound.contains("active")) {
                    if (!shopCompound.contains("active", Tag.TAG_BYTE)) {
                        throw new IllegalArgumentException("Player shop registry active type is invalid");
                    }
                    active = shopCompound.getBoolean("active");
                } else if (version >= 2) {
                    rewrite = true;
                }
                data.addLoaded(new ShopRecord(shopId, owner,
                        dimension.toString(), position, revision, active));
            }
        }
        data.validateInvariants();
        if (rewrite) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        validateInvariants();
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        Map<UUID, List<ShopRecord>> byOwner = new LinkedHashMap<>();
        recordsById.values().stream()
                .sorted(Comparator.comparing(ShopRecord::owner)
                        .thenComparing(ShopRecord::shopId))
                .forEach(record -> byOwner.computeIfAbsent(
                        record.owner(), ignored -> new ArrayList<>()).add(record));
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, List<ShopRecord>> entry : byOwner.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("owner", entry.getKey());
            ListTag shops = new ListTag();
            for (ShopRecord record : entry.getValue()) {
                CompoundTag shopTag = new CompoundTag();
                shopTag.putUUID("shop_id", record.shopId());
                shopTag.putString("dimension", record.dimension());
                shopTag.putLong("pos", record.posLong());
                shopTag.putLong("revision", record.revision());
                shopTag.putBoolean("active", record.active());
                shops.add(shopTag);
            }
            ownerTag.put("shops", shops);
            owners.add(ownerTag);
        }
        tag.put("owners", owners);
        return tag;
    }

    public static PlayerShopRegistrySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                PlayerShopRegistrySavedData::load,
                PlayerShopRegistrySavedData::new,
                DATA_NAME);
    }

    public synchronized ShopRef register(UUID owner, ResourceLocation dimension, long posLong) {
        requireId(owner, "owner");
        ShopLocation location = new ShopLocation(
                requireDimension(dimension), posLong);
        UUID existingId = activeIdsByLocation.get(location);
        if (existingId != null) {
            ShopRecord existing = requireRecord(existingId);
            if (!existing.owner().equals(owner)) {
                throw new IllegalStateException("Player shop location belongs to another owner");
            }
            return existing.asRef();
        }
        ensureNewRecordCapacity(owner);
        UUID shopId = newIdentity();
        ShopRecord created = new ShopRecord(shopId, owner,
                dimension.toString(), posLong, 0L, true);
        recordsById.put(shopId, created);
        activeIdsByLocation.put(location, shopId);
        setDirty();
        return created.asRef();
    }

    public synchronized ShopRef reconcile(UUID owner, Optional<UUID> identity,
                                          long evidenceRevision,
                                          ResourceLocation dimension, long posLong) {
        requireId(owner, "owner");
        identity = Objects.requireNonNull(identity, "identity");
        ShopLocation location = new ShopLocation(
                requireDimension(dimension), posLong);
        if (identity.isEmpty()) {
            if (evidenceRevision != 0L) {
                throw new IllegalArgumentException("Player shop identity evidence is incomplete");
            }
            return register(owner, dimension, posLong);
        }
        UUID shopId = identity.orElseThrow();
        requireId(shopId, "shop");
        if (evidenceRevision < 0L) {
            throw new IllegalArgumentException("Player shop revision is invalid");
        }
        ShopRecord existing = recordsById.get(shopId);
        if (existing == null) {
            if (activeIdsByLocation.containsKey(location)) {
                throw new IllegalStateException("Player shop location identity conflicts with evidence");
            }
            ensureNewRecordCapacity(owner);
            ShopRecord reconstructed = new ShopRecord(shopId, owner,
                    dimension.toString(), posLong, evidenceRevision, true);
            recordsById.put(shopId, reconstructed);
            activeIdsByLocation.put(location, shopId);
            setDirty();
            return reconstructed.asRef();
        }
        if (!existing.owner().equals(owner)) {
            throw new IllegalStateException("Player shop evidence owner does not match registry");
        }
        if (existing.active()) {
            if (!existing.location().equals(location)) {
                throw new IllegalStateException("Player shop identity is active at another location");
            }
            if (evidenceRevision > existing.revision()) {
                throw new IllegalStateException("Player shop evidence revision is ahead of registry");
            }
            return existing.asRef();
        }
        if (evidenceRevision != existing.revision()) {
            throw new IllegalStateException("Player shop tombstone revision does not match evidence");
        }
        UUID locationOwner = activeIdsByLocation.get(location);
        if (locationOwner != null) {
            throw new IllegalStateException("Player shop relocation target is occupied");
        }
        long nextRevision = incrementRevision(existing.revision());
        ShopRecord reactivated = new ShopRecord(shopId, owner,
                dimension.toString(), posLong, nextRevision, true);
        recordsById.put(shopId, reactivated);
        activeIdsByLocation.put(location, shopId);
        setDirty();
        return reactivated.asRef();
    }

    public synchronized ShopRef transferOwnership(UUID shopId, UUID expectedOwner,
                                                  long expectedRevision, UUID newOwner) {
        requireId(shopId, "shop");
        requireId(expectedOwner, "expected owner");
        requireId(newOwner, "new owner");
        if (expectedOwner.equals(newOwner)) {
            throw new IllegalArgumentException("Player shop ownership transfer requires a new owner");
        }
        ShopRecord existing = requireRecord(shopId);
        if (!existing.active()) {
            throw new IllegalStateException("Player shop ownership cannot change while inactive");
        }
        if (!existing.owner().equals(expectedOwner)
                || existing.revision() != expectedRevision) {
            throw new IllegalStateException("Player shop ownership evidence is stale");
        }
        ensureOwnerCapacity(newOwner, shopId);
        long nextRevision = incrementRevision(existing.revision());
        ShopRecord transferred = new ShopRecord(shopId, newOwner,
                existing.dimension(), existing.posLong(), nextRevision, true);
        recordsById.put(shopId, transferred);
        setDirty();
        return transferred.asRef();
    }

    public synchronized ShopRecord tombstone(UUID shopId, UUID owner,
                                              long expectedRevision,
                                              ResourceLocation dimension, long posLong) {
        requireId(shopId, "shop");
        requireId(owner, "owner");
        ShopLocation location = new ShopLocation(
                requireDimension(dimension), posLong);
        ShopRecord existing = requireRecord(shopId);
        if (!existing.owner().equals(owner)
                || existing.revision() != expectedRevision
                || !existing.location().equals(location)) {
            throw new IllegalStateException("Player shop tombstone evidence is stale");
        }
        if (!existing.active()) {
            return existing;
        }
        if (!shopId.equals(activeIdsByLocation.get(location))) {
            throw new IllegalStateException("Player shop active index does not match tombstone");
        }
        ShopRecord inactive = existing.withActive(false);
        activeIdsByLocation.remove(location);
        recordsById.put(shopId, inactive);
        setDirty();
        return inactive;
    }

    public synchronized Optional<ShopRecord> remove(ResourceLocation dimension, long posLong) {
        ShopLocation location = new ShopLocation(
                requireDimension(dimension), posLong);
        UUID shopId = activeIdsByLocation.get(location);
        if (shopId == null) {
            return Optional.empty();
        }
        ShopRecord existing = requireRecord(shopId);
        ShopRecord inactive = existing.withActive(false);
        activeIdsByLocation.remove(location);
        recordsById.put(shopId, inactive);
        setDirty();
        return Optional.of(inactive);
    }

    public synchronized List<ShopRef> getOwnedShops(UUID owner) {
        requireId(owner, "owner");
        return recordsById.values().stream()
                .filter(ShopRecord::active)
                .filter(record -> record.owner().equals(owner))
                .sorted(Comparator.comparing(ShopRecord::dimension)
                        .thenComparingLong(ShopRecord::posLong))
                .map(ShopRecord::asRef)
                .toList();
    }

    public synchronized Map<UUID, List<ShopRef>> snapshot() {
        Map<UUID, List<ShopRef>> snapshot = new LinkedHashMap<>();
        recordsById.values().stream()
                .filter(ShopRecord::active)
                .sorted(Comparator.comparing(ShopRecord::owner)
                        .thenComparing(ShopRecord::dimension)
                        .thenComparingLong(ShopRecord::posLong))
                .forEach(record -> snapshot.computeIfAbsent(
                        record.owner(), ignored -> new ArrayList<>()).add(record.asRef()));
        Map<UUID, List<ShopRef>> immutable = new LinkedHashMap<>();
        snapshot.forEach((owner, refs) -> immutable.put(owner, List.copyOf(refs)));
        return Map.copyOf(immutable);
    }

    public synchronized Map<ShopLocation, ShopRecord> getAllShops() {
        Map<ShopLocation, ShopRecord> active = new LinkedHashMap<>();
        activeIdsByLocation.forEach((location, shopId) ->
                active.put(location, requireRecord(shopId)));
        return Map.copyOf(active);
    }

    public synchronized Map<UUID, ShopRecord> getAllRecords() {
        return Map.copyOf(recordsById);
    }

    public synchronized Optional<ShopRecord> get(ResourceLocation dimension, long posLong) {
        UUID shopId = activeIdsByLocation.get(new ShopLocation(
                requireDimension(dimension), posLong));
        return shopId == null ? Optional.empty()
                : Optional.of(requireRecord(shopId));
    }

    public synchronized Optional<ShopRecord> get(UUID shopId) {
        requireId(shopId, "shop");
        return Optional.ofNullable(recordsById.get(shopId));
    }

    public synchronized Optional<DimensionAwareShopReference> reference(UUID shopId) {
        return get(shopId).map(record -> new DimensionAwareShopReference(
                record.shopId().toString(), record.dimension(),
                record.blockX(), record.blockY(), record.blockZ()));
    }

    private void addLoaded(ShopRecord record) {
        if (recordsById.putIfAbsent(record.shopId(), record) != null) {
            throw new IllegalArgumentException("Player shop registry identity is duplicated");
        }
        if (record.active()) {
            UUID previous = activeIdsByLocation.putIfAbsent(
                    record.location(), record.shopId());
            if (previous != null) {
                throw new IllegalArgumentException("Player shop registry active location is duplicated");
            }
        }
    }

    private void validateInvariants() {
        if (recordsById.size() > MAXIMUM_SHOPS
                || activeIdsByLocation.size() > recordsById.size()) {
            throw new IllegalArgumentException("Player shop registry size is invalid");
        }
        Map<UUID, Integer> ownerCounts = new LinkedHashMap<>();
        int activeCount = 0;
        for (Map.Entry<UUID, ShopRecord> entry : recordsById.entrySet()) {
            ShopRecord record = entry.getValue();
            if (!entry.getKey().equals(record.shopId())) {
                throw new IllegalArgumentException("Player shop registry identity key is invalid");
            }
            int ownerCount = Math.addExact(
                    ownerCounts.getOrDefault(record.owner(), 0), 1);
            if (ownerCount > MAXIMUM_SHOPS_PER_OWNER) {
                throw new IllegalArgumentException("Player shop registry owner shop limit is exceeded");
            }
            ownerCounts.put(record.owner(), ownerCount);
            UUID indexed = activeIdsByLocation.get(record.location());
            if (record.active()) {
                activeCount = Math.addExact(activeCount, 1);
                if (!record.shopId().equals(indexed)) {
                    throw new IllegalArgumentException("Player shop registry active index is incomplete");
                }
            } else if (record.shopId().equals(indexed)) {
                throw new IllegalArgumentException("Player shop registry tombstone is active");
            }
        }
        if (ownerCounts.size() > MAXIMUM_OWNERS
                || activeCount != activeIdsByLocation.size()) {
            throw new IllegalArgumentException("Player shop registry index size is invalid");
        }
        for (Map.Entry<ShopLocation, UUID> entry : activeIdsByLocation.entrySet()) {
            ShopRecord record = recordsById.get(entry.getValue());
            if (record == null || !record.active()
                    || !record.location().equals(entry.getKey())) {
                throw new IllegalArgumentException("Player shop registry active index is invalid");
            }
        }
    }

    private void ensureNewRecordCapacity(UUID owner) {
        if (recordsById.size() >= MAXIMUM_SHOPS) {
            throw new IllegalStateException("Player shop registry shop limit is reached");
        }
        ensureOwnerCapacity(owner, null);
    }

    private void ensureOwnerCapacity(UUID owner, UUID excludedShopId) {
        long ownerCount = recordsById.values().stream()
                .filter(record -> record.owner().equals(owner))
                .filter(record -> excludedShopId == null
                        || !record.shopId().equals(excludedShopId))
                .count();
        if (ownerCount >= MAXIMUM_SHOPS_PER_OWNER) {
            throw new IllegalStateException("Player shop registry owner shop limit is reached");
        }
        boolean ownerExists = recordsById.values().stream()
                .anyMatch(record -> record.owner().equals(owner));
        if (!ownerExists) {
            long ownerTotal = recordsById.values().stream()
                    .map(ShopRecord::owner).distinct().count();
            if (ownerTotal >= MAXIMUM_OWNERS) {
                throw new IllegalStateException("Player shop registry owner limit is reached");
            }
        }
    }

    private UUID newIdentity() {
        UUID identity;
        do {
            identity = UUID.randomUUID();
        } while (ZERO.equals(identity) || recordsById.containsKey(identity));
        return identity;
    }

    private ShopRecord requireRecord(UUID shopId) {
        ShopRecord record = recordsById.get(shopId);
        if (record == null) {
            throw new IllegalStateException("Player shop registry identity is missing");
        }
        return record;
    }

    private static long incrementRevision(long revision) {
        try {
            return Math.incrementExact(revision);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Player shop revision overflowed", exception);
        }
    }

    private static ResourceLocation requireDimension(ResourceLocation dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return parseDimension(dimension.toString());
    }

    private static ResourceLocation parseDimension(String value) {
        Objects.requireNonNull(value, "dimension");
        if (value.length() > MAXIMUM_DIMENSION_ID_LENGTH) {
            throw new IllegalArgumentException("Player shop dimension is invalid");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()
                || normalized.length() > MAXIMUM_DIMENSION_ID_LENGTH) {
            throw new IllegalArgumentException("Player shop dimension is invalid");
        }
        return ResourceLocation.parse(normalized);
    }

    private static UUID legacyId(ShopLocation location) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("futureshops_player_shop_v1".getBytes(StandardCharsets.UTF_8));
            digest.update(location.dimension().toString().getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(location.posLong()).array());
            byte[] bytes = digest.digest();
            bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x50);
            bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Player shop identity algorithm is unavailable", exception);
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || ZERO.equals(id)) {
            throw new IllegalArgumentException("Player shop " + label + " identifier is required");
        }
    }

    public record ShopLocation(ResourceLocation dimension, long posLong) {
        public ShopLocation {
            dimension = requireDimension(dimension);
        }
    }

    public record ShopRef(UUID shopId, ResourceLocation dimension, long posLong, long revision) {
        public ShopRef {
            requireId(shopId, "shop");
            dimension = requireDimension(dimension);
            if (revision < 0L) {
                throw new IllegalArgumentException("Player shop revision is invalid");
            }
        }
    }

    public record ShopRecord(UUID shopId, UUID owner, String dimension,
                             long posLong, long revision, boolean active) {
        public ShopRecord {
            requireId(shopId, "shop");
            requireId(owner, "owner");
            dimension = parseDimension(dimension).toString();
            if (revision < 0L) {
                throw new IllegalArgumentException("Player shop revision is invalid");
            }
        }

        public ShopLocation location() {
            return new ShopLocation(ResourceLocation.parse(dimension), posLong);
        }

        public ShopRef asRef() {
            return new ShopRef(shopId, ResourceLocation.parse(dimension), posLong, revision);
        }

        public ShopRecord withActive(boolean nextActive) {
            return new ShopRecord(shopId, owner, dimension,
                    posLong, revision, nextActive);
        }

        public int blockX() {
            return net.minecraft.core.BlockPos.getX(posLong);
        }

        public int blockY() {
            return net.minecraft.core.BlockPos.getY(posLong);
        }

        public int blockZ() {
            return net.minecraft.core.BlockPos.getZ(posLong);
        }
    }
}
