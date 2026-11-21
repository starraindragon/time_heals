package com.xing_yu_dragon.timeheals;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * 时序裁决器：3×3 底面的仪式装置。
 *
 * <p>本体只占中心 1 格，视觉由 {@link VerdictDeviceBlockEntity} 的自定义渲染器跨格绘制，
 * 因此碰撞箱只保留底座那一小块，避免玩家被“看不见的墙”挡住。
 */
public class VerdictDeviceBlock extends BaseEntityBlock {

    public static final MapCodec<VerdictDeviceBlock> CODEC = simpleCodec(VerdictDeviceBlock::new);

    /** 底座 2 单位高（模型 y=0~2），其余部分不参与碰撞。 */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public VerdictDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VerdictDeviceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof VerdictDeviceBlockEntity device) {
                device.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof VerdictDeviceBlockEntity device) {
            startRitual(level, device, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** 手持时间水晶才能启动；启动逻辑由控制器与结构块共用。 */
    public static void startRitual(Level level, VerdictDeviceBlockEntity device, Player player) {
        if (device.isRunning()) {
            return;
        }
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (!isTimeCrystal(held)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.time_heals.need_time_crystal"), true);
            return;
        }
        // 一次仪式消耗一整组（64 个）
        if (held.getCount() < 64) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.time_heals.need_full_stack"), true);
            return;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(64);
        }
        device.setStarter(player);
        device.startRitual();
    }

    private static boolean isTimeCrystal(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                .equals("kubejs:time_crystal");
    }

    /** 仪式期间不可挖掘。 */
    @Override
    protected float getDestroyProgress(BlockState state, Player player,
                                       net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof VerdictDeviceBlockEntity device
                && device.isRitualActive()) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** 仪式期间免疫爆炸（返回极高的抗性）。 */
    @Override
    public float getExplosionResistance() {
        return 3600000.0F;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VerdictDeviceBlockEntity device) {
            boolean usedUnbreakable = stack.getOrDefault(
                    TimeHealsComponents.USED_UNBREAKABLE_SWORD.get(), false);
            device.setUsedUnbreakableSword(usedUnbreakable);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        // 3×3 底座的另外 8 格：用不可见结构块补碰撞
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos partPos = pos.offset(dx, 0, dz);
                if (level.getBlockState(partPos).canBeReplaced()) {
                    level.setBlock(partPos, TimeHeals.VERDICT_DEVICE_PART.get().defaultBlockState(), 3);
                }
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos partPos = pos.offset(dx, 0, dz);
                    if (level.getBlockState(partPos).is(TimeHeals.VERDICT_DEVICE_PART.get())) {
                        level.removeBlock(partPos, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
