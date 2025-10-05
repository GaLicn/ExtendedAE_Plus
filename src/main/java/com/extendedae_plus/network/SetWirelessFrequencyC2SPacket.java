package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 客户端发送到服务端，用于设置无线收发器的频率
 * 
 * API变化说明：
 * - 1.20.1 Forge使用SimpleChannel.messageBuilder
 * - 1.21.1 NeoForge使用CustomPacketPayload + StreamCodec
 * - FriendlyByteBuf的读写方法保持一致
 */
public class SetWirelessFrequencyC2SPacket implements CustomPacketPayload {
    
    public static final Type<SetWirelessFrequencyC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtendedAEPlus.MODID, "set_wireless_frequency"));

    /**
     * StreamCodec用于序列化和反序列化数据包
     * 第一个函数：编码（写入）
     * 第二个函数：解码（读取）
     */
    public static final StreamCodec<FriendlyByteBuf, SetWirelessFrequencyC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeLong(pkt.frequency);
            },
            buf -> new SetWirelessFrequencyC2SPacket(
                    buf.readBlockPos(),
                    buf.readLong()
            )
    );

    private final BlockPos pos;
    private final long frequency;
    
    public SetWirelessFrequencyC2SPacket(BlockPos pos, long frequency) {
        this.pos = pos;
        this.frequency = frequency;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 服务端处理逻辑
     * 
     * API变化说明：
     * - ctx.enqueueWork确保线程安全（在主线程执行）
     * - IPayloadContext替代了Forge的NetworkEvent.Context
     */
    public static void handle(final SetWirelessFrequencyC2SPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            
            // 验证玩家是否在附近（防止作弊）
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64.0) {
                ExtendedAEPlus.LOGGER.warn("Player {} tried to set frequency from too far away", player.getName().getString());
                return;
            }
            
            // 获取方块实体
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof WirelessTransceiverBlockEntity transceiver)) {
                ExtendedAEPlus.LOGGER.warn("Invalid block entity at {} for frequency setting", msg.pos);
                return;
            }
            
            // 使用强制设置方法，忽略锁定状态
            // 扳手GUI调整频率时应该能够绕过锁定限制
            transceiver.setFrequencyForced(msg.frequency);
            ExtendedAEPlus.LOGGER.debug("Set transceiver frequency at {} to {} (forced)", msg.pos, msg.frequency);
        });
    }
}

