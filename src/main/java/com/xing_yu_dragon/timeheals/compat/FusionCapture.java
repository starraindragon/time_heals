package com.xing_yu_dragon.timeheals.compat;

import com.brandon3055.draconicevolution.api.crafting.IFusionInjector;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore;
import com.xing_yu_dragon.timeheals.TimeHeals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 在融合合成「开始」时抓一份注入器快照。
 *
 * <p>DE 在产出阶段（{@code FusionRecipe#assemble}）调用 {@code IFusionDataTransfer} 时，
 * 注入器可能已经被清空，导致拿不到「用的是哪把剑」。所以这里在
 * {@code TileFusionCraftingCore#startCraft} 的 HEAD 先看一眼。
 */
public final class FusionCapture {

    private static final ResourceLocation TIME_SWORD =
            ResourceLocation.fromNamespaceAndPath("kubejs", "time_sword");

    private static boolean pendingUnbreakableSword = false;

    private FusionCapture() {
    }

    public static void capture(TileFusionCraftingCore core) {
        boolean found = false;
        try {
            for (IFusionInjector injector : core.getInjectors()) {
                ItemStack stack = injector.getInjectorStack();
                if (stack.isEmpty()) {
                    continue;
                }
                boolean unbreakable = stack.has(DataComponents.UNBREAKABLE);
                int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
                TimeHeals.LOGGER.info("[debug] 合成前注入器: {} 不可破坏={} damage={}",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), unbreakable, damage);
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TIME_SWORD) && unbreakable) {
                    found = true;
                }
            }
        } catch (Throwable t) {
            TimeHeals.LOGGER.warn("[time_heals] 融合注入器快照失败: {}", t.toString());
        }
        pendingUnbreakableSword = found;
        TimeHeals.LOGGER.info("[debug] 融合合成开始，记录不可破坏之剑={}", found);
    }

    /** 取走并清空本次合成的记录。 */
    public static boolean consumePending() {
        boolean value = pendingUnbreakableSword;
        pendingUnbreakableSword = false;
        return value;
    }
}
