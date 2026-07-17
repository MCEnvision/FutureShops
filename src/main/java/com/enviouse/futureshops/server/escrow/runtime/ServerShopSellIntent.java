package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public record ServerShopSellIntent(
        UUID requestId,
        UUID playerId,
        String shopId,
        String listingId,
        String itemId,
        int quantity,
        long unitPriceMinorUnits,
        long quoteRevision,
        long expectedStockRevision,
        Instant quoteCreatedAt,
        long walletBeforeMinorUnits,
        long debtBeforeMinorUnits,
        long reservedBeforeMinorUnits,
        long walletBalanceLimitMinorUnits,
        long configurationGeneration,
        String currencyName,
        int currencyDecimals,
        byte[] exactItemTemplate,
        DimensionAwareShopReference shopReference,
        Status status,
        long revision
) {
    public ServerShopSellIntent {
        requestId = ServerShopSellCommit.requireUuid(requestId, "requestId");
        playerId = ServerShopSellCommit.requireUuid(playerId, "playerId");
        shopId = ServerShopSellCommit.requireIdentifier(shopId, "shopId");
        listingId = ServerShopSellCommit.requireIdentifier(listingId, "listingId");
        itemId = ServerShopSellCommit.requireIdentifier(itemId, "itemId");
        if (quantity <= 0 || unitPriceMinorUnits <= 0L) {
            throw new IllegalArgumentException("Server shop sell intent value is invalid");
        }
        Math.multiplyExact(unitPriceMinorUnits, quantity);
        ServerShopSellCommit.requireRevision(quoteRevision, "quote revision");
        ServerShopSellCommit.requireRevision(expectedStockRevision, "stock revision");
        quoteCreatedAt = Objects.requireNonNull(quoteCreatedAt, "quoteCreatedAt");
        ServerShopSellCommit.requireWalletSnapshot(walletBeforeMinorUnits,
                debtBeforeMinorUnits, reservedBeforeMinorUnits,
                walletBalanceLimitMinorUnits);
        if (configurationGeneration < 0L || currencyDecimals < 0
                || currencyDecimals > 6) {
            throw new IllegalArgumentException("Server shop sell intent policy is invalid");
        }
        currencyName = ServerShopSellCommit.normalizeCurrencyName(currencyName);
        exactItemTemplate = Objects.requireNonNull(exactItemTemplate,
                "exactItemTemplate").clone();
        ServerShopSellCommit.requireExactTemplate(exactItemTemplate, itemId);
        shopReference = Objects.requireNonNull(shopReference, "shopReference");
        status = Objects.requireNonNull(status, "status");
        if (!shopReference.shopId().equals(shopId) || revision < 0L
                || revision > 1L || status == Status.PREPARED && revision != 0L
                || status != Status.PREPARED && revision != 1L) {
            throw new IllegalArgumentException("Server shop sell intent state is invalid");
        }
    }

    public static ServerShopSellIntent prepared(
            ServerShopSellService.PreparedRequest request,
            ServerShopSellService.WalletSnapshot wallet
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(wallet, "wallet");
        ServerShopSellService.Identity identity = request.identity();
        return new ServerShopSellIntent(identity.requestId(),
                identity.playerId(), identity.shopId(), identity.listingId(),
                request.itemId(), identity.quantity(),
                request.unitPriceMinorUnits(), request.quoteRevision(),
                request.expectedStockRevision(), request.quoteCreatedAt(),
                wallet.walletMinorUnits(), wallet.debtMinorUnits(),
                wallet.reservedMinorUnits(),
                wallet.walletBalanceLimitMinorUnits(),
                wallet.configurationGeneration(), wallet.currencyName(),
                wallet.currencyDecimals(), request.exactItemTemplate(),
                request.shopReference(), Status.PREPARED, 0L);
    }

    @Override
    public byte[] exactItemTemplate() {
        return exactItemTemplate.clone();
    }

    public ServerShopSellIntent abort(Status terminalStatus) {
        Objects.requireNonNull(terminalStatus, "terminalStatus");
        if (terminalStatus == Status.PREPARED
                || terminalStatus == Status.COMMITTED) {
            throw new IllegalArgumentException("Server shop sell intent abort status is invalid");
        }
        return transition(terminalStatus);
    }

    public ServerShopSellIntent complete() {
        return transition(Status.COMMITTED);
    }

    public String wireFingerprint() {
        return ServerShopSellCommit.wireFingerprint(requestId, playerId,
                shopId, listingId, quantity);
    }

    public String intentFingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops server shop sell intent v1");
            output.writeLong(requestId.getMostSignificantBits());
            output.writeLong(requestId.getLeastSignificantBits());
            output.writeLong(playerId.getMostSignificantBits());
            output.writeLong(playerId.getLeastSignificantBits());
            output.writeUTF(shopId);
            output.writeUTF(listingId);
            output.writeUTF(itemId);
            output.writeInt(quantity);
            output.writeLong(unitPriceMinorUnits);
            output.writeLong(quoteRevision);
            output.writeLong(expectedStockRevision);
            output.writeLong(quoteCreatedAt.getEpochSecond());
            output.writeInt(quoteCreatedAt.getNano());
            output.writeLong(walletBeforeMinorUnits);
            output.writeLong(debtBeforeMinorUnits);
            output.writeLong(reservedBeforeMinorUnits);
            output.writeLong(walletBalanceLimitMinorUnits);
            output.writeLong(configurationGeneration);
            output.writeUTF(currencyName);
            output.writeInt(currencyDecimals);
            output.writeInt(exactItemTemplate.length);
            output.write(exactItemTemplate);
            output.writeUTF(shopReference.shopId());
            output.writeUTF(shopReference.dimensionId());
            output.writeInt(shopReference.blockX());
            output.writeInt(shopReference.blockY());
            output.writeInt(shopReference.blockZ());
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint server shop sell intent", exception);
        }
    }

    public ServerShopSellCommit commit(ItemInventoryMutationReceipt receipt) {
        if (status != Status.PREPARED && status != Status.COMMITTED) {
            throw new IllegalStateException("Server shop sell intent is terminal");
        }
        return ServerShopSellCommit.create(requestId, playerId, shopId,
                listingId, itemId, quantity, unitPriceMinorUnits,
                quoteRevision, expectedStockRevision, quoteCreatedAt,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                configurationGeneration, currencyName, currencyDecimals,
                exactItemTemplate, Objects.requireNonNull(receipt, "receipt"),
                shopReference);
    }

    private ServerShopSellIntent transition(Status terminalStatus) {
        Objects.requireNonNull(terminalStatus, "terminalStatus");
        if (status == terminalStatus) {
            return this;
        }
        if (status != Status.PREPARED) {
            throw new IllegalStateException("Server shop sell intent is terminal");
        }
        return copy(terminalStatus, 1L);
    }

    private ServerShopSellIntent copy(Status newStatus, long newRevision) {
        return new ServerShopSellIntent(requestId, playerId, shopId,
                listingId, itemId, quantity, unitPriceMinorUnits,
                quoteRevision, expectedStockRevision, quoteCreatedAt,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                configurationGeneration, currencyName, currencyDecimals,
                exactItemTemplate, shopReference, newStatus, newRevision);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ServerShopSellIntent other
                && requestId.equals(other.requestId)
                && playerId.equals(other.playerId)
                && shopId.equals(other.shopId)
                && listingId.equals(other.listingId)
                && itemId.equals(other.itemId)
                && quantity == other.quantity
                && unitPriceMinorUnits == other.unitPriceMinorUnits
                && quoteRevision == other.quoteRevision
                && expectedStockRevision == other.expectedStockRevision
                && quoteCreatedAt.equals(other.quoteCreatedAt)
                && walletBeforeMinorUnits == other.walletBeforeMinorUnits
                && debtBeforeMinorUnits == other.debtBeforeMinorUnits
                && reservedBeforeMinorUnits == other.reservedBeforeMinorUnits
                && walletBalanceLimitMinorUnits == other.walletBalanceLimitMinorUnits
                && configurationGeneration == other.configurationGeneration
                && currencyName.equals(other.currencyName)
                && currencyDecimals == other.currencyDecimals
                && Arrays.equals(exactItemTemplate, other.exactItemTemplate)
                && shopReference.equals(other.shopReference)
                && status == other.status && revision == other.revision;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(requestId, playerId, shopId, listingId,
                itemId, quantity, unitPriceMinorUnits, quoteRevision,
                expectedStockRevision, quoteCreatedAt,
                walletBeforeMinorUnits, debtBeforeMinorUnits,
                reservedBeforeMinorUnits, walletBalanceLimitMinorUnits,
                configurationGeneration, currencyName, currencyDecimals,
                shopReference, status, revision)
                + Arrays.hashCode(exactItemTemplate);
    }

    public enum Status {
        PREPARED,
        ABORTED_MISSING_ITEMS,
        ABORTED_UNSUPPORTED_ITEM,
        ABORTED_CUSTODY,
        COMMITTED
    }
}
