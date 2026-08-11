package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryAllocation;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BazaarSellItemCustody(
        UUID requestId,
        UUID orderId,
        UUID ownerId,
        UUID activationTransactionId,
        UUID custodyLotId,
        String productId,
        long productVersion,
        String registryId,
        String exactIdentity,
        ItemInventoryMutationReceipt receipt,
        List<ExactItemClaimPayload> exactItems
) {
    public BazaarSellItemCustody {
        requestId = BazaarEscrowIds.requireId(requestId, "requestId");
        orderId = BazaarEscrowIds.requireId(orderId, "orderId");
        ownerId = BazaarEscrowIds.requireId(ownerId, "ownerId");
        activationTransactionId = BazaarEscrowIds.requireId(
                activationTransactionId, "activationTransactionId");
        custodyLotId = BazaarEscrowIds.requireId(custodyLotId,
                "custodyLotId");
        productId = Objects.requireNonNull(productId, "productId");
        registryId = Objects.requireNonNull(registryId, "registryId");
        exactIdentity = Objects.requireNonNull(exactIdentity,
                "exactIdentity");
        receipt = Objects.requireNonNull(receipt, "receipt");
        exactItems = List.copyOf(Objects.requireNonNull(exactItems,
                "exactItems"));
        if (productId.isBlank() || productId.length() > 96
                || productVersion <= 0L || registryId.isBlank()
                || registryId.length() > 256
                || exactIdentity.length() > 256
                || exactItems.isEmpty()
                || exactItems.size() != receipt.actualPortions().size()
                || !receipt.token().playerId().equals(ownerId)
                || !receipt.token().transactionId().equals(
                activationTransactionId)
                || !receipt.token().requestId().equals(requestId)
                || receipt.token().direction()
                != ItemInventoryMutationDirection.EXTRACT) {
            throw new IllegalArgumentException(
                    "Bazaar sell custody identity is invalid");
        }
        List<ItemInventoryAllocation> portions = receipt.actualPortions();
        long total = 0L;
        String sourceKey = sourceKey(orderId);
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
                    || !item.registryItemId().equals(registryId)
                    || !Arrays.equals(item.serializedStackSnapshot(),
                    portion.actualStackSnapshot())
                    || !matchesIdentity(exactIdentity,
                    item.canonicalOneCountTemplate())) {
                throw new IllegalArgumentException(
                        "Bazaar sell custody item evidence is invalid");
            }
            total = Math.addExact(total, item.stackCount());
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Bazaar sell custody quantity is invalid");
        }
    }

    public static BazaarSellItemCustody create(
            CreateBazaarOrderCommand command,
            BazaarProduct product,
            ItemInventoryMutationReceipt receipt,
            List<ExactItemClaimPayload> exactItems
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(product, "product");
        return new BazaarSellItemCustody(command.requestId(),
                command.orderId(), command.ownerId(),
                command.activationTransactionId(),
                command.custodyLotId().orElseThrow(), product.productId(),
                product.version(), product.registryId(),
                product.exactIdentity(), receipt, exactItems);
    }

    public void requireMatches(
            CreateBazaarOrderCommand command,
            BazaarProduct product
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(product, "product");
        if (!requestId.equals(command.requestId())
                || !orderId.equals(command.orderId())
                || !ownerId.equals(command.ownerId())
                || !activationTransactionId.equals(
                command.activationTransactionId())
                || !command.custodyLotId().filter(
                custodyLotId::equals).isPresent()
                || !productId.equals(command.productId())
                || productVersion != command.productVersion()
                || !productId.equals(product.productId())
                || productVersion != product.version()
                || !registryId.equals(product.registryId())
                || !exactIdentity.equals(product.exactIdentity())
                || totalQuantity() != command.quantity()) {
            throw new IllegalArgumentException(
                    "Bazaar sell custody differs from the order");
        }
    }

    public int totalQuantity() {
        int total = 0;
        for (ExactItemClaimPayload item : exactItems) {
            total = Math.addExact(total, item.stackCount());
        }
        return total;
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

    public static String sourceKey(UUID orderId) {
        return "bazaar.custody."
                + BazaarEscrowIds.requireId(orderId, "orderId");
    }

    static boolean matchesIdentity(
            String exactIdentity,
            byte[] canonicalTemplate
    ) {
        if (exactIdentity.isEmpty()) {
            return true;
        }
        try {
            var stack = ItemStackSnapshotCodec.decode(canonicalTemplate);
            String canonicalTag = stack.getTag() == null ? ""
                    : stack.getTag().toString();
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonicalTemplate));
            return exactIdentity.equals(canonicalTag)
                    || exactIdentity.equals(digest)
                    || exactIdentity.equals("sha256:" + digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }
}
