package com.enviouse.futureshops.catalog.offer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ServerShopOfferRevision {
    public static final long MAXIMUM_REVISION = 1_000_000_000_000L;

    private ServerShopOfferRevision() {
    }

    public static long compute(ServerShopOfferListing listing) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeText(output, "futureshops server shop offer revision 2");
            writeListing(output, listing);
            output.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray());
            long value = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                value = value << 8 | digest[index] & 0xffL;
            }
            return Long.remainderUnsigned(value, MAXIMUM_REVISION + 1L);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Offer revision encoding failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Offer revision hashing is unavailable", exception);
        }
    }

    private static void writeListing(
            DataOutputStream output,
            ServerShopOfferListing listing
    ) throws IOException {
        writeText(output, listing.listingId());
        writeText(output, listing.displayName());
        writeText(output, listing.description());
        writeText(output, listing.categoryId());
        writeText(output, listing.iconItemId());
        writeText(output, listing.iconNbt());
        output.writeBoolean(listing.active());
        output.writeLong(listing.expiresAtEpoch());
        writeText(output, listing.permissionNode());
        writeComponents(output, listing.outputs());
        output.writeInt(listing.acquireOptions().size());
        for (AcquireOfferOption option : listing.acquireOptions()) {
            writeAcquire(output, option);
        }
        output.writeInt(listing.sellOptions().size());
        for (SellOfferOption option : listing.sellOptions()) {
            writeSell(output, option);
        }
        output.writeInt(listing.stockPolicy().type().ordinal());
        output.writeLong(listing.stockPolicy().quantity());
        output.writeLong(listing.stockPolicy().refreshSeconds());
        writeLimits(output, listing.limits());
        writeSchedule(output, listing.schedule());
        output.writeInt(listing.bundleComparisons().size());
        for (OfferBundleComparison comparison :
                listing.bundleComparisons()) {
            writeText(output, comparison.componentId());
            writeText(output, comparison.listingId());
            writeText(output, comparison.optionId());
        }
    }

    private static void writeAcquire(
            DataOutputStream output,
            AcquireOfferOption option
    ) throws IOException {
        writeText(output, option.optionId());
        writeText(output, option.label());
        output.writeBoolean(option.free());
        output.writeBoolean(option.moneyCostPresent());
        output.writeLong(option.moneyCostMinorUnits());
        writeComponents(output, option.itemCosts());
        output.writeInt(option.outputMultiplier());
        writeLimits(output, option.limits());
        writeSchedule(output, option.schedule());
        writeText(output, option.permissionNode());
    }

    private static void writeSell(
            DataOutputStream output,
            SellOfferOption option
    ) throws IOException {
        writeText(output, option.optionId());
        writeText(output, option.label());
        writeComponents(output, option.itemInputs());
        output.writeLong(option.moneyPayoutMinorUnits());
        output.writeLong(option.capacity());
        writeLimits(output, option.limits());
        writeSchedule(output, option.schedule());
        writeText(output, option.permissionNode());
    }

    private static void writeComponents(
            DataOutputStream output,
            java.util.List<OfferItemComponent> components
    ) throws IOException {
        output.writeInt(components.size());
        for (OfferItemComponent component : components) {
            writeText(output, component.componentId());
            writeText(output, component.itemId());
            output.writeInt(component.count());
            writeText(output, component.exactNbt());
        }
    }

    private static void writeLimits(
            DataOutputStream output,
            OfferLimitPolicy limits
    ) throws IOException {
        output.writeInt(limits.maximumPerRequest());
        output.writeLong(limits.lifetimeLimit());
        output.writeLong(limits.periodLimit());
        output.writeLong(limits.periodSeconds());
        output.writeLong(limits.cooldownSeconds());
    }

    private static void writeSchedule(
            DataOutputStream output,
            OfferSchedule schedule
    ) throws IOException {
        output.writeLong(schedule.startsAtEpoch());
        output.writeLong(schedule.endsAtEpoch());
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
