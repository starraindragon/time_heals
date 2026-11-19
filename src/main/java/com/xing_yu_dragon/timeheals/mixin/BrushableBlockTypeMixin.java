package com.xing_yu_dragon.timeheals.mixin;

import com.xing_yu_dragon.timeheals.TimeHeals;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 允许自定义残骸方块使用原版刷子方块实体（复用原版刷取逻辑与动画）。 */
@Mixin(BlockEntityType.class)
public abstract class BrushableBlockTypeMixin {

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void time_heals$allowRemains(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == BlockEntityType.BRUSHABLE_BLOCK
                && state.is(TimeHeals.VERDICT_REMAINS.get())) {
            cir.setReturnValue(true);
        }
    }
}
