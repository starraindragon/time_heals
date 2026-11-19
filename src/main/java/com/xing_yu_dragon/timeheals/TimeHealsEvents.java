package com.xing_yu_dragon.timeheals;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 「时间混乱」的运行时入口。
 *
 * <p>命中路径：时间系列武器近战命中生物（{@link net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent}）
 * → 叠加「时间混乱」层数。
 */
@EventBusSubscriber(modid = TimeHeals.MODID)
public final class TimeHealsEvents {

    public static final String AI_TAG = "time_heals_ai_stripped";

    /** 当前带有「时间混乱」的生物（弱引用，避免长期持有实体）。 */
    private static final Set<Mob> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

    private TimeHealsEvents() {
    }

    // ------------------------------------------------------------------
    // 1) 普通生物受到伤害
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }

        LivingEntity victim = event.getEntity();

        // (a) 先按「当前」层数放大伤害（本次命中施加的新层不影响这一次伤害）
        MobEffectInstance effect = victim.getEffect(TimeEffects.TIME_CONFUSION);
        if (effect != null) {
            int layer = Math.max(0, Math.min(2, effect.getAmplifier()));
            event.setAmount(event.getAmount() * (float) TimeHealsConfig.LAYER_DAMAGE_MULTIPLIER[layer]);
        }

        // (b) 时间系列武器命中 → 叠层
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (!isTimeWeapon(player.getMainHandItem())) {
            return;
        }
        if (victim instanceof Mob mob) {
            TimeEffects.TimeHealsLayers.stackOnHit(mob);
            // 凋零这类生物 addEffect 恒返回 false，挂不上效果 → 改用兜底追踪
            if (!mob.hasEffect(TimeEffects.TIME_CONFUSION)) {
                TimeConfusionFallback.hit(mob);
            } else {
                TRACKED.add(mob);
            }
        }
    }



    /** 左键攻击：即使目标因无敌帧/免疫拒绝 hurt 事件，也先把时间混乱叠上。 */
    @SubscribeEvent
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getTarget().level().isClientSide()) {
            return;
        }
        Mob mob;
        if (event.getTarget() instanceof Mob target) {
            mob = target;
        } else if (event.getTarget() instanceof net.neoforged.neoforge.entity.PartEntity<?> part
                && part.getParent() instanceof Mob parent) {
            // 末影龙这类多部位生物：准星命中的是部位，本体才是要处理的目标
            mob = parent;
        } else {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isTimeWeapon(player.getMainHandItem())) {
            return;
        }
        TimeEffects.TimeHealsLayers.stackOnHit(mob);
        if (!mob.hasEffect(TimeEffects.TIME_CONFUSION)) {
            TimeConfusionFallback.hit(mob);
        } else {
            TRACKED.add(mob);
        }
    }

    /** 命令：/timeheals mark —— 把手持物品标记为「不可破坏 + 裁决器专用」。 */
    @SubscribeEvent
    public static void onRegisterTimeHealsCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("timeheals")
                .then(net.minecraft.commands.Commands.literal("mark")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var stack = player.getMainHandItem();
                            if (stack.isEmpty()) {
                                return 0;
                            }
                            stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                                    new net.minecraft.world.item.component.Unbreakable(true));
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                    .literal("已设为不可破坏"), false);
                            return 1;
                        })));
    }

    /** 调试：/timeheals backlash [self] —— 在脚下触发一次时序反噬。 */
    @SubscribeEvent
    public static void onRegisterBacklashCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("timeheals")
                .then(net.minecraft.commands.Commands.literal("backlash")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            int hit = TemporalBacklash.trigger((net.minecraft.server.level.ServerLevel)
                                    player.level(), player.blockPosition(), false, player);
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                    .literal("时序反噬已触发，命中 " + hit + " 名玩家（不含自己）"), false);
                            return hit;
                        })
                        .then(net.minecraft.commands.Commands.literal("self")
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    int hit = TemporalBacklash.trigger(
                                            (net.minecraft.server.level.ServerLevel) player.level(),
                                            player.blockPosition(), true, player);
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                            .literal("时序反噬已触发（含自己），命中 " + hit + " 名玩家"), false);
                                    return hit;
                                }))));
    }

    /** 测试快捷：/timeheals give —— 直接发一套测试物品。 */
    @SubscribeEvent
    public static void onRegisterGiveCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("timeheals")
                .then(net.minecraft.commands.Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            int given = 0;

                            // 机器：预先带上「用不可破坏之剑合成」的标记，直接可测返还逻辑
                            var device = new net.minecraft.world.item.ItemStack(TimeHeals.VERDICT_DEVICE_ITEM.get());
                            device.set(TimeHealsComponents.USED_UNBREAKABLE_SWORD.get(), true);
                            if (!player.getInventory().add(device)) {
                                player.drop(device, false);
                            }
                            given++;

                            // 一整组时间水晶（KubeJS 物品）
                            net.minecraft.world.item.Item crystal = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("kubejs", "time_crystal"));
                            if (crystal != net.minecraft.world.item.Items.AIR) {
                                var stack = new net.minecraft.world.item.ItemStack(crystal, 64);
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                                given++;
                            }

                            // 不可破坏的时间之剑
                            net.minecraft.world.item.Item sword = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("kubejs", "time_sword"));
                            if (sword != net.minecraft.world.item.Items.AIR) {
                                var stack = new net.minecraft.world.item.ItemStack(sword);
                                stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                                        new net.minecraft.world.item.component.Unbreakable(true));
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                                given++;
                            }

                            int total = given;
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component
                                    .literal("已发放测试物品：" + total + " 类（裁决器已标记为不可破坏之剑合成）"), false);
                            return given;
                        })));
    }

    /** 调试命令：/timeaccel —— 给视线 10 格内的方块施加时间加速。 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("timeaccel")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                        return 0;
                    }
                    var hit = player.pick(10.0D, 0.0F, false);
                    if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) {
                        return 0;
                    }
                    var pos = blockHit.getBlockPos();
                    boolean ok = TimeAcceleration.forceAccelerate(level, pos);
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            ok ? "已加速 " + pos.toShortString() : "失败"), false);
                    return ok ? 1 : 0;
                }));
    }


    // ------------------------------------------------------------------
    // 3) 效果被添加（命令/其它模组）时纳入跟踪
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getEntity().level().isClientSide()) {
            return;
        }
        MobEffectInstance added = event.getEffectInstance();
        if (!added.getEffect().is(TimeEffects.TIME_CONFUSION)) {
            return;
        }
        TRACKED.add(mob);
        TimeEffects.TimeHealsLayers.maintain(mob, added.getAmplifier());
    }

    // ------------------------------------------------------------------
    // 4) 每 tick：清理过期状态 + 非生物护盾侵蚀
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 时间加速：每 tick 对加速中的方块补跑 tick
        for (var level : event.getServer().getAllLevels()) {
            TimeAcceleration.tick(level);
            TimeConfusionFallback.tick(level);
        }

        if (TRACKED.isEmpty()) {
            return;
        }
        Iterator<Mob> it = TRACKED.iterator();
        while (it.hasNext()) {
            Mob mob = it.next();
            if (mob == null || mob.isRemoved()) {
                it.remove();
                continue;
            }
            if (!mob.hasEffect(TimeEffects.TIME_CONFUSION)) {
                TimeEffects.TimeHealsLayers.cleanup(mob);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // 5) 实体进入世界（含重载/重启）：校正状态
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof net.minecraft.world.entity.Display.TextDisplay display) {
            TimeAcceleration.discardStaleDisplay(display);
            return;
        }
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        MobEffectInstance effect = mob.getEffect(TimeEffects.TIME_CONFUSION);
        if (effect != null) {
            TRACKED.add(mob);
            TimeEffects.TimeHealsLayers.maintain(mob, effect.getAmplifier());
        } else if (mob.getPersistentData().getBoolean(AI_TAG)) {
            TimeEffects.TimeHealsLayers.cleanup(mob);
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static boolean isTimeWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && TimeHealsConfig.isTimeWeapon(id.toString());
    }

    /** 是否有「时间混乱」的 AI 剥夺标记（调试/其它模组可用）。 */
    public static boolean isAiStripped(Entity entity) {
        return entity instanceof Mob mob && mob.getPersistentData().getBoolean(AI_TAG);
    }
}
