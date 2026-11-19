package com.xing_yu_dragon.timeheals.mixin;

import com.xing_yu_dragon.timeheals.TimeAdvancements;
import com.xing_yu_dragon.timeheals.TimeHeals;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 刷开裁决器残骸时发放「回收废品」成就。 */
@Mixin(BrushableBlockEntity.class)
public abstract class BrushableRemainsMixin {

    @Inject(method = "brushingCompleted", at = @At("HEAD"))
    private void time_heals$onBrushed(Player player, CallbackInfo ci) {
        BrushableBlockEntity self = (BrushableBlockEntity) (Object) this;
        if (self.getBlockState().is(TimeHeals.VERDICT_REMAINS.get())) {
            TimeAdvancements.event(player, "recycling");
        }
    }
}
