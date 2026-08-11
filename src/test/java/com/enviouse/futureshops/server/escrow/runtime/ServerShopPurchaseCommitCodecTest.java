package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopPurchaseCommitCodecTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T18:00:00Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void cartCommitRoundTripsEveryAtomicComponent() {
        ServerShopPurchaseCommit commit = commit(-45L,
                PaymentSource.PHYSICAL, true);

        ServerShopPurchaseCommit decoded =
                ServerShopPurchaseCommitCodec.decode(
                        ServerShopPurchaseCommitCodec.encode(commit));

        assertEquals(commit, decoded);
        assertEquals(1, decoded.completedLineTransactions().size());
        assertEquals(decoded.completedTransaction().transactionId(),
                decoded.completedLineTransactions().get(0)
                        .parentTransactionId().orElseThrow());
        assertEquals(3, decoded.totalQuantity());
        assertEquals(75L, decoded.totalCostMinorUnits());
    }

    @Test
    void protectedPhysicalFundingRoundTripsWithoutWalletDebit() {
        UUID requestId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        ServerShopPurchaseCommit.Line line =
                ServerShopPurchaseService.captureLine(requestId, 0,
                        "diamond_offer", "minecraft:diamond", 3, 75L,
                        4L, List.of(stack));
        ServerShopPurchaseCommit.PhysicalFunding funding =
                new ServerShopPurchaseCommit.PhysicalFunding(requestId,
                        UUID.randomUUID(), UUID.randomUUID(), 75L);
        ServerShopPurchaseCommit commit = ServerShopPurchaseCommit.create(
                requestId, playerId, "default", false,
                PaymentSource.PHYSICAL, 500L, 0L, "Funds", 2,
                List.of(line), new DimensionAwareShopReference("default",
                        "minecraft:overworld", 0, 64, 0), NOW,
                Optional.of(funding));

        ServerShopPurchaseCommit decoded =
                ServerShopPurchaseCommitCodec.decode(
                        ServerShopPurchaseCommitCodec.encode(commit));

        assertEquals(commit, decoded);
        assertEquals(500L, decoded.resultingWalletMinorUnits());
        assertTrue(decoded.physicalFunding().isPresent());
        assertEquals(ServerShopPurchaseCommit.claimAccount(funding.claimId()),
                decoded.ledgerTransaction().legs().get(0).account());
    }

    @Test
    void negativeDebtSnapshotIncludesLongMinimum() {
        ServerShopPurchaseCommit minimum = commit(Long.MIN_VALUE,
                PaymentSource.WALLET, false);

        assertEquals(Long.MIN_VALUE,
                ServerShopPurchaseCommitCodec.decode(
                        ServerShopPurchaseCommitCodec.encode(minimum))
                        .debtBeforeMinorUnits());
        assertEquals(-45L, commit(-45L, PaymentSource.WALLET, false)
                .debtBeforeMinorUnits());
    }

    @Test
    void positiveDebtSnapshotIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> commit(1L, PaymentSource.WALLET, false));
    }

    private static ServerShopPurchaseCommit commit(
            long debt,
            PaymentSource source,
            boolean cart
    ) {
        UUID requestId = UUID.randomUUID();
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.getOrCreateTag().putString("quote", "exact");
        ServerShopPurchaseCommit.Line line =
                ServerShopPurchaseService.captureLine(requestId, 0,
                        "diamond_offer", "minecraft:diamond", 3, 75L,
                        4L, List.of(stack));
        DimensionAwareShopReference reference =
                new DimensionAwareShopReference("default",
                        "minecraft:overworld", 0, 64, 0);
        if (source == PaymentSource.PHYSICAL) {
            return ServerShopPurchaseCommit.createLegacyPhysical(requestId,
                    UUID.randomUUID(), "default", cart, 500L, debt,
                    "Funds", 2, List.of(line), reference, NOW);
        }
        return ServerShopPurchaseCommit.create(requestId, UUID.randomUUID(),
                "default", cart, source, 500L, debt, "Funds", 2,
                List.of(line), reference, NOW);
    }
}
