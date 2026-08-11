package com.enviouse.futureshops.server.market.claim;

import com.enviouse.futureshops.client.market.MarketModule;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public record MarketClaimCollectionCommand(
        UUID requestId,
        UUID routeNonce,
        MarketModule module,
        String view,
        UUID claimId
) {
    public static final String CLAIMS_VIEW = "claims";
    private static final UUID ZERO = new UUID(0L, 0L);

    public MarketClaimCollectionCommand {
        requestId = requireId(requestId, "requestId");
        routeNonce = requireId(routeNonce, "routeNonce");
        module = Objects.requireNonNull(module, "module");
        view = Objects.requireNonNull(view, "view");
        claimId = requireId(claimId, "claimId");
        if (!CLAIMS_VIEW.equals(view)) {
            throw new IllegalArgumentException(
                    "Market claim collection view is invalid");
        }
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeId(output, requestId);
                writeId(output, routeNonce);
                writeText(output, module.id());
                writeText(output, view);
                writeId(output, claimId);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market claim collection fingerprint failed",
                    exception);
        }
    }

    private static UUID requireId(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market claim collection identity is invalid");
        }
        return result;
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
}
