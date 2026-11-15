package com.xing_yu_dragon.timeheals.mixin;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.init.DEDamage;
import com.xing_yu_dragon.timeheals.TimeHealsConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 前置行为：让配置白名单里的武器在 DE 眼里成为「混沌级」。
 *
 * <p>DE 的 {@code DEDamage.getDamageLevel(DamageSource)} 先查伤害类型标签，
 * 再查攻击者主手物品是否实现 {@code IDraconicMelee}。整合包的时间系列五件套
 * 由 KubeJS 注册（无法实现 Java 接口），因此这里按物品注册名白名单直接返回
 * {@link TechLevel#CHAOTIC}。
 *
 * <p>只影响玩家近战：伤害源的造成实体必须是 {@link Player}，且主手物品命中
 * {@link TimeHealsConfig#TIME_WEAPONS}。不会影响其它武器、怪物攻击或空手。
 *
 * <p>{@code DEDamage} 是 DE 的类（非 Minecraft 类），故 {@code remap = false}。
 */
@Mixin(value = DEDamage.class, remap = false)
public abstract class ChaosWeaponDamageMixin {

    @Inject(method = "getDamageLevel", at = @At("HEAD"), cancellable = true)
    private static void time_heals$chaoticWeaponWhitelist(DamageSource source,
                                                          CallbackInfoReturnable<TechLevel> cir) {
        if (!(source.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (id == null) {
            return;
        }
        if (TimeHealsConfig.isTimeWeapon(id.toString())) {
            cir.setReturnValue(TechLevel.CHAOTIC);
        }
    }
}
