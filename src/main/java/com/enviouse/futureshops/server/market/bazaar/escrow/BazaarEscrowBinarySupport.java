package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceiptCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarExecutionPricePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarSelfTradePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class BazaarEscrowBinarySupport {
    static final int MAX_TEXT_BYTES = 16_384;

    private BazaarEscrowBinarySupport() {
    }

    static void writeCommand(
            DataOutputStream output,
            CreateBazaarOrderCommand command
    ) throws IOException {
        writeUuid(output, command.requestId());
        writeUuid(output, command.orderId());
        writeUuid(output, command.ownerId());
        writeUuid(output, command.activationTransactionId());
        writeOptionalUuid(output, command.moneyHoldAccountId());
        writeOptionalUuid(output, command.custodyLotId());
        writeText(output, command.productId());
        output.writeLong(command.productVersion());
        output.writeInt(command.side().wireCode());
        output.writeInt(command.type().wireCode());
        output.writeInt(command.timeInForce().wireCode());
        output.writeLong(command.limitPriceMinor());
        output.writeInt(command.quantity());
        output.writeLong(command.createdAtMillis());
        output.writeLong(command.expiresAtMillis());
        writeRules(output, command.rules());
    }

    static CreateBazaarOrderCommand readCommand(DataInputStream input)
            throws IOException {
        UUID requestId = readUuid(input);
        UUID orderId = readUuid(input);
        UUID ownerId = readUuid(input);
        UUID activationId = readUuid(input);
        Optional<UUID> holdId = readOptionalUuid(input);
        Optional<UUID> custodyId = readOptionalUuid(input);
        String productId = readText(input, 96, false);
        long version = input.readLong();
        BazaarOrderSide side = BazaarOrderSide.fromWireCode(
                input.readInt());
        BazaarOrderType type = BazaarOrderType.fromWireCode(
                input.readInt());
        BazaarTimeInForce timeInForce = BazaarTimeInForce.fromWireCode(
                input.readInt());
        long price = input.readLong();
        int quantity = input.readInt();
        long created = input.readLong();
        long expires = input.readLong();
        return new CreateBazaarOrderCommand(requestId, orderId, ownerId,
                activationId, holdId, custodyId, productId, version, side,
                type, timeInForce, price, quantity, created, expires,
                readRules(input));
    }

    static void writeFunding(
            DataOutputStream output,
            BazaarBuyFundingEvidence funding
    ) throws IOException {
        writeUuid(output, funding.requestId());
        writeUuid(output, funding.orderId());
        writeUuid(output, funding.ownerId());
        writeUuid(output, funding.holdAccountId());
        output.writeInt(funding.source().ordinal());
        writeWallet(output, funding.wallet());
        output.writeBoolean(funding.physicalFunding().isPresent());
        if (funding.physicalFunding().isPresent()) {
            BazaarPhysicalFundingEvidence physical = funding
                    .physicalFunding().orElseThrow();
            writeUuid(output, physical.depositRequestId());
            writeUuid(output, physical.depositTransactionId());
            writeText(output, physical.currencySignature());
            output.writeLong(physical.depositedMinor());
            output.writeLong(physical.walletCreditMinor());
            output.writeLong(physical.overflowClaimMinor());
            output.writeLong(physical.resultingWalletMinor());
        }
        writeText(output, funding.currencyId());
    }

    static BazaarBuyFundingEvidence readFunding(DataInputStream input)
            throws IOException {
        UUID requestId = readUuid(input);
        UUID orderId = readUuid(input);
        UUID ownerId = readUuid(input);
        UUID holdId = readUuid(input);
        BazaarEscrowPaymentSource source = readEnum(input,
                BazaarEscrowPaymentSource.values(), "payment source");
        BazaarEscrowWalletSnapshot wallet = readWallet(input);
        Optional<BazaarPhysicalFundingEvidence> physical = Optional.empty();
        if (input.readBoolean()) {
            physical = Optional.of(new BazaarPhysicalFundingEvidence(
                    readUuid(input), readUuid(input),
                    readText(input, 64, false), input.readLong(),
                    input.readLong(), input.readLong(), input.readLong()));
        }
        return new BazaarBuyFundingEvidence(requestId, orderId, ownerId,
                holdId, source, wallet, physical,
                readText(input,
                        BazaarBuyFundingEvidence.MAX_CURRENCY_ID_LENGTH,
                        false));
    }

    static void writeCustody(
            DataOutputStream output,
            BazaarSellItemCustody custody
    ) throws IOException {
        writeUuid(output, custody.requestId());
        writeUuid(output, custody.orderId());
        writeUuid(output, custody.ownerId());
        writeUuid(output, custody.activationTransactionId());
        writeUuid(output, custody.custodyLotId());
        writeText(output, custody.productId());
        output.writeLong(custody.productVersion());
        writeText(output, custody.registryId());
        writeText(output, custody.exactIdentity());
        writeBytes(output, ItemInventoryMutationReceiptCodec.encode(
                custody.receipt()),
                ItemInventoryMutationReceiptCodec.MAX_ENCODED_BYTES);
        output.writeInt(custody.exactItems().size());
        for (ExactItemClaimPayload item : custody.exactItems()) {
            writeBytes(output, ExactItemClaimPayloadCodec.encode(item),
                    ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES);
        }
    }

    static BazaarSellItemCustody readCustody(DataInputStream input)
            throws IOException {
        UUID requestId = readUuid(input);
        UUID orderId = readUuid(input);
        UUID ownerId = readUuid(input);
        UUID activationId = readUuid(input);
        UUID custodyId = readUuid(input);
        String productId = readText(input, 96, false);
        long productVersion = input.readLong();
        String registryId = readText(input, 256, false);
        String exactIdentity = readText(input, 256, true);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceiptCodec.decode(readBytes(input,
                        ItemInventoryMutationReceiptCodec
                                .MAX_ENCODED_BYTES));
        int count = input.readInt();
        if (count <= 0 || count
                > ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
            throw invalid("Bazaar custody item count is invalid");
        }
        List<ExactItemClaimPayload> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            items.add(ExactItemClaimPayloadCodec.decode(readBytes(input,
                    ExactItemClaimPayloadCodec.MAX_ENCODED_BYTES)));
        }
        return new BazaarSellItemCustody(requestId, orderId, ownerId,
                activationId, custodyId, productId, productVersion,
                registryId, exactIdentity, receipt, items);
    }

    static void writeCustodyState(
            DataOutputStream output,
            BazaarSellCustodyState state
    ) throws IOException {
        writeCustody(output, state.custody());
        output.writeInt(state.remainingCounts().size());
        for (int count : state.remainingCounts()) {
            output.writeInt(count);
        }
    }

    static BazaarSellCustodyState readCustodyState(DataInputStream input)
            throws IOException {
        BazaarSellItemCustody custody = readCustody(input);
        int count = input.readInt();
        if (count != custody.exactItems().size()) {
            throw invalid("Bazaar custody remainder count is invalid");
        }
        List<Integer> remaining = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            remaining.add(input.readInt());
        }
        return new BazaarSellCustodyState(custody, remaining);
    }

    static void writeBacking(
            DataOutputStream output,
            BazaarEscrowOrderBacking backing
    ) throws IOException {
        writeUuid(output, backing.orderId());
        output.writeInt(backing.side().wireCode());
        if (backing.side() == BazaarOrderSide.BUY) {
            writeFunding(output, backing.buyFunding().orElseThrow());
        } else {
            writeCustodyState(output,
                    backing.sellCustody().orElseThrow());
        }
    }

    static BazaarEscrowOrderBacking readBacking(DataInputStream input)
            throws IOException {
        UUID orderId = readUuid(input);
        BazaarOrderSide side = BazaarOrderSide.fromWireCode(
                input.readInt());
        BazaarEscrowOrderBacking backing = side == BazaarOrderSide.BUY
                ? BazaarEscrowOrderBacking.buy(readFunding(input))
                : BazaarEscrowOrderBacking.sell(readCustodyState(input));
        if (!backing.orderId().equals(orderId)) {
            throw invalid("Bazaar backing order identity is invalid");
        }
        return backing;
    }

    static void writeView(
            DataOutputStream output,
            BazaarEscrowOrderView view
    ) throws IOException {
        writeUuid(output, view.orderId());
        writeUuid(output, view.ownerId());
        writeText(output, view.productId());
        output.writeLong(view.productVersion());
        output.writeInt(view.side().wireCode());
        output.writeInt(view.state().wireCode());
        output.writeLong(view.revision());
        output.writeInt(view.originalQuantity());
        output.writeInt(view.remainingQuantity());
        output.writeInt(view.filledQuantity());
        output.writeLong(view.reservedMoneyMinor());
        output.writeInt(view.reservedItemQuantity());
        writeOptionalUuid(output, view.holdAccountId());
        writeOptionalUuid(output, view.custodyLotId());
    }

    static BazaarEscrowOrderView readView(DataInputStream input)
            throws IOException {
        return new BazaarEscrowOrderView(readUuid(input), readUuid(input),
                readText(input, 96, false), input.readLong(),
                BazaarOrderSide.fromWireCode(input.readInt()),
                BazaarOrderState.fromWireCode(input.readInt()),
                input.readLong(), input.readInt(), input.readInt(),
                input.readInt(), input.readLong(), input.readInt(),
                readOptionalUuid(input), readOptionalUuid(input));
    }

    static void writeWallet(
            DataOutputStream output,
            BazaarEscrowWalletSnapshot wallet
    ) throws IOException {
        writeUuid(output, wallet.ownerId());
        output.writeLong(wallet.walletMinor());
        output.writeLong(wallet.configurationGeneration());
    }

    static BazaarEscrowWalletSnapshot readWallet(DataInputStream input)
            throws IOException {
        return new BazaarEscrowWalletSnapshot(readUuid(input),
                input.readLong(), input.readLong());
    }

    static void writeRules(
            DataOutputStream output,
            BazaarRuleSnapshot rules
    ) throws IOException {
        output.writeInt(rules.makerFeeBasisPoints());
        output.writeInt(rules.takerFeeBasisPoints());
        output.writeInt(rules.maximumOrderQuantity());
        output.writeLong(rules.maximumNotionalMinor());
        output.writeInt(rules.maximumOpenOrdersPerPlayer());
        output.writeInt(rules.maximumOpenOrdersPerProductPerPlayer());
        output.writeLong(rules.maximumEscrowedValuePerPlayerMinor());
        output.writeInt(rules.selfTradePolicy().ordinal());
        output.writeInt(rules.executionPricePolicy().ordinal());
        output.writeBoolean(rules.circuitBreakerEnabled());
        output.writeInt(rules.priceBandBasisPoints());
        output.writeLong(rules.minimumLifetimeMillis());
        output.writeLong(rules.configRevision());
    }

    static BazaarRuleSnapshot readRules(DataInputStream input)
            throws IOException {
        int makerFee = input.readInt();
        int takerFee = input.readInt();
        int maximumQuantity = input.readInt();
        long maximumNotional = input.readLong();
        int maximumOpen = input.readInt();
        int maximumProductOpen = input.readInt();
        long maximumEscrow = input.readLong();
        BazaarSelfTradePolicy selfTrade = readEnum(input,
                BazaarSelfTradePolicy.values(), "self trade policy");
        BazaarExecutionPricePolicy pricePolicy = readEnum(input,
                BazaarExecutionPricePolicy.values(),
                "execution price policy");
        boolean breaker = input.readBoolean();
        return new BazaarRuleSnapshot(makerFee, takerFee,
                maximumQuantity, maximumNotional, maximumOpen,
                maximumProductOpen, maximumEscrow, selfTrade,
                pricePolicy, breaker, input.readInt(), input.readLong(),
                input.readLong());
    }

    static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeOptionalUuid(
            DataOutputStream output,
            Optional<UUID> value
    ) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeUuid(output, value.orElseThrow());
        }
    }

    static Optional<UUID> readOptionalUuid(DataInputStream input)
            throws IOException {
        return input.readBoolean()
                ? Optional.of(readUuid(input)) : Optional.empty();
    }

    static void writeInstant(DataOutputStream output, Instant value)
            throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    static Instant readInstant(DataInputStream input) throws IOException {
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw invalid("Bazaar escrow instant is invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw invalid("Bazaar escrow instant is invalid", exception);
        }
    }

    static void writeText(DataOutputStream output, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw invalid("Bazaar escrow text is too large");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static String readText(
            DataInputStream input,
            int maximumCharacters,
            boolean emptyAllowed
    ) throws IOException {
        int length = input.readInt();
        int maximum = Math.min(MAX_TEXT_BYTES,
                Math.multiplyExact(maximumCharacters, 4));
        if (length < 0 || !emptyAllowed && length == 0
                || length > maximum || length > input.available()) {
            throw invalid("Bazaar escrow text size is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Bazaar escrow text is truncated");
        }
        String result = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, result.getBytes(StandardCharsets.UTF_8))) {
            throw invalid("Bazaar escrow text is not valid UTF8");
        }
        return result;
    }

    static void writeBytes(
            DataOutputStream output,
            byte[] value,
            int maximum
    ) throws IOException {
        if (value.length == 0 || value.length > maximum) {
            throw invalid("Bazaar escrow component size is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    static byte[] readBytes(DataInputStream input, int maximum)
            throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum
                || length > input.available()) {
            throw invalid("Bazaar escrow component size is invalid");
        }
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException(
                    "Bazaar escrow component is truncated");
        }
        return result;
    }

    static <T> T readEnum(
            DataInputStream input,
            T[] values,
            String label
    ) throws IOException {
        int ordinal = input.readInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw invalid("Bazaar escrow " + label + " is invalid");
        }
        return values[ordinal];
    }

    static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    static IllegalArgumentException invalid(
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(message, cause);
    }
}
