package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

public final class PlayerInventoryReceiptStore {
    static final String RECEIPTS_KEY =
            "futureshops_cash_claim_delivery_receipts";
    private static final int MAX_RECEIPTS = 1024;
    private static final long MAX_COMPRESSED_PLAYER_BYTES = 16_777_216L;
    private static final long MAX_DECODED_PLAYER_BYTES = 67_108_864L;

    public void append(
            ServerPlayer player,
            PlayerInventoryDeliveryReceipt receipt
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(receipt, "receipt");
        if (!player.getUUID().equals(receipt.playerId())) {
            throw new IllegalArgumentException(
                    "Player inventory receipt owner does not match");
        }
        CompoundTag persistent = player.getPersistentData();
        ListTag stored = receiptList(persistent);
        for (int index = 0; index < stored.size(); index++) {
            PlayerInventoryDeliveryReceipt current =
                    PlayerInventoryDeliveryReceipt.fromTag(
                            stored.getCompound(index));
            if (current.receiptId().equals(receipt.receiptId())) {
                if (!current.equals(receipt)) {
                    throw new IllegalStateException(
                            "Player inventory receipt ID is already in use");
                }
                return;
            }
        }
        if (stored.size() >= MAX_RECEIPTS) {
            throw new IllegalStateException(
                    "Player inventory receipt limit is exceeded");
        }
        stored.add(receipt.toTag());
        persistent.put(RECEIPTS_KEY, stored);
    }

