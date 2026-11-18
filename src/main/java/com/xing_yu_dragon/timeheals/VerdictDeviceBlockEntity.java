package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
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
    /** 每根柱子上的圆环层数。 */
    public static final int RING_LAYERS = 14;
    /** 每次破晶的间隔（tick），仅演示用。 */
    private static final int DEMO_CRYSTAL_INTERVAL = 50;

    /** 0 = 待机，后续接入锁定/破晶/蓄能/裁决/收尾。 */
    private int phase = 0;
    /** 已破坏的水晶数量（0~8）。 */
    private int crystalsBroken = 0;
    /** 仪式进度 0~1。 */
    private float progress = 0.0F;
    /** 已生成的柱子圆环层数（0~14）。 */
    private int crystalRings = 0;
    /** 仪式已进行的 tick，用于圆环旋转相位。 */
    private int ritualTime = 0;
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

    public int getCrystalRings() {
        return crystalRings;
    }

    public boolean isRunning() {
        return remainingTicks > 0;
    }

    /** 右键启动一次演示仪式。 */
    public void startDemo() {
        this.remainingTicks = DEMO_TICKS;
        this.resetTicks = 0;
        this.progress = 0.0F;
        this.crystalRings = 0;
        this.crystalsBroken = 0;
        this.ritualTime = 0;
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
            ritualTime++;
            progress = 1.0F - remainingTicks / (float) DEMO_TICKS;
            // 每 2 tick 同步一次，够平滑也不至于刷包
            if (remainingTicks % 2 == 0 || remainingTicks == 0) {
                sync();
            }
            if (remainingTicks % DEMO_CRYSTAL_INTERVAL == 0) {
                onCrystalBroken();
            }
            // 仪式期间让已生成的圆环持续存在（每 6 tick 刷新一次，缓慢旋转）
            if (crystalRings > 0 && ritualTime % 6 == 0) {
                tickPillarRings();
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

    /** 打破一颗水晶：在四根柱子上依次生成一层圆环粒子。 */
    public void onCrystalBroken() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (crystalRings >= RING_LAYERS) {
            return;
        }
        int layer = crystalRings;
        crystalRings++;
        crystalsBroken = Math.min(8, crystalsBroken + 1);
        spawnPillarRings(layer);
        setChanged();
        sync();
    }

    /** 仪式期间持续点亮所有已生成的圆环层。 */
    private void tickPillarRings() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        float phase = ritualTime * 0.03F;
        for (int layer = 0; layer < crystalRings; layer++) {
            spawnRingLayer(server, layer, 8, phase, false);
        }
    }

    /** 四根柱子：模型坐标 (-7.5,-7.5)、(23.5,-7.5)、(-7.5,23.5)、(23.5,23.5)（单位 1/16 格）。 */
    private void spawnPillarRings(int layer) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        spawnRingLayer(server, layer, 20, 0.0F, true);
    }

    /** 在第 layer 层、四根柱子上铺一圈粒子。points 为每圈点数，phase 让圆环缓慢转动。 */
    private void spawnRingLayer(net.minecraft.server.level.ServerLevel server, int layer,
                                int points, float phase, boolean burst) {
        float y = 0.30F + (1.62F * layer) / (RING_LAYERS - 1);
        float[] xs = {-7.5F / 16.0F, 23.5F / 16.0F, -7.5F / 16.0F, 23.5F / 16.0F};
        float[] zs = {-7.5F / 16.0F, -7.5F / 16.0F, 23.5F / 16.0F, 23.5F / 16.0F};

        float t = (layer + 1) / (float) RING_LAYERS;
        Vector3f rgb = new Vector3f(
                Mth.lerp(t, 0.22F, 1.00F),
                Mth.lerp(t, 0.83F, 0.23F),
                Mth.lerp(t, 0.62F, 0.19F));
        DustParticleOptions dust = new DustParticleOptions(rgb, burst ? 0.9F : 0.7F);

        double x0 = worldPosition.getX();
        double y0 = worldPosition.getY();
        double z0 = worldPosition.getZ();
        for (int p = 0; p < 4; p++) {
            double cx = x0 + xs[p];
            double cz = z0 + zs[p];
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0D) * i / points + phase;
                double px = cx + Math.cos(a) * 0.34D;
                double pz = cz + Math.sin(a) * 0.34D;
                server.sendParticles(dust, px, y0 + y, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (burst && i % 5 == 0) {
                    server.sendParticles(ParticleTypes.END_ROD, px, y0 + y, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
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
        tag.putInt("rings", crystalRings);
        tag.putInt("remaining", remainingTicks);
        tag.putInt("reset", resetTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        phase = tag.getInt("phase");
        crystalsBroken = tag.getInt("crystals");
        progress = tag.getFloat("progress");
        crystalRings = tag.getInt("rings");
        remainingTicks = tag.getInt("remaining");
        resetTicks = tag.getInt("reset");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("progress", progress);
        tag.putInt("rings", crystalRings);
        tag.putInt("phase", phase);
        tag.putInt("crystals", crystalsBroken);
        tag.putInt("rings", crystalRings);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
