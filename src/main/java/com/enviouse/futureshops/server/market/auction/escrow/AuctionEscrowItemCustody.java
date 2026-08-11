package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AuctionEscrowItemCustody(
        UUID listingId,
        UUID activationTransactionId,
        ItemInventoryMutationReceipt receipt,
        List<ExactItemClaimPayload> exactItems
) {
    public AuctionEscrowItemCustody {
        listingId = AuctionEscrowIds.requireId(listingId, "listingId");
        activationTransactionId = AuctionEscrowIds.requireId(
                activationTransactionId, "activationTransactionId");
        receipt = Objects.requireNonNull(receipt, "receipt");
        exactItems = List.copyOf(Objects.requireNonNull(
                exactItems, "exactItems"));
        if (exactItems.isEmpty()
                || exactItems.size() != receipt.actualPortions().size()) {
            throw new IllegalArgumentException(
                    "Auction exact item custody count is invalid");
        }
        String sourceKey = sourceKey(listingId);
        List<ItemInventoryAllocation> portions = receipt.actualPortions();
        for (int index = 0; index < exactItems.size(); index++) {
            ExactItemClaimPayload item = Objects.requireNonNull(
                    exactItems.get(index), "exactItem");
            ItemInventoryAllocation portion = portions.get(index);
            if (!item.sourceTransactionId().equals(
                    activationTransactionId)
                    || !item.sourceKey().equals(sourceKey)
                    || item.portionIndex() != index
                    || item.portionCount() != exactItems.size()
                    || item.stackCount() != portion.count()
                    || !Arrays.equals(item.serializedStackSnapshot(),
                    portion.actualStackSnapshot())) {
                throw new IllegalArgumentException(
                        "Auction exact item custody evidence differs from its receipt");
            }
        }
    }

    public static String sourceKey(UUID listingId) {
        return "auction.custody."
                + AuctionEscrowIds.requireId(listingId,
                "listingId");
    }

    public boolean matches(AuctionItemLot lot) {
        Objects.requireNonNull(lot, "lot");
        long count = 0L;
        long bytes = 0L;
        for (ExactItemClaimPayload item : exactItems) {
            if (!item.registryItemId().equals(lot.registryId())) {
                return false;
            }
            count = Math.addExact(count, item.stackCount());
            bytes = Math.addExact(bytes,
                    item.serializedStackSnapshot().length);
        }
        return count == lot.count() && bytes == lot.serializedBytes();
    }

    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(receipt.digest());
            for (ExactItemClaimPayload item : exactItems) {
                digest.update(ExactItemClaimPayloadCodec.encode(item));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }
}
