package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.mixin.PlayerListInvoker;
import com.enviouse.futureshops.server.escrow.inventory.PlayerDataDurabilityBarrier;
import com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryReceiptStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public final class ProtectedCashRedemptionIntentStore {
    public static final String EVIDENCE_KEY =
            CashDepositEvidenceKeys.PROTECTED;
    public static final int MAX_DISCOVERY_FILES = 100_000;

    private static final long MAX_COMPRESSED_PLAYER_BYTES = 33_554_432L;
    private static final long MAX_DECODED_PLAYER_BYTES = 100_663_296L;

    private final PlayerDataDurabilityBarrier durabilityBarrier;

    public ProtectedCashRedemptionIntentStore() {
        this(new PlayerDataDurabilityBarrier());
    }

    ProtectedCashRedemptionIntentStore(
            PlayerDataDurabilityBarrier durabilityBarrier
    ) {
        this.durabilityBarrier = Objects.requireNonNull(
                durabilityBarrier, "durabilityBarrier");
    }

    public synchronized void persistIntent(
            MinecraftServer server,
            ServerPlayer player,
            ProtectedCashRedemptionEvidence evidence
    ) throws IOException {
        requireOwner(player, evidence);
        if (evidence.phase()
                != ProtectedCashRedemptionEvidence.Phase.INTENT) {
            throw new IllegalArgumentException(
                    "Protected cash intent phase is invalid");
        }
        CompoundTag persistent = player.getPersistentData();
        Tag current = persistent.get(EVIDENCE_KEY);
        if (current != null) {
            ProtectedCashRedemptionEvidence existing = decodeTag(current);
            if (existing.equals(evidence)) {
                saveAndForce(server, player);
                return;
            }
            throw new IllegalStateException(
                    "A protected cash redemption is already active");
        }
        if (CashDepositEvidenceKeys.hasConflict(persistent,
                EVIDENCE_KEY)) {
            throw new IllegalStateException(
                    "A foreign cash deposit is already active");
        }
        persistent.putByteArray(EVIDENCE_KEY, evidence.encode());
        saveAndForce(server, player);
    }

    public synchronized void persistUpgrade(
            MinecraftServer server,
            ServerPlayer player,
            ProtectedCashRedemptionEvidence evidence
    ) throws IOException {
        requireOwner(player, evidence);
        if (evidence.phase()
                == ProtectedCashRedemptionEvidence.Phase.INTENT) {
            throw new IllegalArgumentException(
                    "Protected cash upgrade phase is invalid");
        }
        CompoundTag persistent = player.getPersistentData();
        ProtectedCashRedemptionEvidence existing = decodeTag(
                persistent.get(EVIDENCE_KEY));
        requireUpgrade(existing, evidence);
        persistent.putByteArray(EVIDENCE_KEY, evidence.encode());
        saveAndForce(server, player);
    }

    public synchronized void persistUpgradeOffline(
            MinecraftServer server,
            UUID playerId,
            ProtectedCashRedemptionEvidence evidence
    ) throws IOException {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerId, "playerId");
        if (!playerId.equals(evidence.playerId())
                || evidence.phase()
                == ProtectedCashRedemptionEvidence.Phase.INTENT) {
            throw new IllegalArgumentException(
                    "Protected cash offline upgrade is invalid");
        }
        Path playerFile = PlayerInventoryReceiptStore.playerFile(
                server, playerId);
        CompoundTag root = readPlayerData(playerFile);
        CompoundTag forge = forgeData(root);
        ProtectedCashRedemptionEvidence existing = decodeTag(
                forge.get(EVIDENCE_KEY));
        requireUpgrade(existing, evidence);
        forge.putByteArray(EVIDENCE_KEY, evidence.encode());
        root.put("ForgeData", forge);
        writePlayerData(server, playerId, root);
    }

    public synchronized void cleanup(
            MinecraftServer server,
            ServerPlayer player,
            UUID transactionId
    ) throws IOException {
        Objects.requireNonNull(transactionId, "transactionId");
        CompoundTag persistent = player.getPersistentData();
        Tag raw = persistent.get(EVIDENCE_KEY);
        if (raw == null) {
            saveAndForce(server, player);
            return;
        }
        ProtectedCashRedemptionEvidence current = decodeTag(raw);
        if (!current.playerId().equals(player.getUUID())
                || !current.transactionId().equals(transactionId)) {
            throw new IllegalStateException(
                    "Protected cash cleanup identity conflicts");
        }
        requireTerminalCleanup(current);
        persistent.remove(EVIDENCE_KEY);
        saveAndForce(server, player);
    }

    public synchronized void discardIntent(
            MinecraftServer server,
            ServerPlayer player,
            ProtectedCashRedemptionEvidence evidence
    ) throws IOException {
        requireOwner(player, evidence);
        if (evidence.phase()
                != ProtectedCashRedemptionEvidence.Phase.INTENT
                || !evidence.inventoryState().matches(
                player.getInventory())) {
            throw new IllegalStateException(
                    "Protected cash intent is not safe to discard");
        }
        CompoundTag persistent = player.getPersistentData();
        ProtectedCashRedemptionEvidence current = decodeTag(
                persistent.get(EVIDENCE_KEY));
        if (!current.equals(evidence)) {
            throw new IllegalStateException(
                    "Protected cash intent cleanup identity conflicts");
        }
        persistent.remove(EVIDENCE_KEY);
        saveAndForce(server, player);
    }

    public synchronized void cleanup(
            MinecraftServer server,
            UUID playerId,
            UUID transactionId
    ) throws IOException {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            cleanup(server, player, transactionId);
            return;
        }
        Path playerFile = PlayerInventoryReceiptStore.playerFile(
                server, playerId);
        if (!Files.isRegularFile(playerFile)) {
            return;
        }
        CompoundTag root = readPlayerData(playerFile);
        CompoundTag forge = forgeData(root);
        Tag raw = forge.get(EVIDENCE_KEY);
        if (raw == null) {
            return;
        }
        ProtectedCashRedemptionEvidence current = decodeTag(raw);
        if (!current.playerId().equals(playerId)
                || !current.transactionId().equals(transactionId)) {
            throw new IllegalStateException(
                    "Protected cash cleanup identity conflicts");
        }
        requireTerminalCleanup(current);
        forge.remove(EVIDENCE_KEY);
        root.put("ForgeData", forge);
        writePlayerData(server, playerId, root);
    }

    public Inspection inspect(
            MinecraftServer server,
            UUID playerId,
            UUID transactionId
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        return inspectFile(PlayerInventoryReceiptStore.playerFile(
                server, playerId), playerId, Optional.of(transactionId));
    }

    Inspection inspect(
            Path playerFile,
            UUID playerId,
            UUID transactionId
    ) {
        Objects.requireNonNull(playerFile, "playerFile");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        return inspectFile(playerFile, playerId,
                Optional.of(transactionId));
    }

    public List<Inspection> discover(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Path directory = PlayerInventoryReceiptStore.playerDirectory(server);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".dat"))
                    .sorted(Comparator.comparing(path ->
                            path.getFileName().toString()))
                    .limit(MAX_DISCOVERY_FILES + 1L).toList();
            if (files.size() > MAX_DISCOVERY_FILES) {
                return List.of(Inspection.unknown(null, Optional.empty(),
                        "Protected cash player data discovery exceeds its limit"));
            }
            List<Inspection> result = new ArrayList<>();
            for (Path path : files) {
                UUID playerId;
                try {
                    String name = path.getFileName().toString();
                    playerId = UUID.fromString(name.substring(
                            0, name.length() - 4));
                } catch (RuntimeException exception) {
                    continue;
                }
                Inspection inspection = inspectFile(path, playerId,
                        Optional.empty());
                if (inspection.status() != InspectionStatus.MISSING) {
                    result.add(inspection);
                }
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException exception) {
            return List.of(Inspection.unknown(null, Optional.empty(),
                    "Protected cash player data discovery failed"));
        }
    }

    private Inspection inspectFile(Path playerFile,
                                   UUID playerId,
                                   Optional<UUID> expectedTransaction) {
        if (!Files.isRegularFile(playerFile)) {
            return new Inspection(InspectionStatus.MISSING, playerId,
                    expectedTransaction, Optional.empty(),
                    "Protected cash player data is unavailable");
        }
        try {
            CompoundTag root = readPlayerData(playerFile);
            if (!root.contains("Inventory", Tag.TAG_LIST)) {
                return Inspection.unknown(playerId, expectedTransaction,
                        "Protected cash player inventory is missing");
            }
            CompoundTag forge = forgeData(root);
            Tag raw = forge.get(EVIDENCE_KEY);
            if (raw == null) {
                return new Inspection(InspectionStatus.MISSING, playerId,
                        expectedTransaction, Optional.empty(),
                        "Protected cash evidence is missing");
            }
            ProtectedCashRedemptionEvidence evidence = decodeTag(raw);
            Optional<UUID> transactionId = Optional.of(
                    evidence.transactionId());
            if (!evidence.playerId().equals(playerId)
                    || expectedTransaction.isPresent()
                    && !expectedTransaction.orElseThrow().equals(
                    evidence.transactionId())) {
                return Inspection.unknown(playerId, transactionId,
                        "Protected cash evidence identity conflicts");
            }
            ProtectedCashInventoryState current =
                    ProtectedCashInventoryState.fromPlayerInventoryTag(
                            root.getList("Inventory", Tag.TAG_COMPOUND));
            if (!current.equals(evidence.inventoryState())) {
                return Inspection.unknown(playerId, transactionId,
                        "Protected cash inventory diverges from its evidence");
            }
            InspectionStatus status = switch (evidence.phase()) {
                case INTENT -> InspectionStatus.INTENT_UNCHANGED;
                case SETTLEMENT -> InspectionStatus.SETTLEMENT_PROVED;
                case CANCELLATION -> InspectionStatus.CANCELLATION_PROVED;
            };
            return new Inspection(status, playerId, transactionId,
                    Optional.of(evidence),
                    "Protected cash evidence and inventory match");
        } catch (IOException | RuntimeException exception) {
            return Inspection.unknown(playerId, expectedTransaction,
                    "Protected cash player evidence cannot be verified");
        }
    }

    private void saveAndForce(MinecraftServer server,
                              ServerPlayer player) throws IOException {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(player, "player");
        Files.createDirectories(
                PlayerInventoryReceiptStore.playerDirectory(server));
        ((PlayerListInvoker) server.getPlayerList()).futureshops$save(player);
        durabilityBarrier.forcePlayerData(server, player.getUUID());
    }

    private void writePlayerData(MinecraftServer server,
                                 UUID playerId,
                                 CompoundTag root) throws IOException {
        Path destination = PlayerInventoryReceiptStore.playerFile(
                server, playerId);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(
                destination.getFileName() + ".futureshops.tmp");
        NbtIo.writeCompressed(root, temporary.toFile());
        try {
            Files.move(temporary, destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        durabilityBarrier.forcePlayerData(server, playerId);
    }

    private static CompoundTag readPlayerData(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0L || size > MAX_COMPRESSED_PLAYER_BYTES) {
            throw new IOException(
                    "Protected cash player data size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(
                        Files.newInputStream(path))))) {
            CompoundTag root = NbtIo.read(input,
                    new NbtAccounter(MAX_DECODED_PLAYER_BYTES));
            if (root == null || input.read() != -1) {
                throw new IOException(
                        "Protected cash player data is malformed");
            }
            return root;
        }
    }

    private static CompoundTag forgeData(CompoundTag root) {
        Tag raw = root.get("ForgeData");
        if (raw == null) {
            return new CompoundTag();
        }
        if (!(raw instanceof CompoundTag compound)) {
            throw new IllegalStateException(
                    "Protected cash Forge data is invalid");
        }
        return compound;
    }

    private static ProtectedCashRedemptionEvidence decodeTag(Tag raw) {
        if (!(raw instanceof net.minecraft.nbt.ByteArrayTag bytes)) {
            throw new IllegalStateException(
                    "Protected cash evidence storage is invalid");
        }
        return ProtectedCashRedemptionEvidence.decode(bytes.getAsByteArray());
    }

    private static void requireOwner(
            ServerPlayer player,
            ProtectedCashRedemptionEvidence evidence
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(evidence, "evidence");
        if (!player.getUUID().equals(evidence.playerId())) {
            throw new IllegalArgumentException(
                    "Protected cash evidence owner does not match");
        }
    }

    private static void requireUpgrade(
            ProtectedCashRedemptionEvidence existing,
            ProtectedCashRedemptionEvidence replacement
    ) {
        if (!existing.playerId().equals(replacement.playerId())
                || !existing.transactionId().equals(
                replacement.transactionId())
                || !existing.reservation().equals(
                replacement.reservation())) {
            throw new IllegalStateException(
                    "Protected cash evidence upgrade identity conflicts");
        }
        if (existing.equals(replacement)) {
            return;
        }
        if (existing.phase()
                != ProtectedCashRedemptionEvidence.Phase.INTENT) {
            throw new IllegalStateException(
                    "Protected cash evidence cannot change terminal phase");
        }
    }

    private static void requireTerminalCleanup(
            ProtectedCashRedemptionEvidence evidence
    ) {
        if (evidence.phase()
                == ProtectedCashRedemptionEvidence.Phase.INTENT) {
            throw new IllegalStateException(
                    "Protected cash intent cannot be cleaned before resolution");
        }
    }

    public enum InspectionStatus {
        MISSING,
        INTENT_UNCHANGED,
        SETTLEMENT_PROVED,
        CANCELLATION_PROVED,
        UNKNOWN
    }

    public record Inspection(
            InspectionStatus status,
            UUID playerId,
            Optional<UUID> transactionId,
            Optional<ProtectedCashRedemptionEvidence> evidence,
            String detail
    ) {
        public Inspection {
            Objects.requireNonNull(status, "status");
            transactionId = Objects.requireNonNull(
                    transactionId, "transactionId");
            evidence = Objects.requireNonNull(evidence, "evidence");
            detail = Objects.requireNonNull(detail, "detail").strip();
            if (detail.isEmpty()
                    || status == InspectionStatus.UNKNOWN
                    && evidence.isPresent()
                    || status != InspectionStatus.UNKNOWN
                    && status != InspectionStatus.MISSING
                    && evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "Protected cash inspection is invalid");
            }
        }

        private static Inspection unknown(
                UUID playerId,
                Optional<UUID> transactionId,
                String detail
        ) {
            return new Inspection(InspectionStatus.UNKNOWN, playerId,
                    transactionId, Optional.empty(), detail);
        }
    }
}
