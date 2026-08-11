package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.mixin.PlayerListInvoker;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CashClaimCustodySupport;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapter;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterApplyResult;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspection;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyItemSnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodySimulationResult;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class PlayerInventoryCustodyAdapter implements CustodyAdapter {
    private final MinecraftServer server;
    private final PlayerInventoryReceiptStore receiptStore;
    private final Function<UUID, EscrowClaim> claimLookup;
    private final PlayerDataDurabilityBarrier durabilityBarrier;
    private Session active;

    public PlayerInventoryCustodyAdapter(
            MinecraftServer server,
            ClaimSavedData claims
    ) {
        this(server, new PlayerInventoryReceiptStore(),
                Objects.requireNonNull(claims, "claims")::getClaim,
                new PlayerDataDurabilityBarrier());
    }

    PlayerInventoryCustodyAdapter(
            MinecraftServer server,
            PlayerInventoryReceiptStore receiptStore,
            Function<UUID, EscrowClaim> claimLookup
    ) {
        this(server, receiptStore, claimLookup,
                new PlayerDataDurabilityBarrier());
    }

    PlayerInventoryCustodyAdapter(
            MinecraftServer server,
            PlayerInventoryReceiptStore receiptStore,
            Function<UUID, EscrowClaim> claimLookup,
            PlayerDataDurabilityBarrier durabilityBarrier
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.receiptStore = Objects.requireNonNull(
                receiptStore, "receiptStore");
        this.claimLookup = Objects.requireNonNull(
                claimLookup, "claimLookup");
        this.durabilityBarrier = Objects.requireNonNull(
                durabilityBarrier, "durabilityBarrier");
    }

    @Override
    public String adapterId() {
        return CashClaimCustodySupport.PLAYER_INVENTORY_ADAPTER_ID;
    }

    @Override
    public CustodyAdapterCapability capability() {
        return CustodyAdapterCapability.RECONCILABLE;
    }

    public synchronized Map<UUID, CustodyTransferEvidence> prepare(
            ServerPlayer player,
            UUID claimId,
            CustodyBatchPlan plan,
            ItemStack deliveredStack,
            Instant deliveredAt
    ) {
        requireServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(deliveredStack, "deliveredStack");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        if (active != null) {
            throw new IllegalStateException(
                    "Player inventory delivery is already prepared");
        }
        if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
            throw new IllegalArgumentException(
                    "Cash claim owner is not an active player");
        }
        CustodyLot lot = requirePlan(plan);
        requireStack(lot, deliveredStack);
        PlayerInventoryInsertionPlan insertion =
                PlayerInventoryInsertionPlan.plan(
                        PlayerInventoryInsertionPlan.mainSlots(
                                player.getInventory()), deliveredStack);
        UUID batchId = CustodyPreparedBatch.deterministicId(
                lot.transactionId(), plan.requestKey());
        PlayerInventoryDeliveryToken token =
                PlayerInventoryDeliveryToken.create(player.getUUID(), claimId,
                        lot.transactionId(), batchId, lot.lotId(),
                        plan.requestKey(), lot.assetFingerprint(),
                        insertion.beforeHash(), insertion.afterHash());
        String encodedToken = token.encode();
        CustodyEndpointEvidence destination =
                new CustodyEndpointEvidence(adapterId(), capability(),
                        player.getUUID().toString(), "inventory.main",
                        insertion.beforeHash(), insertion.afterHash(),
                        encodedToken);
        CustodyTransferEvidence evidence = new CustodyTransferEvidence(
                lot.holdEvidence().destination(), destination);
        PlayerInventoryDeliveryReceipt receipt =
                PlayerInventoryDeliveryReceipt.create(token,
                        plan.requestKey(), insertion.changes(), evidence,
                        deliveredAt);
        active = new Session(player, plan, deliveredStack.copy(), insertion,
                token, receipt, evidence);
        return Map.of(lot.lotId(), evidence);
    }

    @Override
    public synchronized CustodySimulationResult simulate(
            CustodyBatchPlan plan
    ) {
        requireServerThread();
        Session session = requireActive(plan);
        if (!session.insertion().fullyFits()) {
            return CustodySimulationResult.rejected(plan.requiredUnits(), 0L,
                    "Player inventory does not have enough space");
        }
        if (!session.insertion().matchesBefore(
                session.player().getInventory())) {
            return CustodySimulationResult.rejected(plan.requiredUnits(), 0L,
                    "Player inventory changed before delivery");
        }
        return CustodySimulationResult.accepted(plan.requiredUnits(),
                plan.requiredUnits(), session.token().encode());
    }

    @Override
    public synchronized CustodyAdapterApplyResult apply(
            CustodyBatchPlan plan,
            String simulationToken
    ) {
        requireServerThread();
        Session session = requireActive(plan);
        if (!session.token().encode().equals(simulationToken)) {
            return CustodyAdapterApplyResult.rejected(
                    "Player inventory delivery token does not match");
        }
        if (!session.insertion().fullyFits()
                || !session.insertion().matchesBefore(
                session.player().getInventory())) {
            return CustodyAdapterApplyResult.rejected(
                    "Player inventory changed before delivery");
        }
        boolean inventoryChanged = false;
        boolean receiptAdded = false;
        boolean saveStarted = false;
        boolean durabilityConfirmed = false;
        try {
            receiptStore.pruneCompletedCashClaims(
                    session.player(), claimLookup);
            session.insertion().apply(session.player().getInventory());
            inventoryChanged = true;
            receiptStore.append(session.player(), session.receipt());
            receiptAdded = true;
            Files.createDirectories(
                    PlayerInventoryReceiptStore.playerDirectory(server));
            saveStarted = true;
            ((PlayerListInvoker) server.getPlayerList())
                    .futureshops$save(session.player());
            durabilityBarrier.force(
                    PlayerInventoryReceiptStore.playerFile(
                            server, session.token().playerId()));
            durabilityConfirmed = true;
            PlayerInventoryReceiptInspection inspection =
                    receiptStore.inspect(server, session.token());
            if (inspection.status()
                    == CustodyAdapterInspectionStatus.APPLIED) {
                return new CustodyAdapterApplyResult(true,
                        Set.of(session.token().lotId()),
                        Map.of(session.token().lotId(), session.evidence()),
                        "Player inventory delivery was saved");
            }
            if (inspection.status()
                    == CustodyAdapterInspectionStatus.NOT_APPLIED) {
                session.insertion().restore(session.player().getInventory());
                receiptStore.remove(session.player(),
                        session.token().receiptId());
            }
            return CustodyAdapterApplyResult.rejected(inspection.detail());
        } catch (IOException | RuntimeException exception) {
            if (saveStarted && !durabilityConfirmed) {
                durabilityBarrier.markUnconfirmed(
                        session.token().receiptId());
                return CustodyAdapterApplyResult.rejected(
                        "Player inventory delivery durability is unknown");
            }
            if (saveStarted) {
                PlayerInventoryReceiptInspection inspection =
                        receiptStore.inspect(server, session.token());
                if (inspection.status()
                        == CustodyAdapterInspectionStatus.APPLIED) {
                    return new CustodyAdapterApplyResult(true,
                            Set.of(session.token().lotId()),
                            Map.of(session.token().lotId(),
                                    session.evidence()),
                            "Player inventory delivery was saved");
                }
                if (inspection.status()
                        == CustodyAdapterInspectionStatus.UNKNOWN) {
                    return CustodyAdapterApplyResult.rejected(
                            "Player inventory delivery outcome is unknown");
                }
            }
            if (inventoryChanged && session.insertion().matchesAfter(
                    session.player().getInventory())) {
                try {
                    session.insertion().restore(
                            session.player().getInventory());
                    if (receiptAdded) {
                        receiptStore.remove(session.player(),
                                session.token().receiptId());
                    }
                } catch (RuntimeException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                    return CustodyAdapterApplyResult.rejected(
                            "Player inventory delivery outcome is unknown");
                }
            }
            return CustodyAdapterApplyResult.rejected(
                    "Player inventory delivery could not be saved");
        }
    }

    @Override
    public synchronized CustodyAdapterInspection inspect(
            String simulationToken
    ) {
        requireServerThread();
        PlayerInventoryDeliveryToken token;
        try {
            token = PlayerInventoryDeliveryToken.decode(simulationToken);
        } catch (RuntimeException exception) {
            return CustodyAdapterInspection.unknown(
                    "Player inventory delivery token is invalid");
        }
        if (durabilityBarrier.isUnconfirmed(token.receiptId())) {
            return CustodyAdapterInspection.unknown(
                    "Player inventory delivery durability is unknown");
        }
        try {
            durabilityBarrier.force(
                    PlayerInventoryReceiptStore.playerFile(
                            server, token.playerId()));
        } catch (IOException | RuntimeException exception) {
            durabilityBarrier.markUnconfirmed(token.receiptId());
            return CustodyAdapterInspection.unknown(
                    "Player inventory delivery durability is unknown");
        }
        PlayerInventoryReceiptInspection inspection =
                receiptStore.inspect(server, token);
        if (inspection.status() == CustodyAdapterInspectionStatus.APPLIED) {
            PlayerInventoryDeliveryReceipt receipt =
                    inspection.receipt().orElseThrow();
            return CustodyAdapterInspection.applied(
                    Map.of(token.lotId(), receipt.evidence()),
                    inspection.detail());
        }
        if (inspection.status()
                == CustodyAdapterInspectionStatus.NOT_APPLIED) {
            return CustodyAdapterInspection.notApplied(inspection.detail());
        }
        return CustodyAdapterInspection.unknown(inspection.detail());
    }

    public synchronized void complete(String simulationToken) {
        requireServerThread();
        PlayerInventoryDeliveryToken token =
                PlayerInventoryDeliveryToken.decode(simulationToken);
        ServerPlayer player = server.getPlayerList().getPlayer(
                token.playerId());
        if (player != null) {
            receiptStore.remove(player, token.receiptId());
            ((PlayerListInvoker) server.getPlayerList())
                    .futureshops$save(player);
        }
        if (active != null && active.token().equals(token)) {
            active = null;
        }
    }

    public synchronized void clearPrepared() {
        requireServerThread();
        active = null;
    }

    private Session requireActive(CustodyBatchPlan plan) {
        if (active == null || !active.plan().equals(plan)) {
            throw new IllegalStateException(
                    "Player inventory delivery was not prepared");
        }
        return active;
    }

    private CustodyLot requirePlan(CustodyBatchPlan plan) {
        if (plan.operation() != CustodyOperation.RELEASE
                || !plan.adapterId().equals(adapterId())
                || plan.capability() != capability()
                || plan.lots().size() != 1) {
            throw new IllegalArgumentException(
                    "Player inventory custody plan is invalid");
        }
        return plan.lots().get(0);
    }

    private static void requireStack(CustodyLot lot, ItemStack stack) {
        if (stack.isEmpty() || lot.itemSnapshots().size() != 1) {
            throw new IllegalArgumentException(
                    "Cash claim stack snapshot is invalid");
        }
        CustodyItemSnapshot snapshot = lot.itemSnapshots().get(0);
        ResourceLocation registryId = ForgeRegistries.ITEMS.getKey(
                stack.getItem());
        if (registryId == null
                || !registryId.toString().equals(snapshot.registryId())
                || stack.getCount() != snapshot.count()
                || !ItemStackSnapshotCodec.snapshotMatchesIdentity(
                snapshot.serializedNbt(), stack)) {
            throw new IllegalArgumentException(
                    "Cash claim stack does not match custody");
        }
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Player inventory custody must run on the server thread");
        }
    }

    private record Session(
            ServerPlayer player,
            CustodyBatchPlan plan,
            ItemStack deliveredStack,
            PlayerInventoryInsertionPlan insertion,
            PlayerInventoryDeliveryToken token,
            PlayerInventoryDeliveryReceipt receipt,
            CustodyTransferEvidence evidence
    ) {
    }
}
