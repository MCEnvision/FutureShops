package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInventoryReceiptCompactionBridgeTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void typedTerminalCheckpointCompactsAFullReceipt() {
        ItemInventoryMutationReceipt receipt = receipt();
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(receipt);
        ItemInventoryTerminalCheckpointEvidence terminal =
                new ItemInventoryTerminalCheckpointEvidence(
                        receipt.token().requestId(),
                        receipt.token().transactionId(),
                        ItemInventoryCheckpointTerminalState.COMMITTED,
                        true, receipt.digest());
        ItemInventoryVerifiedCheckpointEvidence checkpoint =
                new ItemInventoryVerifiedCheckpointEvidence(7L,
                        sha256("trusted checkpoint"), List.of(terminal));

        assertEquals(1, ItemInventoryReceiptCompactionBridge.compact(
                repository, checkpoint));
        assertTrue(repository.findFullReceipt(
                receipt.token().requestId()).isEmpty());
        assertTrue(repository.findEvidence(
                receipt.token().requestId()).isPresent());
    }

    @Test
    void bridgeRejectsNonterminalOrIneligibleEvidence() {
        ItemInventoryMutationReceipt receipt = receipt();

        assertThrows(IllegalArgumentException.class,
                () -> new ItemInventoryTerminalCheckpointEvidence(
                        receipt.token().requestId(),
                        receipt.token().transactionId(),
                        ItemInventoryCheckpointTerminalState.PREPARED,
                        true, receipt.digest()));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemInventoryTerminalCheckpointEvidence(
                        receipt.token().requestId(),
                        receipt.token().transactionId(),
                        ItemInventoryCheckpointTerminalState.COMMITTED,
                        false, receipt.digest()));
    }

    @Test
    void committedCheckpointDigestMustMatchTheFullReceipt() {
        ItemInventoryMutationReceipt receipt = receipt();
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(receipt);
        ItemInventoryTerminalCheckpointEvidence terminal =
                new ItemInventoryTerminalCheckpointEvidence(
                        receipt.token().requestId(),
                        receipt.token().transactionId(),
                        ItemInventoryCheckpointTerminalState.COMMITTED,
                        true, sha256("wrong terminal"));
        ItemInventoryVerifiedCheckpointEvidence checkpoint =
                new ItemInventoryVerifiedCheckpointEvidence(8L,
                        sha256("trusted checkpoint"), List.of(terminal));

        assertThrows(IllegalStateException.class,
                () -> ItemInventoryReceiptCompactionBridge.compact(
                        repository, checkpoint));
        assertTrue(repository.findFullReceipt(
                receipt.token().requestId()).isPresent());
    }

    private static ItemInventoryMutationReceipt receipt() {
        List<ItemStack> main = emptyMain();
        main.set(0, new ItemStack(Items.EMERALD, 4));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(
                        UUID.randomUUID(), ItemInputMatcher.itemOnly(
                                "minecraft:emerald"), 2)));
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                plan);
        return ItemInventoryMutationReceipt.create(token, plan,
                Instant.parse("2026-07-17T12:34:56.789Z"));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<ItemStack> emptyMain() {
        List<ItemStack> slots = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }
}
