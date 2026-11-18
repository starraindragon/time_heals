package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 时序裁决器的结构块：不可见、不可破坏，只负责给 3×3 底座补齐碰撞。
 *
 * <p>方块自身的碰撞箱无法跨出所在格（实体碰撞只检查实体包围盒覆盖到的方块），
 * 所以 3×3 的底面必须由 9 个方块拼出来：中间是控制器，周围 8 个就是本方块。
 * 由控制器在放置/破坏时自动放置与回收。
 */
public class VerdictDevicePartBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public VerdictDevicePartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
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
}
