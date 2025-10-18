package com.extendedae_plus.network;

import com.extendedae_plus.content.wireless.WirelessTransceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 设置无线收发器频率的网络数据包
 * 客户端发送到服务端，用于通过输入框设置频率
 */
public class SetWirelessFrequencyC2SPacket {
    
    private final BlockPos pos;
    private final long frequency;
    
    public SetWirelessFrequencyC2SPacket(BlockPos pos, long frequency) {
        this.pos = pos;
        this.frequency = frequency;
    }
    
    public static void encode(SetWirelessFrequencyC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeLong(packet.frequency);
    }
    
    public static SetWirelessFrequencyC2SPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long frequency = buf.readLong();
        return new SetWirelessFrequencyC2SPacket(pos, frequency);
    }
    
    public static void handle(SetWirelessFrequencyC2SPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            
            // 检查方块实体是否存在
            var level = player.serverLevel();
            if (!level.hasChunkAt(packet.pos)) {
                return;
            }
            
            var blockEntity = level.getBlockEntity(packet.pos);
            if (!(blockEntity instanceof WirelessTransceiverBlockEntity te)) {
                return;
            }
            
            // 通过GUI设置频率时，忽略锁定状态
            // 临时保存锁定状态
            boolean wasLocked = te.isLocked();
            
            // 临时解锁以允许设置
            if (wasLocked) {
                te.setLocked(false);
            }
            
            // 设置频率
            long newFreq = packet.frequency;
            if (newFreq < 0) {
                newFreq = 0;
            }
            te.setFrequency(newFreq);
            
            // 恢复锁定状态
            if (wasLocked) {
                te.setLocked(true);
            }
            
            // 发送反馈消息
            player.displayClientMessage(
                Component.literal("频道已设置为：" + te.getFrequency()), 
                true
            );
        });
        ctx.get().setPacketHandled(true);
    }
}

