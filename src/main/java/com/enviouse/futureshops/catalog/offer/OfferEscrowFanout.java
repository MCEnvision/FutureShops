package com.enviouse.futureshops.catalog.offer;

import com.enviouse.futureshops.server.escrow.playershop
        .PlayerShopEscrowConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

public final class OfferEscrowFanout {
    private OfferEscrowFanout() {
    }

    public static boolean fits(
            List<ComponentUnits> inputs,
            List<ComponentUnits> outputs
    ) {
        return fits(inputs, outputs, ignored -> 64);
    }

    public static boolean fits(
            List<ComponentUnits> inputs,
            List<ComponentUnits> outputs,
            ToIntFunction<String> maximumStackSize
    ) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(maximumStackSize, "maximumStackSize");
        try {
            long inputPortions = portions(inputs, maximumStackSize);
            long outputPortions = portions(outputs, maximumStackSize);
            return outputPortions <= PlayerShopEscrowConstants.MAX_CLAIMS
                    && Math.addExact(inputPortions, outputPortions)
                    <= PlayerShopEscrowConstants.MAX_TRANSFERS;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return false;
        }
    }

    public static int registeredMaximumStackSize(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id == null
                ? 1
                : BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> item.getMaxStackSize())
                .orElse(1);
    }

    private static long portions(
            List<ComponentUnits> components,
            ToIntFunction<String> maximumStackSize
    ) {
        long total = 0L;
        for (ComponentUnits component : components) {
            int stackSize = Math.max(1, Math.min(Byte.MAX_VALUE,
                    maximumStackSize.applyAsInt(component.itemId())));
            long portions = Math.floorDiv(
                    Math.addExact(component.units(), stackSize - 1L),
                    stackSize);
            if (portions > PlayerShopEscrowConstants.MAX_ITEM_PORTIONS) {
                throw new IllegalArgumentException(
                        "Offer component exceeds the escrow portion limit");
            }
            total = Math.addExact(total, portions);
        }
        return total;
    }

    public record ComponentUnits(
            String itemId,
            String exactNbt,
            long units
    ) {
        public ComponentUnits {
            itemId = Objects.requireNonNullElse(itemId, "").strip();
            exactNbt = Objects.requireNonNullElse(exactNbt, "");
            if (itemId.isEmpty() || units <= 0L) {
                throw new IllegalArgumentException(
                        "Offer escrow component is invalid");
            }
        }
    }
}
