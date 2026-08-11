package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import com.enviouse.futureshops.server.escrow.inventory.PlayerDataDurabilityBarrier;
import com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryReceiptStore;
import com.enviouse.futureshops.mixin.PlayerListInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;

public final class ServerPlayerItemInventoryAccess
        implements ItemInventoryAccess {
    private final ServerPlayer player;
    private final PlayerDataDurabilityBarrier durabilityBarrier;

    public ServerPlayerItemInventoryAccess(ServerPlayer player) {
        this(player, new PlayerDataDurabilityBarrier());
    }

    ServerPlayerItemInventoryAccess(
            ServerPlayer player,
            PlayerDataDurabilityBarrier durabilityBarrier
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.durabilityBarrier = Objects.requireNonNull(
                durabilityBarrier, "durabilityBarrier");
    }

    @Override
    public UUID playerId() {
        return player.getUUID();
    }

    @Override
    public ItemInventoryState capture() {
        requireServerThread();
        return ItemInventoryState.capture(player.getInventory());
    }

    @Override
    public void write(ItemInventorySlot slot, ItemStack stack) {
        requireServerThread();
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(stack, "stack");
        Inventory inventory = player.getInventory();
        ItemStack copied = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (slot.isOffhand()) {
            inventory.offhand.set(0, copied);
        } else {
            inventory.items.set(slot.serializedSlot(), copied);
        }
    }

    @Override
    public void flush() {
        requireServerThread();
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    @Override
    public void savePlayerData() {
        requireServerThread();
        try {
            Files.createDirectories(
                    PlayerInventoryReceiptStore.playerDirectory(
                            player.server));
            ((PlayerListInvoker) player.server.getPlayerList())
                    .futureshops$save(player);
        } catch (IOException | RuntimeException exception) {
            throw new ItemInventoryDurabilityException(
                    "Player item inventory save failed", exception);
        }
    }

    @Override
    public void forcePlayerData() {
        requireServerThread();
        try {
            durabilityBarrier.forcePlayerData(player.server,
                    player.getUUID());
        } catch (IOException | RuntimeException exception) {
            throw new ItemInventoryDurabilityException(
                    "Player item inventory force failed", exception);
        }
    }

    private void requireServerThread() {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException(
                    "Player item mutation requires the server thread");
        }
    }
}
