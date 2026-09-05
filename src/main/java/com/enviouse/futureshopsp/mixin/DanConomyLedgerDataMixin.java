package com.enviouse.futureshopsp.mixin;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.compat.danconomy.DanConomyLedgerAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Mixin(targets = "com.danners45.danconomy.data.LedgerData")
public abstract class DanConomyLedgerDataMixin implements DanConomyLedgerAccess {
    @Unique
    private static final String FUTURESHOPS_RECEIPTS = "FutureShopsReceipts";
    @Unique
    private static final String FUTURESHOPS_RECEIPT_ENTRIES = "entries";
    @Unique
    private static final String FUTURESHOPS_PROVIDER = "danconomy";
    @Unique
    private static final int FUTURESHOPS_SCHEMA_VERSION = 1;
    @Unique
    private static final int FUTURESHOPS_MAX_RECEIPTS = 100_000;
    @Unique
    private static final long FUTURESHOPS_MAX_LEDGER_BYTES = 64L * 1024L * 1024L;
    @Unique
    private final Map<RequestId, NativeReceipt> futureshopsReceipts = new LinkedHashMap<>();
    @Unique
    private boolean futureshopsReceiptIntegrityValid = true;
    @Unique
    private Tag futureshopsUnrecognizedReceiptRoot;

