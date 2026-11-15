package com.xing_yu_dragon.timeheals.client;

import com.xing_yu_dragon.timeheals.TimeHeals;

import guideme.Guide;
import net.minecraft.resources.ResourceLocation;

/**
 * 注册 GuideME 指南。
 *
 * <p>GuideME（AE2 指南用的框架）自带热键：鼠标悬停物品时<b>长按 G</b>（默认 0.5 秒）
 * 即可打开该物品对应的指南页。物品↔页面的映射来自页面 frontmatter 的 {@code item_ids}。
 *
 * <p>注册时机很关键：必须早于资源重载的 prepare 阶段（GuideME 那时就扫描完页面了），
 * 所以由 {@code TimeHeals} 构造时调用 {@link #register()}，而不是 FMLClientSetupEvent。
 *
 * <p>注意：
 * <ul>
 *   <li>本类<b>不能</b>加 {@code @EventBusSubscriber}——它没有任何 {@code @SubscribeEvent}
 *       方法，NeoForge 自动注册时会抛「has no @SubscribeEvent methods」并让模组进入损坏状态；</li>
 *   <li>不要在客户端 tick 里访问 {@code Guide#getPages()}，资源重载完成前会抛
 *       「Pages are not loaded yet」。</li>
 * </ul>
 */
public final class TimeGuide {

    private static final ResourceLocation GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "guide");

    private TimeGuide() {
    }

    /** 由主类在客户端构造阶段调用（必须早于资源重载）。 */
    public static void register() {
        Guide guide = Guide.builder(GUIDE_ID)
                .folder("time_heals_guide")
                .startPage(ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "index.md"))
                .build();
        TimeHeals.LOGGER.info("[time_heals] 指南已注册: {}（悬停物品长按 G 打开）", guide.getId());
    }
}
