package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.InternalBillAuthorityRouter;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.MoneyNbtKeys;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.CashDepositMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class ProtectedCashRedemptionSupport {
    static final int MAX_PORTIONS = 37;
    static final int MAX_BATCHES = 37;
    static final int MAX_TOTAL_SNAPSHOT_BYTES = 1_048_576;
    static final int MAX_PLAN_ENCODED_BYTES = 1_200_000;
    static final int HASH_BYTES = 32;
    static final String CURRENCY_ID = "futureshops:wallet";
    static final String LEDGER_REASON = "Protected cash redemption";

    private static final int PLAN_MAGIC = 0x46535052;
    private static final int PLAN_SCHEMA = 1;

    private ProtectedCashRedemptionSupport() {
    }

    static InternalBillInventoryPlanner.ExactPlan canonicalPlan(
            InternalBillInventoryPlanner.ExactPlan plan
    ) {
        Objects.requireNonNull(plan, "plan");
        if (plan.status() != InternalBillInventoryPlanner.PlanStatus.SUCCESS
                || plan.authority() != InternalBillAuthorityRouter.Authority.PROTECTED
                || plan.requestedMinorUnits() <= 0L
                || plan.selectedMinorUnits() != plan.requestedMinorUnits()
                || plan.portions().isEmpty()
                || plan.portions().size() > MAX_PORTIONS) {
            throw new IllegalArgumentException("Protected cash plan is invalid");
        }
        List<InternalBillInventoryPlanner.Portion> ordered = new ArrayList<>(
                plan.portions());
        ordered.forEach(value -> Objects.requireNonNull(value, "portion"));
        ordered.sort(Comparator.comparing(InternalBillInventoryPlanner.Portion::slot));
        InternalBillInventoryPlanner.ExactPlan canonical =
                new InternalBillInventoryPlanner.ExactPlan(
                        InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                        plan.requestedMinorUnits(), plan.selectedMinorUnits(),
                        InternalBillAuthorityRouter.Authority.PROTECTED, ordered);
        analyze(canonical);
        return canonical;
    }

    static PlanFacts analyze(InternalBillInventoryPlanner.ExactPlan plan) {
        if (plan.status() != InternalBillInventoryPlanner.PlanStatus.SUCCESS
                || plan.authority() != InternalBillAuthorityRouter.Authority.PROTECTED
                || plan.requestedMinorUnits() <= 0L
                || plan.selectedMinorUnits() != plan.requestedMinorUnits()
                || plan.portions().isEmpty()
                || plan.portions().size() > MAX_PORTIONS) {
            throw new IllegalArgumentException("Protected cash plan is invalid");
        }
        Map<InternalBillInventoryPlanner.SlotIdentity, BillSnapshot> snapshots =
                new LinkedHashMap<>();
        Map<UUID, MutableBatchFacts> mutableBatches = new LinkedHashMap<>();
        long snapshotBytes = 0L;
        InternalBillInventoryPlanner.SlotIdentity previous = null;
        for (InternalBillInventoryPlanner.Portion portion : plan.portions()) {
            if (portion.authority()
                    != InternalBillAuthorityRouter.Authority.PROTECTED) {
                throw new IllegalArgumentException(
                        "Protected cash plan has mixed authority");
            }
            requireSlot(portion.slot());
            if (previous != null && previous.compareTo(portion.slot()) >= 0) {
                throw new IllegalArgumentException(
                        "Protected cash plan slot ordering is invalid");
            }
            previous = portion.slot();
            if (portion.authorizedCount() > ProtectedMintBatch.MAX_AUTHORIZED_COUNT) {
                throw new IllegalArgumentException(
                        "Protected cash authorization exceeds its limit");
            }
            snapshotBytes = Math.addExact(snapshotBytes,
                    portion.exactStackSnapshot().length);
            if (snapshotBytes > MAX_TOTAL_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash snapshots exceed their total limit");
            }
            BillSnapshot snapshot = decodeBill(portion);
            if (snapshots.put(portion.slot(), snapshot) != null) {
                throw new IllegalArgumentException(
                        "Protected cash plan repeats an inventory slot");
            }
            MutableBatchFacts batch = mutableBatches.get(snapshot.mintId());
            if (batch == null) {
                batch = new MutableBatchFacts(snapshot.mintId(),
                        portion.denominationMinorUnits(), portion.authorizedCount(),
                        snapshot.serverIdentityEvidence(), snapshot.checksumEvidence());
                mutableBatches.put(snapshot.mintId(), batch);
            } else {
                batch.requireSame(portion, snapshot);
            }
            batch.add(portion.selectedCount());
        }
        if (mutableBatches.isEmpty() || mutableBatches.size() > MAX_BATCHES) {
            throw new IllegalArgumentException(
                    "Protected cash mint batch count is invalid");
        }
        Map<UUID, BatchFacts> batches = new LinkedHashMap<>();
        mutableBatches.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(UUID::toString)))
                .forEach(entry -> batches.put(entry.getKey(), entry.getValue().freeze()));
        return new PlanFacts(plan.requestedMinorUnits(), Map.copyOf(snapshots),
                Map.copyOf(batches));
    }

    static byte[] encodePlan(InternalBillInventoryPlanner.ExactPlan supplied) {
        InternalBillInventoryPlanner.ExactPlan plan = canonicalPlan(supplied);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(PLAN_MAGIC);
            output.writeInt(PLAN_SCHEMA);
            output.writeLong(plan.requestedMinorUnits());
            output.writeLong(plan.selectedMinorUnits());
            output.writeInt(plan.authority().ordinal());
            output.writeInt(plan.portions().size());
            for (InternalBillInventoryPlanner.Portion portion : plan.portions()) {
                output.writeInt(portion.slot().container().ordinal());
                output.writeInt(portion.slot().index());
                output.writeInt(portion.authority().ordinal());
                writeString(output, portion.mintId(), 64);
                output.writeLong(portion.denominationMinorUnits());
                output.writeInt(portion.authorizedCount());
                output.writeInt(portion.originalStackCount());
                output.writeInt(portion.selectedCount());
                writeBytes(output, portion.exactStackSnapshot(),
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash stack snapshot");
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_PLAN_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash plan exceeds its binary limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode protected cash plan",
                    exception);
        }
    }

    static InternalBillInventoryPlanner.ExactPlan decodePlan(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_PLAN_ENCODED_BYTES) {
            throw new IllegalArgumentException("Protected cash plan size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != PLAN_MAGIC) {
                throw new IllegalArgumentException(
                        "Protected cash plan magic is invalid");
            }
            int schema = input.readInt();
            if (schema > PLAN_SCHEMA) {
                throw new IllegalStateException(
                        "Protected cash plan schema is newer than this build");
            }
            if (schema != PLAN_SCHEMA) {
                throw new IllegalArgumentException(
                        "Protected cash plan schema is unsupported");
            }
            long requested = input.readLong();
            long selected = input.readLong();
            InternalBillAuthorityRouter.Authority authority = readEnum(
                    input.readInt(), InternalBillAuthorityRouter.Authority.values(),
                    "Protected cash authority");
            int count = readCount(input, MAX_PORTIONS,
                    "Protected cash portion count");
            if (count == 0) {
                throw new IllegalArgumentException(
                        "Protected cash portion count is invalid");
            }
            List<InternalBillInventoryPlanner.Portion> portions =
                    new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                InternalBillInventoryPlanner.Container container = readEnum(
                        input.readInt(), InternalBillInventoryPlanner.Container.values(),
                        "Protected cash inventory container");
                int slotIndex = input.readInt();
                InternalBillAuthorityRouter.Authority portionAuthority = readEnum(
                        input.readInt(), InternalBillAuthorityRouter.Authority.values(),
                        "Protected cash portion authority");
                String mintId = readString(input, bytes, 64,
                        "Protected cash mint ID");
                long denomination = input.readLong();
                int authorizedCount = input.readInt();
                int originalCount = input.readInt();
                int selectedCount = input.readInt();
                byte[] snapshot = readBytes(input, bytes,
                        ItemStackSnapshotCodec.MAXIMUM_BYTES,
                        "Protected cash stack snapshot");
                portions.add(new InternalBillInventoryPlanner.Portion(
                        new InternalBillInventoryPlanner.SlotIdentity(
                                container, slotIndex), portionAuthority, mintId,
                        denomination, authorizedCount, originalCount,
                        selectedCount, snapshot));
            }
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash plan has trailing data");
            }
            InternalBillInventoryPlanner.ExactPlan decoded =
                    new InternalBillInventoryPlanner.ExactPlan(
                            InternalBillInventoryPlanner.PlanStatus.SUCCESS,
                            requested, selected, authority, portions);
            InternalBillInventoryPlanner.ExactPlan canonical = canonicalPlan(decoded);
            if (!decoded.equals(canonical)) {
                throw new IllegalArgumentException(
                        "Protected cash plan ordering is not canonical");
            }
            return canonical;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Protected cash plan is truncated",
                    exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Protected cash plan is invalid",
                    exception);
        }
    }

    static UUID reservationId(UUID playerId,
                              LedgerAccountId destinationAccount,
                              long walletBalanceLimitMinorUnits,
                              CashDepositMode depositMode,
                              byte[] inventoryBeforeHash,
                              EscrowTransaction transaction,
                              InternalBillInventoryPlanner.ExactPlan plan) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        Objects.requireNonNull(depositMode, "depositMode");
        requireHash(inventoryBeforeHash,
                "Protected cash reservation inventory hash");
        Objects.requireNonNull(transaction, "transaction");
        String material = "futureshops protected cash reservation v4 "
                + playerId + " " + transaction.transactionId().value() + " "
                + transaction.requestKey().value() + " "
                + destinationAccount.type().name() + " "
                + destinationAccount.ownerKey() + " "
                + walletBalanceLimitMinorUnits + " "
                + depositMode + " "
                + hex(inventoryBeforeHash) + " "
                + hex(sha256(encodePlan(plan)));
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    static UUID legacyReservationId(
            UUID playerId,
            LedgerAccountId destinationAccount,
            long walletBalanceLimitMinorUnits,
            byte[] inventoryBeforeHash,
            EscrowTransaction transaction,
            InternalBillInventoryPlanner.ExactPlan plan
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destinationAccount, "destinationAccount");
        requireHash(inventoryBeforeHash,
                "Protected cash reservation inventory hash");
        Objects.requireNonNull(transaction, "transaction");
        String material = "futureshops protected cash reservation v3 "
                + playerId + " " + transaction.transactionId().value() + " "
                + transaction.requestKey().value() + " "
                + destinationAccount.type().name() + " "
                + destinationAccount.ownerKey() + " "
                + walletBalanceLimitMinorUnits + " "
                + hex(inventoryBeforeHash) + " "
                + hex(sha256(encodePlan(plan)));
        return UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
    }

    static UUID lotId(UUID transactionId,
                      InternalBillInventoryPlanner.Portion portion) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(portion, "portion");
        String material = "futureshops protected cash lot v1 " + transactionId
                + " " + portion.slot().container().name() + " "
                + portion.slot().index() + " " + portion.mintId();
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    static String reserveLotRequestKey(UUID transactionId,
                                       LedgerAccountId destination,
                                       UUID lotId) {
        return "protected.cash." + transactionId + ".destination."
                + destinationKey(destination) + ".lot." + lotId + ".reserve";
    }

    static String consumeLotRequestKey(UUID transactionId,
                                       LedgerAccountId destination,
                                       UUID lotId) {
        return "protected.cash." + transactionId + ".destination."
                + destinationKey(destination) + ".lot." + lotId + ".consume";
    }

    static String reserveMintRequestKey(UUID transactionId,
                                        LedgerAccountId destination,
                                        UUID batchId) {
        return mintRequestKey(transactionId, destination, batchId,
                "reserve");
    }

    static String commitMintRequestKey(UUID transactionId,
                                       LedgerAccountId destination,
                                       UUID batchId) {
        return mintRequestKey(transactionId, destination, batchId,
                "commit");
    }

    static String releaseLotRequestKey(UUID transactionId,
                                       LedgerAccountId destination,
                                       UUID lotId) {
        return "protected.cash." + transactionId + ".destination."
                + destinationKey(destination) + ".lot." + lotId + ".release";
    }

    static String releaseMintRequestKey(UUID transactionId,
                                        LedgerAccountId destination,
                                        UUID batchId) {
        return mintRequestKey(transactionId, destination, batchId,
                "release");
    }

    static String inventoryRequestKey(UUID transactionId,
                                      LedgerAccountId destination) {
        return "protected.cash." + transactionId + ".destination."
                + destinationKey(destination) + ".inventory.consume";
    }

    static String noMutationProofRequestKey(UUID transactionId,
                                            LedgerAccountId destination) {
        return "protected.cash." + transactionId + ".destination."
                + destinationKey(destination) + ".inventory.no_mutation";
    }

    static UUID inventoryReceiptId(String requestKey) {
        return UUID.nameUUIDFromBytes(("futureshops protected cash inventory "
                + Objects.requireNonNull(requestKey, "requestKey"))
                .getBytes(StandardCharsets.UTF_8));
    }

    static String ledgerIdempotencyKey(UUID transactionId,
                                       LedgerAccountId destination) {
        return "protected.cash." + Objects.requireNonNull(transactionId,
                "transactionId") + ".destination."
                + destinationKey(destination) + ".ledger";
    }

    private static String destinationKey(LedgerAccountId destination) {
        Objects.requireNonNull(destination, "destination");
        String canonical = destination.type().name() + "\u0000"
                + destination.ownerKey();
        return destination.type().name().toLowerCase(java.util.Locale.ROOT)
                + "." + hex(sha256(canonical.getBytes(
                StandardCharsets.UTF_8)));
    }

    private static String mintRequestKey(
            UUID transactionId,
            LedgerAccountId destination,
            UUID batchId,
            String operation
    ) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(operation, "operation");
        String canonical = "futureshops protected cash mint request v1\u0000"
                + transactionId + "\u0000"
                + destination.type().name() + "\u0000"
                + destination.ownerKey() + "\u0000" + batchId + "\u0000"
                + operation;
        return "protected.cash.mint." + operation + "."
                + hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] expectedAfterSnapshot(
            InternalBillInventoryPlanner.Portion portion) {
        ItemStack stack = ItemStackSnapshotCodec.decode(
                portion.exactStackSnapshot());
        int remaining = Math.subtractExact(portion.originalStackCount(),
                portion.selectedCount());
        if (remaining == 0) {
            return new byte[0];
        }
        stack.setCount(remaining);
        return ItemStackSnapshotCodec.encode(stack);
    }

    static byte[] sha256(byte[] value) {
        Objects.requireNonNull(value, "value");
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String fingerprint(byte[] value) {
        return hex(sha256(value));
    }

    static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    static void requireHash(byte[] value, String label) {
        if (value == null || value.length != HASH_BYTES) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }

    static boolean equal(byte[] first, byte[] second) {
        return MessageDigest.isEqual(first, second);
    }

    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    static void writeInstant(DataOutputStream output, Instant value)
            throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    static Instant readInstant(DataInputStream input) throws IOException {
        try {
            return Instant.ofEpochSecond(input.readLong(), input.readInt());
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Protected cash instant is invalid", exception);
        }
    }

    static void writeString(DataOutputStream output,
                            String value,
                            int maximumBytes) throws IOException {
        byte[] encoded = Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8);
        writeBytes(output, encoded, maximumBytes, "Protected cash string");
    }

    static String readString(DataInputStream input,
                             ByteArrayInputStream source,
                             int maximumBytes,
                             String label) throws IOException {
        byte[] encoded = readBytes(input, source, maximumBytes, label);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(label + " is not valid UTF-8",
                    exception);
        }
    }

    static void writeBytes(DataOutputStream output,
                           byte[] value,
                           int maximumBytes,
                           String label) throws IOException {
        Objects.requireNonNull(value, "value");
        if (value.length > maximumBytes) {
            throw new IllegalArgumentException(label + " exceeds its binary limit");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    static byte[] readBytes(DataInputStream input,
                            ByteArrayInputStream source,
                            int maximumBytes,
                            String label) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > maximumBytes || size > source.available()) {
            throw new IllegalArgumentException(label + " size is invalid");
        }
        byte[] value = input.readNBytes(size);
        if (value.length != size) {
            throw new EOFException(label + " is truncated");
        }
        return value;
    }

    static void writeComponent(DataOutputStream output,
                               byte[] value,
                               int maximumBytes,
                               String label) throws IOException {
        if (value.length == 0) {
            throw new IllegalArgumentException(label + " is empty");
        }
        writeBytes(output, value, maximumBytes, label);
    }

    static byte[] readComponent(DataInputStream input,
                                ByteArrayInputStream source,
                                int maximumBytes,
                                String label) throws IOException {
        byte[] value = readBytes(input, source, maximumBytes, label);
        if (value.length == 0) {
            throw new IllegalArgumentException(label + " is empty");
        }
        return value;
    }

    static int readCount(DataInputStream input, int maximum, String label)
            throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return count;
    }

    static <T> T readEnum(int ordinal, T[] values, String label) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return values[ordinal];
    }

    private static void requireSlot(InternalBillInventoryPlanner.SlotIdentity slot) {
        if (slot.container() == InternalBillInventoryPlanner.Container.MAIN) {
            if (slot.index() >= 36) {
                throw new IllegalArgumentException(
                        "Protected cash main inventory slot is invalid");
            }
        } else if (slot.index() != 0) {
            throw new IllegalArgumentException(
                    "Protected cash offhand inventory slot is invalid");
        }
    }

    private static BillSnapshot decodeBill(
            InternalBillInventoryPlanner.Portion portion) {
        byte[] encoded = portion.exactStackSnapshot();
        ItemStack stack = ItemStackSnapshotCodec.decode(encoded);
        String registryId = BuiltInRegistries.ITEM.getKey(
                stack.getItem()).toString();
        if (stack.isEmpty()
                || stack.getItem() != ModItems.MONEY_ITEM.get()
                || !registryId.equals(Futureshops.MODID + ":money")
                || stack.getCount() != portion.originalStackCount()
                || !ItemStackSnapshotCodec.snapshotMatchesIdentity(
                encoded, stack)) {
            throw new IllegalArgumentException(
                    "Protected cash stack is not registered protected money");
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(MoneyNbtKeys.ROOT, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(
                    "Protected cash stack lacks mint data");
        }
        CompoundTag data = root.getCompound(MoneyNbtKeys.ROOT);
        if (!data.contains(MoneyNbtKeys.DENOMINATION, Tag.TAG_LONG)
                || !data.contains(MoneyNbtKeys.MINT_ID, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.MINT_TIMESTAMP, Tag.TAG_LONG)
                || !data.contains(MoneyNbtKeys.MINT_PLAYER, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.MINT_SERVER, Tag.TAG_STRING)
                || !data.contains(MoneyNbtKeys.AUTHORIZED_COUNT, Tag.TAG_INT)
                || !data.contains(MoneyNbtKeys.CHECKSUM, Tag.TAG_STRING)) {
            throw new IllegalArgumentException(
                    "Protected cash stack mint data is incomplete");
        }
        String mintText = data.getString(MoneyNbtKeys.MINT_ID);
        UUID mintId = canonicalUuid(mintText, "Protected cash mint ID");
        canonicalUuid(data.getString(MoneyNbtKeys.MINT_PLAYER),
                "Protected cash mint player");
        String serverEvidence = requireEvidence(
                data.getString(MoneyNbtKeys.MINT_SERVER), 256,
                "Protected cash server evidence");
        String checksumEvidence = requireEvidence(
                data.getString(MoneyNbtKeys.CHECKSUM), 512,
                "Protected cash checksum evidence");
        if (!mintText.equals(portion.mintId())
                || data.getLong(MoneyNbtKeys.DENOMINATION)
                != portion.denominationMinorUnits()
                || data.getInt(MoneyNbtKeys.AUTHORIZED_COUNT)
                != portion.authorizedCount()) {
            throw new IllegalArgumentException(
                    "Protected cash stack does not match its plan portion");
        }
        return new BillSnapshot(mintId, registryId,
                portion.denominationMinorUnits(), portion.authorizedCount(),
                serverEvidence, checksumEvidence);
    }

    private static UUID canonicalUuid(String value, String label) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException(label + " is not canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " is invalid", exception);
        }
    }

    private static String requireEvidence(String value,
                                          int maximumLength,
                                          String label) {
        if (value == null || value.isEmpty() || value.length() > maximumLength
                || !value.equals(value.trim())) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value;
    }

    record BillSnapshot(UUID mintId,
                        String registryId,
                        long denominationMinorUnits,
                        int authorizedCount,
                        String serverIdentityEvidence,
                        String checksumEvidence) {
    }

    record BatchFacts(UUID batchId,
                      long denominationMinorUnits,
                      int authorizedCount,
                      int selectedCount,
                      String serverIdentityEvidence,
                      String checksumEvidence) {
    }

    record PlanFacts(long totalMinorUnits,
                     Map<InternalBillInventoryPlanner.SlotIdentity, BillSnapshot> snapshots,
                     Map<UUID, BatchFacts> batches) {
    }

    private static final class MutableBatchFacts {
        private final UUID batchId;
        private final long denominationMinorUnits;
        private final int authorizedCount;
        private final String serverIdentityEvidence;
        private final String checksumEvidence;
        private int selectedCount;

        private MutableBatchFacts(UUID batchId,
                                  long denominationMinorUnits,
                                  int authorizedCount,
                                  String serverIdentityEvidence,
                                  String checksumEvidence) {
            this.batchId = batchId;
            this.denominationMinorUnits = denominationMinorUnits;
            this.authorizedCount = authorizedCount;
            this.serverIdentityEvidence = serverIdentityEvidence;
            this.checksumEvidence = checksumEvidence;
        }

        private void requireSame(InternalBillInventoryPlanner.Portion portion,
                                 BillSnapshot snapshot) {
            if (denominationMinorUnits != portion.denominationMinorUnits()
                    || authorizedCount != portion.authorizedCount()
                    || !serverIdentityEvidence.equals(
                    snapshot.serverIdentityEvidence())
                    || !checksumEvidence.equals(snapshot.checksumEvidence())) {
                throw new IllegalArgumentException(
                        "Protected cash mint batch metadata changed across slots");
            }
        }

        private void add(int count) {
            selectedCount = Math.addExact(selectedCount, count);
            if (selectedCount > authorizedCount) {
                throw new IllegalArgumentException(
                        "Protected cash plan exceeds a mint authorization");
            }
        }

        private BatchFacts freeze() {
            return new BatchFacts(batchId, denominationMinorUnits, authorizedCount,
                    selectedCount, serverIdentityEvidence, checksumEvidence);
        }
    }
}
