package com.xing_yu_dragon.timeheals;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time Heals —— 时间系列装备的**前置**模组。
 *
 * <p>不注册任何物品：时间系列五件套仍由 KubeJS 脚本注册。本模组负责：
 * <ul>
 *   <li>注册「时间混乱」效果（{@code time_heals:time_confusion}）；</li>
 *   <li>时间系列武器命中生物时施加该效果并剥夺 AI 1 分钟；</li>
 *   <li>把时间系列武器的伤害按「混沌级」处理（需安装龙之进化）。</li>
 * </ul>
 */
@Mod(TimeHeals.MODID)
public class TimeHeals {

    public static final String MODID = "time_heals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public TimeHeals(IEventBus modEventBus, ModContainer container) {
        TimeEffects.MOB_EFFECTS.register(modEventBus);
        TimeSounds.SOUNDS.register(modEventBus);
        // 客户端：尽早注册指南（必须早于资源重载的 prepare 阶段）
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.xing_yu_dragon.timeheals.client.TimeGuide.register();
        }
        container.registerConfig(ModConfig.Type.COMMON, TimeHealsConfig.SPEC);
        LOGGER.info("[time_heals] 前置已加载：时间系列武器白名单 + 时间混乱效果");
    }
}
