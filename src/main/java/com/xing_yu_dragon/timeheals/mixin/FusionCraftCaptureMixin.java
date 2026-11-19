package com.xing_yu_dragon.timeheals.mixin;

import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore;
import com.xing_yu_dragon.timeheals.compat.FusionCapture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 融合合成开始时抓取注入器内容（在消耗之前）。 */
@Mixin(TileFusionCraftingCore.class)
public abstract class FusionCraftCaptureMixin {

    @Inject(method = "startCraft", at = @At("HEAD"))
    private void time_heals$captureInjectors(CallbackInfo ci) {
        FusionCapture.capture((TileFusionCraftingCore) (Object) this);
    }
}