    public void remove(ServerPlayer player, UUID receiptId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(receiptId, "receiptId");
        CompoundTag persistent = player.getPersistentData();
        ListTag stored = receiptList(persistent);
        ListTag retained = new ListTag();
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag tag = stored.getCompound(index);
            PlayerInventoryDeliveryReceipt receipt =
                    PlayerInventoryDeliveryReceipt.fromTag(tag);
            if (!receipt.receiptId().equals(receiptId)) {
                retained.add(tag.copy());
            }
        }
        if (retained.isEmpty()) {
            persistent.remove(RECEIPTS_KEY);
        } else {
            persistent.put(RECEIPTS_KEY, retained);
        }
    }

    int pruneCompletedCashClaims(
            ServerPlayer player,
            Function<UUID, EscrowClaim> claimLookup
    ) {
        Objects.requireNonNull(player, "player");
        return pruneCompletedCashClaims(player.getPersistentData(),
                player.getUUID(), claimLookup);
    }

    int pruneCompletedCashClaims(
            CompoundTag persistent,
            UUID playerId,
            Function<UUID, EscrowClaim> claimLookup
    ) {
        Objects.requireNonNull(persistent, "persistent");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(claimLookup, "claimLookup");
        Tag raw = persistent.get(RECEIPTS_KEY);
        if (!(raw instanceof ListTag stored)
                || (!stored.isEmpty()
                && stored.getElementType() != Tag.TAG_COMPOUND)) {
            return 0;
        }
        ListTag retained = new ListTag();
        int pruned = 0;
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag tag = stored.getCompound(index);
            PlayerInventoryDeliveryReceipt receipt;
            try {
                receipt = PlayerInventoryDeliveryReceipt.fromTag(tag);
            } catch (RuntimeException exception) {
                retained.add(tag.copy());
                continue;
            }
            EscrowClaim claim;
            try {
                claim = claimLookup.apply(receipt.claimId());
            } catch (RuntimeException exception) {
                retained.add(tag.copy());
                continue;
            }
            if (isCompletedCashClaim(playerId, receipt, claim)) {
                pruned++;
            } else {
                retained.add(tag.copy());
            }
        }
        if (pruned == 0) {
            return 0;
        }
        if (retained.isEmpty()) {
            persistent.remove(RECEIPTS_KEY);
        } else {
            persistent.put(RECEIPTS_KEY, retained);
        }
        return pruned;
    }

    public PlayerInventoryReceiptInspection inspect(
            MinecraftServer server,
            PlayerInventoryDeliveryToken token
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(token, "token");
        return inspect(playerFile(server, token.playerId()), token);
    }

    PlayerInventoryReceiptInspection inspect(
            Path playerFile,
            PlayerInventoryDeliveryToken token
    ) {
        Objects.requireNonNull(playerFile, "playerFile");
        Objects.requireNonNull(token, "token");
        if (!Files.isRegularFile(playerFile)) {
            return unknown("Player data file is unavailable");
        }
        CompoundTag root;
        try {
            root = readPlayerData(playerFile);
        } catch (IOException | RuntimeException exception) {
            return unknown("Player data file cannot be verified");
        }
        if (!root.contains("Inventory", Tag.TAG_LIST)) {
            return unknown("Player inventory data is missing");
        }
        List<net.minecraft.world.item.ItemStack> inventory;
        try {
            inventory = PlayerInventoryHashes.readMainInventory(
                    root.getList("Inventory", Tag.TAG_COMPOUND));
        } catch (RuntimeException exception) {
            return unknown("Player inventory data is invalid");
        }
        CompoundTag forgeData = root.contains("ForgeData", Tag.TAG_COMPOUND)
                ? root.getCompound("ForgeData") : new CompoundTag();
        PlayerInventoryDeliveryReceipt found = null;
        try {
            ListTag receipts = receiptList(forgeData);
            if (receipts.size() > MAX_RECEIPTS) {
                return unknown("Player inventory receipt limit is invalid");
            }
            for (int index = 0; index < receipts.size(); index++) {
                PlayerInventoryDeliveryReceipt receipt =
                        PlayerInventoryDeliveryReceipt.fromTag(
                                receipts.getCompound(index));
                if (receipt.receiptId().equals(token.receiptId())) {
                    if (found != null || !receipt.simulationToken()
                            .equals(token.encode())) {
                        return unknown(
                                "Player inventory receipt identity conflicts");
                    }
                    found = receipt;
                }
            }
        } catch (RuntimeException exception) {
            return unknown("Player inventory receipt data is invalid");
        }
        if (found != null) {
            if (!found.matchesInventory(inventory)) {
                return unknown(
                        "Player inventory receipt does not match inventory");
            }
            return new PlayerInventoryReceiptInspection(
                    CustodyAdapterInspectionStatus.APPLIED,
                    java.util.Optional.of(found),
                    "Player inventory receipt proves delivery");
        }
        byte[] currentHash = PlayerInventoryHashes.hashInventory(inventory);
        if (PlayerInventoryHashes.equal(
                currentHash, token.beforeInventoryHash())) {
            return new PlayerInventoryReceiptInspection(
                    CustodyAdapterInspectionStatus.NOT_APPLIED,
                    java.util.Optional.empty(),
                    "Player data proves delivery was not applied");
        }
        return unknown(
                "Player inventory changed without a delivery receipt");
    }

    public static Path playerFile(MinecraftServer server, UUID playerId) {
        return Objects.requireNonNull(server, "server")
                .getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(Objects.requireNonNull(playerId, "playerId")
                        + ".dat");
    }

    public static Path playerDirectory(MinecraftServer server) {
        return Objects.requireNonNull(server, "server")
                .getWorldPath(LevelResource.PLAYER_DATA_DIR);
    }

    private static CompoundTag readPlayerData(Path path) throws IOException {
        long compressedBytes = Files.size(path);
        if (compressedBytes <= 0L
                || compressedBytes > MAX_COMPRESSED_PLAYER_BYTES) {
            throw new IOException("Player data file size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(
                        Files.newInputStream(path))))) {
            CompoundTag tag = NbtIo.read(input,
                    new NbtAccounter(MAX_DECODED_PLAYER_BYTES));
            if (tag == null || input.read() != -1) {
                throw new IOException("Player data file is malformed");
            }
            return tag;
        }
    }

    private static ListTag receiptList(CompoundTag persistent) {
        Tag raw = persistent.get(RECEIPTS_KEY);
        if (raw == null) {
            return new ListTag();
        }
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty()
                && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException(
                    "Player inventory receipt storage is invalid");
        }
        return list.copy();
    }

    private static PlayerInventoryReceiptInspection unknown(String detail) {
        return new PlayerInventoryReceiptInspection(
                CustodyAdapterInspectionStatus.UNKNOWN,
                java.util.Optional.empty(), detail);
    }

    private static boolean isCompletedCashClaim(
            UUID playerId,
            PlayerInventoryDeliveryReceipt receipt,
            EscrowClaim claim
    ) {
        return claim != null
                && receipt.playerId().equals(playerId)
                && claim.claimId().equals(receipt.claimId())
                && claim.ownerId().equals(playerId)
                && claim.transactionId().equals(receipt.transactionId())
                && (claim.kind() == ClaimKind.PROTECTED_CASH
                || claim.kind() == ClaimKind.FOREIGN_CASH)
                && claim.status() == ClaimStatus.COMPLETED
                && claim.remainingUnits() == 0L
                && claim.updatedAt().equals(receipt.deliveredAt());
    }
}
