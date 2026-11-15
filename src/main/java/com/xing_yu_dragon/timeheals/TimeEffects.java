package com.xing_yu_dragon.timeheals;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 「时间混乱」效果注册与分层逻辑。
 *
 * <p>共 3 层（amplifier 0/1/2），每次被时间系列武器命中时叠加一层：
 * <ul>
 *   <li><b>I</b>：移速 -30%，受到伤害 ×1.10，持续 10 秒；</li>
 *   <li><b>II</b>：无法移动（移速归零），受到伤害 ×1.32，持续 30 秒；</li>
 *   <li><b>III</b>：剥夺 AI + 无法移动，受到伤害 ×1.584，
 *       每秒损失当前生命值的 10%（无视护甲/抗性/吸收/混沌守卫护盾），持续 60 秒。</li>
 * </ul>
 *
 * <p>第 III 层附带视觉/听觉反馈：进入时爆发粒子与共鸣音效，
 * 持续期间环绕紫色时空粒子，每秒侵蚀时迸发伤害粒子。
 */
public final class TimeEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, TimeHeals.MODID);

    /** 时间侵蚀伤害类型（仅用于死亡信息；实际扣血走 setHealth 直减）。 */
    public static final ResourceKey<DamageType> TIME_EROSION = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "time_erosion"));

    /** 移速修饰符 ID。 */
    private static final ResourceLocation SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "time_confusion_speed");

    public static final DeferredHolder<MobEffect, MobEffect> TIME_CONFUSION =
            MOB_EFFECTS.register("time_confusion", TimeConfusionEffect::new);

    private TimeEffects() {
    }

    // ------------------------------------------------------------------
    // 效果实现
    // ------------------------------------------------------------------

    public static final class TimeConfusionEffect extends MobEffect {

        public TimeConfusionEffect() {
            super(MobEffectCategory.HARMFUL, 0x8E44AD);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide()) {
                TimeHealsLayers.maintain(entity, amplifier);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------
    // 分层逻辑（供效果 tick / 事件复用）
    // ------------------------------------------------------------------

    public static final class TimeHealsLayers {

        /** 持久化数据：当前层数（用于检测升级到第 3 层）。 */
        private static final String LAYER_TAG = "time_heals_layer";

        private TimeHealsLayers() {
        }

        /** 每 tick 维护：移速、AI、每秒侵蚀伤害、第 3 层表现。 */
        public static void maintain(LivingEntity entity, int amplifier) {
            int layer = Math.max(0, Math.min(2, amplifier));
            if (!(entity instanceof Mob mob)) {
                return;
            }

            // 层数变化（尤其是进入第 3 层）时播放一次性特效
            int previousLayer = mob.getPersistentData().getInt(LAYER_TAG);
            if (layer != previousLayer) {
                mob.getPersistentData().putInt(LAYER_TAG, layer);
                if (layer == 2) {
                    playLayer3Enter(mob);
                }
            }

            // 移速：第 1 层 -30%，第 2 层起归零
            double speedModifier = layer == 0 ? TimeHealsConfig.LAYER1_SPEED : -1.0D;
            applySpeed(mob, speedModifier);

            // AI：只有第 3 层剥夺
            if (layer >= 2) {
                stripAi(mob);
                // 环绕的时空粒子
                if (mob.tickCount % 10 == 0) {
                    ambientLayer3(mob);
                }
            } else {
                restoreAi(mob);
            }

            // 第 3 层：每秒扣除当前生命值 10%
            if (layer >= 2 && mob.tickCount % 20 == 0) {
                erode(mob);
            }
        }

        /** 效果结束/被清除时的清理。 */
        public static void cleanup(Mob mob) {
            removeSpeed(mob);
            restoreAi(mob);
            mob.getPersistentData().remove(LAYER_TAG);
        }

        private static void applySpeed(Mob mob, double multiplierAdd) {
            AttributeInstance instance = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance == null) {
                return;
            }
            instance.addOrUpdateTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, multiplierAdd, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        private static void removeSpeed(Mob mob) {
            AttributeInstance instance = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance != null && instance.hasModifier(SPEED_MODIFIER_ID)) {
                instance.removeModifier(SPEED_MODIFIER_ID);
            }
        }

        private static void stripAi(Mob mob) {
            if (!mob.isNoAi()) {
                mob.setNoAi(true);
                mob.getPersistentData().putBoolean(TimeHealsEvents.AI_TAG, true);
            }
        }

        private static void restoreAi(Mob mob) {
            if (mob.getPersistentData().getBoolean(TimeHealsEvents.AI_TAG)) {
                mob.setNoAi(false);
                mob.getPersistentData().remove(TimeHealsEvents.AI_TAG);
            }
        }

        /**
         * 时间侵蚀：直接扣血，绕过护甲、抗性、吸收与所有「受伤事件」，
         * 因此也能穿透混沌守卫的护盾（它拦截的是 {@code hurt}）。
         */
        private static void erode(Mob mob) {
            // 当前生命 10%，并且每秒至少扣 10 点，避免残血时百分比伤害趋近于 0
            float damage = Math.max(
                    mob.getHealth() * (float) TimeHealsConfig.EROSION_HEALTH_PERCENT,
                    TimeHealsConfig.EROSION_MIN_DAMAGE);
            if (damage <= 0.0F) {
                return;
            }
            erosionFeedback(mob);
            float remaining = mob.getHealth() - damage;
            if (remaining > 0.0F) {
                mob.setHealth(remaining);
            } else {
                DamageSource source = erosionSource(mob);
                mob.setHealth(0.0F);
                mob.die(source);
            }
        }

        private static DamageSource erosionSource(LivingEntity entity) {
            Holder<DamageType> holder = entity.level().registryAccess()
                    .holder(TIME_EROSION).orElse(null);
            if (holder == null) {
                return entity.level().damageSources().generic();
            }
            return new DamageSource(holder);
        }

        // ------------------------------------------------------------------
        // 第 3 层特效
        // ------------------------------------------------------------------

        /** 进入第 3 层：一次性的时空爆裂 + 共鸣音效。 */
        private static void playLayer3Enter(Mob mob) {
            if (!(mob.level() instanceof ServerLevel level)) {
                return;
            }
            double x = mob.getX();
            double y = mob.getY(0.6D);
            double z = mob.getZ();

            // 向外的时空粒子
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 40, 0.6D, 0.8D, 0.6D, 0.06D);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 16, 0.35D, 0.6D, 0.35D, 0.02D);
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 10, 0.5D, 0.5D, 0.5D, 0.01D);

            level.playSound(null, x, y, z, TimeSounds.TIME_BARRIER_BREAK.get(),
                    SoundSource.HOSTILE, 1.2F, 0.8F);
            notifyNearby(level, mob,
                    Component.translatable("message.time_heals.barrier_breaking", mob.getDisplayName()));
        }

        /** 第 3 层持续期间：环绕的紫色时空粒子环。 */
        private static void ambientLayer3(Mob mob) {
            if (!(mob.level() instanceof ServerLevel level)) {
                return;
            }
            double radius = 0.75D;
            double baseAngle = (mob.tickCount % 360) * (Math.PI / 180.0D);
            for (int i = 0; i < 4; i++) {
                double angle = baseAngle + i * (Math.PI / 2.0D);
                double px = mob.getX() + Math.cos(angle) * radius;
                double pz = mob.getZ() + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.PORTAL, px, mob.getY(0.7D), pz, 2, 0.02D, 0.05D, 0.02D, 0.005D);
            }
        }

        /** 每秒侵蚀：目标身上迸发伤害粒子 + 轻微碎裂音效。 */
        private static void erosionFeedback(Mob mob) {
            if (!(mob.level() instanceof ServerLevel level)) {
                return;
            }
            double x = mob.getX();
            double y = mob.getY(0.6D);
            double z = mob.getZ();
            level.sendParticles(ParticleTypes.WITCH, x, y, z, 12, 0.35D, 0.5D, 0.35D, 0.02D);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 6, 0.3D, 0.4D, 0.3D, 0.05D);
            level.playSound(null, x, y, z, TimeSounds.TIME_EROSION.get(),
                    SoundSource.HOSTILE, 0.6F, 0.8F + mob.getRandom().nextFloat() * 0.2F);
            notifyNearby(level, mob,
                    Component.translatable("message.time_heals.erosion", mob.getDisplayName()));
        }

        /** 向目标附近玩家发送快捷栏上方的提示文字。 */
        private static void notifyNearby(ServerLevel level, Mob mob, Component message) {
            double range = 32.0D * 32.0D;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(mob) <= range) {
                    player.displayClientMessage(message, true);
                }
            }
        }

        /** 同一 tick 只叠一次（攻击事件与伤害事件可能各触发一次）。 */
        private static final String STACK_TICK_TAG = "time_heals_stack_tick";

        /** 命中时叠层：没有效果 → I；有 → 层数 +1（上限 III），并刷新持续时间。 */
        public static void stackOnHit(Mob mob) {
            if (mob.getPersistentData().getInt(STACK_TICK_TAG) == mob.tickCount + 1) {
                return;
            }
            mob.getPersistentData().putInt(STACK_TICK_TAG, mob.tickCount + 1);

            MobEffectInstance current = mob.getEffect(TIME_CONFUSION);
            int nextLayer = current == null ? 0 : Math.min(2, current.getAmplifier() + 1);
            int duration = TimeHealsConfig.LAYER_DURATION_TICKS[nextLayer];
            TimeHeals.LOGGER.debug("[时间混乱] {} 叠层 -> {}", mob.getName().getString(), nextLayer + 1);
            mob.addEffect(new MobEffectInstance(TIME_CONFUSION, duration, nextLayer, false, true, true));
            maintain(mob, nextLayer);
        }
    }
}
