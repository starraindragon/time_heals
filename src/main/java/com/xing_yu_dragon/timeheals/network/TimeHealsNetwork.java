package com.xing_yu_dragon.timeheals.network;

import com.xing_yu_dragon.timeheals.TimeAcceleration;
import com.xing_yu_dragon.timeheals.TimeHeals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 网络包注册与服务端处理。 */
@EventBusSubscriber(modid = TimeHeals.MODID)
public final class TimeHealsNetwork {

    private TimeHealsNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AcceleratePayload.TYPE, AcceleratePayload.STREAM_CODEC,
                TimeHealsNetwork::handleAccelerate);
    }

    private static void handleAccelerate(AcceleratePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            // 距离校验，防止伪造远距离请求
            if (player.blockPosition().distSqr(payload.pos()) > 64.0D) {
                return;
            }
            if (TimeAcceleration.tryUse(level, player, payload.pos())) {
                // 若右键同时打开了机器界面，补一 tick 后关掉
                level.getServer().execute(() -> {
                    if (player.containerMenu != player.inventoryMenu) {
                        player.closeContainer();
                    }
                });
            }
        });
    }
}
