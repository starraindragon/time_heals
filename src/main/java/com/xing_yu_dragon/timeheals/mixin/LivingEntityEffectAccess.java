package com.xing_yu_dragon.timeheals.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/** 暴露 LivingEntity 的效果表与回调，供凋零绕过 addEffect 的硬拒绝。 */
@Mixin(LivingEntity.class)
public interface LivingEntityEffectAccess {

    @Accessor("activeEffects")
    Map<Holder<MobEffect>, MobEffectInstance> timeHeals$getActiveEffects();

    @Invoker("onEffectAdded")
    void timeHeals$onEffectAdded(MobEffectInstance instance, Entity source);
}
