package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarCreateEscrowIntent(
        CreateBazaarOrderCommand command,
        Optional<BazaarBuyFundingEvidence> buyFunding,
        Optional<ItemInventoryMutationIntent> itemMutationIntent,
        Optional<BazaarSellItemCustody> sellCustody,
        String currencyId,
        Instant preparedAt,
        Status status,
        long revision
) {
    public BazaarCreateEscrowIntent {
        command = Objects.requireNonNull(command, "command");
        buyFunding = Objects.requireNonNull(buyFunding, "buyFunding");
        itemMutationIntent = Objects.requireNonNull(itemMutationIntent,
                "itemMutationIntent");
        sellCustody = Objects.requireNonNull(sellCustody, "sellCustody");
        currencyId = BazaarBuyFundingEvidence.requireCurrencyId(currencyId);
        preparedAt = Objects.requireNonNull(preparedAt, "preparedAt");
        status = Objects.requireNonNull(status, "status");
        if (revision < 0L || status == Status.PREPARED && revision != 0L
                || status != Status.PREPARED && revision == 0L) {
            throw new IllegalArgumentException(
                    "Bazaar creation intent revision is invalid");
        }
        if (command.side() == BazaarOrderSide.BUY) {
            BazaarBuyFundingEvidence funding = buyFunding.orElseThrow(
                    () -> new IllegalArgumentException(
                            "Bazaar buy order has no funding evidence"));
            if (itemMutationIntent.isPresent() || sellCustody.isPresent()
                    || !funding.requestId().equals(command.requestId())
                    || !funding.orderId().equals(command.orderId())
                    || !funding.ownerId().equals(command.ownerId())
                    || !command.moneyHoldAccountId().filter(
                    funding.holdAccountId()::equals).isPresent()
                    || !funding.currencyId().equals(currencyId)) {
                throw new IllegalArgumentException(
                        "Bazaar buy intent evidence is invalid");
            }
            funding.requireReserve(initialReserve(command));
        } else {
            if (buyFunding.isPresent() || itemMutationIntent.isEmpty()
                    || sellCustody.isEmpty()
                    || !itemMutationIntent.orElseThrow().plannedReceipt()
                    .equals(sellCustody.orElseThrow().receipt())
                    || !itemMutationIntent.orElseThrow().token().equals(
                    sellCustody.orElseThrow().receipt().token())) {
                throw new IllegalArgumentException(
                        "Bazaar sell intent evidence is invalid");
            }
            BazaarSellItemCustody custody = sellCustody.orElseThrow();
            if (!custody.requestId().equals(command.requestId())
                    || !custody.orderId().equals(command.orderId())
                    || !custody.ownerId().equals(command.ownerId())
                    || !custody.activationTransactionId().equals(
                    command.activationTransactionId())
                    || !command.custodyLotId().filter(
                    custody.custodyLotId()::equals).isPresent()
                    || !custody.productId().equals(command.productId())
                    || custody.productVersion()
                    != command.productVersion()
                    || custody.totalQuantity() != command.quantity()) {
                throw new IllegalArgumentException(
                        "Bazaar sell intent differs from its custody");
            }
        }
    }

    public static BazaarCreateEscrowIntent preparedBuy(
            CreateBazaarOrderCommand command,
            BazaarBuyFundingEvidence funding,
            Instant preparedAt
    ) {
        return new BazaarCreateEscrowIntent(command, Optional.of(funding),
                Optional.empty(), Optional.empty(), funding.currencyId(),
                preparedAt, Status.PREPARED, 0L);
    }

    public static BazaarCreateEscrowIntent preparedSell(
            CreateBazaarOrderCommand command,
            BazaarProduct product,
            ItemInventoryMutationIntent itemIntent,
            BazaarSellItemCustody custody,
            String currencyId,
            Instant preparedAt
    ) {
        Objects.requireNonNull(product, "product");
        custody.requireMatches(command, product);
        return new BazaarCreateEscrowIntent(command, Optional.empty(),
                Optional.of(itemIntent), Optional.of(custody), currencyId,
                preparedAt, Status.PREPARED, 0L);
    }

    public BazaarCreateEscrowIntent transition(Status target) {
        Objects.requireNonNull(target, "target");
        if (target == Status.PREPARED || terminal()
                || status == Status.RECOVERY_REQUIRED
                && target == Status.RECOVERY_REQUIRED) {
            throw new IllegalStateException(
                    "Bazaar creation intent transition is invalid");
        }
        return new BazaarCreateEscrowIntent(command, buyFunding,
                itemMutationIntent, sellCustody, currencyId, preparedAt,
                target, Math.addExact(revision, 1L));
    }

    public boolean terminal() {
        return status == Status.COMMITTED || status == Status.REJECTED
                || status == Status.ABORTED;
    }

    public UUID requestId() {
        return command.requestId();
    }

    public UUID orderId() {
        return command.orderId();
    }

    public String intentFingerprint() {
        return BazaarCreateEscrowIntentCodec.fingerprint(this);
    }

    public static long initialReserve(CreateBazaarOrderCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.side() != BazaarOrderSide.BUY) {
            return 0L;
        }
        long notional = Math.multiplyExact(command.limitPriceMinor(),
                command.quantity());
        int rate = Math.max(command.rules().makerFeeBasisPoints(),
                command.rules().takerFeeBasisPoints());
        long fee = com.enviouse.futureshops.server.market.bazaar
                .BazaarFeeMath.cumulativeFee(notional, rate);
        return Math.addExact(notional, fee);
    }

    public enum Status {
        PREPARED,
        COMMITTED,
        REJECTED,
        ABORTED,
        RECOVERY_REQUIRED
    }
}
