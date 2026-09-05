package com.enviouse.futureshopsp.mixin;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class DanConomyEconomyMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DANCONOMY = "danconomy";
    private static final String SUPPORTED_VERSION = "1.2.1";

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("FutureShops DanConomy mixin plugin loaded for {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (!isSupportedDanConomyLoaded()) {
                return false;
            }
            boolean apply = "com.danners45.danconomy.data.LedgerData".equals(targetClassName);
            LOGGER.info("FutureShops DanConomy mixin target {} apply {}", targetClassName, apply);
            return apply;
        } catch (RuntimeException exception) {
            LOGGER.warn("FutureShops DanConomy mixin target check failed", exception);
            return false;
        }
    }

    private static boolean isSupportedDanConomyLoaded() {
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        if (loadingModList == null || loadingModList.getModFileById(DANCONOMY) == null) {
            return false;
        }
        return loadingModList.getModFileById(DANCONOMY).getMods().stream()
                .anyMatch(mod -> DANCONOMY.equals(mod.getModId())
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
