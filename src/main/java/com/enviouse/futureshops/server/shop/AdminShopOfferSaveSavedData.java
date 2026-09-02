package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import com.enviouse.futureshops.network.packets.S2CAdminOfferSaveResultPacket;
import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AdminShopOfferSaveSavedData extends SavedData {
    private static final String DATA_ID =
            "futureshops_admin_offer_save_receipts";
    private static final int CURRENT_VERSION = 1;
    private static final int MAXIMUM_RECEIPTS = 2_048;
    private static final int MAXIMUM_ISSUES = 128;
    private final LinkedHashMap<UUID, Receipt> receipts =
            new LinkedHashMap<>();

    public static AdminShopOfferSaveSavedData get(
            MinecraftServer server
    ) {
        return server.overworld().getDataStorage().computeIfAbsent(
                AdminShopOfferSaveSavedData::load,
                AdminShopOfferSaveSavedData::new,
                DATA_ID);
    }

    public synchronized Optional<Receipt> find(UUID requestId) {
        return Optional.ofNullable(receipts.get(requestId));
    }

    public synchronized boolean record(Receipt receipt) {
        Receipt existing = receipts.get(receipt.requestId());
        if (existing != null) {
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Admin offer save receipt identity conflicts");
            }
            return false;
        }
        receipts.put(receipt.requestId(), receipt);
        while (receipts.size() > MAXIMUM_RECEIPTS) {
            receipts.remove(receipts.keySet().iterator().next());
        }
        setDirty();
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag values = new ListTag();
        for (Receipt receipt : receipts.values()) {
            values.add(receipt.save());
        }
        tag.put("Receipts", values);
        return tag;
    }

    public static AdminShopOfferSaveSavedData load(CompoundTag tag) {
        SavedDataMigrations.needsMigration(
                DATA_ID, SavedDataMigrations.readVersion(tag),
                CURRENT_VERSION);
        AdminShopOfferSaveSavedData data =
                new AdminShopOfferSaveSavedData();
        ListTag values = SavedDataMigrations.requireList(
                tag, "Receipts", Tag.TAG_COMPOUND,
                MAXIMUM_RECEIPTS, "Admin offer save receipts");
        for (int index = 0; index < values.size(); index++) {
            try {
                Receipt receipt = Receipt.load(
                        values.getCompound(index));
                if (data.receipts.putIfAbsent(
                        receipt.requestId(), receipt) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate admin offer save receipt");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Admin offer save receipt row " + index
                                + " is invalid", exception);
            }
        }
        return data;
    }

    public record Receipt(
            UUID requestId,
            UUID playerId,
            String requestFingerprint,
            AdminShopOfferConfigWriter.Status status,
            boolean success,
            long revision,
            Optional<ServerShopOfferListing> snapshot,
            List<OfferValidationIssue> issues
    ) {
        public Receipt {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            requestFingerprint = java.util.Objects.requireNonNull(
                    requestFingerprint, "requestFingerprint");
            java.util.Objects.requireNonNull(status, "status");
            snapshot = java.util.Objects.requireNonNull(
                    snapshot, "snapshot");
            issues = List.copyOf(issues);
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || !requestFingerprint.matches("[0-9a-f]{64}")
                    || revision < 0L
                    || issues.size() > MAXIMUM_ISSUES
                    || success != (status
                    == AdminShopOfferConfigWriter.Status.SUCCESS)) {
                throw new IllegalArgumentException(
                        "Admin offer save receipt is invalid");
            }
        }

        public static Receipt capture(
                UUID playerId,
                String requestFingerprint,
                S2CAdminOfferSaveResultPacket result
        ) {
            return new Receipt(result.requestId(), playerId,
                    requestFingerprint, result.status(),
                    result.success(), result.revision(),
                    result.snapshot(), result.issues());
        }

        public S2CAdminOfferSaveResultPacket packet() {
            return new S2CAdminOfferSaveResultPacket(
                    requestId, status, success, revision,
                    snapshot, issues);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Request", requestId);
            tag.putUUID("Player", playerId);
            tag.putString("Fingerprint", requestFingerprint);
            tag.putString("Status", status.name());
            tag.putBoolean("Success", success);
            tag.putLong("Revision", revision);
            snapshot.ifPresent(value -> tag.putByteArray(
                    "Snapshot",
                    ServerShopOfferNetworkCodec.encodeListingBytes(value)));
            ListTag issueTags = new ListTag();
            for (OfferValidationIssue issue : issues) {
                CompoundTag issueTag = new CompoundTag();
                issueTag.putString("Severity", issue.severity().name());
                issueTag.putString("Path", issue.path());
                issueTag.putString("Code", issue.code());
                issueTags.add(issueTag);
            }
            tag.put("Issues", issueTags);
            return tag;
        }

        private static Receipt load(CompoundTag tag) {
            Optional<ServerShopOfferListing> snapshot =
                    tag.contains("Snapshot", Tag.TAG_BYTE_ARRAY)
                            ? Optional.of(ServerShopOfferNetworkCodec
                            .decodeListingBytes(
                                    tag.getByteArray("Snapshot")))
                            : Optional.empty();
            ListTag issueTags = SavedDataMigrations.requireList(
                    tag, "Issues", Tag.TAG_COMPOUND,
                    MAXIMUM_ISSUES, "Admin offer save issues");
            List<OfferValidationIssue> issues =
                    new ArrayList<>(issueTags.size());
            for (int index = 0; index < issueTags.size(); index++) {
                CompoundTag issue = issueTags.getCompound(index);
                issues.add(new OfferValidationIssue(
                        OfferValidationIssue.Severity.valueOf(
                                issue.getString("Severity")),
                        issue.getString("Path"),
                        issue.getString("Code")));
            }
            return new Receipt(
                    tag.getUUID("Request"),
                    tag.getUUID("Player"),
                    tag.getString("Fingerprint"),
                    AdminShopOfferConfigWriter.Status.valueOf(
                            tag.getString("Status")),
                    tag.getBoolean("Success"),
                    tag.getLong("Revision"),
                    snapshot, issues);
        }
    }
}
