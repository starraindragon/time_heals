package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 裁决器残骸：记录刷子进度。 */
public class VerdictRemainsBlockEntity extends BlockEntity {

    public static final int REQUIRED_BRUSHES = 12;

    private int progress = 0;

    public VerdictRemainsBlockEntity(BlockPos pos, BlockState state) {
        super(TimeHeals.VERDICT_REMAINS_BE.get(), pos, state);
    }

    public int getProgress() {
        return progress;
    }

    public boolean brush() {
        if (progress >= REQUIRED_BRUSHES) {
            return false;
        }
        progress++;
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("brush", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("brush");
    }
}
