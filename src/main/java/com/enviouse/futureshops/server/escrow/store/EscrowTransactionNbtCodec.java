package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.DimensionAwareShopReference;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowPartyType;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowRetryMetadata;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTimestamps;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class EscrowTransactionNbtCodec {
    public static final int CURRENT_SCHEMA = 1;

    private EscrowTransactionNbtCodec() {
    }

    public static CompoundTag encode(EscrowTransaction transaction) {
        validateBounds(transaction);
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", CURRENT_SCHEMA);
        tag.putUUID("transaction_id", transaction.transactionId().value());
        transaction.parentTransactionId().ifPresent(value -> tag.putUUID("parent_transaction_id", value.value()));
        tag.putString("request_key", transaction.requestKey().value());
        tag.putString("operation", transaction.operation().name());
        tag.putString("state", transaction.state().name());
        tag.put("participants", writeParticipants(transaction.participants()));
        tag.put("asset_lots", writeAssetLots(transaction.assetLots()));
        tag.put("timestamps", writeTimestamps(transaction.timestamps()));
        tag.putLong("revision", transaction.revision());
        tag.putLong("config_revision", transaction.configRevision());
        transaction.lastError().ifPresent(value -> tag.put("last_error", writeError(value)));
        tag.put("retry", writeRetry(transaction.retryMetadata()));
        transaction.shopReference().ifPresent(value -> tag.put("shop_reference", writeShopReference(value)));
        return tag;
    }

    public static EscrowTransaction decode(CompoundTag tag) {
        if (tag == null) {
            throw new IllegalStateException("Escrow transaction tag is missing");
        }
        try {
            requireType(tag, "schema", Tag.TAG_INT);
            int schema = tag.getInt("schema");
            if (schema > CURRENT_SCHEMA) {
                throw new IllegalStateException("Escrow transaction schema is newer than this build");
            }
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalStateException("Escrow transaction schema is unsupported");
            }
            EscrowTransaction transaction = new EscrowTransaction(
                    new EscrowTransactionId(readUuid(tag, "transaction_id")),
                    readOptionalUuid(tag, "parent_transaction_id").map(EscrowTransactionId::new),
                    new EscrowRequestKey(readString(tag, "request_key", EscrowRequestKey.MAX_LENGTH)),
                    readEnum(tag, "operation", EscrowOperation.class),
                    readEnum(tag, "state", EscrowState.class),
                    readParticipants(readCompoundList(tag, "participants", EscrowCodecLimits.MAX_PARTICIPANTS)),
                    readAssetLots(readCompoundList(tag, "asset_lots", EscrowCodecLimits.MAX_ASSET_LOTS)),
                    readTimestamps(readCompound(tag, "timestamps")),
                    readLong(tag, "revision"),
                    readLong(tag, "config_revision"),
                    readOptionalCompound(tag, "last_error").map(EscrowTransactionNbtCodec::readError),
                    readRetry(readCompound(tag, "retry")),
                    readOptionalCompound(tag, "shop_reference").map(EscrowTransactionNbtCodec::readShopReference)
            );
            validateBounds(transaction);
            return transaction;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Escrow transaction tag is invalid", exception);
        }
    }

    static void validateBounds(EscrowTransaction transaction) {
        if (transaction == null) {
            throw new IllegalStateException("Escrow transaction is missing");
        }
        EscrowCodecLimits.requireCount("Escrow participants", transaction.participants().size(),
                EscrowCodecLimits.MAX_PARTICIPANTS);
        EscrowCodecLimits.requireCount("Escrow asset lots", transaction.assetLots().size(),
                EscrowCodecLimits.MAX_ASSET_LOTS);
        int totalPayload = 0;
        for (EscrowParticipant participant : transaction.participants()) {
            EscrowCodecLimits.requireCount("Escrow participant roles", participant.roles().size(),
                    EscrowParticipantRole.values().length);
            validateParty(participant.party());
        }
        for (EscrowAssetLot lot : transaction.assetLots()) {
            validateParty(lot.source());
            validateParty(lot.destination());
            byte[] payload = EscrowCodecLimits.requirePayload(lot.serializedPayload());
            totalPayload = addBoundedPayload(totalPayload, payload.length);
            validateStringMap(lot.attributes(), EscrowCodecLimits.MAX_ATTRIBUTES, "Escrow asset attributes");
            lot.money().ifPresent(money -> EscrowCodecLimits.requireString(
                    "Escrow currency id", money.currencyId(), MoneyAmount.MAX_CURRENCY_ID_LENGTH));
        }
        transaction.lastError().ifPresent(error -> {
            EscrowCodecLimits.requireString("Escrow error code", error.code(), EscrowError.MAX_CODE_LENGTH);
            EscrowCodecLimits.requireString("Escrow error message", error.message(), EscrowError.MAX_MESSAGE_LENGTH);
            validateStringMap(error.details(), EscrowCodecLimits.MAX_ERROR_DETAILS, "Escrow error details");
        });
        transaction.shopReference().ifPresent(reference -> {
            EscrowCodecLimits.requireString("Escrow shop id", reference.shopId(),
                    DimensionAwareShopReference.MAX_SHOP_ID_LENGTH);
            EscrowCodecLimits.requireString("Escrow dimension id", reference.dimensionId(),
                    DimensionAwareShopReference.MAX_DIMENSION_ID_LENGTH);
        });
    }

    private static ListTag writeParticipants(Set<EscrowParticipant> participants) {
        List<EscrowParticipant> ordered = new ArrayList<>(participants);
        ordered.sort(Comparator.comparing((EscrowParticipant value) -> value.party().type().name())
                .thenComparing(value -> value.party().id()));
        ListTag tags = new ListTag();
        for (EscrowParticipant participant : ordered) {
            CompoundTag value = new CompoundTag();
            value.put("party", writeParty(participant.party()));
            ListTag roles = new ListTag();
            participant.roles().stream().map(Enum::name).sorted().map(StringTag::valueOf).forEach(roles::add);
            value.put("roles", roles);
            tags.add(value);
        }
        return tags;
    }

    private static Set<EscrowParticipant> readParticipants(ListTag tags) {
        Set<EscrowParticipant> participants = new LinkedHashSet<>();
        for (Tag raw : tags) {
            CompoundTag tag = requireCompoundElement(raw, "Escrow participant");
            EscrowParty party = readParty(readCompound(tag, "party"));
            ListTag roleTags = readStringList(tag, "roles", EscrowParticipantRole.values().length);
            Set<EscrowParticipantRole> roles = new LinkedHashSet<>();
            for (Tag roleTag : roleTags) {
                EscrowParticipantRole role = parseEnum(roleTag.getAsString(), EscrowParticipantRole.class,
                        "Escrow participant role");
                if (!roles.add(role)) {
                    throw new IllegalStateException("Duplicate escrow participant role");
                }
            }
            if (!participants.add(new EscrowParticipant(party, roles))) {
                throw new IllegalStateException("Duplicate escrow participant");
            }
        }
        return participants;
    }

    private static ListTag writeAssetLots(List<EscrowAssetLot> lots) {
        ListTag tags = new ListTag();
        for (EscrowAssetLot lot : lots) {
            CompoundTag value = new CompoundTag();
            value.putUUID("lot_id", lot.lotId());
            value.putString("type", lot.type().name());
            value.putString("protection", lot.protectionLevel().name());
            value.put("source", writeParty(lot.source()));
            value.put("destination", writeParty(lot.destination()));
            value.putLong("quantity", lot.quantity());
            lot.money().ifPresent(money -> value.put("money", writeMoney(money)));
            value.putByteArray("payload", lot.serializedPayload());
            value.put("attributes", writeStringMap(lot.attributes()));
            tags.add(value);
        }
        return tags;
    }

    private static List<EscrowAssetLot> readAssetLots(ListTag tags) {
        List<EscrowAssetLot> lots = new ArrayList<>(tags.size());
        int totalPayload = 0;
        for (Tag raw : tags) {
            CompoundTag tag = requireCompoundElement(raw, "Escrow asset lot");
            byte[] payload = readByteArray(tag, "payload", EscrowCodecLimits.MAX_PAYLOAD_BYTES);
            totalPayload = addBoundedPayload(totalPayload, payload.length);
            lots.add(new EscrowAssetLot(
                    readUuid(tag, "lot_id"),
                    readEnum(tag, "type", EscrowAssetLotType.class),
                    readEnum(tag, "protection", EscrowProtectionLevel.class),
                    readParty(readCompound(tag, "source")),
                    readParty(readCompound(tag, "destination")),
                    readLong(tag, "quantity"),
                    readOptionalCompound(tag, "money").map(EscrowTransactionNbtCodec::readMoney),
                    payload,
                    readStringMap(readCompoundList(tag, "attributes", EscrowCodecLimits.MAX_ATTRIBUTES),
                            "Escrow asset attributes")
            ));
        }
        return List.copyOf(lots);
    }

    private static CompoundTag writeParty(EscrowParty party) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", party.type().name());
        tag.putString("id", party.id());
        return tag;
    }

    private static EscrowParty readParty(CompoundTag tag) {
        return new EscrowParty(
                readEnum(tag, "type", EscrowPartyType.class),
                readString(tag, "id", EscrowParty.MAX_ID_LENGTH));
    }

    private static CompoundTag writeMoney(MoneyAmount money) {
        CompoundTag tag = new CompoundTag();
        tag.putString("currency", money.currencyId());
        tag.putLong("minor_units", money.minorUnits());
        return tag;
    }

    private static MoneyAmount readMoney(CompoundTag tag) {
        return new MoneyAmount(
                readString(tag, "currency", MoneyAmount.MAX_CURRENCY_ID_LENGTH),
                readLong(tag, "minor_units"));
    }

    private static CompoundTag writeTimestamps(EscrowTimestamps timestamps) {
        CompoundTag tag = new CompoundTag();
        tag.put("created", writeInstant(timestamps.createdAt()));
        tag.put("updated", writeInstant(timestamps.updatedAt()));
        timestamps.commitDecidedAt().ifPresent(value -> tag.put("commit_decided", writeInstant(value)));
        timestamps.terminalAt().ifPresent(value -> tag.put("terminal", writeInstant(value)));
        return tag;
    }

    private static EscrowTimestamps readTimestamps(CompoundTag tag) {
        return new EscrowTimestamps(
                readInstant(readCompound(tag, "created")),
                readInstant(readCompound(tag, "updated")),
                readOptionalCompound(tag, "commit_decided").map(EscrowTransactionNbtCodec::readInstant),
                readOptionalCompound(tag, "terminal").map(EscrowTransactionNbtCodec::readInstant));
    }

    private static CompoundTag writeInstant(Instant instant) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("seconds", instant.getEpochSecond());
        tag.putInt("nanos", instant.getNano());
        return tag;
    }

    private static Instant readInstant(CompoundTag tag) {
        long seconds = readLong(tag, "seconds");
        int nanos = readInt(tag, "nanos");
        if (nanos < 0 || nanos > 999_999_999) {
            throw new IllegalStateException("Escrow timestamp nanoseconds are invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw new IllegalStateException("Escrow timestamp is invalid", exception);
        }
    }

    private static CompoundTag writeError(EscrowError error) {
        CompoundTag tag = new CompoundTag();
        tag.putString("code", error.code());
        tag.putString("message", error.message());
        tag.putBoolean("retryable", error.retryable());
        tag.put("occurred", writeInstant(error.occurredAt()));
        tag.put("details", writeStringMap(error.details()));
        return tag;
    }

    private static EscrowError readError(CompoundTag tag) {
        return new EscrowError(
                readString(tag, "code", EscrowError.MAX_CODE_LENGTH),
                readString(tag, "message", EscrowError.MAX_MESSAGE_LENGTH),
                readBoolean(tag, "retryable"),
                readInstant(readCompound(tag, "occurred")),
                readStringMap(readCompoundList(tag, "details", EscrowCodecLimits.MAX_ERROR_DETAILS),
                        "Escrow error details"));
    }

    private static CompoundTag writeRetry(EscrowRetryMetadata retry) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("attempt_count", retry.attemptCount());
        tag.putInt("max_attempts", retry.maxAttempts());
        retry.nextAttemptAt().ifPresent(value -> tag.put("next_attempt", writeInstant(value)));
        retry.resumeState().ifPresent(value -> tag.putString("resume_state", value.name()));
        return tag;
    }

    private static EscrowRetryMetadata readRetry(CompoundTag tag) {
        Optional<Instant> nextAttempt = readOptionalCompound(tag, "next_attempt")
                .map(EscrowTransactionNbtCodec::readInstant);
        Optional<EscrowState> resumeState = Optional.empty();
        if (tag.contains("resume_state")) {
            resumeState = Optional.of(readEnum(tag, "resume_state", EscrowState.class));
        }
        return new EscrowRetryMetadata(
                readInt(tag, "attempt_count"),
                readInt(tag, "max_attempts"),
                nextAttempt,
                resumeState);
    }

    private static CompoundTag writeShopReference(DimensionAwareShopReference reference) {
        CompoundTag tag = new CompoundTag();
        tag.putString("shop_id", reference.shopId());
        tag.putString("dimension_id", reference.dimensionId());
        tag.putInt("x", reference.blockX());
        tag.putInt("y", reference.blockY());
        tag.putInt("z", reference.blockZ());
        return tag;
    }

    private static DimensionAwareShopReference readShopReference(CompoundTag tag) {
        return new DimensionAwareShopReference(
                readString(tag, "shop_id", DimensionAwareShopReference.MAX_SHOP_ID_LENGTH),
                readString(tag, "dimension_id", DimensionAwareShopReference.MAX_DIMENSION_ID_LENGTH),
                readInt(tag, "x"),
                readInt(tag, "y"),
                readInt(tag, "z"));
    }

    private static ListTag writeStringMap(Map<String, String> values) {
        ListTag tags = new ListTag();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", entry.getKey());
            tag.putString("value", entry.getValue());
            tags.add(tag);
        }
        return tags;
    }

    private static Map<String, String> readStringMap(ListTag tags, String field) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Tag raw : tags) {
            CompoundTag tag = requireCompoundElement(raw, field);
            String key = readString(tag, "key", EscrowCodecLimits.MAX_MAP_KEY_LENGTH);
            String value = readString(tag, "value", EscrowCodecLimits.MAX_MAP_VALUE_LENGTH);
            if (result.put(key, value) != null) {
                throw new IllegalStateException(field + " contain a duplicate key");
            }
        }
        return Map.copyOf(result);
    }

    private static void validateParty(EscrowParty party) {
        EscrowCodecLimits.requireString("Escrow party id", party.id(), EscrowParty.MAX_ID_LENGTH);
    }

    private static void validateStringMap(Map<String, String> values, int maximumSize, String field) {
        EscrowCodecLimits.requireCount(field, values.size(), maximumSize);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            EscrowCodecLimits.requireString(field + " key", entry.getKey(), EscrowCodecLimits.MAX_MAP_KEY_LENGTH);
            EscrowCodecLimits.requireString(field + " value", entry.getValue(), EscrowCodecLimits.MAX_MAP_VALUE_LENGTH);
        }
    }

    private static int addBoundedPayload(int current, int additional) {
        int total;
        try {
            total = Math.addExact(current, additional);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Escrow transaction payload exceeds its limit", exception);
        }
        if (total > EscrowCodecLimits.MAX_TOTAL_PAYLOAD_BYTES) {
            throw new IllegalStateException("Escrow transaction payload exceeds its limit");
        }
        return total;
    }

    private static CompoundTag requireCompoundElement(Tag tag, String field) {
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalStateException(field + " entry is invalid");
        }
        return compound;
    }

    private static CompoundTag readCompound(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_COMPOUND);
        return tag.getCompound(key);
    }

    private static Optional<CompoundTag> readOptionalCompound(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(readCompound(tag, key));
    }

    private static ListTag readCompoundList(CompoundTag tag, String key, int maximumSize) {
        requireType(tag, key, Tag.TAG_LIST);
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException(key + " has an invalid element type");
        }
        EscrowCodecLimits.requireCount(key, list.size(), maximumSize);
        return list;
    }

    private static ListTag readStringList(CompoundTag tag, String key, int maximumSize) {
        requireType(tag, key, Tag.TAG_LIST);
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING) {
            throw new IllegalStateException(key + " has an invalid element type");
        }
        EscrowCodecLimits.requireCount(key, list.size(), maximumSize);
        return list;
    }

    private static String readString(CompoundTag tag, String key, int maximumLength) {
        requireType(tag, key, Tag.TAG_STRING);
        return EscrowCodecLimits.requireString(key, tag.getString(key), maximumLength);
    }

    private static long readLong(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_LONG);
        return tag.getLong(key);
    }

    private static int readInt(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_INT);
        return tag.getInt(key);
    }

    private static boolean readBoolean(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_BYTE);
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) {
            throw new IllegalStateException(key + " is not a boolean");
        }
        return value == 1;
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalStateException(key + " is not a UUID");
        }
        return tag.getUUID(key);
    }

    private static Optional<UUID> readOptionalUuid(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(readUuid(tag, key));
    }

    private static byte[] readByteArray(CompoundTag tag, String key, int maximumLength) {
        requireType(tag, key, Tag.TAG_BYTE_ARRAY);
        byte[] bytes = tag.getByteArray(key);
        if (bytes.length > maximumLength) {
            throw new IllegalStateException(key + " exceeds its limit");
        }
        return bytes;
    }

    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> enumType) {
        return parseEnum(readString(tag, key, 128), enumType, key);
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(field + " is unknown", exception);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalStateException(key + " is missing or has the wrong type");
        }
    }
}
