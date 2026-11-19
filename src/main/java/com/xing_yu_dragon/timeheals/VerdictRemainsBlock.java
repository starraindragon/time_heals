package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

/**
 * 裁决器自毁后留下的残骸（看起来是哭泣的黑曜石）。
 *
 * <p>用刷子反复刷，进度满后掉出「报废的神龙权杖」，方块变回普通哭泣的黑曜石。
 */
public class VerdictRemainsBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<VerdictRemainsBlock> CODEC =
            simpleCodec(VerdictRemainsBlock::new);

    public VerdictRemainsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VerdictRemainsBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.BRUSH)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof VerdictRemainsBlockEntity remains)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!remains.brush()) {
            return ItemInteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.BRUSH_SAND, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT,
                    pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                    6, 0.3D, 0.2D, 0.3D, 0.02D);
        }
        if (remains.getProgress() >= VerdictRemainsBlockEntity.REQUIRED_BRUSHES) {
            ItemStack drop = new ItemStack(TimeHeals.DAMAGE_DRACONIC_STAFF.get());
            ItemEntity entity = new ItemEntity(level,
                    pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, drop);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
            level.playSound(null, pos, SoundEvents.BRUSH_SAND_COMPLETED, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.setBlockAndUpdate(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            TimeHeals.LOGGER.info("[时间混乱] 残骸被刷开，掉出报废的神龙权杖");
        }
        return ItemInteractionResult.SUCCESS;
    }
}
