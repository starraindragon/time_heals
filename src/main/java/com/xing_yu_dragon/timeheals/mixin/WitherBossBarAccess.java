package com.xing_yu_dragon.timeheals.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露凋零的 BOSS 血条对象，供本模组在锁 AI 期间手动刷新进度。 */
@Mixin(WitherBoss.class)
public interface WitherBossBarAccess {

    @Accessor("bossEvent")
    ServerBossEvent timeHeals$getBossEvent();
}
