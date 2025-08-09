package com.extendedae_plus.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 样板上传结果网络包
 * 用于从服务器发送上传结果反馈到客户端
 */
public class PatternUploadResultPacket {
    
    private final boolean success;
    private final String message;
    
    public PatternUploadResultPacket(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    /**
     * 编码数据包
     */
    public static void encode(PatternUploadResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message);
    }
    
    /**
     * 解码数据包
     */
    public static PatternUploadResultPacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String message = buffer.readUtf();
        return new PatternUploadResultPacket(success, message);
    }
    
    /**
     * 处理数据包（在客户端执行）
     */
    public static void handle(PatternUploadResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 在客户端显示结果消息
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                String prefix = packet.success ? "✅ ExtendedAE Plus: " : "❌ ExtendedAE Plus: ";
                minecraft.player.displayClientMessage(
                    Component.literal(prefix + packet.message),
                    true
                );
            }
        });
        context.setPacketHandled(true);
    }
}
