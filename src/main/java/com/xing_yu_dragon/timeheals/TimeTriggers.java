package com.xing_yu_dragon.timeheals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/**
 * 本模组的成就触发器框架。
 *
 * <p>只注册一个通用触发器 {@code time_heals:event}，条件是字符串 {@code name}。
 * 进度 JSON 这样写：
 * <pre>
 * "criteria": {
 *   "unlock": {
 *     "trigger": "time_heals:event",
 *     "conditions": { "name": "ritual" }
 *   }
 * }
 * </pre>
 * 代码里发放：{@code TimeAdvancements.event(player, "ritual")}。
 */
public final class TimeTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, TimeHeals.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, EventTrigger> EVENT =
            TRIGGERS.register("event", EventTrigger::new);

    private TimeTriggers() {
    }

    /** 通用事件触发器：按名字匹配。 */
    public static final class EventTrigger extends SimpleCriterionTrigger<EventTrigger.Instance> {

        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC.codec();
        }

        /** 触发一次事件，名字需与进度 JSON 中的 {@code name} 一致。 */
        public void fire(ServerPlayer player, String name) {
            this.trigger(player, instance -> instance.name().equals(name));
        }

        public record Instance(Optional<ContextAwarePredicate> player, String name)
                implements SimpleCriterionTrigger.SimpleInstance {

            public static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                            ContextAwarePredicate.CODEC.optionalFieldOf("player")
                                    .forGetter(Instance::player),
                            Codec.STRING.fieldOf("name").forGetter(Instance::name)
                    ).apply(instance, Instance::new));
        }
    }
}
