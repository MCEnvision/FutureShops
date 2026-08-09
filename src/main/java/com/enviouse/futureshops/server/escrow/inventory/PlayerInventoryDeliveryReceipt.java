package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlayerInventoryDeliveryReceipt(
        int version,
        UUID receiptId,
        UUID playerId,
        UUID claimId,
        UUID transactionId,
        UUID batchId,
        UUID lotId,
        String requestKey,
        String simulationToken,
        byte[] assetFingerprint,
        byte[] beforeInventoryHash,
        byte[] afterInventoryHash,
        List<PlayerInventorySlotChange> changedSlots,
        CustodyTransferEvidence evidence,
        Instant deliveredAt,
        byte[] digest
) {
    private static final int LEGACY_VERSION = 1;
    private static final int CURRENT_VERSION = 2;
    private static final int MAX_REQUEST_KEY_LENGTH = 192;
    private static final int MAX_TOKEN_LENGTH = 2048;

    public PlayerInventoryDeliveryReceipt {
        if (version < LEGACY_VERSION || version > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Player inventory receipt version is invalid");
        }
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(lotId, "lotId");
        requestKey = requireText(
                requestKey, MAX_REQUEST_KEY_LENGTH, "request key");
        simulationToken = requireText(
                simulationToken, MAX_TOKEN_LENGTH, "simulation token");
        assetFingerprint = cloneHash(assetFingerprint,
                "Player inventory asset fingerprint");
        beforeInventoryHash = cloneHash(beforeInventoryHash,
                "Player inventory before hash");
        afterInventoryHash = cloneHash(afterInventoryHash,
                "Player inventory after hash");
        changedSlots = List.copyOf(Objects.requireNonNull(
                changedSlots, "changedSlots"));
        if (changedSlots.isEmpty()
                || changedSlots.size() > PlayerInventoryHashes.MAIN_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "Player inventory receipt slot changes are invalid");
        }
        Set<Integer> uniqueSlots = new HashSet<>();
        for (PlayerInventorySlotChange change : changedSlots) {
            if (!uniqueSlots.add(Objects.requireNonNull(
                    change, "changedSlot").slot())) {
                throw new IllegalArgumentException(
                        "Player inventory receipt contains duplicate slots");
            }
        }
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        digest = cloneHash(digest, "Player inventory receipt digest");
        PlayerInventoryDeliveryToken token =
                PlayerInventoryDeliveryToken.decode(simulationToken);
        if (token.version() != version
                || !token.receiptId().equals(receiptId)
                || !token.playerId().equals(playerId)
                || !token.claimId().equals(claimId)
                || !token.transactionId().equals(transactionId)
                || !token.batchId().equals(batchId)
                || !token.lotId().equals(lotId)
                || !token.matches(requestKey, assetFingerprint)
                || !PlayerInventoryHashes.equal(token.beforeInventoryHash(),
                beforeInventoryHash)
                || !PlayerInventoryHashes.equal(token.afterInventoryHash(),
                afterInventoryHash)
                || !simulationToken.equals(
                evidence.destination().mutationToken())) {
            throw new IllegalArgumentException(
                    "Player inventory receipt does not match its token");
        }
        byte[] expected = computeDigest(version, receiptId,
                playerId, claimId,
                transactionId, batchId, lotId, requestKey, simulationToken,
                assetFingerprint, beforeInventoryHash, afterInventoryHash,
                changedSlots, evidence, deliveredAt);
        if (!PlayerInventoryHashes.equal(expected, digest)) {
            throw new IllegalArgumentException(
                    "Player inventory receipt digest is invalid");
        }
    }

    public static PlayerInventoryDeliveryReceipt create(
            PlayerInventoryDeliveryToken token,
            String requestKey,
            List<PlayerInventorySlotChange> changedSlots,
            CustodyTransferEvidence evidence,
            Instant deliveredAt
    ) {
        Objects.requireNonNull(token, "token");
        String encodedToken = token.encode();
        byte[] digest = computeDigest(token.version(), token.receiptId(),
                token.playerId(),
                token.claimId(), token.transactionId(), token.batchId(),
                token.lotId(), requestKey, encodedToken,
                token.assetFingerprint(), token.beforeInventoryHash(),
                token.afterInventoryHash(), changedSlots, evidence,
                deliveredAt);
        return new PlayerInventoryDeliveryReceipt(token.version(),
                token.receiptId(),
                token.playerId(), token.claimId(), token.transactionId(),
                token.batchId(), token.lotId(), requestKey, encodedToken,
                token.assetFingerprint(), token.beforeInventoryHash(),
                token.afterInventoryHash(), changedSlots, evidence,
                deliveredAt, digest);
    }

    public boolean matchesInventory(List<ItemStack> slots) {
        for (PlayerInventorySlotChange change : changedSlots) {
            if (!PlayerInventoryHashes.equal(change.afterHash(),
                    hashSlot(
                            slots.get(change.slot())))) {
                return false;
            }
        }
        return true;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        tag.putUUID("receipt", receiptId);
        tag.putUUID("player", playerId);
        tag.putUUID("claim", claimId);
        tag.putUUID("transaction", transactionId);
        tag.putUUID("batch", batchId);
        tag.putUUID("lot", lotId);
        tag.putString("request", requestKey);
        tag.putString("token", simulationToken);
        tag.putByteArray("asset", assetFingerprint);
        tag.putByteArray("before", beforeInventoryHash);
        tag.putByteArray("after", afterInventoryHash);
        ListTag slots = new ListTag();
        for (PlayerInventorySlotChange change : changedSlots) {
            CompoundTag slot = new CompoundTag();
            slot.putInt("slot", change.slot());
            slot.putByteArray("before", change.beforeHash());
            slot.putByteArray("after", change.afterHash());
            slots.add(slot);
        }
        tag.put("slots", slots);
        tag.put("source", writeEndpoint(evidence.source()));
        tag.put("destination", writeEndpoint(evidence.destination()));
        tag.putLong("timeSeconds", deliveredAt.getEpochSecond());
        tag.putInt("timeNanos", deliveredAt.getNano());
        tag.putByteArray("digest", digest);
        return tag;
    }

    public static PlayerInventoryDeliveryReceipt fromTag(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        int version = tag.contains("version", Tag.TAG_INT)
                ? tag.getInt("version") : -1;
        if (version < LEGACY_VERSION || version > CURRENT_VERSION
                || !tag.hasUUID("receipt")
                || !tag.hasUUID("player")
                || !tag.hasUUID("claim")
                || !tag.hasUUID("transaction")
                || !tag.hasUUID("batch")
                || !tag.hasUUID("lot")
                || !tag.contains("request", Tag.TAG_STRING)
                || !tag.contains("token", Tag.TAG_STRING)
                || !tag.contains("asset", Tag.TAG_BYTE_ARRAY)
                || !tag.contains("before", Tag.TAG_BYTE_ARRAY)
                || !tag.contains("after", Tag.TAG_BYTE_ARRAY)
                || !tag.contains("slots", Tag.TAG_LIST)
                || !tag.contains("source", Tag.TAG_COMPOUND)
                || !tag.contains("destination", Tag.TAG_COMPOUND)
                || !tag.contains("timeSeconds", Tag.TAG_LONG)
                || !tag.contains("timeNanos", Tag.TAG_INT)
                || !tag.contains("digest", Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException(
                    "Player inventory receipt data is incomplete");
        }
        ListTag slotTags = tag.getList("slots", Tag.TAG_COMPOUND);
        if (slotTags.isEmpty()
                || slotTags.size() > PlayerInventoryHashes.MAIN_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "Player inventory receipt slot data is invalid");
        }
        List<PlayerInventorySlotChange> slots = slotTags.stream()
                .map(value -> (CompoundTag) value)
                .map(value -> new PlayerInventorySlotChange(
                        value.getInt("slot"), value.getByteArray("before"),
                        value.getByteArray("after")))
                .toList();
        Instant deliveredAt;
        try {
            deliveredAt = Instant.ofEpochSecond(
                    tag.getLong("timeSeconds"), tag.getInt("timeNanos"));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Player inventory receipt time is invalid", exception);
        }
        return new PlayerInventoryDeliveryReceipt(version,
                tag.getUUID("receipt"),
                tag.getUUID("player"), tag.getUUID("claim"),
                tag.getUUID("transaction"), tag.getUUID("batch"),
                tag.getUUID("lot"), tag.getString("request"),
                tag.getString("token"), tag.getByteArray("asset"),
                tag.getByteArray("before"), tag.getByteArray("after"),
                slots, new CustodyTransferEvidence(
                readEndpoint(tag.getCompound("source")),
                readEndpoint(tag.getCompound("destination"))),
                deliveredAt, tag.getByteArray("digest"));
    }

    @Override
    public byte[] assetFingerprint() {
        return assetFingerprint.clone();
    }

    @Override
    public byte[] beforeInventoryHash() {
        return beforeInventoryHash.clone();
    }

    @Override
    public byte[] afterInventoryHash() {
        return afterInventoryHash.clone();
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof PlayerInventoryDeliveryReceipt other)) {
            return false;
        }
        return version == other.version
                && receiptId.equals(other.receiptId)
                && playerId.equals(other.playerId)
                && claimId.equals(other.claimId)
                && transactionId.equals(other.transactionId)
                && batchId.equals(other.batchId)
                && lotId.equals(other.lotId)
                && requestKey.equals(other.requestKey)
                && simulationToken.equals(other.simulationToken)
                && Arrays.equals(assetFingerprint, other.assetFingerprint)
                && Arrays.equals(beforeInventoryHash,
                other.beforeInventoryHash)
                && Arrays.equals(afterInventoryHash,
                other.afterInventoryHash)
                && changedSlots.equals(other.changedSlots)
                && evidence.equals(other.evidence)
                && deliveredAt.equals(other.deliveredAt)
                && Arrays.equals(digest, other.digest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, receiptId, playerId, claimId,
                transactionId, batchId, lotId, requestKey, simulationToken,
                changedSlots, evidence, deliveredAt);
        result = 31 * result + Arrays.hashCode(assetFingerprint);
        result = 31 * result + Arrays.hashCode(beforeInventoryHash);
        result = 31 * result + Arrays.hashCode(afterInventoryHash);
        return 31 * result + Arrays.hashCode(digest);
    }

    private static CompoundTag writeEndpoint(CustodyEndpointEvidence endpoint) {
        CompoundTag tag = new CompoundTag();
        tag.putString("adapter", endpoint.adapterId());
        tag.putString("capability", endpoint.capability().name());
        tag.putString("owner", endpoint.ownerKey());
        tag.putString("location", endpoint.locationKey());
        tag.putByteArray("before", endpoint.beforeStateHash());
        tag.putByteArray("after", endpoint.afterStateHash());
        tag.putString("token", endpoint.mutationToken());
        return tag;
    }

    private static CustodyEndpointEvidence readEndpoint(CompoundTag tag) {
        try {
            return new CustodyEndpointEvidence(tag.getString("adapter"),
                    CustodyAdapterCapability.valueOf(
                            tag.getString("capability")),
                    tag.getString("owner"), tag.getString("location"),
                    tag.getByteArray("before"), tag.getByteArray("after"),
                    tag.getString("token"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Player inventory receipt endpoint is invalid", exception);
        }
    }

    private static byte[] computeDigest(
            int version,
            UUID receiptId,
            UUID playerId,
            UUID claimId,
            UUID transactionId,
            UUID batchId,
            UUID lotId,
            String requestKey,
            String simulationToken,
            byte[] assetFingerprint,
            byte[] beforeInventoryHash,
            byte[] afterInventoryHash,
            List<PlayerInventorySlotChange> changedSlots,
            CustodyTransferEvidence evidence,
            Instant deliveredAt
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(version);
                writeUuid(output, receiptId);
                writeUuid(output, playerId);
                writeUuid(output, claimId);
                writeUuid(output, transactionId);
                writeUuid(output, batchId);
                writeUuid(output, lotId);
                writeText(output, requestKey);
                writeText(output, simulationToken);
                writeBytes(output, assetFingerprint);
                writeBytes(output, beforeInventoryHash);
                writeBytes(output, afterInventoryHash);
                output.writeInt(changedSlots.size());
                for (PlayerInventorySlotChange change : changedSlots) {
                    output.writeInt(change.slot());
                    writeBytes(output, change.beforeHash());
                    writeBytes(output, change.afterHash());
                }
                writeEndpoint(output, evidence.source());
                writeEndpoint(output, evidence.destination());
                output.writeLong(deliveredAt.getEpochSecond());
                output.writeInt(deliveredAt.getNano());
            }
            return PlayerInventoryHashes.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to hash player inventory receipt", exception);
        }
    }

    private byte[] hashSlot(ItemStack stack) {
        return version == LEGACY_VERSION
                ? PlayerInventoryHashes.hashSlotLegacy(stack)
                : PlayerInventoryHashes.hashSlot(stack);
    }

    private static void writeEndpoint(
            DataOutputStream output,
            CustodyEndpointEvidence endpoint
    ) throws IOException {
        writeText(output, endpoint.adapterId());
        writeText(output, endpoint.capability().name());
        writeText(output, endpoint.ownerKey());
        writeText(output, endpoint.locationKey());
        writeBytes(output, endpoint.beforeStateHash());
        writeBytes(output, endpoint.afterStateHash());
        writeText(output, endpoint.mutationToken());
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeText(DataOutputStream output, String value)
            throws IOException {
        writeBytes(output, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(DataOutputStream output, byte[] value)
            throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] cloneHash(byte[] value, String label) {
        byte[] copy = Objects.requireNonNull(value, label).clone();
        PlayerInventoryHashes.requireHash(copy, label);
        return copy;
    }

    private static String requireText(
            String value,
            int maximumLength,
            String label
    ) {
        String normalized = Objects.requireNonNull(value, label);
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || !normalized.equals(normalized.strip())) {
            throw new IllegalArgumentException(
                    "Player inventory " + label + " is invalid");
        }
        return normalized;
    }
}
