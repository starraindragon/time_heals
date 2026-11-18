package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 时序裁决器的方块实体。
 *
 * <p>目前负责：
 * <ul>
 *   <li>仪式进度（0~1），同步到客户端供底盘进度槽渲染；</li>
 *   <li>给客户端渲染器提供计时（中央水晶自旋 + 上下浮动）；</li>
 *   <li>为后续状态机预留阶段字段。</li>
 * </ul>
 *
 * <p>右键会启动一次「演示仪式」（20 秒），用于验证进度槽的绿→红过渡；
 * 等状态机接入后，改成由真实仪式驱动即可。
 */
public class VerdictDeviceBlockEntity extends BlockEntity {

    /** 演示仪式总时长（tick）。 */
    public static final int DEMO_TICKS = 20 * 20;

    /** 0 = 待机，后续接入锁定/破晶/蓄能/裁决/收尾。 */
    private int phase = 0;
    /** 已破坏的水晶数量（0~8）。 */
    private int crystalsBroken = 0;
    /** 仪式进度 0~1。 */
    private float progress = 0.0F;
    /** 剩余演示 tick；>0 表示仪式进行中。 */
    private int remainingTicks = 0;
    /** 结束后恢复待机的倒计时。 */
    private int resetTicks = 0;

    public VerdictDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(TimeHeals.VERDICT_DEVICE_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
        setChanged();
    }

    public int getCrystalsBroken() {
        return crystalsBroken;
    }

    public void setCrystalsBroken(int crystalsBroken) {
        this.crystalsBroken = crystalsBroken;
        setChanged();
    }

    public float getProgress() {
        return progress;
    }

    public boolean isRunning() {
        return remainingTicks > 0;
    }

    /** 右键启动一次演示仪式。 */
    public void startDemo() {
        this.remainingTicks = DEMO_TICKS;
        this.resetTicks = 0;
        this.progress = 0.0F;
        this.phase = 1;
        setChanged();
        sync();
    }

    // ------------------------------------------------------------------
    // 服务端 tick
    // ------------------------------------------------------------------

    public void serverTick() {
        if (remainingTicks > 0) {
            remainingTicks--;
            progress = 1.0F - remainingTicks / (float) DEMO_TICKS;
            // 每 2 tick 同步一次，够平滑也不至于刷包
            if (remainingTicks % 2 == 0 || remainingTicks == 0) {
                sync();
            }
            if (remainingTicks == 0) {
                progress = 1.0F;
                resetTicks = 40;
                setChanged();
                sync();
            }
        } else if (resetTicks > 0) {
            resetTicks--;
            if (resetTicks == 0) {
                progress = 0.0F;
                phase = 0;
                setChanged();
                sync();
            }
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------
    // 存档与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("phase", phase);
        tag.putInt("crystals", crystalsBroken);
        tag.putFloat("progress", progress);
        tag.putInt("remaining", remainingTicks);
        tag.putInt("reset", resetTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        phase = tag.getInt("phase");
        crystalsBroken = tag.getInt("crystals");
        progress = tag.getFloat("progress");
        remainingTicks = tag.getInt("remaining");
        resetTicks = tag.getInt("reset");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("progress", progress);
        tag.putInt("phase", phase);
        tag.putInt("crystals", crystalsBroken);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
