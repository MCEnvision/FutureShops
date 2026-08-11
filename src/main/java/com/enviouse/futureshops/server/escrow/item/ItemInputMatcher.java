package com.enviouse.futureshops.server.escrow.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class ItemInputMatcher {
    static final int MAX_SNBT_CHARACTERS = 65536;
    static final int MAX_SNBT_UTF8_BYTES = 262144;
    static final int MAX_SNBT_DEPTH = 64;

    private final String registryItemId;
    private final ItemMatchMode mode;
    private final byte[] exactOneCountSnapshot;
    private final String fingerprint;

    private ItemInputMatcher(
            String registryItemId,
            ItemMatchMode mode,
            byte[] exactOneCountSnapshot
    ) {
        this.registryItemId = ItemStackSnapshotEvidence.requireRegistryId(
                registryItemId);
        this.mode = Objects.requireNonNull(mode, "mode");
        this.exactOneCountSnapshot = Objects.requireNonNull(
                exactOneCountSnapshot, "exactOneCountSnapshot").clone();
        if (mode == ItemMatchMode.EXACT) {
            ItemStackSnapshotEvidence.SnapshotMetadata metadata =
                    ItemStackSnapshotEvidence.inspect(
                            this.exactOneCountSnapshot);
            if (metadata.count() != 1
                    || !metadata.registryId().equals(this.registryItemId)) {
                throw new IllegalArgumentException(
                        "Exact item matcher template is invalid");
            }
        } else if (this.exactOneCountSnapshot.length != 0) {
            throw new IllegalArgumentException(
                    "Item only matcher cannot contain an exact template");
        }
        this.fingerprint = fingerprint(this.registryItemId, this.mode,
                this.exactOneCountSnapshot);
    }

    public static ItemInputMatcher exact(ItemStack template) {
        Objects.requireNonNull(template, "template");
        return new ItemInputMatcher(
                ItemStackSnapshotEvidence.registryId(template),
                ItemMatchMode.EXACT,
                ItemStackSnapshotEvidence.oneCountSnapshot(template));
    }

    public static ItemInputMatcher itemOnly(ItemStack template) {
        return itemOnly(ItemStackSnapshotEvidence.registryId(template));
    }

    public static ItemInputMatcher itemOnly(String registryItemId) {
        return new ItemInputMatcher(registryItemId,
                ItemMatchMode.ITEM_ONLY, new byte[0]);
    }

    public static ItemInputMatcher parse(
            String registryItemId,
            ItemMatchMode mode,
            String itemTagSnbt
    ) {
        String normalizedId = ItemStackSnapshotEvidence.requireRegistryId(
                registryItemId);
        Objects.requireNonNull(mode, "mode");
        String snbt = Objects.requireNonNull(itemTagSnbt, "itemTagSnbt");
        requireBoundedSnbt(snbt);
        ResourceLocation key = ResourceLocation.tryParse(normalizedId);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        if (item == null || item == Items.AIR
                || !ForgeRegistries.ITEMS.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Item matcher registry entry is unavailable");
        }
        if (mode == ItemMatchMode.ITEM_ONLY) {
            if (!snbt.isBlank()) {
                throw new IllegalArgumentException(
                        "Item only matcher cannot contain SNBT");
            }
            return itemOnly(normalizedId);
        }
        ItemStack template = new ItemStack(item, 1);
        if (!snbt.isBlank()) {
            try {
                CompoundTag parsed = TagParser.parseTag(snbt);
                template.setTag(parsed);
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Exact item matcher SNBT is invalid", exception);
            }
        }
        return exact(template);
    }

    private static void requireBoundedSnbt(String snbt) {
        if (snbt.length() > MAX_SNBT_CHARACTERS
                || snbt.getBytes(StandardCharsets.UTF_8).length
                > MAX_SNBT_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "Item matcher SNBT exceeds its size limit");
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        char quote = 0;
        for (int index = 0; index < snbt.length(); index++) {
            char character = snbt.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quoted = false;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quoted = true;
                quote = character;
            } else if (character == '{' || character == '[') {
                depth++;
                if (depth > MAX_SNBT_DEPTH) {
                    throw new IllegalArgumentException(
                            "Item matcher SNBT exceeds its depth limit");
                }
            } else if (character == '}' || character == ']') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException(
                            "Item matcher SNBT nesting is invalid");
                }
            }
        }
        if (depth != 0 || quoted) {
            throw new IllegalArgumentException(
                    "Item matcher SNBT nesting is invalid");
        }
    }

    public boolean matches(ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        String candidateId;
        try {
            candidateId = ItemStackSnapshotEvidence.registryId(candidate);
        } catch (RuntimeException exception) {
            return false;
        }
        return registryItemId.equals(candidateId)
                && (mode == ItemMatchMode.ITEM_ONLY
                || ItemStackSnapshotEvidence.exactOneCountEquals(
                candidate, exactOneCountSnapshot));
    }

    public String registryItemId() {
        return registryItemId;
    }

    public ItemMatchMode mode() {
        return mode;
    }

    public byte[] exactOneCountSnapshot() {
        return exactOneCountSnapshot.clone();
    }

    public String fingerprint() {
        return fingerprint;
    }

    int exactSnapshotBytes() {
        return exactOneCountSnapshot.length;
    }

    boolean matchesEvidence(
            String candidateRegistryId,
            String candidateExactFingerprint
    ) {
        return registryItemId.equals(candidateRegistryId)
                && (mode == ItemMatchMode.ITEM_ONLY
                || fingerprint.equals(candidateExactFingerprint));
    }

    static String exactFingerprint(ItemStack stack) {
        String registryId = ItemStackSnapshotEvidence.registryId(stack);
        return fingerprint(registryId, ItemMatchMode.EXACT,
                ItemStackSnapshotEvidence.oneCountSnapshot(stack));
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInputMatcher other
                && registryItemId.equals(other.registryItemId)
                && mode == other.mode
                && Arrays.equals(exactOneCountSnapshot,
                other.exactOneCountSnapshot)
                && fingerprint.equals(other.fingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(registryItemId, mode, fingerprint);
        return 31 * result + Arrays.hashCode(exactOneCountSnapshot);
    }

    private static String fingerprint(
            String registryItemId,
            ItemMatchMode mode,
            byte[] exactOneCountSnapshot
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0x494D4154);
            output.writeInt(1);
            byte[] itemId = registryItemId.getBytes(StandardCharsets.UTF_8);
            output.writeInt(itemId.length);
            output.write(itemId);
            output.writeByte(mode.fingerprintCode());
            output.writeInt(exactOneCountSnapshot.length);
            output.write(exactOneCountSnapshot);
            output.flush();
            return ItemInventoryHashes.hex(
                    ItemInventoryHashes.sha256(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint item matcher", exception);
        }
    }
}
