package com.xing_yu_dragon.timeheals.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xing_yu_dragon.timeheals.TimeHeals;
import com.xing_yu_dragon.timeheals.TimeHealsConfig;
import com.xing_yu_dragon.timeheals.network.AcceleratePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「时间加速」快捷键。
 *
 * <p>默认绑定是 <b>Shift + 右键</b>；玩家把按键改成别的键（例如 V）后，<b>按下该键即触发</b>，
 * 不需要再点右键。
 *
 * <p>两种绑定分开处理：
 * <ul>
 *   <li>绑定在<b>鼠标键</b>上（默认）：在鼠标按下阶段直接发请求，并吃掉这次右键，
 *       避免同时打开机器界面；</li>
 *   <li>绑定在<b>键盘键</b>上（玩家自改）：在客户端 tick 里消费按键，按一下即触发。</li>
 * </ul>
 */
@EventBusSubscriber(modid = TimeHeals.MODID, value = Dist.CLIENT)
public final class TimeHealsKeys {

    public static final KeyMapping ACCELERATE = new KeyMapping(
            "key.time_heals.accelerate",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_RIGHT,
            "key.time_heals.category");

    private TimeHealsKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ACCELERATE);
        TimeHeals.LOGGER.info("[time_heals] 已注册按键 time_heals.accelerate（默认 Shift + 右键）");
    }

    // ------------------------------------------------------------------
    // 绑定在鼠标键上（默认 Shift + 右键）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (!isMouseTrigger(event.getButton())) {
            return;
        }
        if (!trySendAcceleration()) {
            return;
        }
        // 吃掉这次右键，避免同时打开机器界面
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------
    // 绑定在键盘键上（玩家自改后：按一下即触发）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ACCELERATE.getKey().getType() == InputConstants.Type.MOUSE) {
            return; // 鼠标绑定走上面的按下事件
        }
        boolean pressed = false;
        while (ACCELERATE.consumeClick()) {
            pressed = true;
        }
        if (pressed) {
            trySendAcceleration();
        }
    }

    // ------------------------------------------------------------------
    // 公共逻辑
    // ------------------------------------------------------------------

    private static boolean isMouseTrigger(int mouseButton) {
        InputConstants.Key mapped = ACCELERATE.getKey();
        if (mapped.getType() != InputConstants.Type.MOUSE) {
            return false;
        }
        if (mapped.getValue() != mouseButton) {
            return false;
        }
        return ACCELERATE.isConflictContextAndModifierActive();
    }

    /** 主手是时间之剑且准星命中方块时，把方块坐标发给服务端。 */
    private static boolean trySendAcceleration() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return false;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (id == null || !TimeHealsConfig.isAccelerationWeapon(id.toString())) {
            return false;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) {
            return false;
        }
        PacketDistributor.sendToServer(new AcceleratePayload(hit.getBlockPos()));
        return true;
    }
}
