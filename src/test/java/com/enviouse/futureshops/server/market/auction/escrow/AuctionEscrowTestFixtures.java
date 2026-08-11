package com.enviouse.futureshops.server.market.auction.escrow;

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
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class AuctionEscrowTestFixtures {
    public static final Instant NOW = Instant.parse(
            "2026-07-17T20:00:00Z");
    public static final String CURRENCY_ID = "futureshops:wallet";

    private AuctionEscrowTestFixtures() {
    }

    public static AuctionCreateEscrowIntent preparedCreate() {
        return preparedCreate(100L, 1000L, rules());
    }

    public static AuctionCreateEscrowIntent preparedCreate(
            long startingBidMinor,
            long buyoutMinor,
            AuctionRuleSnapshot rules
    ) {
        UUID requestId = id(1);
        UUID listingId = id(2);
        UUID sellerId = id(3);
        UUID activationId = id(4);
        UUID custodyRequest = AuctionEscrowIds.custodyRequestId(requestId);
        List<ItemStack> main = new ArrayList<>();
        for (int index = 0; index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            main.add(ItemStack.EMPTY);
        }
        main.set(0, new ItemStack(Items.EMERALD, 2));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                ItemInventoryState.of(main, ItemStack.EMPTY),
                List.of(ItemInventoryBatchEntry.extract(id(5),
                        ItemInputMatcher.itemOnly("minecraft:emerald"),
                        2)));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(sellerId, activationId,
                        custodyRequest, plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, NOW);
        ItemInventoryMutationIntent itemIntent =
                ItemInventoryMutationIntent.create(token, plan, receipt);
        byte[] snapshot = receipt.actualPortions().get(0)
                .actualStackSnapshot();
        byte[] template = ItemStackSnapshotCodec.encode(
                new ItemStack(Items.EMERALD, 1));
        ExactItemClaimPayload item = ExactItemClaimPayload.preserveRaw(
                activationId, AuctionEscrowItemCustody.sourceKey(listingId),
                0, 1, "minecraft:emerald", 2, template, snapshot);
        AuctionEscrowItemCustody custody = new AuctionEscrowItemCustody(
                listingId, activationId, receipt, List.of(item));
        CreateAuctionCommand command = new CreateAuctionCommand(requestId,
                listingId, sellerId, activationId,
                new AuctionItemLot(id(6), "minecraft:emerald",
                        sha256(snapshot), 2, snapshot.length,
                        "materials", "emerald minecraft"),
                AuctionListingType.AUCTION_WITH_BUYOUT,
                startingBidMinor, buyoutMinor, rules, 1000L, 2000L);
        return AuctionEscrowLifecyclePlanner.prepareCreate(command,
                itemIntent, custody, wallet(sellerId, 10_000L,
                        100_000L), CURRENCY_ID, NOW.minusSeconds(1));
    }

    public static AuctionEscrowCommit createCommit() {
        AuctionCreateEscrowIntent intent = preparedCreate();
        return AuctionEscrowLifecyclePlanner.commitCreate(
                com.enviouse.futureshops.server.market.auction
                        .AuctionHouseSnapshot.empty(), intent,
                intent.plannedCustody().receipt());
    }

    public static AuctionEscrowWalletSnapshot wallet(
            UUID playerId,
            long wallet,
            long limit
    ) {
        return new AuctionEscrowWalletSnapshot(playerId, wallet, 0L,
                0L, limit, 7L);
    }

    public static AuctionRuleSnapshot rules() {
        return new AuctionRuleSnapshot(10L, 250, 10L, 0, true,
                60L, 60L, 120L, 2, true,
                AuctionTimeBasis.REAL_TIME, true, 7L);
    }

    public static UUID id(long value) {
        return new UUID(37L, value);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
