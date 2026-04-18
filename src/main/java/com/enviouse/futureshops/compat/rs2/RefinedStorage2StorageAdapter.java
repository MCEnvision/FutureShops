package com.enviouse.futureshops.compat.rs2;

import com.enviouse.futureshops.server.shop.ExternalStorageAdapter;
import com.enviouse.futureshops.server.transaction.NbtMatchUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Refined Storage (refinedstorage-1.12.x, mod ID "refinedstorage") storage adapter.
 *
 * RS does NOT expose its full network inventory via IItemHandler on Controller/DiskDrive.
 * Those capabilities only expose physical disk slots — not the unified RS network inventory.
 *
 * This adapter uses reflection to traverse the RS1 internal API chain:
 *   BlockEntity → INetworkNodeProxy.getNode() → INetworkNode.getNetwork() → INetwork
 *     → INetwork.getItemStorageCache() → IStorageCache<ItemStack>
 *       → IStorageCache.getList() → IStackList<ItemStack>
 *         → IStackList.getStacks() → Collection<StackListEntry<ItemStack>>
 *     → INetwork.extractItem(ItemStack, int, Action) → ItemStack (extracted)
 *     → INetwork.insertItem(ItemStack, int, Action) → ItemStack (remainder)
 *
 * Falls back to IItemHandler capability if the reflection path fails.
 *
 * API package: com.refinedmods.refinedstorage.api.*
 */
