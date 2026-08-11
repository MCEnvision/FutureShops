package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntentCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public final class BazaarCreateEscrowIntentCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES =
            EscrowJournalEventCodec.MAX_BODY_BYTES - 64;

    private static final int MAGIC = 0x425A494E;

    private BazaarCreateEscrowIntentCodec() {
    }

    public static byte[] encode(BazaarCreateEscrowIntent intent) {
        Objects.requireNonNull(intent, "intent");
        byte[] core = coreBytes(intent);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            BazaarEscrowBinarySupport.writeBytes(output, core,
                    MAX_ENCODED_BYTES);
            output.writeInt(intent.status().ordinal());
            output.writeLong(intent.revision());
            BazaarEscrowBinarySupport.writeText(output,
                    fingerprint(intent));
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar creation intent", exception);
        }
    }

    public static BazaarCreateEscrowIntent decode(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        requireSize(copy);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(copy))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Bazaar creation intent magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Bazaar creation intent schema is unsupported");
            }
            byte[] core = BazaarEscrowBinarySupport.readBytes(input,
                    MAX_ENCODED_BYTES);
            BazaarCreateEscrowIntent.Status status =
                    BazaarEscrowBinarySupport.readEnum(input,
                            BazaarCreateEscrowIntent.Status.values(),
                            "creation intent status");
            long revision = input.readLong();
            String storedFingerprint = BazaarEscrowBinarySupport.readText(
                    input, 64, false);
            if (input.read() != -1) {
                throw invalid(
                        "Bazaar creation intent has trailing data");
            }
            Core decoded = decodeCore(core);
            BazaarCreateEscrowIntent result = new BazaarCreateEscrowIntent(
                    decoded.command(), decoded.buyFunding(),
                    decoded.itemIntent(), decoded.sellCustody(),
                    decoded.currencyId(), decoded.preparedAt(), status,
                    revision);
            if (!storedFingerprint.equals(fingerprint(result))
                    || !Arrays.equals(copy, encode(result))) {
                throw invalid(
                        "Bazaar creation intent encoding is not canonical");
            }
            return result;
        } catch (EOFException exception) {
            throw invalid("Bazaar creation intent is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw invalid("Bazaar creation intent is invalid", exception);
        }
    }

    static String fingerprint(BazaarCreateEscrowIntent intent) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(coreBytes(intent)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }

    private static byte[] coreBytes(BazaarCreateEscrowIntent intent) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            BazaarEscrowBinarySupport.writeCommand(output,
                    intent.command());
            output.writeBoolean(intent.buyFunding().isPresent());
            if (intent.buyFunding().isPresent()) {
                BazaarEscrowBinarySupport.writeFunding(output,
                        intent.buyFunding().orElseThrow());
            }
            output.writeBoolean(intent.itemMutationIntent().isPresent());
            if (intent.itemMutationIntent().isPresent()) {
                BazaarEscrowBinarySupport.writeBytes(output,
                        ItemInventoryMutationIntentCodec.encode(
                                intent.itemMutationIntent().orElseThrow()),
                        ItemInventoryMutationIntentCodec.MAX_ENCODED_BYTES);
            }
            output.writeBoolean(intent.sellCustody().isPresent());
            if (intent.sellCustody().isPresent()) {
                BazaarEscrowBinarySupport.writeCustody(output,
                        intent.sellCustody().orElseThrow());
            }
            BazaarEscrowBinarySupport.writeText(output,
                    intent.currencyId());
            BazaarEscrowBinarySupport.writeInstant(output,
                    intent.preparedAt());
            output.flush();
            byte[] result = bytes.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode Bazaar creation intent core",
                    exception);
        }
    }

    private static Core decodeCore(byte[] encoded) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(encoded))) {
            CreateBazaarOrderCommand command =
                    BazaarEscrowBinarySupport.readCommand(input);
            Optional<BazaarBuyFundingEvidence> funding =
                    input.readBoolean()
                            ? Optional.of(BazaarEscrowBinarySupport
                            .readFunding(input)) : Optional.empty();
            Optional<ItemInventoryMutationIntent> itemIntent =
                    input.readBoolean()
                            ? Optional.of(ItemInventoryMutationIntentCodec
                            .decode(BazaarEscrowBinarySupport.readBytes(
                                    input,
                                    ItemInventoryMutationIntentCodec
                                            .MAX_ENCODED_BYTES)))
                            : Optional.empty();
            Optional<BazaarSellItemCustody> custody = input.readBoolean()
                    ? Optional.of(BazaarEscrowBinarySupport
                    .readCustody(input)) : Optional.empty();
            String currencyId = BazaarEscrowBinarySupport.readText(input,
                    BazaarBuyFundingEvidence.MAX_CURRENCY_ID_LENGTH,
                    false);
            Instant preparedAt = BazaarEscrowBinarySupport.readInstant(
                    input);
            if (input.read() != -1) {
                throw invalid(
                        "Bazaar creation intent core has trailing data");
            }
            return new Core(command, funding, itemIntent, custody,
                    currencyId, preparedAt);
        }
    }

    private static void requireSize(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw invalid("Bazaar creation intent size is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(message, cause);
    }

    private record Core(
            CreateBazaarOrderCommand command,
            Optional<BazaarBuyFundingEvidence> buyFunding,
            Optional<ItemInventoryMutationIntent> itemIntent,
            Optional<BazaarSellItemCustody> sellCustody,
            String currencyId,
            Instant preparedAt
    ) {
    }
}
