package com.xing_yu_dragon.timeheals.mixin;

import com.xing_yu_dragon.timeheals.TimeEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让末影龙也能吃下「时间混乱」。
 *
 * <p>原版 {@code EnderDragon#addEffect} 恒 {@code return false}，效果挂不上，
 * Jade 里也就看不到图标。这里只对 {@code time_heals:time_confusion} 放行。
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonChaosEffectMixin {

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void time_heals$allowTimeConfusion(MobEffectInstance instance, Entity source,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (instance == null || !instance.getEffect().is(TimeEffects.TIME_CONFUSION)) {
            return;
        }
        LivingEntityEffectAccess access = (LivingEntityEffectAccess) (Object) this;
        access.timeHeals$getActiveEffects().put(instance.getEffect(), instance);
        access.timeHeals$onEffectAdded(instance, source);
        cir.setReturnValue(true);
    }
}
