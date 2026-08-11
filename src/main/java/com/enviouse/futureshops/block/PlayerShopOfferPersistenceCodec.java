package com.enviouse.futureshops.block;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class PlayerShopOfferPersistenceCodec {
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_ENCODED_BYTES = 4_194_304;

    private static final int MAGIC = 0x4653504F;
    private static final int HEADER_BYTES = Integer.BYTES * 3;

    private PlayerShopOfferPersistenceCodec() {
    }

    public static byte[] encode(ServerShopOfferListing offer) {
        Objects.requireNonNull(offer, "offer");
        byte[] payload = ServerShopOfferNetworkCodec
                .encodeListingBytes(offer);
        requirePayloadSize(payload.length);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                    Math.addExact(HEADER_BYTES, payload.length));
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode player shop offer", exception);
        }
    }

    public static ServerShopOfferListing decode(
            int expectedSchema,
            byte[] encoded
    ) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length <= HEADER_BYTES
                || copy.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Player shop offer payload size is invalid");
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException(
                        "Player shop offer payload magic is invalid");
            }
            int schema = input.readInt();
            if (schema != expectedSchema
                    || schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        "Player shop offer payload schema is unsupported");
            }
            int length = input.readInt();
            requirePayloadSize(length);
            if (length != input.available()) {
                throw new IllegalArgumentException(
                        "Player shop offer payload length is invalid");
            }
            byte[] payload = input.readNBytes(length);
            if (payload.length != length || input.available() != 0) {
                throw new IllegalArgumentException(
                        "Player shop offer payload is truncated");
            }
            ServerShopOfferListing offer =
                    ServerShopOfferNetworkCodec.decodeListingBytes(payload);
            if (!Arrays.equals(payload,
                    ServerShopOfferNetworkCodec.encodeListingBytes(offer))) {
                throw new IllegalArgumentException(
                        "Player shop offer payload is not canonical");
            }
            return offer;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Player shop offer payload is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Player shop offer payload is invalid", exception);
        }
    }

    private static void requirePayloadSize(int length) {
        if (length <= 0
                || length > MAX_ENCODED_BYTES - HEADER_BYTES) {
            throw new IllegalArgumentException(
                    "Player shop offer payload exceeds its limit");
        }
    }
}
