package com.xing_yu_dragon.timeheals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 报废的神龙权杖：外表完好，内部千疮百孔。 */
public class DamageDraconicStaffItem extends Item {

    public DamageDraconicStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("外表看似完好无损，实则内部早已千疮百孔")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
