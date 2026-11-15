package com.xing_yu_dragon.timeheals;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置与「时间混乱」分层数值。
 *
 * <p>时间系列武器白名单同时驱动两件事：
 * <ol>
 *   <li>伤害按「混沌级」处理（DE 判定，见 {@code ChaosWeaponDamageMixin}）；</li>
 *   <li>命中生物时叠加「时间混乱」层数（见 {@link TimeHealsLayers}）。</li>
 * </ol>
 *
 * <p>「时间混乱」共 3 层，amplifier 0/1/2：
 * <table border="1">
 *   <tr><th>层</th><th>效果</th><th>持续</th></tr>
 *   <tr><td>I</td><td>移速 -30%，受到伤害 ×1.10</td><td>10 秒</td></tr>
 *   <tr><td>II</td><td>无法移动，受到伤害 ×1.32（在 1.10 基础上再 +20%）</td><td>30 秒</td></tr>
 *   <tr><td>III</td><td>剥夺 AI + 无法移动，受到伤害 ×1.584（再 +20%），
 *       每秒受到当前生命值 5% 的无视防护伤害</td><td>60 秒</td></tr>
 * </table>
 */
public final class TimeHealsConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIME_WEAPONS;

    /** 各层持续时间（tick）：10 秒 / 30 秒 / 60 秒。 */
    public static final int[] LAYER_DURATION_TICKS = {20 * 10, 20 * 30, 20 * 60};

    /** 各层受到伤害的倍率（逐层在当前结果上再 +20%）。 */
    public static final double[] LAYER_DAMAGE_MULTIPLIER = {1.10D, 1.10D * 1.20D, 1.10D * 1.20D * 1.20D};

    /** 第 1 层移速倍率：-30%。 */
    public static final double LAYER1_SPEED = -0.30D;

    /** 第 3 层每秒造成的当前生命值百分比（10%）。 */
    public static final double EROSION_HEALTH_PERCENT = 0.10D;

    /** 第 3 层每秒侵蚀的最低伤害：当前生命 10% 低于此值时按此值扣（10 点）。 */
    public static final float EROSION_MIN_DAMAGE = 10.0F;

    /** 能触发「时间加速」的物品白名单（默认只有时间之剑）。 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ACCELERATION_WEAPONS;

    /** 加速悬浮文字的显示半径（格），默认 5 格。 */
    public static final ModConfigSpec.DoubleValue ACCELERATION_DISPLAY_RANGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        ACCELERATION_WEAPONS = b
                .comment("能触发「时间加速」（潜行右键机器）的物品白名单。", "默认只有 kubejs:time_sword。")
                .defineList("accelerationWeapons", List.of("kubejs:time_sword"), o -> o instanceof String);

        ACCELERATION_DISPLAY_RANGE = b
                .comment("时间加速悬浮文字的可见半径（格）。", "显示距离超过该半径的玩家看不到文字；默认 5 格。")
                .defineInRange("accelerationDisplayRange", 5.0D, 1.0D, 64.0D);

        TIME_WEAPONS = b
                .comment("时间系列武器白名单（按物品注册名填写）。",
                        "主手持有这些物品攻击时：伤害按「混沌级」处理，",
                        "并给命中生物叠加「时间混乱」层数（最多 3 层）。")
                .defineList("timeWeapons",
                        List.of(
                                "kubejs:time_sword",
                                "kubejs:time_pickaxe",
                                "kubejs:time_axe",
                                "kubejs:time_shovel",
                                "kubejs:time_hoe"
                        ),
                        o -> o instanceof String);

        SPEC = b.build();
    }

    private TimeHealsConfig() {
    }

    /** 判断物品注册名能否触发时间加速；配置未就绪时安全返回 false。 */
    public static boolean isAccelerationWeapon(String itemId) {
        try {
            return ACCELERATION_WEAPONS.get().contains(itemId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 判断物品注册名是否属于时间系列武器；配置未就绪时安全返回 false。 */
    public static boolean isTimeWeapon(String itemId) {
        try {
            return TIME_WEAPONS.get().contains(itemId);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
