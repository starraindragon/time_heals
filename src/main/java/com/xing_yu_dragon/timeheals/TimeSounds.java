package com.xing_yu_dragon.timeheals;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义音效事件。
 *
 * <p>只为了拿到「自定义字幕」：音效文件仍然复用原版（紫水晶、钟），
 * 但每个事件在 {@code assets/time_heals/sounds.json} 里声明了自己的
 * {@code subtitle} 语言键，所以开启字幕后右下角显示的是我们想要的文本，
 * 而不是「紫水晶块：被敲击」「钟：回响」。
 */
public final class TimeSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TimeHeals.MODID);

    /** 每秒时间侵蚀：字幕「时间侵蚀」。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_EROSION =
            SOUNDS.register("time_erosion", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "time_erosion")));

    /** 进入第 3 层：字幕「时间屏障正损坏」。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_BARRIER_BREAK =
            SOUNDS.register("time_barrier_break", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "time_barrier_break")));

    private TimeSounds() {
    }
}
