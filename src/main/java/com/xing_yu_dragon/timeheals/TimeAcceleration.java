package com.xing_yu_dragon.timeheals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间加速：潜行 + 右键机器，用时间水晶换取「额外 tick」。
 *
 * <p>工作方式参考无用之物的造化垂青之杖（{@code BeefTimeAcceleration}）：
 * 对目标方块实体每个游戏 tick 额外调用 {@code BlockEntityTicker#tick}，
 * 对没有方块实体但会随机刻的方块则额外掷随机刻。
 *
 * <p>本模组的规则：
 * <ul>
 *   <li>第一次潜行右键：消耗 1 个时间水晶，速度 ×2，持续 30 秒；</li>
 *   <li>再次潜行右键：消耗 1 个时间水晶，速度 ×4，并刷新回 30 秒；</li>
 *   <li>之后潜行右键：消耗 1 个时间水晶，剩余时间 +30 秒。</li>
 * </ul>
 *
 * <p>被加速的机器上方会显示一个 {@link Display.TextDisplay} 悬浮文字，
 * 实时显示「×倍率 · 剩余秒数」。
 */
public final class TimeAcceleration {

    /** 每次加速/续时的持续时间（30 秒）。 */
    private static final int DURATION_TICKS = 20 * 30;

    /** 随机刻基础概率 1/1365（与原版随机刻一致）。 */
    private static final int RANDOM_TICK_CHANCE = 1365;

    private static final ResourceLocation TIME_CRYSTAL =
            ResourceLocation.fromNamespaceAndPath("kubejs", "time_crystal");

    /** 悬浮文字标记，用于清理残留展示实体。 */
    private static final String DISPLAY_MARKER = "time_heals_accel_display";

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** multiplier 只会是 2 或 4；expire 为游戏时间；display 为机器上方的悬浮文字。 */
    private record State(int multiplier, long expireGameTime, Display.TextDisplay display) {
    }

    private static final Map<Key, State> ACCELERATED = new ConcurrentHashMap<>();

    private TimeAcceleration() {
    }

    // ------------------------------------------------------------------
    // 右键使用
    // ------------------------------------------------------------------

    /** 由网络包调用：对指定方块施加/续期时间加速。 */
    public static boolean tryUse(ServerLevel serverLevel, Player player, BlockPos pos) {
        ResourceLocation heldId = itemId(player.getMainHandItem());
        if (heldId == null || !TimeHealsConfig.isAccelerationWeapon(heldId.toString())) {
            return false;
        }

        pos = pos.immutable();
        BlockState state = serverLevel.getBlockState(pos);
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        boolean valid = isValidTarget(serverLevel, state, blockEntity);
        if (!valid) {
            return false;
        }

        Key key = new Key(serverLevel.dimension(), pos);
        State current = ACCELERATED.get(key);
        long now = serverLevel.getGameTime();

        int multiplier;
        long expire;
        if (current == null) {
            multiplier = 2;
            expire = now + DURATION_TICKS;
        } else if (current.multiplier() == 2) {
            multiplier = 4;
            expire = now + DURATION_TICKS;
        } else {
            multiplier = 4;
            expire = Math.max(now, current.expireGameTime()) + DURATION_TICKS;
        }

        if (!consumeTimeCrystal(player)) {
            player.displayClientMessage(Component.translatable("message.time_heals.need_crystal")
                    .withStyle(ChatFormatting.RED), true);
            return true;
        }

        // 先登记状态：创建展示实体时会触发 EntityJoinLevel，若此时查不到记录会被误判为残留
        ACCELERATED.put(key, new State(multiplier, expire, current == null ? null : current.display()));
        Display.TextDisplay display = ensureDisplay(serverLevel, key, current, multiplier, expire, now);
        ACCELERATED.put(key, new State(multiplier, expire, display));

        int seconds = (int) Math.max(0, (expire - now) / 20);
        player.displayClientMessage(Component.translatable("message.time_heals.accelerate",
                multiplier, seconds).withStyle(ChatFormatting.AQUA), true);

        serverLevel.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(),
                SoundSource.PLAYERS, 1.0F, multiplier == 2 ? 1.0F : 1.5F);
        return true;
    }

    /** 调试命令用：直接给坐标施加 ×2 / 30 秒（不消耗水晶、不检查目标）。 */
    public static boolean forceAccelerate(ServerLevel level, BlockPos pos) {
        Key key = new Key(level.dimension(), pos.immutable());
        long now = level.getGameTime();
        State current = ACCELERATED.get(key);
        int multiplier = current == null ? 2 : (current.multiplier() == 2 ? 4 : 4);
        long expire = current == null ? now + DURATION_TICKS
                : Math.max(now, current.expireGameTime()) + DURATION_TICKS;
        ACCELERATED.put(key, new State(multiplier, expire, current == null ? null : current.display()));
        Display.TextDisplay display = ensureDisplay(level, key, current, multiplier, expire, now);
        ACCELERATED.put(key, new State(multiplier, expire, display));
        return true;
    }

    // ------------------------------------------------------------------
    // 每 tick 加速
    // ------------------------------------------------------------------

    public static void tick(ServerLevel level) {
        if (ACCELERATED.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        RandomSource random = level.getRandom();

        Iterator<Map.Entry<Key, State>> it = ACCELERATED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, State> entry = it.next();
            Key key = entry.getKey();
            State data = entry.getValue();

            if (!key.dimension().equals(level.dimension())) {
                continue;
            }
            if (now >= data.expireGameTime()) {
                discardDisplay(data.display());
                it.remove();
                continue;
            }

            BlockPos pos = key.pos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                discardDisplay(data.display());
                it.remove();
                continue;
            }

            // 每秒刷新一次悬浮文字
            if (now % 20 == 0) {
                updateDisplay(level, key, data.display(), data.multiplier(), data.expireGameTime(), now);
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            int extraTicks = data.multiplier() - 1;
            if (blockEntity == null) {
                if (state.isRandomlyTicking()) {
                    for (int i = 0; i < extraTicks; i++) {
                        if (random.nextInt(RANDOM_TICK_CHANCE) == 0) {
                            state.randomTick(level, pos, random);
                        }
                    }
                }
            } else {
                tickBlockEntity(level, pos, state, blockEntity, extraTicks);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void tickBlockEntity(ServerLevel level, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, int extraTicks) {
        BlockEntityTicker ticker = state.getTicker(level, blockEntity.getType());
        if (ticker == null) {
            return;
        }
        for (int i = 0; i < extraTicks; i++) {
            try {
                ticker.tick(level, pos, state, blockEntity);
            } catch (Throwable ignored) {
                // 单个机器 tick 抛错不应拖垮整个加速循环
            }
        }
    }

    // ------------------------------------------------------------------
    // 悬浮文字（Display.TextDisplay）
    // ------------------------------------------------------------------

    private static Display.TextDisplay ensureDisplay(ServerLevel level, Key key, State current,
                                                     int multiplier, long expire, long now) {
        Display.TextDisplay display = current == null ? null : current.display();
        if (display == null || display.isRemoved()) {
            display = EntityType.TEXT_DISPLAY.create(level);
            if (display == null) {
                return null;
            }
            display.getPersistentData().putBoolean(DISPLAY_MARKER, true);
            display.getPersistentData().putLong("time_heals_accel_pos", key.pos().asLong());
            display.setPos(key.pos().getX() + 0.5D, key.pos().getY() + 1.25D, key.pos().getZ() + 0.5D);
            level.addFreshEntity(display);
        }
        updateDisplay(level, key, display, multiplier, expire, now);
        return display;
    }

    private static void updateDisplay(ServerLevel level, Key key, Display.TextDisplay display,
                                      int multiplier, long expire, long now) {
        if (display == null || display.isRemoved()) {
            return;
        }
        BlockPos pos = key.pos();
        int seconds = (int) Math.max(0, (expire - now) / 20);
        Component text = Component.literal("×" + multiplier + " · " + seconds + "s")
                .withStyle(multiplier >= 4 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA);

        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        // Display 的 view_range 是「64 格的倍率」；这里换算成玩家可配置的半径
        double range = TimeHealsConfig.ACCELERATION_DISPLAY_RANGE.get();
        tag.putFloat("view_range", (float) (range / 64.0D));
        display.load(tag);

        display.setPos(pos.getX() + 0.5D, pos.getY() + 1.25D, pos.getZ() + 0.5D);
    }

    private static void discardDisplay(Display.TextDisplay display) {
        if (display != null && !display.isRemoved()) {
            display.discard();
        }
    }

    /** 世界重载/重启后清理没有任何加速记录的残留悬浮文字。 */
    public static void discardStaleDisplay(Display.TextDisplay display) {
        if (!display.getPersistentData().getBoolean(DISPLAY_MARKER)) {
            return;
        }
        if (!display.getPersistentData().contains("time_heals_accel_pos")) {
            display.discard();
            return;
        }
        BlockPos target = BlockPos.of(display.getPersistentData().getLong("time_heals_accel_pos"));
        boolean active = ACCELERATED.keySet().stream()
                .anyMatch(key -> key.pos().equals(target) && key.dimension().equals(display.level().dimension()));
        if (!active) {
            display.discard();
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /** 供事件层判断：主手是加速武器且目标是可加速方块。 */
    public static boolean isValidAccelerationTarget(Level level, ItemStack held, BlockPos pos) {
        if (held.isEmpty()) {
            return false;
        }
        ResourceLocation id = itemId(held);
        if (id == null || !TimeHealsConfig.isAccelerationWeapon(id.toString())) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return isValidTarget(serverLevel, serverLevel.getBlockState(pos), serverLevel.getBlockEntity(pos));
    }

    private static boolean isValidTarget(ServerLevel level, BlockState state, BlockEntity blockEntity) {
        if (blockEntity == null) {
            return state.isRandomlyTicking();
        }
        return state.getTicker(level, blockEntity.getType()) != null;
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** 消耗 1 个时间水晶（创造模式不消耗）。 */
    private static boolean consumeTimeCrystal(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = itemId(stack);
            if (TIME_CRYSTAL.equals(id)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
