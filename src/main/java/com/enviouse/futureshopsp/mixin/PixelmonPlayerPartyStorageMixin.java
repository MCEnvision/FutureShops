package com.enviouse.futureshopsp.mixin;

import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.compat.pixelmon.PixelmonNativeEconomyAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(targets = "com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage")
public abstract class PixelmonPlayerPartyStorageMixin implements PixelmonNativeEconomyAccess {
    @Unique
    private static final String FUTURESHOPS_RECEIPTS = "FutureShopsReceipts";
    @Unique
    private static final String FUTURESHOPS_RECEIPT_ENTRIES = "entries";
    @Unique
    private final Map<RequestId, NativeReceipt> futureshopsReceipts = new LinkedHashMap<>();
    @Unique
    private final List<Tag> futureshopsUnknownReceiptRecords = new ArrayList<>();
    @Unique
    private boolean futureshopsReceiptIntegrityValid = true;

    @Shadow
    public abstract boolean add(BigDecimal amount);

    @Shadow
    public abstract boolean take(BigDecimal amount);

    @Shadow
    public abstract BigDecimal getBalance();

    @Shadow
    public abstract ServerPlayer getPlayer();

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void futureshopsReadReceipts(CompoundTag tag, HolderLookup.Provider registries,
                                         CallbackInfoReturnable<?> callback) {
        futureshopsReceipts.clear();
        futureshopsUnknownReceiptRecords.clear();
        futureshopsReceiptIntegrityValid = true;
        Tag rawReceipts = tag.get(FUTURESHOPS_RECEIPTS);
        if (rawReceipts == null) {
            return;
        }
        if (!(rawReceipts instanceof CompoundTag root)) {
            futureshopsReceiptIntegrityValid = false;
            futureshopsUnknownReceiptRecords.add(rawReceipts.copy());
            return;
        }
        Tag rawEntries = root.get(FUTURESHOPS_RECEIPT_ENTRIES);
        if (!(rawEntries instanceof ListTag entries)) {
            futureshopsReceiptIntegrityValid = false;
            if (rawEntries != null) {
                futureshopsUnknownReceiptRecords.add(rawEntries.copy());
            }
            return;
        }
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                futureshopsReceiptIntegrityValid = false;
                futureshopsUnknownReceiptRecords.add(rawEntry.copy());
                continue;
            }
            try {
                if (!entry.hasUUID("request_id")) {
                    throw new IllegalArgumentException("request id is missing");
                }
                RequestId requestId = new RequestId(entry.getUUID("request_id"));
                MutationKind kind = MutationKind.valueOf(entry.getString("kind"));
                long amount = entry.getLong("amount_minor_units");
                if (amount <= 0L) {
                    throw new IllegalArgumentException("amount is invalid");
                }
                String externalId = entry.getString("external_operation_id");
                if (!externalId.equals("pixelmon:" + requestId.value())) {
                    throw new IllegalArgumentException("external operation id is invalid");
                }
                java.util.OptionalLong resulting;
                if (entry.contains("resulting_balance_minor_units")) {
                    if (!entry.contains("resulting_balance_minor_units", Tag.TAG_LONG)) {
                        throw new IllegalArgumentException("resulting balance has an invalid type");
                    }
                    resulting = java.util.OptionalLong.of(entry.getLong("resulting_balance_minor_units"));
                } else {
                    resulting = java.util.OptionalLong.empty();
                }
                String state = entry.getString("state");
                if (!"COMPLETED".equals(state) && !"PENDING".equals(state)) {
                    throw new IllegalArgumentException("receipt state is unknown");
                }
                if (futureshopsReceipts.putIfAbsent(requestId, new NativeReceipt(requestId, kind, amount,
                        externalId, resulting, "COMPLETED".equals(state))) != null) {
                    throw new IllegalArgumentException("duplicate request id");
                }
            } catch (RuntimeException exception) {
                futureshopsReceiptIntegrityValid = false;
                futureshopsUnknownReceiptRecords.add(entry.copy());
            }
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void futureshopsWriteReceipts(CompoundTag tag, HolderLookup.Provider registries,
                                          CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        for (NativeReceipt receipt : futureshopsReceipts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("request_id", receipt.requestId().value());
            entry.putString("kind", receipt.kind().name());
            entry.putLong("amount_minor_units", receipt.amountMinorUnits());
            entry.putString("external_operation_id", receipt.externalOperationId());
            entry.putString("state", receipt.completed() ? "COMPLETED" : "PENDING");
            receipt.resultingBalanceMinorUnits().ifPresent(value ->
                    entry.putLong("resulting_balance_minor_units", value));
            entries.add(entry);
        }
        for (Tag unknown : futureshopsUnknownReceiptRecords) {
            entries.add(unknown.copy());
        }
        root.put(FUTURESHOPS_RECEIPT_ENTRIES, entries);
        tag.put(FUTURESHOPS_RECEIPTS, root);
    }

