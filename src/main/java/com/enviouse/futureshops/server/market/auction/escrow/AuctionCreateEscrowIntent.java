package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuctionCreateEscrowIntent(
        CreateAuctionCommand command,
        ItemInventoryMutationIntent itemMutationIntent,
        AuctionEscrowItemCustody plannedCustody,
        AuctionEscrowWalletSnapshot sellerWallet,
        String currencyId,
        Instant preparedAt,
        Status status,
        long revision
) {
    public static final int MAX_CURRENCY_ID_LENGTH = 128;

    public AuctionCreateEscrowIntent {
        command = Objects.requireNonNull(command, "command");
        itemMutationIntent = Objects.requireNonNull(
                itemMutationIntent, "itemMutationIntent");
        plannedCustody = Objects.requireNonNull(
                plannedCustody, "plannedCustody");
        sellerWallet = Objects.requireNonNull(
                sellerWallet, "sellerWallet");
        currencyId = requireCurrencyId(currencyId);
        preparedAt = Objects.requireNonNull(preparedAt, "preparedAt");
        status = Objects.requireNonNull(status, "status");
        var token = itemMutationIntent.token();
        if (!token.playerId().equals(command.sellerId())
                || !token.transactionId().equals(
                command.activationTransactionId())
                || !token.requestId().equals(
                AuctionEscrowIds.custodyRequestId(command.requestId()))
                || token.direction()
                != ItemInventoryMutationDirection.EXTRACT
                || !itemMutationIntent.plannedReceipt().equals(
                plannedCustody.receipt())
                || !plannedCustody.listingId().equals(
                command.listingId())
                || !plannedCustody.activationTransactionId().equals(
                command.activationTransactionId())
                || !plannedCustody.matches(command.itemLot())
                || !sellerWallet.playerId().equals(command.sellerId())
                || revision < 0L || revision > 1L
                || status == Status.PREPARED && revision != 0L
                || status != Status.PREPARED && revision != 1L) {
            throw new IllegalArgumentException(
                    "Auction creation intent evidence is invalid");
        }
        sellerWallet.requireAvailable(command.rules().listingFeeMinor());
    }

    public static AuctionCreateEscrowIntent prepared(
            CreateAuctionCommand command,
            ItemInventoryMutationIntent itemMutationIntent,
            AuctionEscrowItemCustody plannedCustody,
            AuctionEscrowWalletSnapshot sellerWallet,
            String currencyId,
            Instant preparedAt
    ) {
        return new AuctionCreateEscrowIntent(command, itemMutationIntent,
                plannedCustody, sellerWallet, currencyId, preparedAt,
                Status.PREPARED, 0L);
    }

    public UUID requestId() {
        return command.requestId();
    }

    public UUID listingId() {
        return command.listingId();
    }

    public UUID sellerId() {
        return command.sellerId();
    }

    public String intentFingerprint() {
        return AuctionCreateEscrowIntentCodec.fingerprint(this);
    }

    public AuctionCreateEscrowIntent complete() {
        return transition(Status.COMMITTED);
    }

    public AuctionCreateEscrowIntent abort(Status terminalStatus) {
        if (terminalStatus == Status.PREPARED
                || terminalStatus == Status.COMMITTED) {
            throw new IllegalArgumentException(
                    "Auction creation abort status is invalid");
        }
        return transition(terminalStatus);
    }

    private AuctionCreateEscrowIntent transition(Status target) {
        if (status == target) {
            return this;
        }
        if (status != Status.PREPARED) {
            throw new IllegalStateException(
                    "Auction creation intent is terminal");
        }
        return new AuctionCreateEscrowIntent(command, itemMutationIntent,
                plannedCustody, sellerWallet, currencyId, preparedAt,
                target, 1L);
    }

    static String requireCurrencyId(String value) {
        String normalized = Objects.requireNonNull(value,
                "currencyId").strip();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CURRENCY_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Auction currency identifier is invalid");
        }
        return normalized;
    }

    public enum Status {
        PREPARED,
        ABORTED_MISSING_ITEMS,
        ABORTED_CUSTODY,
        ABORTED_FEE,
        QUARANTINED,
        COMMITTED
    }
}
