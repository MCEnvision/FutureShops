package com.enviouse.futureshopsp.block;

import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import com.enviouse.futureshopsp.server.shop.PlayerShopRegistrySavedData;
import com.enviouse.futureshopsp.server.shop.ShopLimitsSavedData;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ShopBlock extends BaseEntityBlock {
    public static final MapCodec<ShopBlock> CODEC = simpleCodec(ShopBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Matches the GeckoLib model bounds (geo cubes span ~[-6.5, 6.5] X/Z and
    // [0, 8.5] Y in blockbench units; block-local voxel space is 0..16 with the
    // model anchor at (8, 0, 8)). Shape is symmetrical so it doesn't need to
    // vary per facing.
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(
            1.5D, 0.0D, 1.5D, 14.5D, 9.0D, 14.5D);

    public ShopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the player — model's "front" points toward whoever placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
        // The actual block model is a GeckoLib animatable drawn by the block
        // entity renderer — disable the vanilla model pipeline.
        return RenderShape.ENTITYBLOCK_ANIMATED;
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
            // Stamp admin-shop eligibility when the placer was in creative mode.
            // One-time at placement; persists with the block entity.
            if (placer instanceof Player creativePlacer && creativePlacer.getAbilities().instabuild) {
                shop.setPlacedByCreative(true);
                shop.setChanged();
            }
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
                shop.setOwnerUuid(player.getUUID(), player.getGameProfile().getName());
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // If the player is sneaking while holding a placeable block item, pass through so
        // they can place the block against the shop instead of opening its UI. Fixes the
        // "shop UI pops up while I'm building" complaint.
        ItemStack held = player.getMainHandItem();
        if (player.isShiftKeyDown()
                && held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() != this) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
            return InteractionResult.PASS;
        }

        if (shop.getOwnerUuid() == null) {
            shop.setOwnerUuid(player.getUUID(), player.getGameProfile().getName());
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
