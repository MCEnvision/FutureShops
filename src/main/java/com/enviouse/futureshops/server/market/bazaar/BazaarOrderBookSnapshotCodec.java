package com.enviouse.futureshops.server.market.bazaar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BazaarOrderBookSnapshotCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 67_107_840;

    private static final int MAGIC = 0x4653425A;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_PRODUCTS = 65_536;
    private static final int MAX_ORDERS = 262_144;
    private static final int MAX_FILLS = 262_144;
    private static final int MAX_RULES = 65_536;
    private static final int MAX_REFERENCES = 65_536;
    private static final int MAX_RESULT_FILLS =
            BazaarOrderBook.MAXIMUM_FILLS_PER_OPERATION;
    private static final int MAX_RESULT_SETTLEMENTS = 65_536;
    private static final int MAX_CANCELLED_MAKERS = 65_536;

    private BazaarOrderBookSnapshotCodec() {
    }

    public static byte[] encode(BazaarOrderBookSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        BazaarSnapshotValidator.validate(snapshot);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeInt(snapshot.schemaVersion());
            output.writeLong(snapshot.nextSequence());
            output.writeInt(snapshot.retentionPolicy().maximumReceipts());
            output.writeInt(snapshot.retentionPolicy()
                    .maximumTerminalTransactions());
            output.writeLong(snapshot.effectiveRuleRevision());
            writeProducts(output, snapshot.products());
            writeOrders(output, snapshot.orders());
            writeFills(output, snapshot.fills());
            writeReceipts(output, snapshot.receipts());
            writeReferencePrices(output, snapshot.referencePrices());
            writeUuidSet(output, snapshot.terminalTransactions());
            writeRules(output, snapshot.ruleSnapshots());
            writeLifecycleReceipts(output, snapshot.lifecycleReceipts());
            output.flush();
            return appendDigest(bytes.toByteArray(), MAX_ENCODED_BYTES,
                    "Bazaar snapshot");
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar snapshot", exception);
        }
    }

    public static BazaarOrderBookSnapshot decode(byte[] encoded) {
        byte[] copy = requireAndVerify(encoded, MAX_ENCODED_BYTES,
                "Bazaar snapshot");
        byte[] payload = Arrays.copyOf(copy,
                copy.length - DIGEST_BYTES);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Bazaar snapshot magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid("Bazaar snapshot schema is unsupported");
            }
            int snapshotSchema = input.readInt();
            long nextSequence = input.readLong();
            BazaarRetentionPolicy retention = new BazaarRetentionPolicy(
                    input.readInt(), input.readInt());
            long effectiveRuleRevision = input.readLong();
            List<BazaarProduct> products = readProducts(input);
            List<BazaarOrder> orders = readOrders(input);
            List<BazaarFill> fills = readFills(input);
            Map<UUID, BazaarRequestReceipt> receipts = readReceipts(input,
                    retention.maximumReceipts());
            Map<String, Long> references = readReferencePrices(input);
            Set<UUID> terminalTransactions = readUuidSet(input,
                    retention.maximumTerminalTransactions(), "terminal");
            List<BazaarRuleSnapshot> rules = readRules(input);
            int remainingReceipts = Math.subtractExact(
                    retention.maximumReceipts(), receipts.size());
            Map<UUID, String> lifecycleReceipts = readLifecycleReceipts(
                    input, remainingReceipts);
            if (input.read() != -1) {
                throw invalid("Bazaar snapshot has trailing data");
            }
            BazaarOrderBookSnapshot snapshot = new BazaarOrderBookSnapshot(
                    snapshotSchema, nextSequence, products, orders, fills,
                    receipts, references, terminalTransactions, rules,
                    effectiveRuleRevision, retention, lifecycleReceipts);
            BazaarSnapshotValidator.validate(snapshot);
            if (!Arrays.equals(copy, encode(snapshot))) {
                throw invalid("Bazaar snapshot encoding is not canonical");
            }
            return snapshot;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Bazaar snapshot is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Bazaar snapshot is invalid", exception);
        }
    }

    public static String fingerprint(BazaarOrderBookSnapshot snapshot) {
        return HexFormat.of().formatHex(digest(encode(snapshot)));
    }

    static byte[] encodeReceipt(BazaarRequestReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeReceipt(output, receipt);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0
                    || encoded.length > BazaarMutationCodec.MAX_ENCODED_BYTES) {
                throw invalid("Bazaar receipt size is invalid");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar receipt", exception);
        }
    }

    static BazaarRequestReceipt decodeReceipt(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length == 0
                || copy.length > BazaarMutationCodec.MAX_ENCODED_BYTES) {
            throw invalid("Bazaar receipt size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            BazaarRequestReceipt receipt = readReceipt(input);
            if (input.read() != -1
                    || !Arrays.equals(copy, encodeReceipt(receipt))) {
                throw invalid("Bazaar receipt encoding is not canonical");
            }
            return receipt;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Bazaar receipt is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Bazaar receipt is invalid", exception);
        }
    }

    static byte[] encodeLifecycleCommand(BazaarLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeLifecycleCommand(output, command);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > 4_096) {
                throw invalid("Bazaar lifecycle command size is invalid");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar lifecycle command", exception);
        }
    }

    static BazaarLifecycleCommand decodeLifecycleCommand(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length == 0 || copy.length > 4_096) {
            throw invalid("Bazaar lifecycle command size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            BazaarLifecycleCommand command = readLifecycleCommand(input);
            if (input.read() != -1 || !Arrays.equals(copy,
                    encodeLifecycleCommand(command))) {
                throw invalid(
                        "Bazaar lifecycle command encoding is not canonical");
            }
            return command;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Bazaar lifecycle command is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Bazaar lifecycle command is invalid", exception);
        }
    }

    static String lifecycleFingerprint(BazaarLifecycleCommand command) {
        return HexFormat.of().formatHex(digest(
                encodeLifecycleCommand(command)));
    }

    private static void writeProducts(DataOutputStream output,
                                      List<BazaarProduct> products)
            throws IOException {
        requireCount(products.size(), MAX_PRODUCTS, "product");
        output.writeInt(products.size());
        for (BazaarProduct product : products) {
            writeProduct(output, product);
        }
    }

    private static List<BazaarProduct> readProducts(DataInputStream input)
            throws IOException {
        int count = readCount(input, MAX_PRODUCTS, "product");
        List<BazaarProduct> products = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            products.add(readProduct(input));
        }
        return products;
    }

    private static void writeProduct(DataOutputStream output,
                                     BazaarProduct product)
            throws IOException {
        writeString(output, product.productId(), 96, false);
        output.writeLong(product.version());
        writeString(output, product.registryId(), 256, false);
        writeString(output, product.exactIdentity(), 256, true);
        writeString(output, product.categoryId(), 96, true);
        output.writeInt(product.lotSize());
        output.writeLong(product.priceTickMinor());
        output.writeLong(product.minimumPriceMinor());
        output.writeLong(product.maximumPriceMinor());
        output.writeInt(product.maximumQuantity());
        output.writeInt(product.status().wireCode());
    }

    private static BazaarProduct readProduct(DataInputStream input)
            throws IOException {
        return new BazaarProduct(readString(input, 96, false),
                input.readLong(), readString(input, 256, false),
                readString(input, 256, true),
                readString(input, 96, true), input.readInt(),
                input.readLong(), input.readLong(), input.readLong(),
                input.readInt(), BazaarProductStatus.fromWireCode(
                input.readInt()));
    }

    private static void writeOrders(DataOutputStream output,
                                    List<BazaarOrder> orders)
            throws IOException {
        requireCount(orders.size(), MAX_ORDERS, "order");
        output.writeInt(orders.size());
        for (BazaarOrder order : orders) {
            writeOrder(output, order);
        }
    }

    private static List<BazaarOrder> readOrders(DataInputStream input)
            throws IOException {
        int count = readCount(input, MAX_ORDERS, "order");
        List<BazaarOrder> orders = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            orders.add(readOrder(input));
        }
        return orders;
    }

    private static void writeOrder(DataOutputStream output,
                                   BazaarOrder order) throws IOException {
        writeUuid(output, order.orderId());
        writeUuid(output, order.ownerId());
        writeUuid(output, order.activationTransactionId());
        writeOptionalUuid(output, order.moneyHoldAccountId());
        writeOptionalUuid(output, order.custodyLotId());
        writeString(output, order.productId(), 96, false);
        output.writeLong(order.productVersion());
        output.writeInt(order.side().wireCode());
        output.writeInt(order.type().wireCode());
        output.writeInt(order.timeInForce().wireCode());
        output.writeInt(order.state().wireCode());
        output.writeLong(order.revision());
        output.writeLong(order.limitPriceMinor());
        output.writeInt(order.originalQuantity());
        output.writeInt(order.remainingQuantity());
        output.writeInt(order.filledQuantity());
        output.writeLong(order.makerGrossMinor());
        output.writeLong(order.takerGrossMinor());
        output.writeLong(order.accruedFeeMinor());
        output.writeLong(order.reservedMoneyMinor());
        output.writeInt(order.reservedItemQuantity());
        output.writeLong(order.acceptedSequence());
        output.writeLong(order.createdAtMillis());
        output.writeLong(order.expiresAtMillis());
        writeRule(output, order.rules());
    }

    private static BazaarOrder readOrder(DataInputStream input)
            throws IOException {
        return new BazaarOrder(readUuid(input), readUuid(input),
                readUuid(input), readOptionalUuid(input),
                readOptionalUuid(input), readString(input, 96, false),
                input.readLong(), BazaarOrderSide.fromWireCode(input.readInt()),
                BazaarOrderType.fromWireCode(input.readInt()),
                BazaarTimeInForce.fromWireCode(input.readInt()),
                BazaarOrderState.fromWireCode(input.readInt()),
                input.readLong(), input.readLong(), input.readInt(),
                input.readInt(), input.readInt(), input.readLong(),
                input.readLong(), input.readLong(), input.readLong(),
                input.readInt(), input.readLong(), input.readLong(),
                input.readLong(), readRule(input));
    }

    private static void writeFills(DataOutputStream output,
                                   List<BazaarFill> fills)
            throws IOException {
        requireCount(fills.size(), MAX_FILLS, "fill");
        output.writeInt(fills.size());
        for (BazaarFill fill : fills) {
            writeFill(output, fill);
        }
    }

    private static List<BazaarFill> readFills(DataInputStream input)
            throws IOException {
        int count = readCount(input, MAX_FILLS, "fill");
        List<BazaarFill> fills = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            fills.add(readFill(input));
        }
        return fills;
    }

    private static void writeFill(DataOutputStream output,
                                  BazaarFill fill) throws IOException {
        writeUuid(output, fill.fillId());
        writeUuid(output, fill.buyOrderId());
        writeUuid(output, fill.sellOrderId());
        writeUuid(output, fill.makerOrderId());
        writeUuid(output, fill.takerOrderId());
        writeString(output, fill.productId(), 96, false);
        output.writeLong(fill.productVersion());
        output.writeInt(fill.quantity());
        output.writeLong(fill.priceMinor());
        output.writeLong(fill.grossMinor());
        output.writeLong(fill.buyerFeeMinor());
        output.writeLong(fill.sellerFeeMinor());
        output.writeLong(fill.buyerPriceImprovementMinor());
        output.writeLong(fill.sequence());
        output.writeLong(fill.filledAtMillis());
        writeUuid(output, fill.settlementTransactionId());
    }

    private static BazaarFill readFill(DataInputStream input)
            throws IOException {
        return new BazaarFill(readUuid(input), readUuid(input),
                readUuid(input), readUuid(input), readUuid(input),
                readString(input, 96, false), input.readLong(),
                input.readInt(), input.readLong(), input.readLong(),
                input.readLong(), input.readLong(), input.readLong(),
                input.readLong(), input.readLong(), readUuid(input));
    }

    private static void writeReceipts(DataOutputStream output,
                                      Map<UUID, BazaarRequestReceipt> receipts)
            throws IOException {
        requireCount(receipts.size(),
                BazaarRetentionPolicy.MAXIMUM_RECEIPT_LIMIT, "receipt");
        output.writeInt(receipts.size());
        for (Map.Entry<UUID, BazaarRequestReceipt> entry : receipts.entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeUuid(output, entry.getKey());
            writeReceipt(output, entry.getValue());
        }
    }

    private static Map<UUID, BazaarRequestReceipt> readReceipts(
            DataInputStream input, int maximum) throws IOException {
        int count = readCount(input, maximum, "receipt");
        Map<UUID, BazaarRequestReceipt> receipts = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            UUID key = readUuid(input);
            BazaarRequestReceipt receipt = readReceipt(input);
            if (!key.equals(receipt.requestId())
                    || receipts.putIfAbsent(key, receipt) != null) {
                throw invalid("Bazaar receipt identity is invalid");
            }
        }
        return receipts;
    }

    private static void writeReceipt(DataOutputStream output,
                                     BazaarRequestReceipt receipt)
            throws IOException {
        writeString(output, receipt.fingerprint(), 64, false);
        writeResult(output, receipt.result());
        output.writeInt(receipt.result().operation().wireCode());
        switch (receipt.result().operation()) {
            case CREATE -> writeCreateCommand(output,
                    receipt.createCommand().orElseThrow());
            case CANCEL -> writeCancelCommand(output,
                    receipt.cancelCommand().orElseThrow());
            case EXPIRE -> writeExpireCommand(output,
                    receipt.expireCommand().orElseThrow());
        }
    }

    private static BazaarRequestReceipt readReceipt(DataInputStream input)
            throws IOException {
        String fingerprint = readString(input, 64, false);
        BazaarOperationResult result = readResult(input);
        BazaarOperationType commandType = BazaarOperationType.fromWireCode(
                input.readInt());
        if (commandType != result.operation()) {
            throw invalid("Bazaar receipt command type is invalid");
        }
        return switch (commandType) {
            case CREATE -> BazaarRequestReceipt.create(fingerprint,
                    readCreateCommand(input), result);
            case CANCEL -> BazaarRequestReceipt.cancel(fingerprint,
                    readCancelCommand(input), result);
            case EXPIRE -> BazaarRequestReceipt.expire(fingerprint,
                    readExpireCommand(input), result);
        };
    }

    private static void writeResult(DataOutputStream output,
                                    BazaarOperationResult result)
            throws IOException {
        writeUuid(output, result.requestId());
        writeUuid(output, result.orderId());
        output.writeInt(result.operation().wireCode());
        output.writeInt(result.status().wireCode());
        output.writeBoolean(result.replayed());
        output.writeLong(result.observedRevision());
        output.writeBoolean(result.order().isPresent());
        if (result.order().isPresent()) {
            writeOrder(output, result.order().orElseThrow());
        }
        requireCount(result.fills().size(), MAX_RESULT_FILLS,
                "result fill");
        output.writeInt(result.fills().size());
        for (BazaarFill fill : result.fills()) {
            writeFill(output, fill);
        }
        requireCount(result.settlements().size(), MAX_RESULT_SETTLEMENTS,
                "settlement");
        output.writeInt(result.settlements().size());
        for (BazaarEscrowSettlement settlement : result.settlements()) {
            writeSettlement(output, settlement);
        }
        requireCount(result.cancelledMakerOrderIds().size(),
                MAX_CANCELLED_MAKERS, "cancelled maker");
        output.writeInt(result.cancelledMakerOrderIds().size());
        for (UUID orderId : result.cancelledMakerOrderIds()) {
            writeUuid(output, orderId);
        }
        output.writeBoolean(result.productHalted());
    }

    private static BazaarOperationResult readResult(DataInputStream input)
            throws IOException {
        UUID requestId = readUuid(input);
        UUID orderId = readUuid(input);
        BazaarOperationType operation = BazaarOperationType.fromWireCode(
                input.readInt());
        BazaarOperationStatus status = BazaarOperationStatus.fromWireCode(
                input.readInt());
        boolean replayed = readBoolean(input);
        long observedRevision = input.readLong();
        Optional<BazaarOrder> order = readBoolean(input)
                ? Optional.of(readOrder(input)) : Optional.empty();
        int fillCount = readCount(input, MAX_RESULT_FILLS,
                "result fill");
        List<BazaarFill> fills = new ArrayList<>(fillCount);
        for (int index = 0; index < fillCount; index++) {
            fills.add(readFill(input));
        }
        int settlementCount = readCount(input, MAX_RESULT_SETTLEMENTS,
                "settlement");
        List<BazaarEscrowSettlement> settlements = new ArrayList<>(
                settlementCount);
        for (int index = 0; index < settlementCount; index++) {
            settlements.add(readSettlement(input));
        }
        int makerCount = readCount(input, MAX_CANCELLED_MAKERS,
                "cancelled maker");
        List<UUID> cancelledMakers = new ArrayList<>(makerCount);
        for (int index = 0; index < makerCount; index++) {
            cancelledMakers.add(readUuid(input));
        }
        return new BazaarOperationResult(requestId, orderId, operation,
                status, replayed, observedRevision, order, fills,
                settlements, cancelledMakers, readBoolean(input));
    }

    private static void writeSettlement(DataOutputStream output,
                                        BazaarEscrowSettlement settlement)
            throws IOException {
        writeUuid(output, settlement.transactionId());
        writeUuid(output, settlement.ownerId());
        writeOptionalUuid(output, settlement.orderId());
        output.writeInt(settlement.kind().wireCode());
        output.writeLong(settlement.moneyMinor());
        output.writeInt(settlement.itemQuantity());
        writeOptionalUuid(output, settlement.counterpartyId());
        writeOptionalUuid(output, settlement.fillId());
    }

    private static BazaarEscrowSettlement readSettlement(
            DataInputStream input) throws IOException {
        return new BazaarEscrowSettlement(readUuid(input), readUuid(input),
                readOptionalUuid(input),
                BazaarSettlementKind.fromWireCode(input.readInt()),
                input.readLong(), input.readInt(), readOptionalUuid(input),
                readOptionalUuid(input));
    }

    private static void writeCreateCommand(DataOutputStream output,
                                           CreateBazaarOrderCommand command)
            throws IOException {
        writeUuid(output, command.requestId());
        writeUuid(output, command.orderId());
        writeUuid(output, command.ownerId());
        writeUuid(output, command.activationTransactionId());
        writeOptionalUuid(output, command.moneyHoldAccountId());
        writeOptionalUuid(output, command.custodyLotId());
        writeString(output, command.productId(), 96, false);
        output.writeLong(command.productVersion());
        output.writeInt(command.side().wireCode());
        output.writeInt(command.type().wireCode());
        output.writeInt(command.timeInForce().wireCode());
        output.writeLong(command.limitPriceMinor());
        output.writeInt(command.quantity());
        output.writeLong(command.createdAtMillis());
        output.writeLong(command.expiresAtMillis());
        writeRule(output, command.rules());
    }

    private static CreateBazaarOrderCommand readCreateCommand(
            DataInputStream input) throws IOException {
        return new CreateBazaarOrderCommand(readUuid(input), readUuid(input),
                readUuid(input), readUuid(input), readOptionalUuid(input),
                readOptionalUuid(input), readString(input, 96, false),
                input.readLong(), BazaarOrderSide.fromWireCode(input.readInt()),
                BazaarOrderType.fromWireCode(input.readInt()),
                BazaarTimeInForce.fromWireCode(input.readInt()),
                input.readLong(), input.readInt(), input.readLong(),
                input.readLong(), readRule(input));
    }

    private static void writeCancelCommand(DataOutputStream output,
                                           CancelBazaarOrderCommand command)
            throws IOException {
        writeUuid(output, command.requestId());
        writeUuid(output, command.orderId());
        writeUuid(output, command.actorId());
        writeUuid(output, command.terminalTransactionId());
        output.writeLong(command.expectedRevision());
        output.writeLong(command.nowMillis());
    }

    private static CancelBazaarOrderCommand readCancelCommand(
            DataInputStream input) throws IOException {
        return new CancelBazaarOrderCommand(readUuid(input), readUuid(input),
                readUuid(input), readUuid(input), input.readLong(),
                input.readLong());
    }

    private static void writeExpireCommand(DataOutputStream output,
                                           ExpireBazaarOrderCommand command)
            throws IOException {
        writeUuid(output, command.requestId());
        writeUuid(output, command.orderId());
        writeUuid(output, command.terminalTransactionId());
        output.writeLong(command.expectedRevision());
        output.writeLong(command.nowMillis());
    }

    private static ExpireBazaarOrderCommand readExpireCommand(
            DataInputStream input) throws IOException {
        return new ExpireBazaarOrderCommand(readUuid(input), readUuid(input),
                readUuid(input), input.readLong(), input.readLong());
    }

    private static void writeReferencePrices(DataOutputStream output,
                                             Map<String, Long> references)
            throws IOException {
        requireCount(references.size(), MAX_REFERENCES, "reference");
        output.writeInt(references.size());
        for (Map.Entry<String, Long> entry : references.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey(), 96, false);
            output.writeLong(entry.getValue());
        }
    }

    private static Map<String, Long> readReferencePrices(
            DataInputStream input) throws IOException {
        int count = readCount(input, MAX_REFERENCES, "reference");
        Map<String, Long> references = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String productId = readString(input, 96, false);
            long price = input.readLong();
            if (references.putIfAbsent(productId, price) != null) {
                throw invalid("Bazaar reference identity is duplicated");
            }
        }
        return references;
    }

    private static void writeUuidSet(DataOutputStream output,
                                     Set<UUID> values) throws IOException {
        output.writeInt(values.size());
        for (UUID value : values.stream().sorted().toList()) {
            writeUuid(output, value);
        }
    }

    private static Set<UUID> readUuidSet(DataInputStream input, int maximum,
                                         String label) throws IOException {
        int count = readCount(input, maximum, label);
        Set<UUID> values = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            if (!values.add(readUuid(input))) {
                throw invalid("Bazaar " + label + " identity is duplicated");
            }
        }
        return values;
    }

    private static void writeRules(DataOutputStream output,
                                   List<BazaarRuleSnapshot> rules)
            throws IOException {
        requireCount(rules.size(), MAX_RULES, "rule");
        output.writeInt(rules.size());
        for (BazaarRuleSnapshot rule : rules) {
            writeRule(output, rule);
        }
    }

    private static List<BazaarRuleSnapshot> readRules(DataInputStream input)
            throws IOException {
        int count = readCount(input, MAX_RULES, "rule");
        List<BazaarRuleSnapshot> rules = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rules.add(readRule(input));
        }
        return rules;
    }

    private static void writeRule(DataOutputStream output,
                                  BazaarRuleSnapshot rules)
            throws IOException {
        output.writeInt(rules.makerFeeBasisPoints());
        output.writeInt(rules.takerFeeBasisPoints());
        output.writeInt(rules.maximumOrderQuantity());
        output.writeLong(rules.maximumNotionalMinor());
        output.writeInt(rules.maximumOpenOrdersPerPlayer());
        output.writeInt(rules.maximumOpenOrdersPerProductPerPlayer());
        output.writeLong(rules.maximumEscrowedValuePerPlayerMinor());
        output.writeInt(rules.selfTradePolicy().wireCode());
        output.writeInt(rules.executionPricePolicy().wireCode());
        output.writeBoolean(rules.circuitBreakerEnabled());
        output.writeInt(rules.priceBandBasisPoints());
        output.writeLong(rules.minimumLifetimeMillis());
        output.writeLong(rules.configRevision());
    }

    private static BazaarRuleSnapshot readRule(DataInputStream input)
            throws IOException {
        return new BazaarRuleSnapshot(input.readInt(), input.readInt(),
                input.readInt(), input.readLong(), input.readInt(),
                input.readInt(), input.readLong(),
                BazaarSelfTradePolicy.fromWireCode(input.readInt()),
                BazaarExecutionPricePolicy.fromWireCode(input.readInt()),
                readBoolean(input), input.readInt(), input.readLong(),
                input.readLong());
    }

    private static void writeLifecycleReceipts(DataOutputStream output,
                                               Map<UUID, String> receipts)
            throws IOException {
        requireCount(receipts.size(),
                BazaarRetentionPolicy.MAXIMUM_RECEIPT_LIMIT,
                "lifecycle receipt");
        output.writeInt(receipts.size());
        for (Map.Entry<UUID, String> entry : receipts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            writeUuid(output, entry.getKey());
            writeString(output, entry.getValue(), 64, false);
        }
    }

    private static Map<UUID, String> readLifecycleReceipts(
            DataInputStream input, int maximum) throws IOException {
        int count = readCount(input, maximum, "lifecycle receipt");
        Map<UUID, String> receipts = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            UUID mutationId = readUuid(input);
            String fingerprint = readString(input, 64, false);
            if (receipts.putIfAbsent(mutationId, fingerprint) != null) {
                throw invalid(
                        "Bazaar lifecycle receipt identity is duplicated");
            }
        }
        return receipts;
    }

    private static void writeLifecycleCommand(DataOutputStream output,
                                              BazaarLifecycleCommand command)
            throws IOException {
        writeUuid(output, command.mutationId());
        output.writeInt(command.type().wireCode());
        switch (command.type()) {
            case SET_EFFECTIVE_RULES -> writeRule(output,
                    command.rules().orElseThrow());
            case REGISTER_PRODUCT -> writeProduct(output,
                    command.product().orElseThrow());
            case SET_PRODUCT_STATUS -> {
                writeString(output, command.productId(), 96, false);
                output.writeInt(command.productStatus().orElseThrow()
                        .wireCode());
            }
            case SET_REFERENCE_PRICE -> {
                writeString(output, command.productId(), 96, false);
                output.writeLong(command.referencePriceMinor());
            }
        }
    }

    private static BazaarLifecycleCommand readLifecycleCommand(
            DataInputStream input) throws IOException {
        UUID mutationId = readUuid(input);
        BazaarLifecycleType type = BazaarLifecycleType.fromWireCode(
                input.readInt());
        return switch (type) {
            case SET_EFFECTIVE_RULES -> BazaarLifecycleCommand
                    .setEffectiveRules(mutationId, readRule(input));
            case REGISTER_PRODUCT -> BazaarLifecycleCommand
                    .registerProduct(mutationId, readProduct(input));
            case SET_PRODUCT_STATUS -> BazaarLifecycleCommand
                    .setProductStatus(mutationId,
                            readString(input, 96, false),
                            BazaarProductStatus.fromWireCode(
                                    input.readInt()));
            case SET_REFERENCE_PRICE -> BazaarLifecycleCommand
                    .setReferencePrice(mutationId,
                            readString(input, 96, false),
                            input.readLong());
        };
    }

    private static void writeOptionalUuid(DataOutputStream output,
                                          Optional<UUID> value)
            throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            writeUuid(output, value.orElseThrow());
        }
    }

    private static Optional<UUID> readOptionalUuid(DataInputStream input)
            throws IOException {
        return readBoolean(input) ? Optional.of(readUuid(input))
                : Optional.empty();
    }

    private static void writeUuid(DataOutputStream output, UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value,
                                    int maximum, boolean allowEmpty)
            throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximum
                || !allowEmpty && bytes.length == 0) {
            throw invalid("Bazaar string size is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maximum,
                                     boolean allowEmpty) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > maximum
                || !allowEmpty && size == 0
                || size > input.available()) {
            throw invalid("Bazaar string size is invalid");
        }
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) {
            throw new EOFException("Bazaar string is truncated");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (!Arrays.equals(bytes,
                    value.getBytes(StandardCharsets.UTF_8))) {
                throw invalid("Bazaar string encoding is not canonical");
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "Bazaar string is not valid UTF8", exception);
        }
    }

    private static boolean readBoolean(DataInputStream input)
            throws IOException {
        int value = input.readUnsignedByte();
        if (value != 0 && value != 1) {
            throw invalid("Bazaar boolean encoding is invalid");
        }
        return value == 1;
    }

    private static int readCount(DataInputStream input, int maximum,
                                 String label) throws IOException {
        int count = input.readInt();
        requireCount(count, maximum, label);
        return count;
    }

    private static void requireCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw invalid("Bazaar " + label + " count is invalid");
        }
    }

    private static byte[] appendDigest(byte[] payload, int maximum,
                                       String label) {
        int total = Math.addExact(payload.length, DIGEST_BYTES);
        if (payload.length == 0 || total > maximum) {
            throw invalid(label + " size is invalid");
        }
        byte[] encoded = Arrays.copyOf(payload, total);
        byte[] digest = digest(payload);
        System.arraycopy(digest, 0, encoded, payload.length,
                DIGEST_BYTES);
        return encoded;
    }

    private static byte[] requireAndVerify(byte[] encoded, int maximum,
                                           String label) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length <= DIGEST_BYTES || copy.length > maximum) {
            throw invalid(label + " size is invalid");
        }
        int payloadLength = copy.length - DIGEST_BYTES;
        byte[] expected = digest(Arrays.copyOf(copy, payloadLength));
        byte[] actual = Arrays.copyOfRange(copy, payloadLength,
                copy.length);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalid(label + " digest is invalid");
        }
        return copy;
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Bazaar hashing is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
