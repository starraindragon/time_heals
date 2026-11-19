package com.xing_yu_dragon.timeheals;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 裁决器自毁后留下的残骸。
 *
 * <p>直接继承原版 {@link BrushableBlock}，因此刷子交互、动画、粒子、音效、
 * DUSTED 进度全部走原版逻辑；刷满后变成哭泣的黑曜石，
 * 掉落表指向本模组的「报废的神龙权杖」。
 */
public class VerdictRemainsBlock extends BrushableBlock {

    /** 刷取时用的战利品表。 */
    public static final ResourceKey<LootTable> BRUSH_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "blocks/verdict_remains_brush"));

    public VerdictRemainsBlock(Properties properties) {
        super(Blocks.CRYING_OBSIDIAN, SoundEvents.BRUSH_SAND, SoundEvents.BRUSH_SAND_COMPLETED, properties);
    }

    @Override
    public MapCodec<BrushableBlock> codec() {
        return BrushableBlock.CODEC;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BrushableBlockEntity brushable) {
            brushable.setLootTable(BRUSH_LOOT, level.random.nextLong());
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrushableBlockEntity(pos, state);
    }
}
