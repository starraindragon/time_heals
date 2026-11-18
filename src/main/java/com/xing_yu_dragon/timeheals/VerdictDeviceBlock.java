package com.xing_yu_dragon.timeheals;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * 时序裁决器：3×3 底面的仪式装置。
 *
 * <p>本体只占中心 1 格，视觉由 {@link VerdictDeviceBlockEntity} 的自定义渲染器跨格绘制，
 * 因此碰撞箱只保留底座那一小块，避免玩家被“看不见的墙”挡住。
 */
public class VerdictDeviceBlock extends BaseEntityBlock {

    public static final MapCodec<VerdictDeviceBlock> CODEC = simpleCodec(VerdictDeviceBlock::new);

    /** 底座 2 单位高（模型 y=0~2），其余部分不参与碰撞。 */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public VerdictDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VerdictDeviceBlockEntity(pos, state);
    }
}
