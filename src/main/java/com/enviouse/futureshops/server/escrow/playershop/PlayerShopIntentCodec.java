package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopIntentCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            PlayerShopEscrowConstants.MAX_ENCODED_BYTES;

    private static final int MAGIC = 0x46535049;

    private PlayerShopIntentCodec() {
    }

    public static byte[] encode(PlayerShopEscrowIntent intent) {
        Objects.requireNonNull(intent, "intent");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            writeCore(output, intent.requestId(), intent.actorId(),
                    intent.ownerId(), intent.shopIdentity(),
                    intent.operation(), intent.tradeMethod(),
                    intent.paymentSource(), intent.requestedUnits(),
                    intent.quoteCreatedAt(), intent.listing(),
                    intent.moneyTransfers(), intent.itemTransfers(),
                    intent.claims(), intent.storageMutations());
            output.writeByte(intent.status().ordinal());
            output.writeLong(intent.revision());
            PlayerShopBinarySupport.writeString(output,
                    intent.intentFingerprint(), 64);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            requireSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode player shop intent", exception);
        }
    }

    public static PlayerShopEscrowIntent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Player shop intent magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Player shop intent schema is unsupported");
            }
            Core core = readCore(input);
            PlayerShopEscrowIntent.Status status = PlayerShopBinarySupport.readEnum(
                    input, PlayerShopEscrowIntent.Status.values(), "intent status");
            long revision = input.readLong();
            String fingerprint = PlayerShopBinarySupport.readString(input, 64,
                    "intent fingerprint");
            PlayerShopBinarySupport.requireFinished(input, "intent");
            PlayerShopEscrowIntent intent = new PlayerShopEscrowIntent(
                    core.requestId(), core.actorId(), core.ownerId(),
                    core.shopIdentity(), core.operation(), core.tradeMethod(),
                    core.paymentSource(), core.requestedUnits(),
                    core.quoteCreatedAt(), core.listing(),
                    core.moneyTransfers(), core.itemTransfers(), core.claims(),
                    core.storageMutations(), status, revision, fingerprint);
            if (!java.util.Arrays.equals(copy, encode(intent))) {
                throw new IllegalArgumentException("Player shop intent is not canonical");
            }
            return intent;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Player shop intent is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Player shop intent is invalid", exception);
        }
    }

    static void writeCore(
            DataOutputStream output,
            UUID requestId,
            UUID actorId,
            UUID ownerId,
            PlayerShopIdentity identity,
            PlayerShopOperation operation,
            PlayerShopTradeMethod tradeMethod,
            PlayerShopPaymentSource paymentSource,
            int requestedUnits,
            Instant quoteCreatedAt,
            PlayerShopListingSnapshot listing,
            List<PlayerShopMoneyTransfer> moneyTransfers,
            List<PlayerShopItemTransfer> itemTransfers,
            List<PlayerShopClaimPlan> claims,
            List<PlayerShopStorageMutationPlan> storageMutations
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, requestId);
        PlayerShopBinarySupport.writeUuid(output, actorId);
        PlayerShopBinarySupport.writeUuid(output, ownerId);
        writeIdentity(output, identity);
        output.writeByte(operation.ordinal());
        output.writeByte(tradeMethod.ordinal());
        output.writeByte(paymentSource.ordinal());
        output.writeInt(requestedUnits);
        output.writeLong(quoteCreatedAt.getEpochSecond());
        output.writeInt(quoteCreatedAt.getNano());
        output.writeBoolean(listing != null);
        if (listing != null) {
            writeListing(output, listing);
        }
        output.writeInt(moneyTransfers.size());
        for (PlayerShopMoneyTransfer transfer : moneyTransfers) {
            writeMoneyTransfer(output, transfer);
        }
        output.writeInt(itemTransfers.size());
        for (PlayerShopItemTransfer transfer : itemTransfers) {
            writeItemTransfer(output, transfer);
        }
        output.writeInt(claims.size());
        for (PlayerShopClaimPlan claim : claims) {
            writeClaim(output, claim);
        }
        output.writeInt(storageMutations.size());
        for (PlayerShopStorageMutationPlan mutation : storageMutations) {
            writeStorageMutation(output, mutation);
        }
    }

    static Core readCore(DataInputStream input) throws IOException {
        UUID requestId = PlayerShopBinarySupport.readUuid(input, "request id");
        UUID actorId = PlayerShopBinarySupport.readUuid(input, "actor id");
        UUID ownerId = PlayerShopBinarySupport.readUuid(input, "owner id");
        PlayerShopIdentity identity = readIdentity(input);
        PlayerShopOperation operation = PlayerShopBinarySupport.readEnum(
                input, PlayerShopOperation.values(), "operation");
        PlayerShopTradeMethod method = PlayerShopBinarySupport.readEnum(
                input, PlayerShopTradeMethod.values(), "trade method");
        PlayerShopPaymentSource source = PlayerShopBinarySupport.readEnum(
                input, PlayerShopPaymentSource.values(), "payment source");
        int requestedUnits = input.readInt();
        long seconds = input.readLong();
        int nanos = input.readInt();
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalArgumentException("Player shop quote instant is invalid");
        }
        Instant quoteCreatedAt = Instant.ofEpochSecond(seconds, nanos);
        PlayerShopListingSnapshot listing = input.readBoolean()
                ? readListing(input) : null;
        List<PlayerShopMoneyTransfer> money = readList(input,
                PlayerShopEscrowConstants.MAX_TRANSFERS,
                PlayerShopIntentCodec::readMoneyTransfer, "money transfers");
        List<PlayerShopItemTransfer> items = readList(input,
                PlayerShopEscrowConstants.MAX_TRANSFERS,
                PlayerShopIntentCodec::readItemTransfer, "item transfers");
        List<PlayerShopClaimPlan> claims = readList(input,
                PlayerShopEscrowConstants.MAX_CLAIMS,
                PlayerShopIntentCodec::readClaim, "claims");
        List<PlayerShopStorageMutationPlan> storage = readList(input,
                PlayerShopEscrowConstants.MAX_STORAGE_MUTATIONS,
                PlayerShopIntentCodec::readStorageMutation,
                "storage mutations");
        return new Core(requestId, actorId, ownerId, identity, operation,
                method, source, requestedUnits, quoteCreatedAt, listing,
                money, items, claims, storage);
    }

    static void writeIdentity(DataOutputStream output,
                              PlayerShopIdentity identity) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, identity.registryShopId());
        output.writeLong(identity.identityRevision());
        PlayerShopBinarySupport.writeString(output, identity.shopId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
        PlayerShopBinarySupport.writeString(output, identity.dimensionId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
        output.writeInt(identity.blockX());
        output.writeInt(identity.blockY());
        output.writeInt(identity.blockZ());
        PlayerShopBinarySupport.writeUuid(output, identity.ownerId());
    }

    static PlayerShopIdentity readIdentity(DataInputStream input)
            throws IOException {
        return new PlayerShopIdentity(
                PlayerShopBinarySupport.readUuid(input, "registry shop id"),
                input.readLong(),
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                        "shop id"),
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                        "dimension id"),
                input.readInt(), input.readInt(), input.readInt(),
                PlayerShopBinarySupport.readUuid(input, "owner id"));
    }

    static void writeListing(DataOutputStream output,
                             PlayerShopListingSnapshot listing) throws IOException {
        PlayerShopBinarySupport.writeString(output, listing.listingId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
        output.writeInt(listing.listingIndex());
        output.writeByte(listing.direction().ordinal());
        output.writeByte(listing.configuredTradeMode().ordinal());
        output.writeInt(listing.baseQuantity());
        output.writeLong(listing.moneyPriceMinorUnits());
        output.writeBoolean(listing.barterTemplate() != null);
        if (listing.barterTemplate() != null) {
            PlayerShopListingSnapshot.writeTemplate(output,
                    listing.barterTemplate());
        }
        output.writeInt(listing.barterUnitsPerPurchase());
        output.writeLong(listing.buybackPriceMinorUnits());
        output.writeInt(listing.buybackCap());
        output.writeInt(listing.buybackBought());
        output.writeInt(listing.outputs().size());
        for (PlayerShopListingSnapshot.ItemTemplate template
                : listing.outputs()) {
            PlayerShopListingSnapshot.writeTemplate(output, template);
        }
        PlayerShopListingSnapshot.writePromotion(output, listing.promotion());
        output.writeBoolean(listing.hidden());
        output.writeBoolean(listing.showcase());
        output.writeBoolean(listing.adminShop());
        PlayerShopBinarySupport.writeString(output,
                listing.revisionFingerprint(), 64);
    }

    static PlayerShopListingSnapshot readListing(DataInputStream input)
            throws IOException {
        String listingId = PlayerShopBinarySupport.readString(input,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "listing id");
        int listingIndex = input.readInt();
        PlayerShopListingSnapshot.Direction direction =
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopListingSnapshot.Direction.values(),
                        "listing direction");
        PlayerShopListingSnapshot.ConfiguredTradeMode mode =
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopListingSnapshot.ConfiguredTradeMode.values(),
                        "configured trade mode");
        int baseQuantity = input.readInt();
        long moneyPrice = input.readLong();
        PlayerShopListingSnapshot.ItemTemplate barter = input.readBoolean()
                ? readTemplate(input) : null;
        int barterUnits = input.readInt();
        long buybackPrice = input.readLong();
        int buybackCap = input.readInt();
        int buybackBought = input.readInt();
        List<PlayerShopListingSnapshot.ItemTemplate> outputs = readList(input,
                PlayerShopEscrowConstants.MAX_LISTING_OUTPUTS,
                PlayerShopIntentCodec::readTemplate, "listing outputs");
        PlayerShopListingSnapshot.PromotionSnapshot promotion =
                readPromotion(input);
        boolean hidden = input.readBoolean();
        boolean showcase = input.readBoolean();
        boolean admin = input.readBoolean();
        String revision = PlayerShopBinarySupport.readString(input, 64,
                "listing revision");
        return new PlayerShopListingSnapshot(listingId, listingIndex,
                direction, mode, baseQuantity, moneyPrice, barter,
                barterUnits, buybackPrice, buybackCap, buybackBought,
                outputs, promotion, hidden, showcase, admin, revision);
    }

    static PlayerShopListingSnapshot.ItemTemplate readTemplate(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopListingSnapshot.ItemTemplate(
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                        "template item id"),
                input.readInt(),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopItemMatchMode.values(), "item match mode"),
                PlayerShopBinarySupport.readBytes(input,
                        PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                        "item template"));
    }

    static PlayerShopListingSnapshot.PromotionSnapshot readPromotion(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopListingSnapshot.PromotionSnapshot(
                PlayerShopBinarySupport.readOptionalString(input, 64,
                        "promotion type"),
                Double.longBitsToDouble(input.readLong()),
                input.readInt(), input.readInt(), input.readLong(),
                input.readLong(), input.readBoolean(), input.readBoolean());
    }

    static void writeEndpoint(DataOutputStream output,
                              PlayerShopAssetEndpoint endpoint) throws IOException {
        output.writeByte(endpoint.kind().ordinal());
        PlayerShopBinarySupport.writeUuid(output, endpoint.participantId());
        PlayerShopBinarySupport.writeString(output, endpoint.reference(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
    }

    static PlayerShopAssetEndpoint readEndpoint(DataInputStream input)
            throws IOException {
        PlayerShopAssetEndpoint.Kind kind = PlayerShopBinarySupport.readEnum(
                input, PlayerShopAssetEndpoint.Kind.values(), "asset endpoint");
        UUID participant = PlayerShopBinarySupport.readRawUuid(input);
        String reference = PlayerShopBinarySupport.readString(input,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                "asset endpoint reference");
        return new PlayerShopAssetEndpoint(kind, participant, reference);
    }

    static void writeMoneyTransfer(DataOutputStream output,
                                   PlayerShopMoneyTransfer transfer) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, transfer.transferId());
        writeEndpoint(output, transfer.source());
        writeEndpoint(output, transfer.destination());
        output.writeLong(transfer.amountMinorUnits());
        output.writeByte(transfer.paymentSource().ordinal());
        output.writeLong(transfer.sourceBalanceBeforeMinorUnits());
        output.writeLong(transfer.destinationBalanceBeforeMinorUnits());
    }

    static PlayerShopMoneyTransfer readMoneyTransfer(DataInputStream input)
            throws IOException {
        return new PlayerShopMoneyTransfer(
                PlayerShopBinarySupport.readUuid(input, "money transfer id"),
                readEndpoint(input), readEndpoint(input), input.readLong(),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopPaymentSource.values(), "money source"),
                input.readLong(), input.readLong());
    }

    static void writeItemLot(DataOutputStream output, PlayerShopItemLot lot)
            throws IOException {
        PlayerShopBinarySupport.writeUuid(output, lot.lotId());
        PlayerShopBinarySupport.writeUuid(output, lot.sourceTransactionId());
        PlayerShopBinarySupport.writeString(output, lot.sourceKey(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
        output.writeInt(lot.portionIndex());
        output.writeInt(lot.portionCount());
        PlayerShopBinarySupport.writeString(output, lot.itemId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
        output.writeInt(lot.quantity());
        output.writeByte(lot.matchMode().ordinal());
        PlayerShopBinarySupport.writeBytes(output,
                lot.canonicalOneCountTemplate(),
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
        PlayerShopBinarySupport.writeBytes(output, lot.serializedExactStack(),
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
        PlayerShopBinarySupport.writeString(output, lot.fingerprint(), 64);
    }

    static PlayerShopItemLot readItemLot(DataInputStream input)
            throws IOException {
        return new PlayerShopItemLot(
                PlayerShopBinarySupport.readUuid(input, "item lot id"),
                PlayerShopBinarySupport.readUuid(input,
                        "item source transaction id"),
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                        "item source key"),
                input.readInt(), input.readInt(),
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                        "item id"),
                input.readInt(),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopItemMatchMode.values(), "item match mode"),
                PlayerShopBinarySupport.readBytes(input,
                        PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                        "item template"),
                PlayerShopBinarySupport.readBytes(input,
                        PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                        "item stack"),
                PlayerShopBinarySupport.readString(input, 64,
                        "item fingerprint"));
    }

    static void writeItemTransfer(DataOutputStream output,
                                  PlayerShopItemTransfer transfer) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, transfer.transferId());
        writeEndpoint(output, transfer.source());
        writeEndpoint(output, transfer.destination());
        writeItemLot(output, transfer.lot());
    }

    static PlayerShopItemTransfer readItemTransfer(DataInputStream input)
            throws IOException {
        return new PlayerShopItemTransfer(
                PlayerShopBinarySupport.readUuid(input, "item transfer id"),
                readEndpoint(input), readEndpoint(input), readItemLot(input));
    }

    static void writeClaim(DataOutputStream output, PlayerShopClaimPlan claim)
            throws IOException {
        PlayerShopBinarySupport.writeUuid(output, claim.claimId());
        PlayerShopBinarySupport.writeUuid(output, claim.beneficiaryId());
        output.writeByte(claim.kind().ordinal());
        PlayerShopBinarySupport.writeString(output, claim.purpose(),
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
        output.writeLong(claim.moneyAmountMinorUnits());
        if (claim.kind() == PlayerShopClaimPlan.Kind.EXACT_ITEM) {
            writeItemLot(output, claim.itemLot());
        }
    }

    static PlayerShopClaimPlan readClaim(DataInputStream input)
            throws IOException {
        UUID claimId = PlayerShopBinarySupport.readUuid(input, "claim id");
        UUID beneficiary = PlayerShopBinarySupport.readUuid(input,
                "claim beneficiary id");
        PlayerShopClaimPlan.Kind kind = PlayerShopBinarySupport.readEnum(input,
                PlayerShopClaimPlan.Kind.values(), "claim kind");
        String purpose = PlayerShopBinarySupport.readString(input,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "claim purpose");
        long money = input.readLong();
        PlayerShopItemLot lot = kind == PlayerShopClaimPlan.Kind.EXACT_ITEM
                ? readItemLot(input) : null;
        return new PlayerShopClaimPlan(claimId, beneficiary, kind, purpose,
                money, lot);
    }

    static void writeStorageEndpoint(DataOutputStream output,
                                     PlayerShopStorageEndpoint endpoint) throws IOException {
        PlayerShopBinarySupport.writeString(output, endpoint.dimensionId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
        output.writeInt(endpoint.blockX());
        output.writeInt(endpoint.blockY());
        output.writeInt(endpoint.blockZ());
        output.writeInt(endpoint.linkOrdinal());
        output.writeLong(endpoint.linkRevision());
        PlayerShopBinarySupport.writeString(output, endpoint.adapterId(),
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
    }

    static PlayerShopStorageEndpoint readStorageEndpoint(DataInputStream input)
            throws IOException {
        return new PlayerShopStorageEndpoint(
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                        "storage dimension"),
                input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readLong(),
                PlayerShopBinarySupport.readString(input,
                        PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH,
                        "storage adapter"));
    }

    static void writeStorageMutation(
            DataOutputStream output,
            PlayerShopStorageMutationPlan mutation
    ) throws IOException {
        PlayerShopBinarySupport.writeUuid(output, mutation.mutationId());
        output.writeInt(mutation.sequence());
        output.writeByte(mutation.direction().ordinal());
        writeStorageEndpoint(output, mutation.endpoint());
        PlayerShopBinarySupport.writeUuid(output, mutation.itemTransferId());
        PlayerShopBinarySupport.writeUuid(output, mutation.claimId());
        writeItemLot(output, mutation.lot());
        PlayerShopBinarySupport.writeString(output,
                mutation.expectedStateFingerprint(), 128);
    }

    static PlayerShopStorageMutationPlan readStorageMutation(
            DataInputStream input
    ) throws IOException {
        return new PlayerShopStorageMutationPlan(
                PlayerShopBinarySupport.readUuid(input, "storage mutation id"),
                input.readInt(),
                PlayerShopBinarySupport.readEnum(input,
                        PlayerShopStorageMutationPlan.Direction.values(),
                        "storage direction"),
                readStorageEndpoint(input),
                PlayerShopBinarySupport.readUuid(input,
                        "storage transfer id"),
                PlayerShopBinarySupport.readRawUuid(input),
                readItemLot(input),
                PlayerShopBinarySupport.readString(input, 128,
                        "storage state fingerprint"));
    }

    private static <T> List<T> readList(
            DataInputStream input,
            int maximum,
            Reader<T> reader,
            String label
    ) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Player shop " + label + " size is invalid");
        }
        List<T> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(reader.read(input));
        }
        return List.copyOf(result);
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Player shop intent size is invalid");
        }
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream input) throws IOException;
    }

    record Core(
            UUID requestId,
            UUID actorId,
            UUID ownerId,
            PlayerShopIdentity shopIdentity,
            PlayerShopOperation operation,
            PlayerShopTradeMethod tradeMethod,
            PlayerShopPaymentSource paymentSource,
            int requestedUnits,
            Instant quoteCreatedAt,
            PlayerShopListingSnapshot listing,
            List<PlayerShopMoneyTransfer> moneyTransfers,
            List<PlayerShopItemTransfer> itemTransfers,
            List<PlayerShopClaimPlan> claims,
            List<PlayerShopStorageMutationPlan> storageMutations
    ) {
    }
}
