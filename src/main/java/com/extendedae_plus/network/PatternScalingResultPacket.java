package com.extendedae_plus.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 样板缩放结果网络包
 * 用于从服务器发送样板缩放操作结果到客户端
 */
public class PatternScalingResultPacket {
    
    private final boolean successful;
    private final int scaledPatterns;
    private final int failedPatterns;
    private final int totalPatterns;
    private final String errorMessage;
    
    public PatternScalingResultPacket(boolean successful, int scaledPatterns, int failedPatterns, int totalPatterns, String errorMessage) {
        this.successful = successful;
        this.scaledPatterns = scaledPatterns;
        this.failedPatterns = failedPatterns;
        this.totalPatterns = totalPatterns;
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }
    
    /**
     * 编码数据包
     */
    public static void encode(PatternScalingResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.successful);
        buffer.writeInt(packet.scaledPatterns);
        buffer.writeInt(packet.failedPatterns);
        buffer.writeInt(packet.totalPatterns);
        buffer.writeUtf(packet.errorMessage);
    }
    
    /**
     * 解码数据包
     */
    public static PatternScalingResultPacket decode(FriendlyByteBuf buffer) {
        boolean successful = buffer.readBoolean();
        int scaledPatterns = buffer.readInt();
        int failedPatterns = buffer.readInt();
        int totalPatterns = buffer.readInt();
        String errorMessage = buffer.readUtf();
        return new PatternScalingResultPacket(successful, scaledPatterns, failedPatterns, totalPatterns, errorMessage);
    }
    
    /**
     * 处理数据包（客户端）
     */
    public static void handle(PatternScalingResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                // 在客户端显示结果消息
                String message;
                if (packet.successful) {
                    if (packet.failedPatterns > 0) {
                        message = String.format("✅ ExtendedAE Plus: 样板缩放部分成功！成功处理 %d 个样板，跳过 %d 个样板", 
                                              packet.scaledPatterns, packet.failedPatterns);
                    } else {
                        message = String.format("✅ ExtendedAE Plus: 样板缩放成功！处理了 %d 个样板", packet.scaledPatterns);
                    }
                } else {
                    message = "❌ ExtendedAE Plus: 样板缩放失败！" + 
                             (packet.errorMessage.isEmpty() ? "" : " 错误: " + packet.errorMessage);
                }
                
                // 显示消息到聊天栏和动作栏
                minecraft.player.displayClientMessage(Component.literal(message), true);
                
                // 同时输出到控制台用于调试
                System.out.println("ExtendedAE Plus 客户端收到结果: " + message);
                
                // 如果有错误信息，也输出详细错误
                if (!packet.errorMessage.isEmpty() && !packet.successful) {
                    System.out.println("ExtendedAE Plus 详细错误信息: " + packet.errorMessage);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
