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

    /** 仪式音效：复用原版音色，但字幕写成「时序裁决器：xxx」。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> VERDICT_BREAK =
            SOUNDS.register("verdict_break", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "verdict_break")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VERDICT_CHARGE =
            SOUNDS.register("verdict_charge", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "verdict_charge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VERDICT_CHARGE_LOOP =
            SOUNDS.register("verdict_charge_loop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "verdict_charge_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VERDICT_LOCK =
            SOUNDS.register("verdict_lock", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "verdict_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VERDICT_ATTACK =
            SOUNDS.register("verdict_attack", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "verdict_attack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> VERDICT_DONE =
            SOUNDS.register("verdict_done", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "verdict_done")));
    /** 混沌水晶爆炸。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_EXPLODE =
            SOUNDS.register("crystal_explode", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "crystal_explode")));

    private TimeSounds() {
    }
}
