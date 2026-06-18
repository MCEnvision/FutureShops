package com.enviouse.futureshopsp.server.shop;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for external storage adapters (Refined Storage, AE2, etc.).
 * Adapters are registered during mod initialization and queried during storage resolution.
 */
public final class ExternalStorageRegistry {
    private static final List<ExternalStorageAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private ExternalStorageRegistry() {
    }

    /**
     * Registers an external storage adapter. Call during common setup.
     */
    public static void register(ExternalStorageAdapter adapter) {
        ADAPTERS.add(adapter);
    }

    /**
     * Finds the first adapter that can handle the given block entity, or null.
     */
    public static ExternalStorageAdapter findAdapter(BlockEntity blockEntity) {
        for (ExternalStorageAdapter adapter : ADAPTERS) {
            if (adapter.canHandle(blockEntity)) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * Whether any adapter can handle the given block entity.
     */
    public static boolean hasAdapter(BlockEntity blockEntity) {
        return findAdapter(blockEntity) != null;
    }

    /**
     * Returns all registered adapters.
     */
    public static List<ExternalStorageAdapter> getAdapters() {
        return List.copyOf(ADAPTERS);
    }
}

