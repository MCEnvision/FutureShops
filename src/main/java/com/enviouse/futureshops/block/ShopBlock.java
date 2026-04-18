package com.enviouse.futureshops.block;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import com.enviouse.futureshops.server.shop.PlayerShopRegistrySavedData;
import com.enviouse.futureshops.server.shop.ShopLimitsSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ShopBlock extends BaseEntityBlock {
    public ShopBlock(Properties properties) {
        super(properties);
    }

    /**
     * Item 14 (fix): Prevent CarryOn and pistons from moving shop blocks.
     * CarryOn respects PushReaction.BLOCK — returning it denies pickup entirely.
     */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
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
        if (level.getBlockEntity(pos) instanceof ShopBlockEntity shop) {
            if (shop.getOwnerUuid() == null && placer instanceof Player player) {
                // Check per-player shop block limit before assigning ownership
                if (!level.isClientSide && level.getServer() != null) {
                    ShopLimitsSavedData limits = ShopLimitsSavedData.get(level.getServer());
                    int currentCount = PlayerShopRegistrySavedData.get(level.getServer())
                            .getOwnedShops(player.getUUID()).size();
                    if (!limits.canPlace(player.getUUID(), currentCount)) {
                        // Deny placement — break block and return item
                        player.sendSystemMessage(Component.literal("§cYou have reached your shop block limit ("
                                + limits.getMaxShopBlocks(player.getUUID()) + "). Ask an admin to increase it.")
                                .withStyle(ChatFormatting.RED));
                        level.destroyBlock(pos, true);
                        return;
                    }
                }
                shop.setOwnerUuid(player.getUUID());
            }
            // Item 14: Always (re-)register the shop at its new position.
            // This covers CarryOn mod compatibility: when a shop is picked up, its
            // block entity NBT (owner, listings, etc.) is preserved. On removal the
            // old position is deregistered. On placement we must register the new
            // position so the Nearby Shops scanner and dashboard can find it.
            if (!level.isClientSide && level.getServer() != null && shop.getOwnerUuid() != null) {
                PlayerShopRegistrySavedData.get(level.getServer()).register(
                        shop.getOwnerUuid(), level.dimension().location(), pos.asLong());
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
            // Register new shop in the registry
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
