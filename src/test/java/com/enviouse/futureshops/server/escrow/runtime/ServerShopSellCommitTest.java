package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopSellCommitTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void exactCustodyInboundStockAndPayoutRemainConserved() {
        ServerShopSellCommit commit = ServerShopSellTestFixtures.commit();

        assertEquals(300L, commit.payoutMinorUnits());
        assertEquals(250L, commit.acceptedPayoutMinorUnits());
        assertEquals(150L, commit.debtCreditMinorUnits());
        assertEquals(100L, commit.walletCreditMinorUnits());
        assertEquals(50L, commit.overflowClaimMinorUnits());
        assertEquals(100L, commit.resultingBalanceMinorUnits());
        assertTrue(commit.overflowClaim().isPresent());
        assertEquals(50L, commit.overflowClaim().orElseThrow()
                .remainingUnits());
        assertEquals(StockReservationDirection.INBOUND,
                commit.stockReservation().reservations().get(0)
                        .direction());
        assertEquals(StockMutationType.COMMIT_BATCH,
                commit.stockCommit().operation());
        assertEquals(11L, commit.stockReservation().reservations().get(0)
                .expectedListingRevision());
        assertTrue(commit.ledgerTransaction().legs().stream().anyMatch(
                value -> value.account().type()
                        == LedgerAccountType.PLAYER_CLAIM));
        ServerShopSellConservationValidator.validate(commit);
    }

    @Test
    void longMinimumDebtIsNormalizedWithoutNegationOverflow() {
        UUID requestId = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        byte[] template = ServerShopSellTestFixtures.template();
        ItemInventoryMutationReceipt receipt =
                ServerShopSellTestFixtures.receipt(requestId,
                        ServerShopSellTestFixtures.PLAYER_ID, 1, template,
                        ServerShopSellTestFixtures.APPLIED_AT);

        ServerShopSellCommit commit = ServerShopSellCommit.create(
                requestId, ServerShopSellTestFixtures.PLAYER_ID,
                "default", "emerald.offer", "minecraft:emerald", 1,
                Long.MAX_VALUE, 1L, 0L,
                ServerShopSellTestFixtures.QUOTED_AT, 0L, Long.MIN_VALUE,
                0L, 0L, 1L, "Credits", 2, template, receipt,
                new DimensionAwareShopReference("default",
                        "minecraft:overworld", 0, 64, 0));

        assertEquals(Long.MAX_VALUE, commit.acceptedPayoutMinorUnits());
        assertEquals(Long.MAX_VALUE, commit.debtCreditMinorUnits());
        assertEquals(0L, commit.walletCreditMinorUnits());
        assertEquals(-1L, commit.resultingBalanceMinorUnits());
        assertFalse(commit.overflowClaim().isPresent());
        ServerShopSellConservationValidator.validate(commit);
    }

    @Test
    void multiplicationOverflowFailsBeforeEvidenceIsCreated() {
        byte[] template = ServerShopSellTestFixtures.template();
        UUID requestId = UUID.randomUUID();
        ItemInventoryMutationReceipt receipt =
                ServerShopSellTestFixtures.receipt(requestId,
                        ServerShopSellTestFixtures.PLAYER_ID, 2, template,
                        ServerShopSellTestFixtures.APPLIED_AT);

        assertThrows(ArithmeticException.class, () ->
                ServerShopSellCommit.create(requestId,
                        ServerShopSellTestFixtures.PLAYER_ID, "default",
                        "emerald.offer", "minecraft:emerald", 2,
                        Long.MAX_VALUE, 0L, 0L,
                        ServerShopSellTestFixtures.QUOTED_AT,
                        0L, 0L, 0L, Long.MAX_VALUE, 0L,
                        "Credits", 2, template, receipt,
                        new DimensionAwareShopReference("default",
                                "minecraft:overworld", 0, 64, 0)));
    }

    @Test
    void differentExactNbtCannotReuseCustodyEvidence() {
        ServerShopSellCommit valid = ServerShopSellTestFixtures.commit();
        ItemStack other = new ItemStack(Items.EMERALD, 1);
        other.getOrCreateTag().putString("sell_variant", "different");
        byte[] different = ItemStackSnapshotCodec.encode(other);

        assertThrows(IllegalArgumentException.class, () ->
                new ServerShopSellCommit(valid.requestId(),
                        valid.playerId(), valid.shopId(), valid.listingId(),
                        valid.itemId(), valid.quantity(),
                        valid.unitPriceMinorUnits(), valid.quoteRevision(),
                        valid.expectedStockRevision(),
                        valid.quoteCreatedAt(),
                        valid.walletBeforeMinorUnits(),
                        valid.debtBeforeMinorUnits(),
                        valid.reservedBeforeMinorUnits(),
                        valid.walletBalanceLimitMinorUnits(),
                        valid.configurationGeneration(),
                        valid.currencyName(), valid.currencyDecimals(),
                        different, valid.itemCustodyReceipt(),
                        valid.completedTransaction(),
                        valid.ledgerTransaction(),
                        valid.stockReservation(), valid.stockCommit(),
                        valid.overflowClaim()));
    }
}
