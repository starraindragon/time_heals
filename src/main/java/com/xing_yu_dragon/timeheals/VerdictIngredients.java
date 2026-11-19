package com.xing_yu_dragon.timeheals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.stream.Stream;

/**
 * 裁决器融合配方用的自定义材料类型。
 *
 * <ul>
 *   <li>{@code time_heals:full_sword}：时间之剑，且「满耐久」或「无法破坏 + 已打标记」；</li>
 *   <li>{@code time_heals:charged_capacitor}：指定物品，且 OP 能量不低于阈值。</li>
 * </ul>
 */
public final class VerdictIngredients {

    public static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, TimeHeals.MODID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<FullSwordIngredient>> FULL_SWORD =
            TYPES.register("full_sword", () -> new IngredientType<>(
                    FullSwordIngredient.CODEC, FullSwordIngredient.STREAM_CODEC));

    public static final DeferredHolder<IngredientType<?>, IngredientType<ChargedCapacitorIngredient>> CHARGED_CAPACITOR =
            TYPES.register("charged_capacitor", () -> new IngredientType<>(
                    ChargedCapacitorIngredient.CODEC, ChargedCapacitorIngredient.STREAM_CODEC));

    private static final ResourceLocation TIME_SWORD =
            ResourceLocation.fromNamespaceAndPath("kubejs", "time_sword");

    private VerdictIngredients() {
    }

    /** 满耐久的时间之剑；或「无法破坏 + 打了 verdict_mark 标记」的时间之剑。 */
    public record FullSwordIngredient() implements ICustomIngredient {

        public static final FullSwordIngredient INSTANCE = new FullSwordIngredient();
        public static final MapCodec<FullSwordIngredient> CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, FullSwordIngredient> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public boolean test(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TIME_SWORD)) {
                return false;
            }
            int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
            // 满耐久，或带「不可破坏」组件即可
            return damage == 0 || stack.has(DataComponents.UNBREAKABLE);
        }

        @Override
        public Stream<ItemStack> getItems() {
            Item item = BuiltInRegistries.ITEM.get(TIME_SWORD);
            return item == Items.AIR ? Stream.empty() : Stream.of(new ItemStack(item));
        }

        @Override
        public boolean isSimple() {
            return true;
        }

        @Override
        public IngredientType<?> getType() {
            return FULL_SWORD.get();
        }
    }

    /** 指定物品，且 OP 能量 ≥ minEnergy。 */
    public record ChargedCapacitorIngredient(String item, long minEnergy) implements ICustomIngredient {

        public static final MapCodec<ChargedCapacitorIngredient> CODEC = RecordCodecBuilder.mapCodec(builder ->
                builder.group(
                        Codec.STRING.fieldOf("item").forGetter(ChargedCapacitorIngredient::item),
                        Codec.LONG.optionalFieldOf("minEnergy", 0L)
                                .forGetter(ChargedCapacitorIngredient::minEnergy)
                ).apply(builder, ChargedCapacitorIngredient::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ChargedCapacitorIngredient> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ChargedCapacitorIngredient::item,
                        ByteBufCodecs.VAR_LONG, ChargedCapacitorIngredient::minEnergy,
                        ChargedCapacitorIngredient::new);

        @Override
        public boolean test(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) {
                return false;
            }
            try {
                var storage = com.brandon3055.brandonscore.utils.EnergyUtils.getStorage(stack);
                return storage != null && storage.getEnergyStored() >= minEnergy;
            } catch (Throwable ignored) {
                // 没有 Brandon's Core 时直接不匹配
                return false;
            }
        }

        @Override
        public Stream<ItemStack> getItems() {
            ResourceLocation id = ResourceLocation.tryParse(item);
            Item found = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            return found == Items.AIR ? Stream.empty() : Stream.of(new ItemStack(found));
        }

        @Override
        public boolean isSimple() {
            return true;
        }

        @Override
        public IngredientType<?> getType() {
            return CHARGED_CAPACITOR.get();
        }

        /** 便捷构造：直接给出 Ingredient。 */
        public Ingredient toIngredient() {
            return toVanilla();
        }
    }
}
