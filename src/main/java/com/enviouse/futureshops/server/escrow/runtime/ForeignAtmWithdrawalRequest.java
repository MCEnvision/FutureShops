package com.enviouse.futureshops.server.escrow.runtime;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record ForeignAtmWithdrawalRequest(
        UUID requestId,
        UUID playerId,
        String providerId,
        String currencySignature,
        List<ForeignAtmStackSelection> stacks,
        Instant requestedAt
) {
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

    public ForeignAtmWithdrawalRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        providerId = Objects.requireNonNull(providerId, "providerId").strip();
        currencySignature = Objects.requireNonNull(
                currencySignature, "currencySignature");
        stacks = canonicalStacks(stacks);
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (providerId.isEmpty() || providerId.length()
                > ForeignCashClaimPayload.MAX_PROVIDER_ID_LENGTH
                || providerId.equalsIgnoreCase("futureshops")
                || !SIGNATURE.matcher(currencySignature).matches()
                || stacks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal request identity is invalid");
        }
        try {
            validateGroups(stacks);
            validatePayloadBudget(providerId, currencySignature, stacks);
            if (billCount(stacks)
                    > ForeignAtmWithdrawalCommit.MAX_TOTAL_STACK_COUNT
                    || amount(stacks) <= 0L) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal request exceeds its limit");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal request arithmetic overflow",
                    exception);
        }
    }

    public long amountMinorUnits() {
        return amount(stacks);
    }

    public int billCount() {
        return billCount(stacks);
    }

    public String fingerprint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(1);
            BinaryCodecSupport.writeUuid(output, requestId);
            BinaryCodecSupport.writeUuid(output, playerId);
            BinaryCodecSupport.writeString(output, providerId,
                    ForeignCashClaimPayload.MAX_PROVIDER_ID_LENGTH * 4);
            BinaryCodecSupport.writeString(output, currencySignature,
                    ForeignCashClaimPayload.CONFIG_SIGNATURE_LENGTH);
            output.writeInt(stacks.size());
            for (ForeignAtmStackSelection stack : stacks) {
                output.writeInt(stack.denominationIndex());
                BinaryCodecSupport.writeString(output,
                        stack.registryItemId(),
                        ForeignCashClaimPayload.MAX_REGISTRY_ITEM_ID_LENGTH * 4);
                output.writeLong(stack.denominationMinorUnits());
                output.writeInt(stack.stackCount());
                output.writeInt(stack.portionIndex());
                output.writeInt(stack.portionCount());
                byte[] nbt = stack.serializedItemStackNbt();
                output.writeInt(nbt.length);
                output.write(nbt);
            }
            output.flush();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint foreign ATM request", exception);
        }
    }

    public ForeignAtmWithdrawalRequest at(Instant instant) {
        return new ForeignAtmWithdrawalRequest(
                requestId, playerId, providerId, currencySignature,
                stacks, instant);
    }

    private static List<ForeignAtmStackSelection> canonicalStacks(
            List<ForeignAtmStackSelection> values
    ) {
        Objects.requireNonNull(values, "stacks");
        if (values.size()
                > ForeignAtmWithdrawalCommit.MAX_CASH_CLAIMS) {
            throw new IllegalArgumentException(
                    "Foreign ATM withdrawal has too many stack portions");
        }
        List<ForeignAtmStackSelection> ordered = new ArrayList<>(values);
        ordered.forEach(value -> Objects.requireNonNull(value, "stack"));
        ordered.sort(Comparator
                .comparingInt(ForeignAtmStackSelection::denominationIndex)
                .thenComparingInt(ForeignAtmStackSelection::portionIndex));
        return List.copyOf(ordered);
    }

    private static void validatePayloadBudget(
            String providerId,
            String currencySignature,
            List<ForeignAtmStackSelection> stacks
    ) {
        long encodedBytes = 0L;
        for (ForeignAtmStackSelection stack : stacks) {
            ForeignCashClaimPayload payload =
                    ForeignCashClaimPayload.capture(
                            providerId, currencySignature,
                            stack.registryItemId(),
                            stack.denominationMinorUnits(),
                            stack.stackCount(),
                            stack.denominationIndex(),
                            stack.portionIndex(),
                            stack.portionCount(),
                            stack.serializedItemStackNbt());
            encodedBytes = Math.addExact(encodedBytes,
                    ForeignCashClaimPayloadCodec.encode(payload).length);
            if (encodedBytes
                    > ForeignAtmWithdrawalCommit
                    .MAX_TOTAL_CLAIM_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "Foreign ATM withdrawal payload exceeds its limit");
            }
        }
    }

    private static void validateGroups(
            List<ForeignAtmStackSelection> stacks
    ) {
        Map<Integer, ForeignAtmStackSelection> definitions = new HashMap<>();
        Map<Integer, Set<Integer>> portions = new HashMap<>();
        Map<Integer, Integer> portionCounts = new HashMap<>();
        Map<String, Integer> itemIndexes = new HashMap<>();
        for (ForeignAtmStackSelection stack : stacks) {
            ForeignAtmStackSelection prior = definitions.putIfAbsent(
                    stack.denominationIndex(), stack);
            if (prior != null
                    && (!prior.registryItemId().equals(stack.registryItemId())
                    || prior.denominationMinorUnits()
                    != stack.denominationMinorUnits()
                    || prior.portionCount() != stack.portionCount())) {
                throw new IllegalArgumentException(
                        "Foreign ATM denomination changed across portions");
            }
            Integer priorItemIndex = itemIndexes.putIfAbsent(
                    stack.registryItemId(), stack.denominationIndex());
            if (priorItemIndex != null
                    && priorItemIndex != stack.denominationIndex()) {
                throw new IllegalArgumentException(
                        "Foreign ATM item has multiple denomination indexes");
            }
            if (!portions.computeIfAbsent(
                    stack.denominationIndex(), ignored -> new HashSet<>())
                    .add(stack.portionIndex())) {
                throw new IllegalArgumentException(
                        "Foreign ATM stack portion is duplicated");
            }
            portionCounts.put(stack.denominationIndex(), stack.portionCount());
        }
        for (Map.Entry<Integer, Set<Integer>> entry : portions.entrySet()) {
            int expected = portionCounts.get(entry.getKey());
            if (entry.getValue().size() != expected) {
                throw new IllegalArgumentException(
                        "Foreign ATM stack portions are incomplete");
            }
            for (int index = 0; index < expected; index++) {
                if (!entry.getValue().contains(index)) {
                    throw new IllegalArgumentException(
                            "Foreign ATM stack portions are not contiguous");
                }
            }
        }
    }

    private static int billCount(List<ForeignAtmStackSelection> stacks) {
        int total = 0;
        for (ForeignAtmStackSelection stack : stacks) {
            total = Math.addExact(total, stack.stackCount());
        }
        return total;
    }

    private static long amount(List<ForeignAtmStackSelection> stacks) {
        long total = 0L;
        for (ForeignAtmStackSelection stack : stacks) {
            total = Math.addExact(total, Math.multiplyExact(
                    stack.denominationMinorUnits(),
                    (long) stack.stackCount()));
        }
        return total;
    }
}