public final class RefinedStorage2StorageAdapter implements ExternalStorageAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<String> RS_NAMESPACES = List.of(
            "refinedstorage",
            "refinedstorage2",
            "refinedstorageaddons"
    );

    private static final List<String> RS_CLASS_PREFIXES = List.of(
            "com.refinedmods.refinedstorage"
    );

    // ══════════════════════ Reflected RS1 API classes/methods (cached) ══════════════════════
    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean reflectionAvailable = false;

    // INetworkNodeProxy<T extends INetworkNode> → T getNode()
    private static Class<?> networkNodeProxyClass;
    private static Method getNodeMethod;

    // INetworkNode → INetwork getNetwork()
    private static Method getNetworkMethod;

    // INetwork → IStorageCache<ItemStack> getItemStorageCache()
    private static Method getItemStorageCacheMethod;

    // INetwork → ItemStack insertItem(ItemStack, int, Action)
    private static Method insertItemMethod;

    // INetwork → ItemStack extractItem(ItemStack, int, Action) [3-arg convenience overload]
    private static Method extractItemMethod;

    // IStorageCache<T> → IStackList<T> getList()
    private static Method getListMethod;

    // IStackList<T> → Collection<StackListEntry<T>> getStacks()
    private static Method getStacksMethod;

    // IStackList<T> → int getCount(T stack, int flags)
    private static Method getCountMethod;

    // StackListEntry<T> → T getStack()
    private static Method entryGetStackMethod;

    // Action enum: PERFORM, SIMULATE
    private static Object actionPerform;
    private static Object actionSimulate;

    @SuppressWarnings("unchecked")
    private static synchronized void initReflection() {
        // Retry if a prior attempt failed — RS classes may not have been loaded yet at first call.
        if (reflectionInitialized && reflectionAvailable) return;
        try {
            // ─── INetworkNodeProxy ───
            networkNodeProxyClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy");
            getNodeMethod = networkNodeProxyClass.getMethod("getNode");

            // ─── INetworkNode → getNetwork() ───
            Class<?> networkNodeClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.network.node.INetworkNode");
            getNetworkMethod = networkNodeClass.getMethod("getNetwork");

            // ─── INetwork ───
            Class<?> networkClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.network.INetwork");
            getItemStorageCacheMethod = networkClass.getMethod("getItemStorageCache");

            // Action enum
            Class<?> actionClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.util.Action");
            actionPerform = Enum.valueOf((Class<Enum>) actionClass, "PERFORM");
            actionSimulate = Enum.valueOf((Class<Enum>) actionClass, "SIMULATE");

            // INetwork.insertItem(ItemStack, int, Action) → ItemStack (remainder)
            insertItemMethod = networkClass.getMethod("insertItem", ItemStack.class, int.class, actionClass);

            // INetwork.extractItem(ItemStack, int, Action) → ItemStack (extracted)
            extractItemMethod = networkClass.getMethod("extractItem", ItemStack.class, int.class, actionClass);

            // ─── IStorageCache<T> → IStackList<T> getList() ───
            Class<?> storageCacheClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.storage.cache.IStorageCache");
            getListMethod = storageCacheClass.getMethod("getList");

            // ─── IStackList<T> ───
            Class<?> stackListClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.util.IStackList");
            getStacksMethod = stackListClass.getMethod("getStacks");
            // getCount(T stack, int flags)
            getCountMethod = stackListClass.getMethod("getCount", Object.class, int.class);

            // ─── StackListEntry<T> → T getStack() ───
            Class<?> stackListEntryClass = Class.forName(
                    "com.refinedmods.refinedstorage.api.util.StackListEntry");
            entryGetStackMethod = stackListEntryClass.getMethod("getStack");

            reflectionAvailable = true;
            reflectionInitialized = true;
            LOGGER.info("FutureShops RS: Reflection-based RS1 network API access initialized successfully.");
        } catch (Throwable t) {
            reflectionAvailable = false;
            // Leave reflectionInitialized=false so the next call retries (RS classes may load later).
            LOGGER.debug("FutureShops RS: Reflection initialization failed (will retry) — {}.", t.getMessage());
        }
    }

    // ══════════════════════ Adapter interface ══════════════════════

    @Override
    public boolean canHandle(BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        if (!isRSBlockEntity(blockEntity)) return false;
        initReflection();
        if (reflectionAvailable && resolveNetwork(blockEntity) != null) {
            return true;
        }
        return getHandler(blockEntity) != null;
    }

    @Override
    public int countItem(BlockEntity blockEntity, Item item) {
        return countItem(blockEntity, item, false, null);
    }

    @Override
    public int countItem(BlockEntity blockEntity, Item item, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        Object network = resolveNetwork(blockEntity);
        if (network != null) {
            return countItemViaNetwork(network, item, nbtAware, requiredTag);
        }
        return countItemViaHandler(blockEntity, item, nbtAware, requiredTag);
    }

    @Override
    public boolean canExtract(BlockEntity blockEntity, Item item, int count) {
        return canExtract(blockEntity, item, count, false, null);
    }

    @Override
    public boolean canExtract(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        Object network = resolveNetwork(blockEntity);
        if (network != null) {
            return canExtractViaNetwork(network, item, count, nbtAware, requiredTag);
        }
        return canExtractViaHandler(blockEntity, item, count, nbtAware, requiredTag);
    }

    @Override
    public List<ItemStack> extract(BlockEntity blockEntity, Item item, int count) {
        return extract(blockEntity, item, count, false, null);
    }

    @Override
    public List<ItemStack> extract(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        Object network = resolveNetwork(blockEntity);
        if (network != null) {
            return extractViaNetwork(network, item, count, nbtAware, requiredTag);
        }
        return extractViaHandler(blockEntity, item, count, nbtAware, requiredTag);
    }

    @Override
    public boolean canInsert(BlockEntity blockEntity, List<ItemStack> stacks) {
        Object network = resolveNetwork(blockEntity);
        if (network != null) {
            return canInsertViaNetwork(network, stacks);
        }
        return canInsertViaHandler(blockEntity, stacks);
    }

    @Override
    public boolean insert(BlockEntity blockEntity, List<ItemStack> stacks) {
        Object network = resolveNetwork(blockEntity);
        if (network != null) {
            return insertViaNetwork(network, stacks);
        }
        return insertViaHandler(blockEntity, stacks);
    }

    // ══════════════════════ RS1 Network API methods (reflection) ══════════════════════

    /**
     * Traverses: BlockEntity (INetworkNodeProxy) → getNode() → getNetwork() → INetwork
     */
    @Nullable
    private Object resolveNetwork(BlockEntity blockEntity) {
        if (!reflectionAvailable) return null;
        try {
            if (!networkNodeProxyClass.isInstance(blockEntity)) return null;
            Object node = getNodeMethod.invoke(blockEntity);
            if (node == null) return null;
            return getNetworkMethod.invoke(node); // may be null if not connected
        } catch (Throwable t) {
            LOGGER.debug("FutureShops RS: Failed to resolve network for {}: {}", blockEntity.getBlockPos(), t.getMessage());
            return null;
        }
    }

    /**
     * INetwork → getItemStorageCache() → getList() → getStacks()
     */
    @Nullable
    private Collection<?> getNetworkStacks(Object network) {
        try {
            Object storageCache = getItemStorageCacheMethod.invoke(network);
            if (storageCache == null) return null;
            Object stackList = getListMethod.invoke(storageCache);
            if (stackList == null) return null;
            return (Collection<?>) getStacksMethod.invoke(stackList);
        } catch (Throwable t) {
            LOGGER.debug("FutureShops RS: Failed to get network stacks: {}", t.getMessage());
            return null;
        }
    }

    @Nullable
    private ItemStack entryStack(Object entry) {
        try {
            return (ItemStack) entryGetStackMethod.invoke(entry);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean stackMatches(ItemStack stack, Item item, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() != item) return false;
        if (nbtAware && requiredTag != null) {
            return requiredTag.equals(stack.getTag());
        }
        if (nbtAware) {
            return stack.getTag() == null || stack.getTag().isEmpty();
        }
        return true;
    }

    private int countItemViaNetwork(Object network, Item item, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        try {
            if (!nbtAware) {
                // Fast path: use IStackList.getCount(probe, flags=0) for type-only match
                Object storageCache = getItemStorageCacheMethod.invoke(network);
                if (storageCache == null) return 0;
                Object stackList = getListMethod.invoke(storageCache);
                if (stackList == null) return 0;
                ItemStack probe = new ItemStack(item, 1);
                return (int) getCountMethod.invoke(stackList, probe, 0);
            }
            // NBT-aware: iterate all stacks
            Collection<?> entries = getNetworkStacks(network);
            if (entries == null) return 0;
            long total = 0;
            for (Object entry : entries) {
                ItemStack stack = entryStack(entry);
                if (stackMatches(stack, item, true, requiredTag)) {
                    total += stack.getCount();
                }
            }
            return (int) Math.min(total, Integer.MAX_VALUE);
        } catch (Throwable t) {
            LOGGER.debug("FutureShops RS: countItem via network failed: {}", t.getMessage());
            return 0;
        }
    }

    private boolean canExtractViaNetwork(Object network, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        try {
            ItemStack probe = new ItemStack(item, 1);
            if (nbtAware && requiredTag != null) probe.setTag(requiredTag.copy());
            // INetwork.extractItem(stack, size, SIMULATE) → returns the EXTRACTED items
            ItemStack extracted = (ItemStack) extractItemMethod.invoke(network, probe, count, actionSimulate);
            return !extracted.isEmpty() && extracted.getCount() >= count;
        } catch (Throwable t) {
            return countItemViaNetwork(network, item, nbtAware, requiredTag) >= count;
        }
    }

    private List<ItemStack> extractViaNetwork(Object network, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        try {
            ItemStack probe = new ItemStack(item, 1);
            if (nbtAware && requiredTag != null) probe.setTag(requiredTag.copy());
            // RS1 INetwork.extractItem(stack, size, Action) → returns the EXTRACTED ItemStack
            ItemStack extracted = (ItemStack) extractItemMethod.invoke(network, probe, count, actionPerform);
            if (extracted == null || extracted.isEmpty()) {
                return List.of();
            }
            if (extracted.getCount() < count) {
                // Partial extract — put it back
                LOGGER.warn("FutureShops RS: Partial extract ({}/{}) — rolling back.", extracted.getCount(), count);
                insertItemMethod.invoke(network, extracted, extracted.getCount(), actionPerform);
                return List.of();
            }
            return List.of(extracted);
        } catch (Throwable t) {
            LOGGER.warn("FutureShops RS: extract via network failed: {}", t.getMessage());
            return List.of();
        }
    }

    private boolean canInsertViaNetwork(Object network, List<ItemStack> stacks) {
        try {
            for (ItemStack stack : stacks) {
                // INetwork.insertItem(stack, size, Action) → returns REMAINDER
                ItemStack remainder = (ItemStack) insertItemMethod.invoke(network, stack, stack.getCount(), actionSimulate);
                if (remainder != null && !remainder.isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            LOGGER.debug("FutureShops RS: canInsert via network failed: {}", t.getMessage());
            return false;
        }
    }

    private boolean insertViaNetwork(Object network, List<ItemStack> stacks) {
        try {
            List<ItemStack> insertedStacks = new ArrayList<>();
            for (ItemStack stack : stacks) {
                ItemStack remainder = (ItemStack) insertItemMethod.invoke(network, stack, stack.getCount(), actionPerform);
                if (remainder != null && !remainder.isEmpty()) {
                    // Rollback
                    for (ItemStack prev : insertedStacks) {
                        extractItemMethod.invoke(network, prev, prev.getCount(), actionPerform);
                    }
                    return false;
                }
                insertedStacks.add(stack.copy());
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("FutureShops RS: insert via network failed: {}", t.getMessage());
            return false;
        }
    }

    // ══════════════════════ IItemHandler Fallback ══════════════════════

    private int countItemViaHandler(BlockEntity blockEntity, Item item, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (NbtMatchUtil.matches(stack, item, nbtAware, requiredTag)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean canExtractViaHandler(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return false;
        int remaining = count;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (!probe.isEmpty() && NbtMatchUtil.matches(probe, item, nbtAware, requiredTag)) {
                remaining -= probe.getCount();
            }
        }
        return remaining <= 0;
    }

    private List<ItemStack> extractViaHandler(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, requiredTag)) continue;
            ItemStack real = handler.extractItem(i, remaining, false);
            if (!real.isEmpty()) {
                remaining -= real.getCount();
                result.add(real);
            }
        }
        return remaining <= 0 ? result : List.of();
    }

    private boolean canInsertViaHandler(BlockEntity blockEntity, List<ItemStack> stacks) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return false;
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, true);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private boolean insertViaHandler(BlockEntity blockEntity, List<ItemStack> stacks) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return false;
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    // ══════════════════════ Utility ══════════════════════

    private boolean isRSBlockEntity(BlockEntity blockEntity) {
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        if (key != null) {
            String ns = key.getNamespace();
            for (String known : RS_NAMESPACES) {
                if (known.equals(ns)) return true;
            }
        }
        String className = blockEntity.getClass().getName();
        for (String prefix : RS_CLASS_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private IItemHandler getHandler(BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler != null) return handler;
        for (Direction direction : Direction.values()) {
            handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).resolve().orElse(null);
            if (handler != null) return handler;
        }
        return null;
    }
}

