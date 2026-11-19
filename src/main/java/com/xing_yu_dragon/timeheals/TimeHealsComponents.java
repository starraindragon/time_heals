package com.xing_yu_dragon.timeheals;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组自定义数据组件。
 *
 * <p>{@code time_heals:verdict_mark}：给「无法破坏的时间之剑」打的特殊标记，
 * 用于裁决器配方识别（后续也用于其它校验）。
 */
public final class TimeHealsComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TimeHeals.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> VERDICT_MARK =
            COMPONENTS.register("verdict_mark", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    private TimeHealsComponents() {
    }
}
