package com.enviouse.futureshops.network;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferBundleComparison;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;
import io.netty.handler.codec.DecoderException;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class ServerShopOfferNetworkCodec {
    public static final int MAX_LISTINGS = 10_000;
    public static final int MAX_ENCODED_LISTING_BYTES = 1_048_576;
    public static final int MAX_ENCODED_CATALOG_BYTES = 1_048_576;
    private static final int MAX_COMPONENTS =
            ServerShopOfferValidator.MAX_COMPONENTS;
    private static final int MAX_OPTIONS =
            ServerShopOfferValidator.MAX_OPTIONS;
    private static final int MAX_COMPARISONS = MAX_COMPONENTS;
    private static final int MAX_IDENTIFIER =
            ServerShopOfferValidator.MAX_IDENTIFIER_LENGTH;
    private static final int MAX_TEXT =
            ServerShopOfferValidator.MAX_TEXT_LENGTH;
    private static final int MAX_NBT = 65_535;

    private ServerShopOfferNetworkCodec() {
    }

    public static byte[] encodeListingBytes(
            ServerShopOfferListing listing
    ) {
        FriendlyByteBuf buffer =
                new FriendlyByteBuf(Unpooled.buffer());
        try {
            encodeListing(buffer, listing);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    public static ServerShopOfferListing decodeListingBytes(
            byte[] encoded
    ) {
        if (encoded.length > MAX_ENCODED_LISTING_BYTES) {
            throw new DecoderException(
                    "Server shop offer listing payload is too large");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.wrappedBuffer(encoded));
        try {
            ServerShopOfferListing listing = decodeListing(buffer);
            if (buffer.isReadable()) {
                throw new DecoderException(
                        "Server shop offer listing has trailing bytes");
            }
            return listing;
        } finally {
            buffer.release();
        }
    }

    public static void encodeListings(
            FriendlyByteBuf buffer,
            List<ServerShopOfferListing> listings
    ) {
        if (listings.size() > MAX_LISTINGS) {
            throw new IllegalArgumentException(
                    "Server shop offer listing count is too large");
        }
        FriendlyByteBuf encoded = new FriendlyByteBuf(
                Unpooled.buffer(256, MAX_ENCODED_CATALOG_BYTES));
        try {
            encoded.writeVarInt(listings.size());
            for (ServerShopOfferListing listing : listings) {
                encodeListing(encoded, listing);
            }
            byte[] payload = new byte[encoded.readableBytes()];
            encoded.readBytes(payload);
            buffer.writeByteArray(payload);
        } catch (IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException(
                    "Server shop offer catalog payload is too large",
                    exception);
        } finally {
            encoded.release();
        }
    }

    public static List<ServerShopOfferListing> decodeListings(
            FriendlyByteBuf buffer
    ) {
        byte[] payload;
        try {
            payload = buffer.readByteArray(
                    MAX_ENCODED_CATALOG_BYTES);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Server shop offer catalog payload is too large",
                    exception);
        }
        FriendlyByteBuf encoded = new FriendlyByteBuf(
                Unpooled.wrappedBuffer(payload));
        try {
            int count = readCount(encoded, MAX_LISTINGS, "listings");
            List<ServerShopOfferListing> listings =
                    new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                listings.add(decodeListing(encoded));
            }
            if (encoded.isReadable()) {
                throw new DecoderException(
                        "Server shop offer catalog has trailing bytes");
            }
            return List.copyOf(listings);
        } finally {
            encoded.release();
        }
    }

    public static void encodeListing(
            FriendlyByteBuf buffer,
            ServerShopOfferListing listing
    ) {
        FriendlyByteBuf encoded = new FriendlyByteBuf(
                Unpooled.buffer(256, MAX_ENCODED_LISTING_BYTES));
        try {
            encodeListingUnchecked(encoded, listing);
            buffer.writeBytes(encoded);
        } catch (IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException(
                    "Server shop offer listing payload is too large",
                    exception);
        } finally {
            encoded.release();
        }
    }

    private static void encodeListingUnchecked(
            FriendlyByteBuf buffer,
            ServerShopOfferListing listing
    ) {
        writeIdentifier(buffer, listing.listingId());
        buffer.writeVarLong(listing.revision());
        writeText(buffer, listing.displayName());
        writeText(buffer, listing.description());
        writeIdentifier(buffer, listing.categoryId());
        writeIdentifier(buffer, listing.iconItemId());
        writeNbt(buffer, listing.iconNbt());
        buffer.writeBoolean(listing.active());
        buffer.writeVarLong(listing.expiresAtEpoch());
        writeIdentifier(buffer, listing.permissionNode());
        encodeComponents(buffer, listing.outputs());
        encodeAcquireOptions(buffer, listing.acquireOptions());
        encodeSellOptions(buffer, listing.sellOptions());
        encodeStock(buffer, listing.stockPolicy());
        encodeLimits(buffer, listing.limits());
        encodeSchedule(buffer, listing.schedule());
        encodeComparisons(buffer, listing.bundleComparisons());
    }

    public static ServerShopOfferListing decodeListing(
            FriendlyByteBuf buffer
    ) {
        int boundedLength = Math.min(
                buffer.readableBytes(),
                MAX_ENCODED_LISTING_BYTES + 1);
        FriendlyByteBuf encoded = new FriendlyByteBuf(
                buffer.retainedSlice(
                        buffer.readerIndex(), boundedLength));
        try {
            ServerShopOfferListing listing =
                    decodeListingUnchecked(encoded);
            int consumed = encoded.readerIndex();
            if (consumed > MAX_ENCODED_LISTING_BYTES) {
                throw new DecoderException(
                        "Server shop offer listing payload is too large");
            }
            buffer.skipBytes(consumed);
            return listing;
        } finally {
            encoded.release();
        }
    }

    private static ServerShopOfferListing decodeListingUnchecked(
            FriendlyByteBuf buffer
    ) {
        ServerShopOfferListing listing = new ServerShopOfferListing(
                readIdentifier(buffer),
                readNonnegativeVarLong(buffer, "revision"),
                readText(buffer),
                readText(buffer),
                readIdentifier(buffer),
                readIdentifier(buffer),
                readNbt(buffer),
                buffer.readBoolean(),
                readNonnegativeVarLong(buffer, "expiry"),
                readIdentifier(buffer),
                decodeComponents(buffer),
                decodeAcquireOptions(buffer),
                decodeSellOptions(buffer),
                decodeStock(buffer),
                decodeLimits(buffer),
                decodeSchedule(buffer),
                decodeComparisons(buffer));
        if (!ServerShopOfferValidator.validate(listing).valid()) {
            throw new DecoderException(
                    "Server shop offer payload is invalid");
        }
        return listing;
    }

    private static void encodeAcquireOptions(
            FriendlyByteBuf buffer,
            List<AcquireOfferOption> options
    ) {
        writeCount(buffer, options.size(), MAX_OPTIONS, "acquire options");
        for (AcquireOfferOption option : options) {
            writeIdentifier(buffer, option.optionId());
            writeText(buffer, option.label());
            buffer.writeBoolean(option.free());
            buffer.writeBoolean(option.moneyCostPresent());
            buffer.writeVarLong(option.moneyCostMinorUnits());
            encodeComponents(buffer, option.itemCosts());
            buffer.writeVarInt(option.outputMultiplier());
            encodeLimits(buffer, option.limits());
            encodeSchedule(buffer, option.schedule());
            writeIdentifier(buffer, option.permissionNode());
        }
    }

    private static List<AcquireOfferOption> decodeAcquireOptions(
            FriendlyByteBuf buffer
    ) {
        int count = readCount(buffer, MAX_OPTIONS, "acquire options");
        List<AcquireOfferOption> options = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(new AcquireOfferOption(
                    readIdentifier(buffer),
                    readText(buffer),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    readNonnegativeVarLong(buffer, "money cost"),
                    decodeComponents(buffer),
                    buffer.readVarInt(),
                    decodeLimits(buffer),
                    decodeSchedule(buffer),
                    readIdentifier(buffer)));
        }
        return List.copyOf(options);
    }

    private static void encodeSellOptions(
            FriendlyByteBuf buffer,
            List<SellOfferOption> options
    ) {
        writeCount(buffer, options.size(), MAX_OPTIONS, "sell options");
        for (SellOfferOption option : options) {
            writeIdentifier(buffer, option.optionId());
            writeText(buffer, option.label());
            encodeComponents(buffer, option.itemInputs());
            buffer.writeVarLong(option.moneyPayoutMinorUnits());
            buffer.writeVarLong(option.capacity());
            encodeLimits(buffer, option.limits());
            encodeSchedule(buffer, option.schedule());
            writeIdentifier(buffer, option.permissionNode());
        }
    }

    private static List<SellOfferOption> decodeSellOptions(
            FriendlyByteBuf buffer
    ) {
        int count = readCount(buffer, MAX_OPTIONS, "sell options");
        List<SellOfferOption> options = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(new SellOfferOption(
                    readIdentifier(buffer),
                    readText(buffer),
                    decodeComponents(buffer),
                    readNonnegativeVarLong(buffer, "money payout"),
                    readNonnegativeVarLong(buffer, "capacity"),
                    decodeLimits(buffer),
                    decodeSchedule(buffer),
                    readIdentifier(buffer)));
        }
        return List.copyOf(options);
    }

    private static void encodeComponents(
            FriendlyByteBuf buffer,
            List<OfferItemComponent> components
    ) {
        writeCount(buffer, components.size(), MAX_COMPONENTS, "components");
        for (OfferItemComponent component : components) {
            writeIdentifier(buffer, component.componentId());
            writeIdentifier(buffer, component.itemId());
            buffer.writeVarInt(component.count());
            writeNbt(buffer, component.exactNbt());
        }
    }

    private static List<OfferItemComponent> decodeComponents(
            FriendlyByteBuf buffer
    ) {
        int count = readCount(buffer, MAX_COMPONENTS, "components");
        List<OfferItemComponent> components = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            components.add(new OfferItemComponent(
                    readIdentifier(buffer),
                    readIdentifier(buffer),
                    buffer.readVarInt(),
                    readNbt(buffer)));
        }
        return List.copyOf(components);
    }

    private static void encodeStock(
            FriendlyByteBuf buffer,
            OfferStockPolicy stock
    ) {
        buffer.writeEnum(stock.type());
        buffer.writeVarLong(stock.quantity());
        buffer.writeVarLong(stock.refreshSeconds());
    }

    private static OfferStockPolicy decodeStock(FriendlyByteBuf buffer) {
        OfferStockPolicy.Type type;
        try {
            type = buffer.readEnum(OfferStockPolicy.Type.class);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Server shop offer stock type is invalid", exception);
        }
        return new OfferStockPolicy(type,
                readNonnegativeVarLong(buffer, "stock quantity"),
                readNonnegativeVarLong(buffer, "stock refresh"));
    }

    private static void encodeLimits(
            FriendlyByteBuf buffer,
            OfferLimitPolicy limits
    ) {
        buffer.writeVarInt(limits.maximumPerRequest());
        buffer.writeVarLong(limits.lifetimeLimit());
        buffer.writeVarLong(limits.periodLimit());
        buffer.writeVarLong(limits.periodSeconds());
        buffer.writeVarLong(limits.cooldownSeconds());
    }

    private static OfferLimitPolicy decodeLimits(FriendlyByteBuf buffer) {
        return new OfferLimitPolicy(
                buffer.readVarInt(),
                readNonnegativeVarLong(buffer, "lifetime limit"),
                readNonnegativeVarLong(buffer, "period limit"),
                readNonnegativeVarLong(buffer, "period seconds"),
                readNonnegativeVarLong(buffer, "cooldown"));
    }

    private static void encodeSchedule(
            FriendlyByteBuf buffer,
            OfferSchedule schedule
    ) {
        buffer.writeVarLong(schedule.startsAtEpoch());
        buffer.writeVarLong(schedule.endsAtEpoch());
    }

    private static OfferSchedule decodeSchedule(FriendlyByteBuf buffer) {
        return new OfferSchedule(
                readNonnegativeVarLong(buffer, "schedule start"),
                readNonnegativeVarLong(buffer, "schedule end"));
    }

    private static void encodeComparisons(
            FriendlyByteBuf buffer,
            List<OfferBundleComparison> comparisons
    ) {
        writeCount(buffer, comparisons.size(), MAX_COMPARISONS,
                "comparisons");
        for (OfferBundleComparison comparison : comparisons) {
            writeIdentifier(buffer, comparison.componentId());
            writeIdentifier(buffer, comparison.listingId());
            writeIdentifier(buffer, comparison.optionId());
        }
    }

    private static List<OfferBundleComparison> decodeComparisons(
            FriendlyByteBuf buffer
    ) {
        int count = readCount(buffer, MAX_COMPARISONS, "comparisons");
        List<OfferBundleComparison> comparisons =
                new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            comparisons.add(new OfferBundleComparison(
                    readIdentifier(buffer),
                    readIdentifier(buffer),
                    readIdentifier(buffer)));
        }
        return List.copyOf(comparisons);
    }

    private static void writeCount(
            FriendlyByteBuf buffer,
            int count,
            int maximum,
            String field
    ) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Server shop offer " + field + " count is invalid");
        }
        buffer.writeVarInt(count);
    }

    private static int readCount(
            FriendlyByteBuf buffer,
            int maximum,
            String field
    ) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new DecoderException(
                    "Server shop offer " + field + " count is invalid");
        }
        return count;
    }

    private static void writeIdentifier(
            FriendlyByteBuf buffer,
            String value
    ) {
        buffer.writeUtf(value, MAX_IDENTIFIER);
    }

    private static String readIdentifier(FriendlyByteBuf buffer) {
        return buffer.readUtf(MAX_IDENTIFIER);
    }

    private static void writeText(FriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value, MAX_TEXT);
    }

    private static String readText(FriendlyByteBuf buffer) {
        return buffer.readUtf(MAX_TEXT);
    }

    private static void writeNbt(FriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value, MAX_NBT);
    }

    private static String readNbt(FriendlyByteBuf buffer) {
        return buffer.readUtf(MAX_NBT);
    }

    private static long readNonnegativeVarLong(
            FriendlyByteBuf buffer,
            String field
    ) {
        long value = buffer.readVarLong();
        if (value < 0L) {
            throw new DecoderException(
                    "Server shop offer " + field + " is invalid");
        }
        return value;
    }
}
