package com.enviouse.futureshops.catalog;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Server owned identity for one bulk shop selection. An identity is a registry item id and either
 * no tag or one complete canonical compound. The canonical form is structural and independent of
 * SNBT member ordering, which keeps duplicate detection and replay fingerprints deterministic.
 */
public record AdminBulkListingIdentity(String itemId, String canonicalNbt, String digest) {
    public static final int MAX_NBT_BYTES = 32 * 1024;
    public static final int MAX_NBT_DEPTH = 32;
    public static final int MAX_NBT_ENTRIES = 512;
    private static final Logger LOGGER = LogUtils.getLogger();

    public AdminBulkListingIdentity {
        itemId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        canonicalNbt = canonicalNbt == null ? "" : canonicalNbt;
        digest = digest == null ? sha256(itemId + "\u0000" + canonicalNbt) : digest;
    }

    public boolean hasExactNbt() {
        return !canonicalNbt.isBlank();
    }

    public static Result parse(String itemId, String nbt) {
        String normalizedId = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        if (normalizedId.isBlank()) {
            return Result.invalid("item id is blank");
        }
        String source = nbt == null ? "" : nbt.trim();
        if (source.isBlank()) {
            return Result.valid(new AdminBulkListingIdentity(normalizedId, "", ""));
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_NBT_BYTES) {
            return Result.invalid("exact nbt is larger than " + MAX_NBT_BYTES + " bytes");
        }
        try {
            CompoundTag parsed = TagParser.parseTag(source);
            if (parsed.isEmpty()) {
                return Result.valid(new AdminBulkListingIdentity(normalizedId, "", ""));
            }
            Counter counter = new Counter();
            String canonical = canonical(parsed, 0, counter);
            return Result.valid(new AdminBulkListingIdentity(normalizedId, canonical, ""));
        } catch (CommandSyntaxException | RuntimeException exception) {
            LOGGER.debug("Rejected malformed bulk listing nbt for {}: {}", normalizedId,
                    exception.getMessage());
            return Result.invalid("exact nbt is not a valid compound");
        }
    }

    private static String canonical(Tag tag, int depth, Counter counter) {
        if (depth > MAX_NBT_DEPTH) {
            throw new IllegalArgumentException("exact nbt is too deep");
        }
        if (++counter.entries > MAX_NBT_ENTRIES) {
            throw new IllegalArgumentException("exact nbt contains too many values");
        }
        if (tag instanceof CompoundTag compound) {
            List<String> keys = new ArrayList<>(compound.getAllKeys());
            keys.sort(Comparator.naturalOrder());
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                result.append(escape(key)).append(':')
                        .append(canonical(compound.get(key), depth + 1, counter)).append(';');
            }
            return result.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder result = new StringBuilder("[").append(tag.getId()).append(':');
            for (Tag value : list) {
                result.append(canonical(value, depth + 1, counter)).append(';');
            }
            return result.append(']').toString();
        }
        // Primitive and array tags have stable type aware SNBT representations. Prefixing the
        // runtime type prevents values such as byte 1 and int 1 from comparing equal.
        return tag.getId() + ":" + escape(tag.toString());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\u0000", "\\0")
                .replace(";", "\\;")
                .replace(":", "\\:");
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha-256 is unavailable", exception);
        }
    }

    public record Result(AdminBulkListingIdentity identity, String error) {
        public static Result valid(AdminBulkListingIdentity identity) {
            return new Result(identity, "");
        }

        public static Result invalid(String error) {
            return new Result(null, error == null ? "invalid identity" : error);
        }

        public boolean valid() {
            return identity != null;
        }
    }

    private static final class Counter {
        private int entries;
    }
}
