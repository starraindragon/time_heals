package com.xing_yu_dragon.timeheals.compat;

import com.brandon3055.draconicevolution.api.crafting.IFusionDataTransfer;
import com.brandon3055.draconicevolution.api.crafting.IFusionInjector;
import com.brandon3055.draconicevolution.api.crafting.IFusionInventory;
import com.xing_yu_dragon.timeheals.TimeHealsComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 时序裁决器物品（装了龙之进化时使用）。
 *
 * <p>实现 {@link IFusionDataTransfer}：融合合成时检查注入器里是否有
 * 「无法破坏 + 已打标记」的时间之剑，有的话给产物打上
 * {@code time_heals:used_unbreakable_sword}，用于自毁时返还 1 耐久的时间之剑。
 */
public class VerdictDeviceItemDE extends BlockItem implements IFusionDataTransfer {

    private static final ResourceLocation TIME_SWORD =
            ResourceLocation.fromNamespaceAndPath("kubejs", "time_sword");

    public VerdictDeviceItemDE(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void transferIngredientData(ItemStack result, IFusionInventory fusionInventory,
                                       HolderLookup.Provider provider) {
        for (IFusionInjector injector : fusionInventory.getInjectors()) {
            ItemStack stack = injector.getInjectorStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TIME_SWORD)) {
                continue;
            }
            if (stack.has(DataComponents.UNBREAKABLE)) {
                result.set(TimeHealsComponents.USED_UNBREAKABLE_SWORD.get(), true);
                return;
            }
        }
    }
}
