package com.xing_yu_dragon.timeheals.client;

import com.xing_yu_dragon.timeheals.TimeHeals;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** 客户端注册：时序裁决器的自定义渲染器。 */
@EventBusSubscriber(modid = TimeHeals.MODID, value = Dist.CLIENT)
public final class TimeHealsClient {

    private TimeHealsClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TimeHeals.VERDICT_DEVICE_BE.get(),
                VerdictDeviceRenderer::new);
    }
}
