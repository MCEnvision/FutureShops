package com.enviouse.futureshopsp.mixin;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

public final class PixelmonEconomyMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PIXELMON = "pixelmon";
    private static final String SUPPORTED_VERSION = "9.4.0";

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("FutureShops Pixelmon mixin plugin loaded for {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (!isSupportedPixelmonLoaded()) {
                return false;
            }
            boolean apply = "com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage".equals(targetClassName);
            LOGGER.info("FutureShops Pixelmon mixin target {} apply {}", targetClassName, apply);
            return apply;
        } catch (RuntimeException exception) {
            LOGGER.warn("FutureShops Pixelmon mixin target check failed", exception);
            return false;
        }
    }

    private static boolean isSupportedPixelmonLoaded() {
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        if (loadingModList == null || loadingModList.getModFileById(PIXELMON) == null) {
            return false;
        }
        return loadingModList.getModFileById(PIXELMON).getMods().stream()
                .anyMatch(mod -> PIXELMON.equals(mod.getModId())
                        && SUPPORTED_VERSION.equals(mod.getVersion().toString()));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
