package com.xing_yu_dragon.timeheals.client;

import com.xing_yu_dragon.timeheals.VerdictDeviceBlock;
import com.xing_yu_dragon.timeheals.VerdictDeviceBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** 在 Jade 提示里显示裁决器的仪式状态。 */
@WailaPlugin("time_heals")
public class TimeHealsJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RitualStatusProvider.INSTANCE, VerdictDeviceBlock.class);
    }

    /** 仪式进行中追加一行提示。 */
    public enum RitualStatusProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID =
                ResourceLocation.fromNamespaceAndPath("time_heals", "verdict_ritual_status");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity blockEntity = accessor.getBlockEntity();
            if (blockEntity instanceof VerdictDeviceBlockEntity device && device.isRitualActive()) {
                tooltip.add(Component.literal("仪式进行中，无法破坏")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
