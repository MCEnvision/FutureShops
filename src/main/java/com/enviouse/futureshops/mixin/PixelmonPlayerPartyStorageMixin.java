package com.enviouse.futureshops.mixin;

import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeGate;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeReceiptCodec;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeRequestContext;
import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeRequestContext.Request;
import com.enviouse.futureshops.compat.pixelmon.PixelmonStorageSavingAccess;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;

/** Optional target for the exact Pixelmon 9.2.3 native account implementation. */
@Mixin(targets = "com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage")
@Pseudo
abstract class PixelmonPlayerPartyStorageMixin {
    @Shadow(remap = false)
    protected BigDecimal pokeDollars;

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
    }

    @Inject(method = "setBalance(Ljava/math/BigDecimal;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void futureshops$setBalance(BigDecimal amount, CallbackInfo callback) {
        if (futureshops$invalidReceipt
                && PixelmonNativeGate.refuseCurrentRequest("malformed_receipt")) {
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
        if (futureshops$invalidReceipt
                && PixelmonNativeGate.refuseCurrentRequest("malformed_receipt")) {
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
        if (futureshops$invalidReceipt
                && PixelmonNativeGate.refuseCurrentRequest("malformed_receipt")) {
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
            PixelmonNativeReceiptCodec.write(callback.getReturnValue(), request);
            futureshops$loadedReceipt = callback.getReturnValue().copy();
            PixelmonNativeGate.clearPending(this);
        });
        if (futureshops$loadedReceipt != null
                && !callback.getReturnValue().contains("futureshopsReceipt", 10)) {
            if (futureshops$invalidReceipt) {
                callback.getReturnValue().put("futureshopsReceipt",
                        futureshops$loadedReceipt.getCompound("futureshopsReceipt").copy());
            } else {
                PixelmonNativeReceiptCodec.preserve(callback.getReturnValue(),
                        futureshops$loadedReceipt);
            }
        }
    }

    private void markNeedsSaving() {
        ((PixelmonStorageSavingAccess) (Object) this).futureshops$markNeedsSaving();
    }
}
