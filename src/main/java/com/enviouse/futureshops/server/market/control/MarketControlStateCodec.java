package com.enviouse.futureshops.server.market.control;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MarketControlStateCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_ENCODED_BYTES = 67_108_864;

    private static final int MAGIC = 0x46534D43;
    private static final int DIGEST_BYTES = 32;

    private MarketControlStateCodec() {
    }

    public static byte[] encode(MarketControlState state) {
        Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_SCHEMA);
            output.writeLong(state.globalRevision());
            output.writeInt(MarketControlModule.values().length);
            for (MarketControlModule module
                    : MarketControlModule.values()) {
                MarketControlBinarySupport.writeModule(output,
                        state.module(module));
            }
            output.writeInt(state.auditEntries().size());
            for (MarketControlAuditEntry entry
                    : state.auditEntries()) {
                MarketControlBinarySupport.writeAudit(output, entry);
            }
            output.flush();
            return appendDigest(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode market control state", exception);
        }
    }

    public static MarketControlState decode(byte[] encoded) {
        byte[] copy = requireAndVerify(encoded);
        byte[] payload = Arrays.copyOf(copy,
                copy.length - DIGEST_BYTES);
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) {
                throw invalid("Market control state magic is invalid");
            }
            if (input.readInt() != CURRENT_SCHEMA) {
                throw invalid(
                        "Market control state schema is unsupported");
            }
            long revision = input.readLong();
            int moduleCount = input.readInt();
            if (moduleCount != MarketControlModule.values().length) {
                throw invalid(
                        "Market control module count is invalid");
            }
            EnumMap<MarketControlModule, MarketModuleControl> modules =
                    new EnumMap<>(MarketControlModule.class);
            for (int index = 0; index < moduleCount; index++) {
                MarketModuleControl module =
                        MarketControlBinarySupport.readModule(input);
                if (modules.putIfAbsent(module.module(), module)
                        != null) {
                    throw invalid(
                            "Market control module is duplicated");
                }
            }
            int auditCount = input.readInt();
            if (auditCount < 0
                    || auditCount > MarketControlState.MAX_AUDIT_ENTRIES) {
                throw invalid(
                        "Market control audit count is invalid");
            }
            java.util.ArrayList<MarketControlAuditEntry> audits =
                    new java.util.ArrayList<>(auditCount);
            Map<java.util.UUID, MarketControlRequestReceipt> receipts =
                    new HashMap<>();
            for (int index = 0; index < auditCount; index++) {
                MarketControlAuditEntry entry =
                        MarketControlBinarySupport.readAudit(input);
                audits.add(entry);
                if (receipts.putIfAbsent(entry.requestId(),
                        new MarketControlRequestReceipt(
                                entry.requestFingerprint(), entry))
                        != null) {
                    throw invalid(
                            "Market control request is duplicated");
                }
            }
            if (input.read() != -1) {
                throw invalid(
                        "Market control state has trailing data");
            }
            MarketControlState state = new MarketControlState(revision,
                    modules, receipts, List.copyOf(audits));
            if (!Arrays.equals(copy, encode(state))) {
                throw invalid(
                        "Market control state encoding is not canonical");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Market control state is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Market control state is invalid", exception);
        }
    }

    public static String fingerprint(MarketControlState state) {
        return HexFormat.of().formatHex(digest(encode(state)));
    }

    private static byte[] appendDigest(byte[] payload) {
        int total = Math.addExact(payload.length, DIGEST_BYTES);
        if (payload.length == 0 || total > MAX_ENCODED_BYTES) {
            throw invalid("Market control state size is invalid");
        }
        byte[] encoded = Arrays.copyOf(payload, total);
        byte[] digest = digest(payload);
        System.arraycopy(digest, 0, encoded, payload.length,
                DIGEST_BYTES);
        return encoded;
    }

    private static byte[] requireAndVerify(byte[] encoded) {
        byte[] copy = Objects.requireNonNull(encoded, "encoded").clone();
        if (copy.length <= DIGEST_BYTES
                || copy.length > MAX_ENCODED_BYTES) {
            throw invalid("Market control state size is invalid");
        }
        int payloadLength = copy.length - DIGEST_BYTES;
        byte[] expected = digest(Arrays.copyOf(copy, payloadLength));
        byte[] actual = Arrays.copyOfRange(copy, payloadLength,
                copy.length);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalid("Market control state digest is invalid");
        }
        return copy;
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market control hashing is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
