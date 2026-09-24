package com.enviouse.futureshops.mixin;

import com.enviouse.futureshops.compat.pixelmon.PixelmonStorageSavingAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Bridges the inherited Pixelmon save marker without loading Pixelmon in common code. */
@Mixin(targets = "com.pixelmonmod.pixelmon.api.storage.PokemonStorage")
@Pseudo
abstract class PixelmonPokemonStorageMixin implements PixelmonStorageSavingAccess {
    @Shadow(remap = false)
    public abstract void setNeedsSaving();

    @Override
    public void futureshops$markNeedsSaving() {
        setNeedsSaving();
    }
}
