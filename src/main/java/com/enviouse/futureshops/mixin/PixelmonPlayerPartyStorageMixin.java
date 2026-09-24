package com.enviouse.futureshops.mixin;

import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeGate;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeEconomyAccess;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeReceiptCodec;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeRequestContext;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeRequestContext.Request;
import com.enviouse.futureshops.compat.pixelmon.PixelmonStorageSavingAccess;
import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.BindingV1;
import com.enviouse.futureshops.api.economy.LegId;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.ProviderResultStatus;
import com.enviouse.futureshops.api.economy.RequestId;
import com.enviouse.futureshops.api.economy.RootId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Optional target for the exact Pixelmon 9.2.3 native account implementation. */
@Mixin(targets = "com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage")
@Pseudo
abstract class PixelmonPlayerPartyStorageMixin
        implements PixelmonNativeEconomyAccess, PixelmonStorageSavingAccess {
    @Shadow(remap = false)
    protected BigDecimal pokeDollars;

    @Shadow(remap = false)
    public abstract UUID getPlayerUUID();

    @Shadow(remap = false)
    public abstract File getFile();

    @Shadow(remap = false)
    public abstract CompoundTag writeToNBT(CompoundTag tag);

    @Shadow(remap = false)
    public abstract void setNeedsSaving();

    @Shadow(remap = false)
    public abstract boolean add(BigDecimal amount);

    @Shadow(remap = false)
    public abstract boolean take(BigDecimal amount);

    @Inject(method = "<init>(Ljava/util/UUID;)V", at = @At("RETURN"), remap = false)
    private void futureshops$register(UUID playerId, CallbackInfo callback) {
        PixelmonNativeGate.registerAccount(playerId, (PixelmonNativeEconomyAccess) (Object) this);
    }

    @Unique
    private CompoundTag futureshops$loadedReceipt;

    @Unique
    private boolean futureshops$invalidReceipt;

    @Inject(method = "readFromNBT(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), remap = false)
    private void futureshops$readReceipt(CompoundTag tag, CallbackInfoReturnable<?> callback) {
        futureshops$loadedReceipt = null;
        futureshops$invalidReceipt = false;
        if (tag.contains("futureshopsReceipt", 10)) {
            try {
                PixelmonNativeReceiptCodec.read(tag)
                        .ifPresent(ignored -> futureshops$loadedReceipt =
                                tag.copy());
            } catch (IllegalArgumentException ignored) {
                futureshops$loadedReceipt = tag.copy();
                futureshops$invalidReceipt = true;
            }
        }
        PixelmonNativeGate.registerAccount(getPlayerUUID(),
                (PixelmonNativeEconomyAccess) (Object) this);
    }

    @Inject(method = "setBalance(Ljava/math/BigDecimal;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void futureshops$setBalance(BigDecimal amount, CallbackInfo callback) {
        if (futureshops$invalidReceipt) {
            PixelmonNativeGate.refuseCurrentRequest("malformed_receipt");
            callback.cancel();
            return;
        }
        if (PixelmonNativeGate.applySetBalance(this, amount, pokeDollars)) {
            PixelmonNativeRequestContext.current().flatMap(Request::resultingBalance)
                    .ifPresent(balance -> pokeDollars = balance);
            callback.cancel();
            markNeedsSaving();
        }
    }

    @Inject(method = "add(Ljava/math/BigDecimal;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void futureshops$add(BigDecimal amount, CallbackInfoReturnable<Boolean> callback) {
        if (futureshops$invalidReceipt) {
            PixelmonNativeGate.refuseCurrentRequest("malformed_receipt");
            callback.setReturnValue(false);
            return;
        }
        if (PixelmonNativeGate.applyDelta(this, pokeDollars, amount, false)) {
            PixelmonNativeRequestContext.current().flatMap(Request::resultingBalance)
                    .ifPresent(balance -> pokeDollars = balance);
            callback.setReturnValue(PixelmonNativeRequestContext.current()
                    .flatMap(Request::resultingBalance).isPresent());
            markNeedsSaving();
        }
    }

    @Inject(method = "take(Ljava/math/BigDecimal;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void futureshops$take(BigDecimal amount, CallbackInfoReturnable<Boolean> callback) {
        if (futureshops$invalidReceipt) {
            PixelmonNativeGate.refuseCurrentRequest("malformed_receipt");
            callback.setReturnValue(false);
            return;
        }
        if (PixelmonNativeGate.applyDelta(this, pokeDollars, amount, true)) {
            PixelmonNativeRequestContext.current().flatMap(Request::resultingBalance)
                    .ifPresent(balance -> pokeDollars = balance);
            callback.setReturnValue(PixelmonNativeRequestContext.current()
                    .flatMap(Request::resultingBalance).isPresent());
            markNeedsSaving();
        }
    }

    @Inject(method = "writeToNBT(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"), cancellable = false, remap = false)
    private void futureshops$writeReceipt(CompoundTag tag,
                                           CallbackInfoReturnable<CompoundTag> callback) {
        PixelmonNativeGate.pendingRequest(this).ifPresent(request -> {
            if (futureshops$loadedReceipt != null) {
                PixelmonNativeReceiptCodec.preserve(callback.getReturnValue(), futureshops$loadedReceipt);
            }
            PixelmonNativeReceiptCodec.write(callback.getReturnValue(), request);
        });
        if (futureshops$loadedReceipt != null
                && !callback.getReturnValue().contains("futureshopsReceipt", 10)) {
            if (futureshops$invalidReceipt) {
                net.minecraft.nbt.Tag rawReceipt = futureshops$loadedReceipt.get("futureshopsReceipt");
                if (rawReceipt != null) {
                    callback.getReturnValue().put("futureshopsReceipt", rawReceipt.copy());
                }
            } else {
                PixelmonNativeReceiptCodec.preserve(callback.getReturnValue(),
                        futureshops$loadedReceipt);
            }
        }
    }

    private void markNeedsSaving() {
        futureshops$markNeedsSaving();
    }

    @Override
    public void futureshops$markNeedsSaving() {
        setNeedsSaving();
    }

    @Override
    public ProviderResult<BalanceSnapshot> futureshops$balance() {
        try {
            return ProviderResult.confirmed(new BalanceSnapshot(getPlayerUUID(),
                    pokeDollars.setScale(0, RoundingMode.UNNECESSARY).longValueExact()));
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "pixelmon balance is not an exact whole dollar value");
        }
    }

    @Override
    public ProviderResult<MutationReceipt> futureshops$mutate(RequestId requestId,
                                                               MutationKind kind,
                                                               long amountMinorUnits,
                                                               String payloadFingerprint) {
        if (requestId == null || kind == null || amountMinorUnits <= 0L
                || payloadFingerprint == null || !payloadFingerprint.matches("[0-9a-fA-F]{64}")) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "pixelmon mutation identity is invalid");
        }
        if (futureshops$invalidReceipt) {
            return ProviderResult.recoveryRequired("pixelmon account has a malformed receipt");
        }
        Optional<PixelmonNativeReceiptCodec.Receipt> existing = futureshops$receipt(requestId.value());
        if (existing.isPresent()) {
            PixelmonNativeReceiptCodec.Receipt receipt = existing.orElseThrow();
            if (receipt.legId().equals(requestId.value())) {
                if (receipt.amountMinorUnits() != amountMinorUnits
                        || receipt.debit() != futureshops$debit(kind)
                        || !receipt.payloadFingerprint().equalsIgnoreCase(payloadFingerprint)) {
                    return ProviderResult.rejected(ProviderError.REQUEST_CONFLICT,
                            "pixelmon receipt conflicts with the requested mutation");
                }
                return ProviderResult.confirmed(futureshops$toMutationReceipt(receipt));
            }
        }
        BindingV1 binding = PixelmonNativeGate.bindingFor(getPlayerUUID());
        RootId rootId = new RootId(requestId.value());
        LegId legId = new LegId(requestId.value());
        boolean debit = futureshops$debit(kind);
        try (PixelmonNativeRequestContext.Scope ignored = PixelmonNativeRequestContext.open(
                binding, rootId, legId, kind.name().toLowerCase(java.util.Locale.ROOT),
                amountMinorUnits, debit, payloadFingerprint)) {
            BigDecimal amount = BigDecimal.valueOf(amountMinorUnits);
            boolean changed = debit ? take(amount) : add(amount);
            Optional<Request> current = PixelmonNativeRequestContext.current();
            if (!changed || current.flatMap(Request::resultingBalance).isEmpty()) {
                return ProviderResult.rejected(ProviderError.PROVIDER_EXCEPTION,
                        current.flatMap(Request::failure).orElse("pixelmon mutation was refused"));
            }
            CompoundTag written = writeToNBT(new CompoundTag());
            Optional<PixelmonNativeReceiptCodec.Receipt> persisted =
                    PixelmonNativeReceiptCodec.read(written, requestId.value());
            if (persisted.isEmpty() || !persisted.orElseThrow().legId().equals(requestId.value())
                    || !futureshops$saveAtomically(written)) {
                return ProviderResult.recoveryRequired(
                        "pixelmon mutation changed memory but its native save was not verified");
            }
            PixelmonNativeReceiptCodec.Receipt receipt = persisted.orElseThrow();
            futureshops$loadedReceipt = written.copy();
            PixelmonNativeGate.clearPending(this);
            return ProviderResult.confirmed(futureshops$toMutationReceipt(receipt));
        } catch (IOException | RuntimeException exception) {
            return ProviderResult.recoveryRequired("pixelmon native save verification failed");
        }
    }

    @Override
    public ProviderResult<MutationReceipt> futureshops$lookup(RequestId requestId) {
        if (requestId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST,
                    "pixelmon receipt request is required");
        }
        Optional<PixelmonNativeReceiptCodec.Receipt> receipt = futureshops$receipt(requestId.value());
        if (receipt.isEmpty()) {
            return futureshops$invalidReceipt
                    ? ProviderResult.recoveryRequired("pixelmon account has a malformed receipt")
                    : ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND,
                    "pixelmon receipt was not found");
        }
        return receipt.orElseThrow().legId().equals(requestId.value())
                ? ProviderResult.confirmed(futureshops$toMutationReceipt(receipt.orElseThrow()))
                : ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND,
                "pixelmon receipt was not found");
    }

    private Optional<PixelmonNativeReceiptCodec.Receipt> futureshops$receipt(UUID legId) {
        if (futureshops$loadedReceipt == null) {
            return Optional.empty();
        }
        try {
            return PixelmonNativeReceiptCodec.read(futureshops$loadedReceipt, legId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static boolean futureshops$debit(MutationKind kind) {
        return kind == MutationKind.WITHDRAW || kind == MutationKind.TRANSFER_DEBIT
                || kind == MutationKind.FEE;
    }

    private static MutationReceipt futureshops$toMutationReceipt(
            PixelmonNativeReceiptCodec.Receipt receipt) {
        OptionalLong resulting = OptionalLong.empty();
        try {
            resulting = OptionalLong.of(receipt.resultingBalance()
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact());
        } catch (RuntimeException ignored) {
            // The codec bounds the value. A non integral value is still refused as a balance.
        }
        return new MutationReceipt(new RequestId(receipt.legId()),
                receipt.debit() ? MutationKind.WITHDRAW : MutationKind.DEPOSIT,
                receipt.amountMinorUnits(), "pixelmon:" + receipt.legId(), resulting);
    }

    private boolean futureshops$saveAtomically(CompoundTag tag) throws IOException {
        File file = getFile();
        if (file == null) {
            return false;
        }
        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".futureshops.tmp");
        Files.deleteIfExists(temporary);
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)))) {
                NbtIo.write(tag, output);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                    Files.newInputStream(target)))) {
                CompoundTag verified = NbtIo.read(input, new NbtAccounter(4_000_000L));
                return verified != null && input.read() == -1
                        && PixelmonNativeReceiptCodec.read(verified).isPresent();
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
