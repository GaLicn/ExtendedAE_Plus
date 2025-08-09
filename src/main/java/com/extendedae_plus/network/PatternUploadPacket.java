package com.extendedae_plus.network;

import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 样板上传网络包
 * 用于从客户端发送样板上传请求到服务器
 */
public class PatternUploadPacket {
    
    private final int playerSlotIndex;
    private final long providerId;
    
    public PatternUploadPacket(int playerSlotIndex, long providerId) {
        this.playerSlotIndex = playerSlotIndex;
        this.providerId = providerId;
    }
    
    /**
     * 编码数据包
     */
    public static void encode(PatternUploadPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.playerSlotIndex);
        buffer.writeLong(packet.providerId);
    }
    
    /**
     * 解码数据包
     */
    public static PatternUploadPacket decode(FriendlyByteBuf buffer) {
        int playerSlotIndex = buffer.readInt();
        long providerId = buffer.readLong();
        return new PatternUploadPacket(playerSlotIndex, providerId);
    }
    
    /**
     * 处理数据包（在服务器端执行）
     */
    public static void handle(PatternUploadPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 获取发送数据包的玩家
            ServerPlayer player = context.getSender();
            if (player != null) {
                // 在服务器端执行样板上传
                boolean success = ExtendedAEPatternUploadUtil.uploadPatternToProvider(
                    player,
                    packet.playerSlotIndex,
                    packet.providerId
                );
                
                // 发送结果反馈给客户端
                if (success) {
                    // 上传成功，发送成功反馈包
                    PatternUploadResultPacket resultPacket = new PatternUploadResultPacket(true, "样板上传成功！");
                    NetworkHandler.sendToClient(resultPacket, player);
                } else {
                    // 上传失败，发送失败反馈包
                    PatternUploadResultPacket resultPacket = new PatternUploadResultPacket(false, "样板上传失败，请检查供应器状态");
                    NetworkHandler.sendToClient(resultPacket, player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
