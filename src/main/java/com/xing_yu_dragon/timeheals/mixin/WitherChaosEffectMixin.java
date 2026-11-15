package com.xing_yu_dragon.timeheals.mixin;

import com.xing_yu_dragon.timeheals.TimeEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让凋零能吃下「时间混乱」这个效果。
 *
 * <p>原版 {@code WitherBoss#addEffect} 直接 {@code return false}，任何效果都挂不上，
 * 于是 Jade 里看不到效果图标、也不会有正常的药水计时。
 *
 * <p>这里只对 {@code time_heals:time_confusion} 做例外：直接把实例塞进
 * {@code LivingEntity.activeEffects} 并调用 {@code onEffectAdded} 完成同步，
 * 其它效果仍然按原版拒绝。
 */
@Mixin(WitherBoss.class)
public abstract class WitherChaosEffectMixin {

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
