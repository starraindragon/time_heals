package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 时序裁决器的方块实体。
 *
 * <p>目前只承担两件事：
 * <ul>
 *   <li>给客户端渲染器提供计时（中央水晶自旋 + 上下浮动）；</li>
 *   <li>为后续状态机预留 NBT 字段（阶段、进度、目标 UUID）。</li>
 * </ul>
 */
public class VerdictDeviceBlockEntity extends BlockEntity {

    /** 0 = 待机，后续接入锁定/破晶/蓄能/裁决/收尾。 */
    private int phase = 0;
    /** 已破坏的水晶数量（0~8）。 */
    private int crystalsBroken = 0;

    public VerdictDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(TimeHeals.VERDICT_DEVICE_BE.get(), pos, state);
    }

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
}
