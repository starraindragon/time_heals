package com.xing_yu_dragon.timeheals.client;

import com.xing_yu_dragon.timeheals.TimeHeals;

import guideme.Guide;
import guideme.Guides;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 注册 GuideME 指南。
 *
 * <p>GuideME（AE2 指南用的框架）自带热键：鼠标悬停物品时<b>长按 G</b>（默认 0.5 秒）
 * 即可打开该物品对应的指南页。物品↔页面的映射来自页面 frontmatter 的 {@code item_ids}。
 *
 * <p>注册时机很关键：必须早于资源重载的 prepare 阶段（GuideME 那时就扫描完页面了），
 * 所以由 {@code TimeHeals} 构造时调用 {@link #register()}，而不是 FMLClientSetupEvent。
 */
@EventBusSubscriber(modid = TimeHeals.MODID, value = Dist.CLIENT)
public final class TimeGuide {

    private static final ResourceLocation GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "guide");

    private static boolean loggedPages;

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

    /** 临时诊断：进入世界后打印一次页面数量。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (loggedPages) {
            return;
        }
        loggedPages = true;
        Guide guide = Guides.getById(GUIDE_ID);
        if (guide == null) {
            TimeHeals.LOGGER.warn("[time_heals] 指南未注册！");
            return;
        }
        TimeHeals.LOGGER.info("[time_heals] 指南页面数={}，列表={}", guide.getPages().size(),
                guide.getPages().stream().map(page -> page.getId().toString()).toList());
        var itemIndex = guide.getIndex(guideme.indices.ItemIndex.class);
        if (itemIndex != null) {
            TimeHeals.LOGGER.info("[time_heals] 物品索引 time_sword -> {}",
                    itemIndex.get(ResourceLocation.fromNamespaceAndPath("kubejs", "time_sword")));
        }
    }
}
