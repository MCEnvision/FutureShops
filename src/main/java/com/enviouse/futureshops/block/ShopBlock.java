package com.enviouse.futureshops.block;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.enviouse.futureshops.server.shop.PlayerShopRegistrySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ShopBlock extends BaseEntityBlock {
    public ShopBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof ShopBlockEntity shop) {
            if (shop.getOwnerUuid() == null) {
                shop.setOwnerUuid(player.getUUID());
                if (!level.isClientSide && level.getServer() != null) {
                    PlayerShopRegistrySavedData.get(level.getServer()).register(player.getUUID(), level.dimension().location(), pos.asLong());
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getServer() != null) {
            PlayerShopRegistrySavedData.get(level.getServer()).remove(level.dimension().location(), pos.asLong());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return InteractionResult.PASS;
        }

        if (shop.getOwnerUuid() == null) {
            shop.setOwnerUuid(player.getUUID());
            if (level.getServer() != null) {
                PlayerShopRegistrySavedData.get(level.getServer()).register(player.getUUID(), level.dimension().location(), pos.asLong());
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PlayerShopBlockService.openFor(serverPlayer, pos, player.isShiftKeyDown());
        }
        return InteractionResult.CONSUME;
    }
}
