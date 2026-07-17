package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.market.bazaar.BazaarExecutionPricePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarSelfTradePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BazaarEscrowTestFixtures {
    public static final Instant NOW = Instant.parse(
            "2026-07-17T22:00:00Z");
    public static final String CURRENCY = "futureshops:wallet";

    private BazaarEscrowTestFixtures() {
    }

    public static BazaarOrderBookSnapshot baseSnapshot(
            BazaarRuleSnapshot rules
    ) {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product());
        book.setEffectiveRules(rules);
        return book.snapshot();
    }

    public static PreparedSell sell(
            long seed,
            UUID owner,
            int quantity,
            long price,
            BazaarRuleSnapshot rules
    ) {
        UUID requestId = id(seed);
        UUID orderId = id(seed + 1L);
        UUID activationId = id(seed + 2L);
        UUID custodyId = id(seed + 3L);
        CreateBazaarOrderCommand command = new CreateBazaarOrderCommand(
                requestId, orderId, owner, activationId, Optional.empty(),
                Optional.of(custodyId), "emerald", 1L,
                BazaarOrderSide.SELL, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, price, quantity,
                1_000L + seed, 0L, rules);
        List<ItemStack> main = new ArrayList<>();
        for (int index = 0; index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(ItemStack.EMPTY);
        }
        main.set(0, new ItemStack(Items.EMERALD, quantity));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(id(seed + 4L),
                        ItemInputMatcher.itemOnly("minecraft:emerald"),
                        quantity)));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(owner, activationId,
                        requestId, plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan,
                        NOW.minusSeconds(2));
        ItemInventoryMutationIntent itemIntent =
                ItemInventoryMutationIntent.create(token, plan, receipt);
        byte[] snapshot = receipt.actualPortions().get(0)
                .actualStackSnapshot();
        ExactItemClaimPayload payload =
                ExactItemClaimPayload.preserveRaw(activationId,
                        BazaarSellItemCustody.sourceKey(orderId), 0, 1,
                        "minecraft:emerald", quantity,
                        ItemStackSnapshotCodec.encode(
                                new ItemStack(Items.EMERALD, 1)), snapshot);
        BazaarSellItemCustody custody = BazaarSellItemCustody.create(
                command, product(), receipt, List.of(payload));
        BazaarCreateEscrowIntent intent =
                BazaarEscrowLifecyclePlanner.prepareSell(command,
                        product(), itemIntent, custody, CURRENCY,
                        NOW.minusSeconds(1));
        return new PreparedSell(intent, receipt);
    }

    public static BazaarCreateEscrowIntent buy(
            long seed,
            UUID owner,
            int quantity,
            long price,
            BazaarTimeInForce timeInForce,
            BazaarRuleSnapshot rules
    ) {
        UUID requestId = id(seed);
        UUID orderId = id(seed + 1L);
        UUID activationId = id(seed + 2L);
        UUID holdId = id(seed + 3L);
        CreateBazaarOrderCommand command = new CreateBazaarOrderCommand(
                requestId, orderId, owner, activationId,
                Optional.of(holdId), Optional.empty(), "emerald", 1L,
                BazaarOrderSide.BUY, timeInForce.rests()
                ? BazaarOrderType.LIMIT : BazaarOrderType.INSTANT,
                timeInForce, price, quantity, 10_000L + seed,
                timeInForce == BazaarTimeInForce.GOOD_UNTIL_TIME
                        ? 20_000L + seed : 0L, rules);
        BazaarBuyFundingEvidence funding = new BazaarBuyFundingEvidence(
                requestId, orderId, owner, holdId,
                BazaarEscrowPaymentSource.WALLET,
                new BazaarEscrowWalletSnapshot(owner,
                        10_000_000L, 7L), Optional.empty(), CURRENCY);
        return BazaarEscrowLifecyclePlanner.prepareBuy(command, funding,
                NOW.minusSeconds(1));
    }

    public static BazaarRuleSnapshot rules() {
        return new BazaarRuleSnapshot(10, 25, 1_000_000,
                1_000_000_000_000_000L, 32, 8,
                5_000_000_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5_000, 0L, 7L);
    }

    public static BazaarProduct product() {
        return new BazaarProduct("emerald", 1L, "minecraft:emerald", "",
                "materials", 1, 1L, 1L, 1_000_000_000L, 1_000_000,
                BazaarProductStatus.ACTIVE);
    }

    public static UUID id(long value) {
        return new UUID(71L, value);
    }

    public record PreparedSell(
            BazaarCreateEscrowIntent intent,
            ItemInventoryMutationReceipt receipt
    ) {
    }
}
