package com.enviouse.futureshops.coin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CoinItem extends Item {
    private final long denominationMinorUnits;

    public CoinItem(Properties properties, long denominationMinorUnits) {
        super(properties);
        this.denominationMinorUnits = denominationMinorUnits;
    }

    public long getDenominationMinorUnits() {
        return denominationMinorUnits;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        long whole = denominationMinorUnits / 100L;
        long fraction = Math.abs(denominationMinorUnits % 100L);
        tooltip.add(Component.translatable("tooltip.futureshops.coin_value", whole, String.format("%02d", fraction)).withStyle(ChatFormatting.GOLD));
    }
}

