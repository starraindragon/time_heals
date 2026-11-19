package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 成就发放便捷入口。
 *
 * <p>两种用法：
 * <ul>
 *   <li>自定义事件：{@link #event(Player, String)}、{@link #eventNearby(ServerLevel, BlockPos, double, String)}
 *       —— 对应进度 JSON 里的 {@code time_heals:event} 触发器；</li>
 *   <li>强制发放：{@link #award(ServerLevel, Player, String)}
 *       —— 对应 {@code minecraft:impossible} 型进度（criteria 名固定为 {@code unlock}）。</li>
 * </ul>
 */
public final class TimeAdvancements {

    private TimeAdvancements() {
    }

    /** 触发自定义事件（名字对应进度 JSON 的 conditions.name）。 */
    public static void event(Player player, String name) {
        if (player instanceof ServerPlayer serverPlayer) {
            TimeTriggers.EVENT.get().fire(serverPlayer, name);
        }
    }

    /** 对指定位置附近的玩家触发事件。 */
    public static int eventNearby(ServerLevel level, BlockPos pos, double radius, String name) {
        if (level == null) {
            return 0;
        }
        double radiusSq = radius * radius;
        int count = 0;
        for (ServerPlayer player : level.getPlayers(p ->
                p.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radiusSq)) {
            TimeTriggers.EVENT.get().fire(player, name);
            count++;
        }
        return count;
    }

    /** 强制发放 {@code minecraft:impossible} 型进度。 */
    public static void award(ServerLevel level, Player player, String path) {
        if (!(player instanceof ServerPlayer serverPlayer) || level == null) {
            return;
        }
        var holder = level.getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, path));
        if (holder == null) {
            TimeHeals.LOGGER.warn("[time_heals] 找不到进度 {}", path);
            return;
        }
        serverPlayer.getAdvancements().award(holder, "unlock");
    }
}
