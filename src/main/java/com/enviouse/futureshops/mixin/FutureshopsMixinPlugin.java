package com.enviouse.futureshops.mixin;

import com.enviouse.futureshops.compat.pixelmon.PixelmonNativeGate;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Keeps the optional Pixelmon target out of the mixin pipeline when absent. */
public final class FutureshopsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("PixelmonPlayerPartyStorageMixin")
                || mixinClassName.endsWith("PixelmonPokemonStorageMixin")) {
            return PixelmonNativeGate.isSupportedVersionLoaded()
                    && ("com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage".equals(targetClassName)
                    || "com.pixelmonmod.pixelmon.api.storage.PokemonStorage".equals(targetClassName));
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
