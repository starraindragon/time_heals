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

    /** 每根柱子上的圆环层数。 */
    public static final int RING_LAYERS = 14;
    /** 两次破晶之间的间隔（tick）：2.5 秒，保证单个特效能完整演完。 */
    public static final int CRYSTAL_INTERVAL = 50;
    /** 锁定期时长（tick）：5 秒，中央水晶向混沌守卫发射追踪粒子束。 */
    public static final int LOCK_TICKS = 100;
    /** 锁定半径（格）：覆盖整个混沌岛，但不会跨维度/跨岛乱锁。 */
    public static final double LOCK_RADIUS = 256.0D;
    /** 蓄能期时长（tick）：5 秒，柱子向核心发射粒子束。 */
    public static final int CHARGE_TICKS = 100;
    /** 每轮破晶内，先连接多久（tick）再爆；剩下的时间留给爆炸与下一次连接。 */
    public static final int LINK_TICKS = 40;
    /** 攻击阶段时长（tick）：5 tick，尖啸音波从水晶飞到目标。 */
    public static final int ATTACK_TICKS = 5;
    /** 蓄能结束后的收尾时间（tick）。 */
    public static final int TAIL_TICKS = 40;
    /** 演示总时长 = 破晶阶段 + 蓄能 + 收尾，全部由各阶段时长推导。 */
    public static final int RITUAL_TICKS =
            CRYSTAL_INTERVAL * RING_LAYERS + CHARGE_TICKS + LOCK_TICKS
                    + ATTACK_TICKS + TAIL_TICKS;
    /** 阶段常量。 */
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_LOCK = 1;
    public static final int PHASE_BREAK = 2;
    public static final int PHASE_CHARGE = 3;
    public static final int PHASE_ATTACK = 4;
    public static final int PHASE_DONE = 5;

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
    /** 锁定的混沌守卫（UUID）；没有目标时为空。 */
    private java.util.UUID targetUuid = null;
    /** 当前正在连接的混沌水晶（UUID）。 */
    private java.util.UUID crystalUuid = null;
    /** 瞄准点（相对方块坐标，客户端渲染用）。 */
    private float aimX, aimY, aimZ;
    private boolean hasAim = false;

    /** 已进行 tick。 */
    private int elapsedTicks = 0;
    /** 是否已经打印过连接目标（避免刷屏）。 */
    private boolean loggedLinkTarget = false;
    /** 合成时是否使用了「无法破坏 + 已标记」的时间之剑。 */
    private boolean usedUnbreakableSword = false;
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

    public boolean isUsedUnbreakableSword() {
        return usedUnbreakableSword;
    }

    public void setUsedUnbreakableSword(boolean used) {
        this.usedUnbreakableSword = used;
        setChanged();
    }

    public int getCrystalRings() {
        return crystalRings;
    }

    public boolean hasAim() {
        return hasAim;
    }

    public float getAimX() {
        return aimX;
    }

    public float getAimY() {
        return aimY;
    }

    public float getAimZ() {
        return aimZ;
    }

    /** 锁定期：找到混沌守卫并把瞄准点同步到客户端。 */
    private void updateAim() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        net.minecraft.world.entity.Entity target = null;
        if (targetUuid != null) {
            target = server.getEntity(targetUuid);
        }
        if (target == null || !target.isAlive()) {
            // 在半径内扫描混沌守卫：机器放在岛上任意位置都能锁定，
            // 超出岛屿范围（例如主岛）则不会误锁
            double maxSq = LOCK_RADIUS * LOCK_RADIUS;
            double bestSq = Double.MAX_VALUE;
            for (net.minecraft.world.entity.Entity candidate : server.getAllEntities()) {
                if (!candidate.isAlive()
                        || !candidate.getType().builtInRegistryHolder().key().location().toString()
                                .equals("draconicevolution:draconic_guardian")) {
                    continue;
                }
                double d = candidate.distanceToSqr(worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
                if (d <= maxSq && d < bestSq) {
                    bestSq = d;
                    target = candidate;
                }
            }
            targetUuid = target == null ? null : target.getUUID();
        }
        if (target == null) {
            hasAim = false;
            return;
        }
        if (!hasAim) {
            TimeHeals.LOGGER.info("[时间混乱] 时序裁决器锁定目标: {}", target.getName().getString());
        }
        aimX = (float) (target.getX() - (worldPosition.getX() + 0.5D));
        aimY = (float) (target.getY() + target.getBbHeight() * 0.5D - worldPosition.getY());
        aimZ = (float) (target.getZ() - (worldPosition.getZ() + 0.5D));
        hasAim = true;
    }

    public boolean isRunning() {
        return remainingTicks > 0;
    }

    /** 消耗时间水晶启动仪式。 */
    public void startRitual() {
        this.remainingTicks = RITUAL_TICKS;
        this.elapsedTicks = 0;
        this.resetTicks = 0;
        this.progress = 0.0F;
        this.crystalRings = 0;
        this.crystalsBroken = 0;
        this.ritualTime = 0;
        this.elapsedTicks = 0;
        this.loggedLinkTarget = false;
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
            elapsedTicks++;
            ritualTime++;

            // 每 CRYSTAL_INTERVAL 破一颗，保证每个特效都有完整演出时间
            if (elapsedTicks % CRYSTAL_INTERVAL == 0 && crystalRings < RING_LAYERS) {
                onCrystalBroken();
            }

            // 阶段推进：破晶 14 层 → 蓄能 5 秒 → 锁定 5 秒 → 收尾
            int breakEnd = CRYSTAL_INTERVAL * RING_LAYERS;
            int chargeEnd = breakEnd + CHARGE_TICKS;
            int lockEnd = chargeEnd + LOCK_TICKS;
            int attackEnd = lockEnd + ATTACK_TICKS;
            int newPhase;
            if (elapsedTicks <= breakEnd) {
                newPhase = PHASE_BREAK;
                progress = Math.min(1.0F, elapsedTicks / (float) breakEnd);
            } else if (elapsedTicks <= chargeEnd) {
                newPhase = PHASE_CHARGE;
                progress = 1.0F;
            } else if (elapsedTicks <= lockEnd) {
                newPhase = PHASE_LOCK;
                progress = 1.0F;
                updateAim();
            } else if (elapsedTicks <= attackEnd) {
                newPhase = PHASE_ATTACK;
                progress = 1.0F;
                updateAim();
                fireSonicAttack(elapsedTicks - lockEnd);
            } else {
                newPhase = PHASE_DONE;
                progress = 1.0F;
            }
            // 蓄能期持续嗡鸣：音高随时间上升
            if (phase == PHASE_CHARGE && elapsedTicks % 20 == 0) {
                float k = (elapsedTicks - lockEndStart(breakEnd)) / (float) CHARGE_TICKS;
                playAt(TimeSounds.VERDICT_CHARGE_LOOP.get(), 1.2F,
                        0.8F + Math.min(0.7F, k * 0.7F));
            }

            if (newPhase != phase) {
                phase = newPhase;
                onPhaseEnter(newPhase);
                setChanged();
                sync();
            }

            if (remainingTicks % 2 == 0 || remainingTicks == 0) {
                sync();
            }
            if (remainingTicks == 0) {
                progress = 1.0F;
                setChanged();
                sync();
                // 一次性装置：仪式结束即自毁，原地留下哭泣的黑曜石
                convertToCryingObsidian();
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

    /** 仪式结束：机器自毁，原地变成哭泣的黑曜石。 */
    private void convertToCryingObsidian() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        BlockPos pos = worldPosition;
        // 先播完这一下的粒子与音效，再把方块换掉（换掉后 BE 会被移除）
        particles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                40, 1.0D, 1.0D, 1.0D, 0.1D);
        playAt(TimeSounds.VERDICT_DONE.get(), 2.0F, 0.7F);
        server.setBlockAndUpdate(pos, TimeHeals.VERDICT_REMAINS.get().defaultBlockState());
        // 10 格内目睹机器化灰的玩家获得成就
        TimeAdvancements.eventNearby(server, pos, 10.0D, "who_is_crying");
        if (usedUnbreakableSword) {
            spawnWornSword(server, pos);
        }
        // 1% 时序反噬：5 格内所有玩家承受混沌级伤害
        triggerTemporalBacklash(server, pos);
        TimeHeals.LOGGER.info("[时间混乱] 时序裁决器已在 {} 自毁为哭泣的黑曜石", pos);
    }

    /** 自毁时的 1% 时序反噬。 */
    private void triggerTemporalBacklash(net.minecraft.server.level.ServerLevel server, BlockPos pos) {
        if (server.random.nextFloat() >= 0.01F) {
            return;
        }
        TemporalBacklash.trigger(server, pos, true, null, "mutual_destruction");
        playAt(TimeSounds.CRYSTAL_EXPLODE.get(), 3.0F, 0.5F);
    }

    /** 在哭泣的黑曜石上方生成一把只剩 1 点耐久的时间之剑。 */
    private void spawnWornSword(net.minecraft.server.level.ServerLevel server, BlockPos pos) {
        net.minecraft.world.item.Item sword = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("kubejs", "time_sword"));
        if (sword == net.minecraft.world.item.Items.AIR) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(sword);
        stack.set(TimeHealsComponents.VERDICT_WORN_SWORD.get(), true);
        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            stack.set(net.minecraft.core.component.DataComponents.DAMAGE, Math.max(0, maxDamage - 1));
        }
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                server, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, stack);
        entity.setDefaultPickUpDelay();
        server.addFreshEntity(entity);
        TimeHeals.LOGGER.info("[时间混乱] 裁决器返还了一把 1 耐久的时间之剑");
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
        crystalsBroken++;
        spawnPillarRings(layer);
        setChanged();
        sync();
    }

    private static int lockEndStart(int breakEnd) {
        return breakEnd;
    }

    /** 阶段进入时播放对应音效。 */
    private void onPhaseEnter(int newPhase) {
        switch (newPhase) {
            case PHASE_BREAK -> playAt(TimeSounds.VERDICT_BREAK.get(), 1.6F, 1.15F);
            case PHASE_CHARGE -> playAt(TimeSounds.VERDICT_CHARGE.get(), 1.6F, 0.9F);
            case PHASE_LOCK -> playAt(TimeSounds.VERDICT_LOCK.get(), 1.4F, 1.3F);
            case PHASE_ATTACK -> playAt(TimeSounds.VERDICT_ATTACK.get(), 2.0F, 1.0F);
            case PHASE_DONE -> playAt(TimeSounds.VERDICT_DONE.get(), 2.0F, 0.8F);
            default -> {
            }
        }
    }

    /**
     * 对锁定的混沌守卫造成一次混沌级伤害。
     *
     * <p>用 {@code draconicevolution:chaos_implosion}（属于 DE 的 chaotic 标签），
     * DE 会把这次伤害视为混沌级，忽略护盾与常规减伤。
     * 若附近还有存活的水晶（护盾未破），只造成伤害、不强行处决。
     */
    private void strikeGuardian() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        net.minecraft.world.entity.Entity target =
                targetUuid == null ? null : server.getEntity(targetUuid);
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) {
            return;
        }

        var registry = server.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var holder = registry.getHolder(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "draconicevolution", "chaos_implosion")).orElse(null);
        net.minecraft.world.damagesource.DamageSource source = holder != null
                ? new net.minecraft.world.damagesource.DamageSource(holder)
                : server.damageSources().magic();

        living.invulnerableTime = 0;
        // 混沌守卫：护盾 16000 + 本体 1000（DE 默认值），
        // 伤害必须一次性超过「护盾 + 本体」才能连盾带血一起打掉。
        float amount = Math.max(50000.0F, living.getMaxHealth() * 50.0F);
        boolean damaged = living.hurt(source, amount);

        // 护盾已破（附近没有存活的水晶）时，补上处决，保证仪式能真正终结守卫
        if (living.isAlive() && !hasLivingCrystal(server)) {
            living.setHealth(0.0F);
            living.die(source);
        }
        if (!living.isAlive()) {
            TimeHeals.LOGGER.info("[时间混乱] 时序裁决器已处决 {}", living.getName().getString());
            // 混沌守卫确由这次裁决伤害击杀 → 发放成就
            TimeAdvancements.eventNearby(server, worldPosition, 32.0D, "godslayer");
        }
    }

    /** 附近是否还有存活的混沌水晶。 */
    private boolean hasLivingCrystal(net.minecraft.server.level.ServerLevel server) {
        double maxSq = LOCK_RADIUS * LOCK_RADIUS;
        for (net.minecraft.world.entity.Entity candidate : server.getAllEntities()) {
            if (candidate.isAlive()
                    && isGuardianCrystal(candidate)
                    && candidate.distanceToSqr(worldPosition.getX() + 0.5D,
                            worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= maxSq) {
                return true;
            }
        }
        return false;
    }

    /** 破晶阶段：四根柱子顶端用 END_ROD 连到混沌守卫的水晶（末影龙连接水晶同款粒子）。 */
    private void spawnDragonLinks() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        net.minecraft.world.entity.Entity crystal = currentCrystal(server);
        if (crystal == null) {
            return;
        }
        double cx = crystal.getX();
        double cy = crystal.getY() + crystal.getBbHeight() * 0.5D;
        double cz = crystal.getZ();
        float[] px = {-7.5F / 16.0F, 23.5F / 16.0F, -7.5F / 16.0F, 23.5F / 16.0F};
        float[] pz = {-7.5F / 16.0F, -7.5F / 16.0F, 23.5F / 16.0F, 23.5F / 16.0F};
        int points = 12;
        for (int p = 0; p < 4; p++) {
            double sx = worldPosition.getX() + px[p];
            double sy = worldPosition.getY() + 2.0D;
            double sz = worldPosition.getZ() + pz[p];
            for (int i = 0; i < points; i++) {
                double t = (i + server.random.nextDouble()) / points;
                particles(ParticleTypes.END_ROD,
                        Mth.lerp(t, sx, cx), Mth.lerp(t, sy, cy), Mth.lerp(t, sz, cz),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 在机器位置播放音效，强制发给该维度所有玩家（不受距离衰减限制）。 */
    private void playAt(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        playAtPos(sound, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D, volume, pitch);
    }

    /** 在指定坐标播放音效，强制发给该维度所有玩家。 */
    private void playAtPos(net.minecraft.sounds.SoundEvent sound,
                           double x, double y, double z, float volume, float pitch) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder =
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayers(p -> true)) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    holder, net.minecraft.sounds.SoundSource.BLOCKS,
                    x, y, z, volume, pitch, server.random.nextLong()));
        }
    }

    /**
     * 强制把粒子发给该维度所有玩家。
     *
     * <p>{@code ServerLevel#sendParticles} 默认只发给 32 格内的玩家；
     * 混沌岛上的水晶可能离得很远，必须显式逐玩家发送（force=true）。
     */
    private void particles(net.minecraft.core.particles.ParticleOptions type,
                           double x, double y, double z, int count,
                           double dx, double dy, double dz, double speed) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayers(p -> true)) {
            server.sendParticles(player, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** 找到（或刷新）当前要连接的混沌水晶。 */
    private net.minecraft.world.entity.Entity currentCrystal(net.minecraft.server.level.ServerLevel server) {
        net.minecraft.world.entity.Entity crystal = null;
        if (crystalUuid != null) {
            crystal = server.getEntity(crystalUuid);
        }
        if (crystal != null && crystal.isAlive()) {
            return crystal;
        }
        double maxSq = LOCK_RADIUS * LOCK_RADIUS;
        double bestSq = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity candidate : server.getAllEntities()) {
            if (!candidate.isAlive()
                    || !candidate.getType().builtInRegistryHolder().key().location().toString()
                            .equals("draconicevolution:guardian_crystal")) {
                continue;
            }
            double d = candidate.distanceToSqr(worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
            if (d <= maxSq && d < bestSq) {
                bestSq = d;
                crystal = candidate;
            }
        }
        if (crystal == null && targetUuid != null) {
            net.minecraft.world.entity.Entity guardian = server.getEntity(targetUuid);
            if (guardian instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                crystal = living;
            }
        }
        crystalUuid = crystal == null ? null : crystal.getUUID();
        if (crystal != null) {
            aimX = (float) (crystal.getX() - (worldPosition.getX() + 0.5D));
            aimY = (float) (crystal.getY() + crystal.getBbHeight() * 0.5D - worldPosition.getY());
            aimZ = (float) (crystal.getZ() - (worldPosition.getZ() + 0.5D));
            hasAim = true;
        }
        if (crystal != null && !loggedLinkTarget) {
            loggedLinkTarget = true;
            TimeHeals.LOGGER.info("[时间混乱] 破晶连接目标: {} @ {}",
                    crystal.getName().getString(), crystal.blockPosition());
        }
        return crystal;
    }

    /** 是否是混沌守卫的水晶（只有水晶才能在破晶时被摧毁）。 */
    private static boolean isGuardianCrystal(net.minecraft.world.entity.Entity entity) {
        return entity.getType().builtInRegistryHolder().key().location().toString()
                .equals("draconicevolution:guardian_crystal");
    }

    /** 破晶瞬间：核心处爆炸粒子。 */
    private void spawnCrystalExplosion() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        net.minecraft.world.entity.Entity crystal = currentCrystal(server);
        // 没有可破坏的水晶时不播放爆炸（否则会错误地在机器位置炸开）
        if (crystal == null) {
            return;
        }
        double cx = crystal.getX();
        double cy = crystal.getY() + crystal.getBbHeight() * 0.5D;
        double cz = crystal.getZ();
        // 爆炸：低沉 + 远处也听得到
        playAtPos(TimeSounds.CRYSTAL_EXPLODE.get(), cx, cy, cz, 4.0F, 0.8F);
        // 机器处再补一记低频，强化“破晶”的冲击感
        playAt(TimeSounds.CRYSTAL_EXPLODE.get(), 2.5F, 0.6F);
        particles(ParticleTypes.EXPLOSION, cx, cy, cz, 3, 0.4D, 0.4D, 0.4D, 0.0D);
        particles(ParticleTypes.END_ROD, cx, cy, cz, 24, 0.6D, 0.6D, 0.6D, 0.15D);
        particles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 16, 0.5D, 0.5D, 0.5D, 0.2D);
        // 只有混沌水晶会被摧毁；守卫本体只吃粒子
        if (isGuardianCrystal(crystal)) {
            crystal.kill();
            crystalUuid = null;
        }
    }

    /** 攻击阶段：沿水晶→守卫的路径生成尖啸音波粒子（5 tick 内飞到）。 */
    private void fireSonicAttack(int step) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !hasAim) {
            return;
        }
        double sx = worldPosition.getX() + 0.5D;
        double sy = worldPosition.getY() + 1.25D;
        double sz = worldPosition.getZ() + 0.5D;
        double ex = worldPosition.getX() + 0.5D + aimX;
        double ey = worldPosition.getY() + aimY;
        double ez = worldPosition.getZ() + 0.5D + aimZ;

        float t = Math.min(1.0F, step / (float) ATTACK_TICKS);
        // 每个 tick 在当前位置生成一组音波粒子，形成一串冲击
        for (int i = 0; i < 3; i++) {
            double tt = Math.max(0.0D, t - i * 0.08D);
            double px = Mth.lerp(tt, sx, ex);
            double py = Mth.lerp(tt, sy, ey);
            double pz = Mth.lerp(tt, sz, ez);
            particles(ParticleTypes.SONIC_BOOM, px, py, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 最后一步：命中判定
        if (step >= ATTACK_TICKS) {
            strikeGuardian();
            for (int i = 0; i < 4; i++) {
                particles(ParticleTypes.SONIC_BOOM,
                        ex + (server.random.nextDouble() - 0.5D) * 2.0D,
                        ey + (server.random.nextDouble() - 0.5D) * 2.0D,
                        ez + (server.random.nextDouble() - 0.5D) * 2.0D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 四根柱子：模型坐标 (-7.5,-7.5)、(23.5,-7.5)、(-7.5,23.5)、(23.5,23.5)（单位 1/16 格）。 */
    private void spawnPillarRings(int layer) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        spawnRingLayer(server, layer, 20, 0.0F, true);
        playAt(TimeSounds.VERDICT_BREAK.get(), 1.2F, 0.9F + layer * 0.05F);
        spawnCrystalExplosion();
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
                particles(dust, px, y0 + y, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (burst && i % 5 == 0) {
                    particles(ParticleTypes.END_ROD, px, y0 + y, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
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
        tag.putBoolean("unbreakableSword", usedUnbreakableSword);
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
        usedUnbreakableSword = tag.getBoolean("unbreakableSword");
        hasAim = tag.getBoolean("hasAim");
        aimX = tag.getFloat("aimX");
        aimY = tag.getFloat("aimY");
        aimZ = tag.getFloat("aimZ");
        remainingTicks = tag.getInt("remaining");
        resetTicks = tag.getInt("reset");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("progress", progress);
        tag.putInt("rings", crystalRings);
        tag.putBoolean("unbreakableSword", usedUnbreakableSword);
        tag.putInt("phase", phase);
        tag.putInt("crystals", crystalsBroken);
        tag.putInt("rings", crystalRings);
        tag.putBoolean("unbreakableSword", usedUnbreakableSword);
        tag.putBoolean("hasAim", hasAim);
        tag.putFloat("aimX", aimX);
        tag.putFloat("aimY", aimY);
        tag.putFloat("aimZ", aimZ);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
