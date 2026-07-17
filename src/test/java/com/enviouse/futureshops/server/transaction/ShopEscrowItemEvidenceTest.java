package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopEscrowItemEvidenceTest {
    @Test
    void outputIsPartitionedWithoutLosingExactTagEvidence() {
        UUID requestId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        ItemStack prototype = new ItemStack(Items.DIAMOND, 1);
        prototype.getOrCreateTag().putString("variant", "exact");

        List<ExactItemClaimPayload> output =
                ShopEscrowItemEvidence.captureOutput(requestId,
                        "server.shop.output", prototype, 130);

        assertEquals(List.of(64, 64, 2), output.stream()
                .map(ExactItemClaimPayload::stackCount).toList());
        assertEquals(130, output.stream().mapToInt(
                ExactItemClaimPayload::stackCount).sum());
        for (ExactItemClaimPayload payload : output) {
            ItemStack resolved = payload.resolve().resolvedStack()
                    .orElseThrow();
            assertEquals("exact",
                    resolved.getTag().getString("variant"));
        }
    }

    @Test
    void invalidRegistryAndNbtEvidenceFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ShopEscrowItemEvidence.exactTemplate(
                        "minecraft:air", ""));
        assertThrows(IllegalArgumentException.class,
                () -> ShopEscrowItemEvidence.exactTemplate(
                        "minecraft:diamond", "{broken"));
    }
}
