package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the {@code currency.provider} config into a live
 * {@link PhysicalCurrencyAdapter} at server start. Mirrors BalanceManager's
 * static-holder lifecycle: initialize() at ServerStarting, clear() at
 * ServerStopping. Any resolution failure falls back to the built-in currency
 * with an ERROR log rather than leaving the server without physical money.
 */
public final class CurrencyManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String PROVIDER_INTERNAL = InternalCurrencyAdapter.ID;
    public static final String PROVIDER_APOCALYPSENOW = "apocalypsenow";
    public static final String PROVIDER_CUSTOM = "custom";

    /**
     * Apocalypse Now preset. money ≈ $1 (its craft value is ~1 emerald) and
     * coins are a $0.25 quarter — deliberately ABOVE the mod's 8-coins-to-1-money
     * craft ratio so converting coins into money always loses value and the
     * cointomoney recipe can't be used as a money printer. Blocks are accepted
     * at deposit but never handed out: apocalypsenow's 9-money block craft is
     * one-way (no uncraft recipe), so giving them as change would strand value.
     */
    private static final List<CurrencyMath.ItemValue> APOC_ITEMS = List.of(
            new CurrencyMath.ItemValue("apocalypsenow:money", 100L),
            new CurrencyMath.ItemValue("apocalypsenow:coins", 25L));
    private static final List<CurrencyMath.ItemValue> APOC_ACCEPT_ONLY = List.of(
            new CurrencyMath.ItemValue("apocalypsenow:money_block", 900L),
            new CurrencyMath.ItemValue("apocalypsenow:highvaluemoneyblock", 8100L));

    private static volatile PhysicalCurrencyAdapter current;

    private CurrencyManager() {
    }

    public static void initialize() {
        String provider = Config.currencyProvider == null
                ? PROVIDER_INTERNAL
                : Config.currencyProvider.trim().toLowerCase(Locale.ROOT);
        current = switch (provider) {
            case PROVIDER_INTERNAL -> new InternalCurrencyAdapter();
            case PROVIDER_APOCALYPSENOW -> {
                // Overriding EITHER config list replaces BOTH preset halves.
                // Mixing config items with preset blocks would be exploit-prone:
                // retuning money below 100 while the preset still accepts
                // money_block at 900 turns the 9-money block craft into a money
                // printer. All-or-nothing fails safe — un-relisted blocks are
                // simply not depositable.
                boolean overridden = !Config.currencyItems.isEmpty() || !Config.currencyAcceptOnlyItems.isEmpty();
                yield buildItemValueAdapter(PROVIDER_APOCALYPSENOW,
                        overridden ? parseConfig(Config.currencyItems) : APOC_ITEMS,
                        overridden ? parseConfig(Config.currencyAcceptOnlyItems) : APOC_ACCEPT_ONLY);
            }
            case PROVIDER_CUSTOM -> buildItemValueAdapter(PROVIDER_CUSTOM,
                    parseConfig(Config.currencyItems),
                    parseConfig(Config.currencyAcceptOnlyItems));
            default -> {
                LOGGER.error("Unknown currency.provider '{}' — falling back to the built-in FutureShops currency",
                        provider);
                yield new InternalCurrencyAdapter();
            }
        };
        LOGGER.info("FutureShops physical currency provider: {}", current.id());
        if (!current.isInternal()) {
            LOGGER.warn("WARNING: physical currency provider '{}' is UNPROTECTED. FutureShops checksum, "
                    + "mint-id, and spent-mint ledger dupe prevention is disabled for these foreign items; "
                    + "their source mod controls recipes, loot, and duplication behavior.", current.id());
        }
    }

    public static void clear() {
        current = null;
    }

    /** Active adapter; throws if the server lifecycle hasn't initialized it yet. */
    public static PhysicalCurrencyAdapter get() {
        PhysicalCurrencyAdapter adapter = current;
        if (adapter == null) {
            throw new IllegalStateException("CurrencyManager not initialized — server not started?");
        }
        return adapter;
    }

    /** Active adapter or null before server start (for event handlers). */
    public static PhysicalCurrencyAdapter getOrNull() {
        return current;
    }

    public static boolean isInternal() {
        PhysicalCurrencyAdapter adapter = current;
        return adapter == null || adapter.isInternal();
    }

    private static List<CurrencyMath.ItemValue> parseConfig(List<? extends String> entries) {
        return CurrencyMath.parseItemValueList(entries,
                bad -> LOGGER.error("Invalid currency config entry '{}' — expected \"modid:item=value_in_minor_units\"", bad));
    }

    private static PhysicalCurrencyAdapter buildItemValueAdapter(String id,
                                                                 List<CurrencyMath.ItemValue> mintableSpec,
                                                                 List<CurrencyMath.ItemValue> acceptOnlySpec) {
        List<PhysicalCurrencyAdapter.Denomination> mintable = new ArrayList<>();
        Map<Item, Long> acceptValues = new HashMap<>();

        for (CurrencyMath.ItemValue spec : mintableSpec) {
            Item item = resolveItem(spec);
            if (item == null) continue;
            if (acceptValues.putIfAbsent(item, spec.valueMinor()) != null) {
                LOGGER.warn("Duplicate currency item '{}' — keeping the first configured value", spec.itemId());
                continue;
            }
            mintable.add(new PhysicalCurrencyAdapter.Denomination(item, spec.valueMinor()));
        }
        for (CurrencyMath.ItemValue spec : acceptOnlySpec) {
            Item item = resolveItem(spec);
            if (item == null) continue;
            if (acceptValues.putIfAbsent(item, spec.valueMinor()) != null) {
                LOGGER.warn("Duplicate currency item '{}' — keeping the first configured value", spec.itemId());
            }
        }

        if (mintable.isEmpty()) {
            LOGGER.error("Currency provider '{}' resolved no mintable denominations (mod not installed or bad ids?)"
                    + " — falling back to the built-in FutureShops currency", id);
            return new InternalCurrencyAdapter();
        }

        mintable.sort((a, b) -> Long.compare(b.valueMinor(), a.valueMinor()));
        return new ItemValueCurrencyAdapter(id, mintable, acceptValues);
    }

    private static Item resolveItem(CurrencyMath.ItemValue spec) {
        ResourceLocation key = ResourceLocation.tryParse(spec.itemId());
        // Forge registries answer AIR (not null) for unknown keys — gate on containsKey.
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
            LOGGER.warn("Currency item '{}' is not a registered item — skipping", spec.itemId());
            return null;
        }
        return ForgeRegistries.ITEMS.getValue(key);
    }
}
