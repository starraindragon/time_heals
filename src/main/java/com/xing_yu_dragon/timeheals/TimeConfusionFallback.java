package com.xing_yu_dragon.timeheals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「时间混乱」的唯一权威追踪器。
 *
 * <p>层数、到期时间与每 tick 结算（移速 / 定身 / 剥夺 AI / 每秒侵蚀）全部由这里驱动，
 * 不依赖 MobEffect 的 tick——因为凋零这类生物即便被放行挂上了效果，也不会正常 tick 效果，
 * 依赖效果 tick 会导致「Jade 有图标但血量不掉」。
 *
 * <p>MobEffect 只作为<b>可见标记</b>（Jade / 屏幕右上角效果图标）。
 */
public final class TimeConfusionFallback {

    private record State(int layer, long expireGameTime) {
    }

    private static final Map<Mob, State> TRACKED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final String STACK_TICK_TAG = "time_heals_stack_tick";

    private TimeConfusionFallback() {
    }

    /** 命中时叠层（1~3），同步一个仅用于显示的效果，并由追踪器负责全部结算。 */
    public static void hit(Mob mob) {
        if (mob.level().isClientSide() || mob.isRemoved()) {
            return;
        }
        int tick = mob.tickCount + 1;
        if (mob.getPersistentData().getInt(STACK_TICK_TAG) == tick) {
            return;
        }
        mob.getPersistentData().putInt(STACK_TICK_TAG, tick);

        State current = TRACKED.get(mob);
        int layer = current == null ? 1 : Math.min(3, current.layer() + 1);
        int duration = TimeHealsConfig.LAYER_DURATION_TICKS[layer - 1];
        long expire = mob.level().getGameTime() + duration;

        // 显示用效果：凋零由 WitherChaosEffectMixin 放行，普通生物正常挂
        mob.addEffect(new MobEffectInstance(TimeEffects.TIME_CONFUSION,
                duration, layer - 1, false, true, true));

        TRACKED.put(mob, new State(layer, expire));
        TimeEffects.TimeHealsLayers.maintain(mob, layer - 1);
    }

    /** 每 tick 推进所有被追踪生物。 */
    public static void tick(ServerLevel level) {
        if (TRACKED.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        synchronized (TRACKED) {
            Iterator<Map.Entry<Mob, State>> it = TRACKED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Mob, State> entry = it.next();
                Mob mob = entry.getKey();
                State state = entry.getValue();
                if (mob == null || mob.isRemoved()) {
                    if (mob != null) {
                        TimeEffects.TimeHealsLayers.cleanup(mob);
                    }
                    it.remove();
                    continue;
                }
                // 不是当前遍历的世界：跳过，不能删
                if (mob.level() != level) {
                    continue;
                }
                if (now >= state.expireGameTime()) {
                    TimeEffects.TimeHealsLayers.cleanup(mob);
                    it.remove();
                    continue;
                }
                TimeEffects.TimeHealsLayers.maintain(mob, state.layer() - 1);
            }
        }
    }

    /** 生物死亡/移除时清理。 */
    public static void forget(Mob mob) {
        TRACKED.remove(mob);
    }

    /** 是否正在被追踪（调试用）。 */
    public static boolean isTracked(Mob mob) {
        return TRACKED.containsKey(mob);
    }
}
