package com.xing_yu_dragon.timeheals.network;

import com.xing_yu_dragon.timeheals.TimeHeals;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：请求对某个方块施加时间加速。
 *
 * <p>用数据包而不是在服务端读按键，是因为按键状态只在客户端存在；
 * 玩家改键后（例如改成 Ctrl + 右键），服务端也能正确收到请求。
 */
public record AcceleratePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<AcceleratePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "accelerate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AcceleratePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AcceleratePayload::pos,
                    AcceleratePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