    @Inject(method = "load", at = @At("RETURN"))
    private static void futureshopsReadLedgerReceipts(CompoundTag tag, HolderLookup.Provider registries,
                                                       CallbackInfoReturnable<?> callback) {
        Object ledger = callback.getReturnValue();
        if (ledger instanceof DanConomyLedgerAccess access) {
            access.futureshopsLoadReceipts(tag);
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void futureshopsWriteLedgerReceipts(CompoundTag tag, HolderLookup.Provider registries,
                                                 CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag output = callback.getReturnValue();
        if (!futureshopsReceiptIntegrityValid && futureshopsUnrecognizedReceiptRoot != null) {
            output.put(FUTURESHOPS_RECEIPTS, futureshopsUnrecognizedReceiptRoot.copy());
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", FUTURESHOPS_SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (NativeReceipt receipt : futureshopsReceipts.values()) {
            entries.add(futureshopsWriteReceipt(receipt));
        }
        root.put(FUTURESHOPS_RECEIPT_ENTRIES, entries);
        output.put(FUTURESHOPS_RECEIPTS, root);
    }

    @Override
    public synchronized void futureshopsLoadReceipts(CompoundTag tag) {
        futureshopsReceipts.clear();
        futureshopsReceiptIntegrityValid = true;
        futureshopsUnrecognizedReceiptRoot = null;
        Tag rawRoot = tag.get(FUTURESHOPS_RECEIPTS);
        if (rawRoot == null) {
            return;
        }
        futureshopsUnrecognizedReceiptRoot = rawRoot.copy();
        if (!(rawRoot instanceof CompoundTag root)
                || !root.contains("schema_version", Tag.TAG_INT)
                || root.getInt("schema_version") != FUTURESHOPS_SCHEMA_VERSION
                || !(root.get(FUTURESHOPS_RECEIPT_ENTRIES) instanceof ListTag entries)
                || entries.size() > FUTURESHOPS_MAX_RECEIPTS) {
            futureshopsReceiptIntegrityValid = false;
            return;
        }
        Map<RequestId, NativeReceipt> loaded = new LinkedHashMap<>();
        try {
            for (Tag rawEntry : entries) {
                if (!(rawEntry instanceof CompoundTag entry)) {
                    throw new IllegalArgumentException("receipt entry type is invalid");
                }
                NativeReceipt receipt = futureshopsReadReceipt(entry);
                if (loaded.putIfAbsent(receipt.requestId(), receipt) != null) {
                    throw new IllegalArgumentException("receipt request is duplicated");
                }
            }
        } catch (RuntimeException exception) {
            futureshopsReceiptIntegrityValid = false;
            return;
        }
        futureshopsReceipts.putAll(loaded);
        futureshopsUnrecognizedReceiptRoot = null;
    }

    @Override
    public synchronized ProviderResult<BalanceSnapshot> futureshopsBalance(UUID accountId, String currencyId) {
        if (accountId == null || currencyId == null || currencyId.isBlank()) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "danconomy balance request is invalid");
        }
        if (!futureshopsReceiptIntegrityValid) {
            return ProviderResult.recoveryRequired("danconomy receipt data is unknown or contradictory");
        }
        try {
            Object account = futureshopsGetOrCreateAccount(accountId);
            long balance = futureshopsGetBalance(account, currencyId);
            if (balance < 0L) {
                return ProviderResult.recoveryRequired("danconomy ledger balance is invalid");
            }
            return ProviderResult.confirmed(new BalanceSnapshot(accountId, balance));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "danconomy ledger balance read failed");
        }
    }

    @Override
    public synchronized ProviderResult<MutationReceipt> futureshopsMutate(
            ServerLevel level, UUID accountId, String currencyId, RequestId requestId,
            MutationKind kind, long amountMinorUnits) {
        if (level == null || accountId == null || currencyId == null || currencyId.isBlank()
                || requestId == null || kind == null || amountMinorUnits <= 0L) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "danconomy request aware mutation is invalid");
        }
        if (level.getServer() == null || !level.getServer().isSameThread()) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "danconomy mutation must run on the server thread");
        }
        if (!futureshopsReceiptIntegrityValid) {
            return ProviderResult.recoveryRequired("danconomy receipt data is unknown or contradictory");
        }
        NativeReceipt existing = futureshopsReceipts.get(requestId);
        if (existing != null) {
            if (!existing.matches(accountId, currencyId, kind, amountMinorUnits)) {
                return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                        "danconomy request conflicts with its stored receipt");
            }
            if (!existing.durable()) {
                if (!futureshopsDurableSave(level, existing)) {
                    return ProviderResult.recoveryRequired("danconomy receipt durability remains unknown");
                }
                existing = existing.asDurable();
                futureshopsReceipts.put(requestId, existing);
            }
            return ProviderResult.confirmed(existing.toReceipt());
        }
        if (futureshopsHasPendingReceipts()) {
            return ProviderResult.recoveryRequired(
                    "danconomy receipt durability requires reconciliation before a new mutation");
        }
        if (futureshopsReceipts.size() >= FUTURESHOPS_MAX_RECEIPTS) {
            return ProviderResult.unavailable(ProviderError.CAPABILITY_MISSING,
                    "danconomy receipt capacity is exhausted");
        }

        try {
            Object account = futureshopsGetOrCreateAccount(accountId);
            long before = futureshopsGetBalance(account, currencyId);
            if (before < 0L) {
                return ProviderResult.recoveryRequired("danconomy ledger balance is invalid");
            }
            boolean deposit = futureshopsIsDeposit(kind);
            if (!deposit && before < amountMinorUnits) {
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS,
                        "insufficient danconomy funds");
            }
            long after = deposit
                    ? Math.addExact(before, amountMinorUnits)
                    : Math.subtractExact(before, amountMinorUnits);
            NativeReceipt receipt = NativeReceipt.pending(requestId, accountId, currencyId, kind,
                    amountMinorUnits, after);
            futureshopsSetBalance(account, currencyId, after);
            futureshopsReceipts.put(requestId, receipt);
            ((SavedData) (Object) this).setDirty();
            if (!futureshopsDurableSave(level, receipt)) {
                return ProviderResult.recoveryRequired(
                        "danconomy balance and receipt durable replacement could not be proven");
            }
            NativeReceipt durable = receipt.asDurable();
            futureshopsReceipts.put(requestId, durable);
            return ProviderResult.confirmed(durable.toReceipt());
        } catch (ArithmeticException exception) {
            return ProviderResult.rejected(ProviderError.INVALID_AMOUNT,
                    "danconomy balance would exceed the supported range");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "danconomy ledger mutation failed");
        }
    }

    @Override
    public synchronized ProviderResult<MutationReceipt> futureshopsLookup(ServerLevel level, RequestId requestId) {
        if (level == null || requestId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "receipt request is required");
        }
        if (level.getServer() == null || !level.getServer().isSameThread()) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "danconomy receipt lookup must run on the server thread");
        }
        if (!futureshopsReceiptIntegrityValid) {
            return ProviderResult.recoveryRequired("danconomy receipt data is unknown or contradictory");
        }
        NativeReceipt receipt = futureshopsReceipts.get(requestId);
        if (receipt == null) {
            return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND,
                    "danconomy receipt was not found");
        }
        if (!receipt.durable()) {
            if (!futureshopsReceiptIsOnDisk(futureshopsLedgerPath(level), receipt)) {
                return ProviderResult.recoveryRequired("danconomy receipt durability remains unknown");
            }
            receipt = receipt.asDurable();
            futureshopsReceipts.put(requestId, receipt);
        }
        return ProviderResult.confirmed(receipt.toReceipt());
    }

    @Override
    public synchronized boolean futureshopsReceiptIntegrityValid() {
        return futureshopsReceiptIntegrityValid;
    }

    @Override
    public synchronized boolean futureshopsHasPendingReceipts() {
        return futureshopsReceipts.values().stream().anyMatch(receipt -> !receipt.durable());
    }

    @Override
    public synchronized boolean futureshopsReceiptCapacityAvailable() {
        return futureshopsReceipts.size() < FUTURESHOPS_MAX_RECEIPTS;
    }

    @Unique
    private Object futureshopsGetOrCreateAccount(UUID accountId) throws ReflectiveOperationException {
        return futureshopsInvoke(getClass().getMethod("getOrCreateAccount", UUID.class), this, accountId);
    }

    @Unique
    private static long futureshopsGetBalance(Object account, String currencyId)
            throws ReflectiveOperationException {
        Method method = account.getClass().getMethod("getBalance", String.class);
        return ((Number) futureshopsInvoke(method, account, currencyId)).longValue();
    }

    @Unique
    private static void futureshopsSetBalance(Object account, String currencyId, long balance)
            throws ReflectiveOperationException {
        Method method = account.getClass().getMethod("setBalance", String.class, long.class);
        futureshopsInvoke(method, account, currencyId, balance);
    }

    @Unique
    private boolean futureshopsDurableSave(ServerLevel level, NativeReceipt expectedReceipt) {
        SavedData savedData = (SavedData) (Object) this;
        Path path = futureshopsLedgerPath(level);
        try {
            IOUtilities.waitUntilIOWorkerComplete();
            Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent == null || !parent.startsWith(worldRoot) || Files.isSymbolicLink(parent)) {
                return false;
            }
            Files.createDirectories(parent);
            CompoundTag root = new CompoundTag();
            root.put("data", savedData.save(new CompoundTag(), level.registryAccess()));
            NbtUtils.addCurrentDataVersion(root);
            IOUtilities.writeNbtCompressed(root, path);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                savedData.setDirty();
                return false;
            }
            try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                file.force(true);
            }
            try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                directory.force(true);
            }
            if (!futureshopsReceiptIsOnDisk(path, expectedReceipt)) {
                savedData.setDirty();
                return false;
            }
            savedData.setDirty(false);
            return true;
        } catch (java.io.IOException | RuntimeException exception) {
            savedData.setDirty();
            return false;
        }
    }

    @Unique
    private static Path futureshopsLedgerPath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .resolve("data").resolve("danconomy_ledger.dat").normalize();
    }

    @Unique
    private static boolean futureshopsReceiptIsOnDisk(Path path, NativeReceipt expectedReceipt) {
        try {
            CompoundTag fileRoot = NbtIo.readCompressed(path, NbtAccounter.create(FUTURESHOPS_MAX_LEDGER_BYTES));
            if (!(fileRoot.get("data") instanceof CompoundTag data)
                    || !(data.get(FUTURESHOPS_RECEIPTS) instanceof CompoundTag receiptRoot)
                    || !(receiptRoot.get(FUTURESHOPS_RECEIPT_ENTRIES) instanceof ListTag entries)) {
                return false;
            }
            boolean receiptFound = false;
            for (Tag rawEntry : entries) {
                if (!(rawEntry instanceof CompoundTag entry)) {
                    return false;
                }
                NativeReceipt candidate = futureshopsReadReceipt(entry);
                if (candidate.requestId().equals(expectedReceipt.requestId())) {
                    receiptFound = candidate.matches(expectedReceipt.accountId(), expectedReceipt.currencyId(),
                            expectedReceipt.kind(), expectedReceipt.amountMinorUnits())
                            && candidate.resultingBalanceMinorUnits() == expectedReceipt.resultingBalanceMinorUnits();
                }
            }
            if (!receiptFound || !(data.get("accounts") instanceof CompoundTag accounts)
                    || !(accounts.get(expectedReceipt.accountId().toString()) instanceof CompoundTag account)
                    || !(account.get("balances") instanceof CompoundTag balances)
                    || !balances.contains(expectedReceipt.currencyId(), Tag.TAG_LONG)) {
                return false;
            }
            return balances.getLong(expectedReceipt.currencyId()) == expectedReceipt.resultingBalanceMinorUnits();
        } catch (java.io.IOException | RuntimeException exception) {
            return false;
        }
    }

    @Unique
    private static CompoundTag futureshopsWriteReceipt(NativeReceipt receipt) {
        CompoundTag entry = new CompoundTag();
        entry.putString("provider", FUTURESHOPS_PROVIDER);
        entry.putUUID("request_id", receipt.requestId().value());
        entry.putUUID("account_id", receipt.accountId());
        entry.putString("currency_id", receipt.currencyId());
        entry.putString("kind", receipt.kind().name());
        entry.putLong("amount_minor_units", receipt.amountMinorUnits());
        entry.putLong("resulting_balance_minor_units", receipt.resultingBalanceMinorUnits());
        entry.putString("external_operation_id", receipt.externalOperationId());
        entry.putString("checksum", receipt.checksum());
        return entry;
    }

    @Unique
    private static NativeReceipt futureshopsReadReceipt(CompoundTag entry) {
        if (!entry.contains("provider", Tag.TAG_STRING)
                || !entry.hasUUID("request_id")
                || !entry.hasUUID("account_id")
                || !entry.contains("currency_id", Tag.TAG_STRING)
                || !entry.contains("kind", Tag.TAG_STRING)
                || !entry.contains("amount_minor_units", Tag.TAG_LONG)
                || !entry.contains("resulting_balance_minor_units", Tag.TAG_LONG)
                || !entry.contains("external_operation_id", Tag.TAG_STRING)
                || !entry.contains("checksum", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("danconomy receipt is incomplete");
        }
        if (!FUTURESHOPS_PROVIDER.equals(entry.getString("provider"))) {
            throw new IllegalArgumentException("danconomy receipt provider is invalid");
        }
        RequestId requestId = new RequestId(entry.getUUID("request_id"));
        UUID accountId = entry.getUUID("account_id");
        String currencyId = entry.getString("currency_id");
        MutationKind kind = MutationKind.valueOf(entry.getString("kind"));
        long amount = entry.getLong("amount_minor_units");
        long resultingBalance = entry.getLong("resulting_balance_minor_units");
        String operationId = entry.getString("external_operation_id");
        if (currencyId.isBlank() || currencyId.length() > 128 || amount <= 0L || resultingBalance < 0L
                || !operationId.equals("danconomy:" + requestId.value())) {
            throw new IllegalArgumentException("danconomy receipt value is invalid");
        }
        NativeReceipt receipt = new NativeReceipt(requestId, accountId, currencyId, kind, amount,
                resultingBalance, operationId, true);
        if (!receipt.checksum().equals(entry.getString("checksum"))) {
            throw new IllegalArgumentException("danconomy receipt checksum is invalid");
        }
        return receipt;
    }

    @Unique
    private static boolean futureshopsIsDeposit(MutationKind kind) {
        return kind == MutationKind.DEPOSIT || kind == MutationKind.TRANSFER_CREDIT
                || kind == MutationKind.REFUND || kind == MutationKind.COMPENSATION;
    }

    @Unique
    private static Object futureshopsInvoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ReflectiveOperationException("danconomy ledger call failed", cause);
        }
    }

    @Unique
    private static String futureshopsDigest(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha 256 is unavailable", exception);
        }
    }

    @Unique
    private record NativeReceipt(RequestId requestId, UUID accountId, String currencyId, MutationKind kind,
                                 long amountMinorUnits, long resultingBalanceMinorUnits,
                                 String externalOperationId, boolean durable) {
        static NativeReceipt pending(RequestId requestId, UUID accountId, String currencyId, MutationKind kind,
                                     long amountMinorUnits, long resultingBalanceMinorUnits) {
            return new NativeReceipt(requestId, accountId, currencyId, kind, amountMinorUnits,
                    resultingBalanceMinorUnits, "danconomy:" + requestId.value(), false);
        }

        NativeReceipt asDurable() {
            return new NativeReceipt(requestId, accountId, currencyId, kind, amountMinorUnits,
                    resultingBalanceMinorUnits, externalOperationId, true);
        }

        boolean matches(UUID expectedAccountId, String expectedCurrencyId, MutationKind expectedKind,
                        long expectedAmount) {
            return accountId.equals(expectedAccountId) && currencyId.equals(expectedCurrencyId)
                    && kind == expectedKind && amountMinorUnits == expectedAmount;
        }

        String checksum() {
            return futureshopsDigest(FUTURESHOPS_SCHEMA_VERSION + "|" + FUTURESHOPS_PROVIDER + "|"
                    + requestId.value() + "|" + accountId + "|" + currencyId + "|" + kind + "|"
                    + amountMinorUnits + "|" + resultingBalanceMinorUnits + "|" + externalOperationId);
        }

        MutationReceipt toReceipt() {
            return new MutationReceipt(requestId, kind, amountMinorUnits, externalOperationId,
                    OptionalLong.of(resultingBalanceMinorUnits));
        }
    }
}
