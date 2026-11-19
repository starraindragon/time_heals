package com.xing_yu_dragon.timeheals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 时序反噬：对指定位置 5 格内的玩家造成混沌级伤害。
 *
 * <p>机器自毁时有 1% 概率触发；也可以由 {@code /timeheals backlash} 手动触发。
 */
public final class TemporalBacklash {

    public static final float DAMAGE = 50000.0F;
    public static final double RADIUS = 5.0D;

    private static final ResourceKey<DamageType> TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "temporal_backlash"));

    private TemporalBacklash() {
    }

    /**
     * @param includeSource 是否把 {@code sourcePlayer} 也算进去（命令 self 模式用）
     * @param sourcePlayer  调用者，可为 null
     * @return 实际被命中的玩家数
     */
    public static int trigger(ServerLevel level, BlockPos pos, boolean includeSource,
                              Player sourcePlayer) {
        return trigger(level, pos, includeSource, sourcePlayer, null);
    }

    /**
     * @param deathEvent 玩家被反噬打死时触发的成就事件名，可为 null
     */
    public static int trigger(ServerLevel level, BlockPos pos, boolean includeSource,
                              Player sourcePlayer, String deathEvent) {
        AABB area = new AABB(pos).inflate(RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        if (!includeSource && sourcePlayer != null) {
            players = players.stream().filter(p -> p != sourcePlayer).toList();
        }
        if (players.isEmpty()) {
            return 0;
        }
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(TYPE);
        DamageSource source = new DamageSource(holder);
        for (Player player : players) {
            player.invulnerableTime = 0;
            player.hurt(source, DAMAGE);
            if (deathEvent != null && player.isDeadOrDying()) {
                TimeAdvancements.event(player, deathEvent);
            }
        }
        for (int i = 0; i < 40; i++) {
            double dx = (level.random.nextDouble() - 0.5D) * 8.0D;
            double dy = level.random.nextDouble() * 3.0D;
            double dz = (level.random.nextDouble() - 0.5D) * 8.0D;
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    pos.getX() + 0.5D + dx, pos.getY() + 1.0D + dy, pos.getZ() + 0.5D + dz,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        TimeHeals.LOGGER.info("[时间混乱] 时序反噬触发，命中 {} 名玩家", players.size());
        return players.size();
    }
}
