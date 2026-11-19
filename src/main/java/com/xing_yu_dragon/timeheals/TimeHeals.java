package com.xing_yu_dragon.timeheals;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
    /** 本模组的创造模式物品栏。 */
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** 时序裁决器：3×3 造型、中央水晶自旋 + 上下浮动。 */
    public static final DeferredBlock<VerdictDeviceBlock> VERDICT_DEVICE =
            BLOCKS.register("verdict_device", () -> new VerdictDeviceBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0F, 6.0F)
                            .sound(SoundType.AMETHYST)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** 不可见的结构块，仅用于给 3×3 底座补碰撞。 */
    public static final DeferredBlock<VerdictDevicePartBlock> VERDICT_DEVICE_PART =
            BLOCKS.register("verdict_device_part", () -> new VerdictDevicePartBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(-1.0F, 3600000.0F)
                            .noOcclusion()
                            .noLootTable()));

    /** 裁决器自毁后的残骸（可刷）。 */
    public static final DeferredBlock<VerdictRemainsBlock> VERDICT_REMAINS =
            BLOCKS.register("verdict_remains", () -> new VerdictRemainsBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(50.0F, 1200.0F)
                            .sound(SoundType.STONE)
                            .noLootTable()));

    /** 残骸方块的物品形式（仅用于创造栏摆放）。 */
    public static final DeferredItem<BlockItem> VERDICT_REMAINS_ITEM =
            ITEMS.register("verdict_remains",
                    () -> new BlockItem(VERDICT_REMAINS.get(), new Item.Properties()));

    /** 报废的神龙权杖（刷残骸获得）。 */
    public static final DeferredItem<Item> DAMAGE_DRACONIC_STAFF =
            ITEMS.register("damage_draconic_staff",
                    () -> new DamageDraconicStaffItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> VERDICT_DEVICE_ITEM =
            ITEMS.register("verdict_device", () -> {
                if (net.neoforged.fml.ModList.get().isLoaded("draconicevolution")) {
                    // 装了龙研：换成能接收融合合成数据的版本
                    return new com.xing_yu_dragon.timeheals.compat.VerdictDeviceItemDE(
                            VERDICT_DEVICE.get(), new Item.Properties());
                }
                return new VerdictDeviceItem(VERDICT_DEVICE.get(), new Item.Properties());
            });

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
        TimeHealsComponents.COMPONENTS.register(modEventBus);
        VerdictIngredients.TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        TimeTriggers.TRIGGERS.register(modEventBus);

        // 客户端：尽早注册指南（必须早于资源重载的 prepare 阶段）
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.xing_yu_dragon.timeheals.client.TimeGuide.register();
        }
        container.registerConfig(ModConfig.Type.COMMON, TimeHealsConfig.SPEC);
        LOGGER.info("[time_heals] 前置已加载：时间系列武器白名单 + 时间混乱效果 + 时序裁决器");
    }

    /** 本模组的创造模式标签页：只放 time_heals 自己注册的物品。 */
    public static final net.neoforged.neoforge.registries.DeferredHolder<
            net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> TAB =
            CREATIVE_TABS.register("time_heals", () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.time_heals"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(VERDICT_DEVICE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(VERDICT_DEVICE_ITEM.get());
                        output.accept(VERDICT_REMAINS_ITEM.get());
                        output.accept(DAMAGE_DRACONIC_STAFF.get());
                    })
                    .build());
}
