package com.enviouse.futureshopsp.server.pricing;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.catalog.ItemDef;
import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.catalog.ShopDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Implements the spec §30 dynamic pricing formula.
 * <p>
 * The engine runs on a configurable tick schedule and adjusts prices per-shop/per-item
 * based on buy/sell activity since the last recalculation.
 * <pre>
 * demandPressure = buysSinceLastCalc × demand_weight
 * supplyPressure = sellsSinceLastCalc × supply_weight
 * priceDelta     = (demandPressure - supplyPressure) × basePrice × 0.01
 * newPrice       = (currentPrice + priceDelta) × decay_rate
 * newPrice       = clamp(newPrice, base × (1 - max_decrease/100), base × (1 + max_increase/100))
 * </pre>
 */
public final class DynamicPricingEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100L);
    private static final BigDecimal ONE_PERCENT = ONE.divide(HUNDRED);

    /** Ticks since last recalculation (volatile for cross-thread visibility). */
    private static volatile int tickCounter = 0;

    private DynamicPricingEngine() {
    }

    // ---- Activity recording (delegates to SavedData) ----

    public static void recordBuy(MinecraftServer server, String shopId, String itemId, int quantity) {
        if (!Config.dynamicPricingEnabled) return;
        DynamicPricingSavedData.get(server).recordBuy(shopId, itemId, quantity);
    }

    public static void recordSell(MinecraftServer server, String shopId, String itemId, int quantity) {
        if (!Config.dynamicPricingEnabled) return;
        DynamicPricingSavedData.get(server).recordSell(shopId, itemId, quantity);
    }

    // ---- Price query ----

    /**
     * Returns the dynamically adjusted price for an item, or the base price if dynamic pricing
     * is disabled or no adjustment has been recorded.
     */
    public static long getAdjustedPrice(MinecraftServer server, String shopId, String itemId, long basePriceMinor) {
        if (!Config.dynamicPricingEnabled || basePriceMinor <= 0) {
            return basePriceMinor;
        }
        DynamicPricingSavedData data = DynamicPricingSavedData.get(server);
        DynamicPricingSavedData.ItemPricingState state = data.getState(shopId, itemId);
        if (state.currentPriceMinor <= 0) {
            return basePriceMinor;
        }
        return state.currentPriceMinor;
    }

    // ---- Tick scheduler ----

    /**
     * Called every server tick. When the configured interval has elapsed, triggers a recalculation.
     */
    public static void onServerTick(MinecraftServer server) {
        if (!Config.dynamicPricingEnabled) return;
        tickCounter++;
        int intervalTicks = Config.dynamicPricingRecalcIntervalSec * 20;
        if (intervalTicks <= 0) intervalTicks = 6000; // fallback 5 min
        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            recalculate(server);
        }
    }

    /**
     * Runs the pricing formula for all tracked items. Resets activity counters after calculation.
     */
    public static void recalculate(MinecraftServer server) {
        DynamicPricingSavedData data = DynamicPricingSavedData.get(server);
        double demandWeight = Config.dynamicPricingDemandWeight;
        double supplyWeight = Config.dynamicPricingSupplyWeight;
        double decayRate = Config.dynamicPricingDecayRate;
        double maxIncreasePct = Config.dynamicPricingMaxIncreasePct;
        double maxDecreasePct = Config.dynamicPricingMaxDecreasePct;

        int updated = 0;
        for (Map.Entry<String, DynamicPricingSavedData.ItemPricingState> entry : data.allStates().entrySet()) {
            String compositeKey = entry.getKey();
            DynamicPricingSavedData.ItemPricingState state = entry.getValue();

            // Key format is "shopId:listingId". Split on the FIRST colon so a listingId that is itself
            // a resource location (e.g. a legacy "minecraft:diamond" whose listingId == itemId) survives
            // intact as the second component.
            int sep = compositeKey.indexOf(':');
            if (sep < 0) continue;
            String shopId = compositeKey.substring(0, sep);
            String listingId = compositeKey.substring(sep + 1);

            // Resolve base price from catalog (getItem resolves by listingId).
            long basePrice = ShopCatalog.getItem(shopId, listingId)
                    .map(ItemDef::buyPriceMinorUnits)
                    .orElse(0L);
            if (basePrice <= 0) continue;

            OptionalLong newPrice = calculatePrice(basePrice, state.currentPriceMinor,
                    state.buysSinceLastCalc, state.sellsSinceLastCalc,
                    demandWeight, supplyWeight, decayRate, maxIncreasePct, maxDecreasePct);
            if (newPrice.isEmpty()) {
                LOGGER.warn("Dynamic pricing skipped invalid state for {}.", compositeKey);
                continue;
            }
            state.currentPriceMinor = newPrice.getAsLong();
            state.resetCounters();
            updated++;
        }

        if (updated > 0) {
            data.markDirtyExplicit();
            LOGGER.debug("Dynamic pricing recalculated {} item(s).", updated);
        }
    }

    /**
     * Resets the tick counter (e.g., on server start).
     */
    public static void reset() {
        tickCounter = 0;
    }

    static OptionalLong calculatePrice(long basePrice, long currentPrice, int buys, int sells,
                                       double demandWeight, double supplyWeight, double decayRate,
                                       double maxIncreasePct, double maxDecreasePct) {
        if (basePrice <= 0L || buys < 0 || sells < 0) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal demand = decimal(demandWeight);
            BigDecimal supply = decimal(supplyWeight);
            BigDecimal decay = decimal(decayRate);
            BigDecimal maxIncrease = decimal(maxIncreasePct);
            BigDecimal maxDecrease = decimal(maxDecreasePct);
            BigDecimal base = BigDecimal.valueOf(basePrice);
            BigDecimal current = BigDecimal.valueOf(currentPrice > 0L ? currentPrice : basePrice);
            BigDecimal demandPressure = BigDecimal.valueOf(buys).multiply(demand);
            BigDecimal supplyPressure = BigDecimal.valueOf(sells).multiply(supply);
            BigDecimal priceDelta = demandPressure.subtract(supplyPressure)
                    .multiply(base).multiply(ONE_PERCENT);
            BigDecimal calculated = current.add(priceDelta).multiply(decay);
            BigDecimal floor = base.multiply(ONE.subtract(maxDecrease.divide(HUNDRED)));
            BigDecimal ceiling = base.multiply(ONE.add(maxIncrease.divide(HUNDRED)));
            BigDecimal clamped = calculated.max(floor).min(ceiling);
            long rounded = clamped.setScale(0, RoundingMode.HALF_UP).longValueExact();
            return rounded > 0L ? OptionalLong.of(rounded) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private static BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) {
            throw new NumberFormatException("non finite pricing configuration");
        }
        return BigDecimal.valueOf(value);
    }
}
