package com.xing_yu_dragon.timeheals.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EndDragonFight;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露末影龙战斗管理器的 BOSS 血条，供本模组在锁 AI 期间手动刷新进度。 */
@Mixin(EndDragonFight.class)
public interface EndDragonFightBarAccess {

    @Accessor("dragonEvent")
    ServerBossEvent timeHeals$getDragonEvent();
}
