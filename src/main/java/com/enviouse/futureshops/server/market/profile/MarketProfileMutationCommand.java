package com.enviouse.futureshops.server.market.profile;

import com.enviouse.futureshops.client.market.MarketModule;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record MarketProfileMutationCommand(
        UUID requestId,
        UUID routeNonce,
        MarketModule module,
        String view,
        long expectedProfileRevision,
        long expectedReplayEpoch,
        MarketProfileMutation mutation
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public MarketProfileMutationCommand {
        requestId = requireId(requestId, "requestId");
        routeNonce = requireId(routeNonce, "routeNonce");
        module = Objects.requireNonNull(module, "module");
        view = requireView(view);
        mutation = Objects.requireNonNull(mutation, "mutation");
        if (module == MarketModule.SHOP
                || expectedProfileRevision < 0L
                || expectedReplayEpoch < 0L
                || mutation.type()
                == MarketProfileMutationType.AUCTION_WATCH
                && module != MarketModule.AUCTION_HOUSE
                || mutation.type()
                != MarketProfileMutationType.AUCTION_WATCH
                && mutation.type()
                != MarketProfileMutationType.NOTIFICATIONS_READ
                && module != MarketModule.BAZAAR
                || mutation.type()
                == MarketProfileMutationType.NOTIFICATIONS_READ
                && !"claims".equals(view)) {
            throw new IllegalArgumentException(
                    "Market profile mutation route is invalid");
        }
    }

    public MarketProfileMutationCommand(UUID requestId, UUID routeNonce,
                                        MarketModule module, String view,
                                        long expectedProfileRevision,
                                        MarketProfileMutation mutation) {
        this(requestId, routeNonce, module, view,
                expectedProfileRevision, 0L, mutation);
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeId(output, requestId);
                writeId(output, routeNonce);
                writeText(output, module.id());
                writeText(output, view);
                output.writeLong(expectedProfileRevision);
                output.writeLong(expectedReplayEpoch);
                writeText(output, mutation.type().name());
                writeMutation(output, mutation);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market profile mutation fingerprint failed",
                    exception);
        }
    }

    private static void writeMutation(
            DataOutputStream output,
            MarketProfileMutation mutation
    ) throws IOException {
        if (mutation instanceof MarketProfileMutation.AuctionWatch value) {
            writeId(output, value.listingId());
            output.writeBoolean(value.watched());
        } else if (mutation instanceof
                MarketProfileMutation.BazaarFavorite value) {
            writeProduct(output, value.product());
            output.writeBoolean(value.favorite());
        } else if (mutation instanceof
                MarketProfileMutation.PriceAlertAdd value) {
            writeId(output, value.alertId());
            writeProduct(output, value.product());
            writeText(output, value.direction().name());
            output.writeLong(value.thresholdMinor());
        } else if (mutation instanceof
                MarketProfileMutation.PriceAlertRemove value) {
            writeId(output, value.alertId());
        } else if (mutation instanceof
                MarketProfileMutation.NotificationsRead value) {
            output.writeInt(value.notificationIds().size());
            for (UUID notificationId : value.notificationIds()) {
                writeId(output, notificationId);
            }
        } else {
            throw new IllegalArgumentException(
                    "Market profile mutation type is invalid");
        }
    }

    private static void writeProduct(
            DataOutputStream output,
            MarketProfileSavedData.ProductKey product
    ) throws IOException {
        writeText(output, product.productId());
        output.writeLong(product.version());
    }

    private static void writeId(
            DataOutputStream output,
            UUID value
    ) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static UUID requireId(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market profile mutation identity is invalid");
        }
        return result;
    }

    private static String requireView(String value) {
        String result = Objects.requireNonNull(value, "view").strip()
                .toLowerCase(Locale.ROOT);
        if (!result.equals(value) || result.isEmpty()
                || result.length() > 32
                || !result.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Market profile mutation view is invalid");
        }
        return result;
    }
}
