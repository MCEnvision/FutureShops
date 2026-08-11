package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExactItemClaimPayloadCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void exactSnapshotAndDeterministicIdentityRoundTrip() {
        UUID transactionId = UUID.randomUUID();
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD, 1);
        stack.getOrCreateTag().putString("owner", "exact owner");
        stack.getOrCreateTag().putInt("damage proof", 17);

        ExactItemClaimPayload first = ExactItemClaimPayload.capture(
                transactionId, "server.shop.buy.line.4", 0, 1, stack);
        ExactItemClaimPayload second = ExactItemClaimPayload.capture(
                transactionId, "server.shop.buy.line.4", 0, 1, stack);
        ExactItemClaimPayload decoded = ExactItemClaimPayloadCodec.decode(
                ExactItemClaimPayloadCodec.encode(first));

        assertEquals(first, second);
        assertEquals(first, decoded);
        assertEquals(first.lotId(), decoded.lotId());
        assertEquals(1, ItemStackSnapshotCodec.decode(
                decoded.canonicalOneCountTemplate()).getCount());
        ItemStack resolved = decoded.resolve().resolvedStack().orElseThrow();
        assertEquals(stack.getTag(), resolved.getTag());
        assertEquals(ExactItemClaimResolutionStatus.RESOLVED,
                decoded.resolve().status());
    }

    @Test
    void sourceTupleKeepsOneLotIdentityWhenContentConflicts() {
        UUID transactionId = UUID.randomUUID();
        ItemStack firstStack = new ItemStack(Items.EMERALD, 1);
        firstStack.getOrCreateTag().putString("version", "first");
        ItemStack secondStack = new ItemStack(Items.EMERALD, 1);
        secondStack.getOrCreateTag().putString("version", "second");

        ExactItemClaimPayload first = ExactItemClaimPayload.capture(
                transactionId, "server.shop.buy.line.1", 0, 1,
                firstStack);
        ExactItemClaimPayload conflict = ExactItemClaimPayload.capture(
                transactionId, "server.shop.buy.line.1", 0, 1,
                secondStack);

        assertEquals(first.lotId(), conflict.lotId());
        assertNotEquals(first.fingerprint(), conflict.fingerprint());
        assertNotEquals(first, conflict);
    }

    @Test
    void capabilityDataSurvivesClaimReceiptAndSnapshotRestore() {
        ItemStack stack = new ItemStack(
                MinecraftTestBootstrap.capabilityItem(), 2);
        IItemHandlerModifiable handler = (IItemHandlerModifiable) stack
                .getCapability(MinecraftTestBootstrap.capabilityItemHandler())
                .resolve().orElseThrow();
        ItemStack contained = new ItemStack(Items.DIAMOND, 3);
        contained.getOrCreateTag().putString("proof", "capability payload");
        handler.setStackInSlot(0, contained);
        UUID transactionId = UUID.randomUUID();

        ExactItemClaimPayload claim = ExactItemClaimPayload.capture(
                transactionId, "auction.capability.item", 0, 1, stack);
        ExactItemClaimPayload restoredClaim = ExactItemClaimPayloadCodec.decode(
                ExactItemClaimPayloadCodec.encode(claim));
        assertCapabilityPayload(restoredClaim.resolve()
                .resolvedStack().orElseThrow());

        List<ItemStack> main = new ArrayList<>();
        main.add(stack);
        while (main.size() < ItemInventorySlot.MAIN_SLOT_COUNT) {
            main.add(ItemStack.EMPTY);
        }
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.exact(stack), 2)));
        assertEquals(2, plan.allocations().get(0).count());
        assertEquals(ItemStack.EMPTY,
                plan.after().stack(ItemInventorySlot.main(0)));
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                UUID.randomUUID(), transactionId, UUID.randomUUID(), plan);
        ItemInventoryMutationReceipt receipt = ItemInventoryMutationReceipt.create(
                token, plan, Instant.parse("2026-07-19T15:00:00Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(receipt);
        ItemInventoryReceiptSnapshot snapshot = ItemInventoryReceiptSnapshotCodec.decode(
                ItemInventoryReceiptSnapshotCodec.encode(repository.snapshot()));
        ItemInventoryMutationReceipt restoredReceipt = snapshot.receipts()
                .get(token.requestId());

        assertCapabilityPayload(ItemStackSnapshotCodec.decode(
                restoredReceipt.actualPortions().get(0)
                        .actualStackSnapshot()));
    }

    @Test
    void capabilityStacksCannotSplitOrMergeWithoutAnAdapter() {
        ItemStack first = capabilityStack(1);
        List<ItemStack> main = new ArrayList<>();
        main.add(first);
        while (main.size() < ItemInventorySlot.MAIN_SLOT_COUNT) {
            main.add(ItemStack.EMPTY);
        }
        ItemInventoryState state = ItemInventoryState.of(
                main, ItemStack.EMPTY);
        ItemInventoryMutationPlan split = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(replaceFirst(main, capabilityStack(2)),
                        ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(UUID.randomUUID(),
                        ItemInputMatcher.itemOnly(
                                "futureshops:test_capability_item"), 1)));
        ItemInventoryMutationPlan inserted = ItemInventoryBatchPlanner.plan(
                state, List.of(ItemInventoryBatchEntry.insert(
                        UUID.randomUUID(), capabilityStack(1))));

        assertEquals(ItemInventoryPlanStatus.INSUFFICIENT_ITEMS,
                split.status());
        assertEquals(split.before(), split.after());
        assertEquals(1, inserted.after().stack(
                ItemInventorySlot.main(0)).getCount());
        assertEquals(1, inserted.after().stack(
                ItemInventorySlot.main(1)).getCount());
        assertCapabilityPayload(inserted.after().stack(
                ItemInventorySlot.main(0)));
        assertCapabilityPayload(inserted.after().stack(
                ItemInventorySlot.main(1)));
    }

    @Test
    void missingRegistryEntryPreservesRawFallbackBytes() throws Exception {
        UUID transactionId = UUID.randomUUID();
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "missingmod:removed_relic");
        raw.putByte("Count", (byte) 3);
        CompoundTag tag = new CompoundTag();
        tag.putString("custom payload", "must survive");
        raw.put("tag", tag);
        byte[] full = encodeRaw(raw);
        byte[] template = ItemStackSnapshotEvidence
                .canonicalOneCountSnapshot(full);

        ExactItemClaimPayload payload = ExactItemClaimPayload.preserveRaw(
                transactionId, "auction.expired.item", 1, 2,
                "missingmod:removed_relic", 3, template, full);
        ExactItemClaimPayload decoded = ExactItemClaimPayloadCodec.decode(
                ExactItemClaimPayloadCodec.encode(payload));
        ExactItemClaimResolution resolution = decoded.resolve();

        assertEquals(ExactItemClaimResolutionStatus.MISSING,
                resolution.status());
        MissingItemSnapshot missing = resolution.missingSnapshot()
                .orElseThrow();
        assertEquals("missingmod:removed_relic",
                missing.registryItemId());
        assertEquals(transactionId, missing.sourceTransactionId());
        assertEquals("auction.expired.item", missing.sourceKey());
        assertEquals(1, missing.portionIndex());
        assertEquals(2, missing.portionCount());
        assertEquals(3, missing.stackCount());
        assertArrayEquals(template,
                missing.canonicalOneCountTemplate());
        assertArrayEquals(full, missing.serializedStackSnapshot());
        assertEquals(payload.fingerprint(), missing.fingerprint());
    }

    @Test
    void corruptionBoundsAndNoncanonicalTemplatesFailClosed()
            throws Exception {
        ExactItemClaimPayload payload = ExactItemClaimPayload.capture(
                UUID.randomUUID(), "barter.output", 0, 1,
                new ItemStack(Items.EMERALD, 5));
        byte[] encoded = ExactItemClaimPayloadCodec.encode(payload);
        byte[] corrupt = encoded.clone();
        corrupt[corrupt.length / 2] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ExactItemClaimPayloadCodec.decode(corrupt));
        assertThrows(IllegalArgumentException.class,
                () -> ExactItemClaimPayloadCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ExactItemClaimPayloadCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ExactItemClaimPayloadCodec.decode(
                        new byte[ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES
                                + 1]));

        CompoundTag wrongTemplate = new CompoundTag();
        wrongTemplate.putString("id", "minecraft:emerald");
        wrongTemplate.putByte("Count", (byte) 1);
        wrongTemplate.putString("different", "nbt");
        assertThrows(IllegalArgumentException.class,
                () -> ExactItemClaimPayload.preserveRaw(
                        UUID.randomUUID(), "barter.output", 0, 1,
                        "minecraft:emerald", 5,
                        encodeRaw(wrongTemplate),
                        payload.serializedStackSnapshot()));
    }

    private static byte[] encodeRaw(CompoundTag tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        NbtIo.write(tag, output);
        output.flush();
        return bytes.toByteArray();
    }

    private static void assertCapabilityPayload(ItemStack stack) {
        IItemHandlerModifiable handler = (IItemHandlerModifiable) stack
                .getCapability(MinecraftTestBootstrap.capabilityItemHandler())
                .resolve().orElseThrow();
        ItemStack contained = handler.getStackInSlot(0);
        assertEquals(Items.DIAMOND, contained.getItem());
        assertEquals(3, contained.getCount());
        assertEquals("capability payload",
                contained.getTag().getString("proof"));
    }

    private static ItemStack capabilityStack(int count) {
        ItemStack stack = new ItemStack(
                MinecraftTestBootstrap.capabilityItem(), count);
        IItemHandlerModifiable handler = (IItemHandlerModifiable) stack
                .getCapability(MinecraftTestBootstrap.capabilityItemHandler())
                .resolve().orElseThrow();
        ItemStack contained = new ItemStack(Items.DIAMOND, 3);
        contained.getOrCreateTag().putString("proof", "capability payload");
        handler.setStackInSlot(0, contained);
        return stack;
    }

    private static List<ItemStack> replaceFirst(
            List<ItemStack> source,
            ItemStack first
    ) {
        List<ItemStack> copy = new ArrayList<>(source);
        copy.set(0, first);
        return copy;
    }
}
