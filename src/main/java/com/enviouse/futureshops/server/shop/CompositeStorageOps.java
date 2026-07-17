package com.enviouse.futureshops.server.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/**
 * Pure, item-type-agnostic composite-storage algorithms for the multi-link player-shop
 * stock path. These are the load-bearing, money-critical routines (stock summing,
 * all-or-nothing cross-storage extract with rollback, and insert-spill), factored out of
 * {@link PlayerShopBlockService} so they can be unit-tested without a live Minecraft
 * server or {@code ItemStack} bootstrap.
 *
 * <p>Everything is generic over:
 * <ul>
 *   <li>{@code S} — a storage handle ({@code LinkedStorage} in production, a fake in tests).</li>
 *   <li>{@code U} — an extracted/insertable "unit" ({@code ItemStack} in production).</li>
 * </ul>
 * The production code supplies lambdas that perform the real per-storage
 * {@code IItemHandler} / adapter operations; the algorithm here decides ordering,
 * summation, the all-or-nothing gate, and rollback — the parts that are easy to get
 * subtly wrong and must never lose items.
 */
public final class CompositeStorageOps {
    private CompositeStorageOps() {
    }

    /**
     * Saturating sum of availability across all storages. Never overflows: returns
     * {@link Integer#MAX_VALUE} once the running total reaches it (an admin/infinite
     * storage can report {@code Integer.MAX_VALUE} per storage).
     */
    public static <S> int countAcross(List<S> storages, ToIntFunction<S> available) {
        long total = 0L;
        for (S s : storages) {
            total += Math.max(0, available.applyAsInt(s));
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    /**
     * Whether the composite can supply at least {@code count} units — the probe half of
     * the all-or-nothing contract. Short-circuits as soon as the running total reaches
     * {@code count}. A non-positive {@code count} is trivially satisfiable.
     */
    public static <S> boolean canExtractAcross(List<S> storages, int count, ToIntFunction<S> available) {
        if (count <= 0) {
            return true;
        }
        long total = 0L;
        for (S s : storages) {
            total += Math.max(0, available.applyAsInt(s));
            if (total >= count) {
                return true;
            }
        }
        return false;
    }

    /**
     * All-or-nothing extraction across storages, in order.
     *
     * <p>For each storage {@code take} pulls up to the remaining amount and returns the
     * ACTUAL units removed (best-effort per storage — it may return fewer than asked).
     * {@code unitCount} reports how many item-units a returned unit represents.
     * If the composite cannot fully satisfy {@code count}, every unit pulled so far is
     * handed back to the exact storage it came from via {@code reinsert}, and an empty
     * list is returned — so a shortfall extracts nothing net (no item loss), matching the
     * single-storage all-or-nothing contract.
     *
     * @return the extracted units (their {@code unitCount} sums to at least {@code count})
     *         on success, or an empty list on shortfall (after rolling everything back).
     */
    public static <S, U> List<U> extractAcross(
            List<S> storages,
            int count,
            BiFunction<S, Integer, List<U>> take,
            ToIntFunction<U> unitCount,
            BiConsumer<S, U> reinsert) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        List<U> pulled = new ArrayList<>();
        List<S> origin = new ArrayList<>();
        int remaining = count;
        for (S s : storages) {
            if (remaining <= 0) {
                break;
            }
            List<U> got = take.apply(s, remaining);
            if (got == null) {
                continue;
            }
            for (U unit : got) {
                pulled.add(unit);
                origin.add(s);
                remaining -= Math.max(0, unitCount.applyAsInt(unit));
            }
        }
        if (remaining > 0) {
            // Shortfall: put back everything, to the storage it came from.
            for (int i = 0; i < pulled.size(); i++) {
                reinsert.accept(origin.get(i), pulled.get(i));
            }
            return new ArrayList<>();
        }
        return pulled;
    }

    /**
     * Folds an insert of {@code units} across storages in order, spilling the leftover of
     * each storage into the next. {@code insertInto} inserts what it can into one storage
     * and returns the units (or partial units) that did NOT fit.
     *
     * @return the units that still did not fit after every storage was tried — empty when
     *         the whole insert was absorbed.
     */
    public static <S, U> List<U> spillInsert(
            List<S> storages,
            List<U> units,
            BiFunction<S, List<U>, List<U>> insertInto) {
        List<U> remaining = units;
        for (S s : storages) {
            if (remaining.isEmpty()) {
                break;
            }
            List<U> leftover = insertInto.apply(s, remaining);
            remaining = leftover == null ? new ArrayList<>() : leftover;
        }
        return remaining;
    }

    /**
     * Convenience boolean form of {@link #spillInsert}: true when the composite absorbs
     * every unit. Use the same {@code insertInto} lambda in simulate mode for a capacity
     * pre-check and in real mode for the actual insert.
     */
    public static <S, U> boolean insertAllAcross(
            List<S> storages,
            List<U> units,
            BiFunction<S, List<U>, List<U>> insertInto) {
        return spillInsert(storages, units, insertInto).isEmpty();
    }
}