    @Override
    public synchronized ProviderResult<MutationReceipt> futureshopsMutate(RequestId requestId, MutationKind kind,
                                                                           long amountMinorUnits,
                                                                           HolderLookup.Provider registries) {
        if (requestId == null || kind == null || amountMinorUnits <= 0L || registries == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "native Pixelmon request is invalid");
        }
        if (!futureshopsReceiptIntegrityValid) {
            return ProviderResult.recoveryRequired("native Pixelmon receipt data is unknown or contradictory");
        }
        NativeReceipt existing = futureshopsReceipts.get(requestId);
        if (existing != null) {
            if (existing.kind() != kind || existing.amountMinorUnits() != amountMinorUnits) {
                return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                        "native Pixelmon request conflicts with its receipt");
            }
            if (!existing.completed()) {
                return ProviderResult.recoveryRequired("native Pixelmon receipt is pending reconciliation");
            }
            return ProviderResult.confirmed(existing.toReceipt());
        }
        ServerPlayer player = getPlayer();
        if (player == null || player.server == null || !player.server.isSameThread()) {
            return ProviderResult.unavailable(ProviderError.NOT_READY,
                    "native Pixelmon mutation must run on the server thread");
        }
        NativeReceipt pending = NativeReceipt.pending(requestId, kind, amountMinorUnits);
        futureshopsReceipts.put(requestId, pending);
        if (!futureshopsDurableSave(registries, requestId, false)) {
            futureshopsReceipts.remove(requestId);
            return ProviderResult.recoveryRequired("native Pixelmon pending receipt could not be saved");
        }
        boolean changed;
        try {
            BigDecimal amount = BigDecimal.valueOf(amountMinorUnits);
            changed = kind == MutationKind.DEPOSIT || kind == MutationKind.REFUND
                    || kind == MutationKind.TRANSFER_CREDIT || kind == MutationKind.COMPENSATION
                    ? add(amount) : take(amount);
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "native Pixelmon mutation failed before completion");
        }
        if (!changed) {
            futureshopsReceipts.remove(requestId);
            if (!futureshopsDurableSave(registries, null, false)) {
                futureshopsReceipts.put(requestId, pending);
                return ProviderResult.recoveryRequired("native Pixelmon rejection could not be durably saved");
            }
            return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS,
                    "native Pixelmon account rejected the mutation");
        }
        java.util.OptionalLong resulting = java.util.OptionalLong.empty();
        try {
            resulting = java.util.OptionalLong.of(getBalance().setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        } catch (RuntimeException ignored) {
            // An optional resulting balance does not change receipt validity.
        }
        NativeReceipt completed = pending.completed(resulting);
        futureshopsReceipts.put(requestId, completed);
        if (!futureshopsDurableSave(registries, requestId, true)) {
            futureshopsReceipts.put(requestId, pending);
            return ProviderResult.recoveryRequired("native Pixelmon completed effect has no durable receipt");
        }
        return ProviderResult.confirmed(completed.toReceipt());
    }

    @Override
    public synchronized ProviderResult<MutationReceipt> futureshopsLookup(RequestId requestId) {
        if (requestId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "receipt request is required");
        }
        if (!futureshopsReceiptIntegrityValid) {
            return ProviderResult.recoveryRequired("native Pixelmon receipt data is unknown or contradictory");
        }
        NativeReceipt receipt = futureshopsReceipts.get(requestId);
        if (receipt == null) {
            return ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "native Pixelmon receipt not found");
        }
        if (!receipt.completed()) {
            return ProviderResult.recoveryRequired("native Pixelmon receipt is pending reconciliation");
        }
        return ProviderResult.confirmed(receipt.toReceipt());
    }

    @Unique
    private boolean futureshopsDurableSave(HolderLookup.Provider registries, RequestId expectedRequestId,
                                           boolean expectedCompleted) {
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> storage = Class.forName("com.pixelmonmod.pixelmon.api.storage.PokemonStorage", false, loader);
            storage.getMethod("setNeedsSaving").invoke(this);
            Class<?> proxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy", false, loader);
            Object adapter = proxy.getMethod("getSaveAdapter").invoke(null);
            if (adapter == null) {
                return false;
            }
            adapter.getClass().getMethod("save", storage, HolderLookup.Provider.class)
                    .invoke(adapter, this, registries);
            Object fileValue = adapter.getClass().getMethod("getFile", storage).invoke(adapter, this);
            if (!(fileValue instanceof File file)) {
                return false;
            }
            Path path = file.toPath();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                channel.force(true);
            }
            return expectedRequestId == null || futureshopsReceiptIsOnDisk(path, expectedRequestId, expectedCompleted);
        } catch (java.io.IOException | ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Unique
    private boolean futureshopsReceiptIsOnDisk(Path path, RequestId expectedRequestId, boolean expectedCompleted) {
        try {
            CompoundTag persisted = NbtIo.read(path);
            if (persisted == null || !(persisted.get(FUTURESHOPS_RECEIPTS) instanceof CompoundTag root)) {
                return false;
            }
            if (!(root.get(FUTURESHOPS_RECEIPT_ENTRIES) instanceof ListTag entries)) {
                return false;
            }
            for (Tag rawEntry : entries) {
                if (!(rawEntry instanceof CompoundTag entry)) {
                    return false;
                }
                if (entry.hasUUID("request_id") && expectedRequestId.value().equals(entry.getUUID("request_id"))
                        && (expectedCompleted ? "COMPLETED" : "PENDING").equals(entry.getString("state"))) {
                    return true;
                }
            }
            return false;
        } catch (java.io.IOException | RuntimeException exception) {
            return false;
        }
    }

    @Unique
    private record NativeReceipt(RequestId requestId, MutationKind kind, long amountMinorUnits,
                                 String externalOperationId, java.util.OptionalLong resultingBalanceMinorUnits,
                                 boolean completed) {
        static NativeReceipt pending(RequestId requestId, MutationKind kind, long amountMinorUnits) {
            return new NativeReceipt(requestId, kind, amountMinorUnits,
                    "pixelmon:" + requestId.value(), java.util.OptionalLong.empty(), false);
        }

        NativeReceipt completed(java.util.OptionalLong resulting) {
            return new NativeReceipt(requestId, kind, amountMinorUnits, externalOperationId, resulting, true);
        }

        MutationReceipt toReceipt() {
            return new MutationReceipt(requestId, kind, amountMinorUnits, externalOperationId,
                    resultingBalanceMinorUnits);
        }
    }
}
