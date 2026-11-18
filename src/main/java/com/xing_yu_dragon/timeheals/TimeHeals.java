package com.xing_yu_dragon.timeheals;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time Heals —— 时间系列装备的**前置**模组（1.2.0 起附带一台仪式机器）。
 *
 * <p>物品仍由 KubeJS 注册；本模组负责：
 * <ul>
 *   <li>注册「时间混乱」效果（{@code time_heals:time_confusion}）；</li>
 *   <li>时间系列武器命中生物时施加该效果并剥夺 AI；</li>
 *   <li>把时间系列武器的伤害按「混沌级」处理（需安装龙之进化）；</li>
 *   <li>注册「时序裁决器」方块（3×3 视觉，逻辑 1 格，自定义渲染）。</li>
 * </ul>
 */
@Mod(TimeHeals.MODID)
public class TimeHeals {

    public static final String MODID = "time_heals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    /** 时序裁决器：3×3 造型、中央水晶自旋 + 上下浮动。 */
    public static final DeferredBlock<VerdictDeviceBlock> VERDICT_DEVICE =
            BLOCKS.register("verdict_device", () -> new VerdictDeviceBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(6.0F, 1200.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> VERDICT_DEVICE_ITEM =
            ITEMS.register("verdict_device",
                    () -> new BlockItem(VERDICT_DEVICE.get(), new Item.Properties()));

    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>,
            BlockEntityType<VerdictDeviceBlockEntity>> VERDICT_DEVICE_BE =
            BLOCK_ENTITIES.register("verdict_device",
                    () -> BlockEntityType.Builder.of(VerdictDeviceBlockEntity::new,
                            VERDICT_DEVICE.get()).build(null));

    public TimeHeals(IEventBus modEventBus, ModContainer container) {
        TimeEffects.MOB_EFFECTS.register(modEventBus);
        TimeSounds.SOUNDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        // 客户端：尽早注册指南（必须早于资源重载的 prepare 阶段）
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.xing_yu_dragon.timeheals.client.TimeGuide.register();
        }
        container.registerConfig(ModConfig.Type.COMMON, TimeHealsConfig.SPEC);
        LOGGER.info("[time_heals] 前置已加载：时间系列武器白名单 + 时间混乱效果 + 时序裁决器");
    }
}
