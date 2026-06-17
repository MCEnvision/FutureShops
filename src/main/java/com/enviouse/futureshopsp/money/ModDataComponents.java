package com.enviouse.futureshopsp.money;

import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers FutureShops' custom data components. {@code COMPONENTS.register(modEventBus)}
 * is wired from the {@code @Mod} constructor (entrypoint tranche).
 *
 * <p>The component id is {@code futureshops:coin_data} (modid kept as "futureshops"),
 * deliberately matching the legacy NBT root key for namespace continuity.
 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Futureshops.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CoinData>> COIN_DATA =
            COMPONENTS.registerComponentType("coin_data", builder -> builder
                    .persistent(CoinData.CODEC)
                    .networkSynchronized(CoinData.STREAM_CODEC));

    private ModDataComponents() {
    }
}
