package com.extendedae_plus.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络处理器
 * 管理ExtendedAE Plus的所有网络通信
 */
public class NetworkHandler {
    
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;
    private static boolean registered = false;

    /**
     * 在合法的注册阶段创建通道。可重复调用（幂等）。
     */
    private static void initChannel() {
        if (INSTANCE == null) {
            INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("extendedae_plus", "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
            );
        }
    }

    /**
     * 供 Mod 早期调用，确保在注册窗口关闭前完成通道与包注册。
     */
    public static void initialize() {
        if (!registered) {
            initChannel();
            registerPackets();
        }
    }
    
    /**
     * 注册所有网络包
     */
    public static void registerPackets() {
        if (registered) return;
        initChannel();
        // 样板上传请求包（客户端 -> 服务器）
        INSTANCE.messageBuilder(PatternUploadPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(PatternUploadPacket::decode)
                .encoder(PatternUploadPacket::encode)
                .consumerMainThread(PatternUploadPacket::handle)
                .add();
        
        // 样板上传结果包（服务器 -> 客户端）
        INSTANCE.messageBuilder(PatternUploadResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PatternUploadResultPacket::decode)
                .encoder(PatternUploadResultPacket::encode)
                .consumerMainThread(PatternUploadResultPacket::handle)
                .add();
        
        // 样板缩放请求包（客户端 -> 服务器）
        INSTANCE.messageBuilder(PatternScalingPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(PatternScalingPacket::decode)
                .encoder(PatternScalingPacket::encode)
                .consumerMainThread(PatternScalingPacket::handle)
                .add();
        
        // 样板缩放结果包（服务器 -> 客户端）
        INSTANCE.messageBuilder(PatternScalingResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PatternScalingResultPacket::decode)
                .encoder(PatternScalingResultPacket::encode)
                .consumerMainThread(PatternScalingResultPacket::handle)
                .add();
        registered = true;
    }
    
    /**
     * 发送包到服务器
     */
    public static void sendToServer(Object packet) {
        // 如果出现 null，说明初始化阶段未被调用，避免在锁定后临时创建通道
        if (INSTANCE == null) throw new IllegalStateException("Network channel not initialized");
        INSTANCE.sendToServer(packet);
    }
    
    /**
     * 发送包到指定客户端
     */
    public static void sendToClient(Object packet, ServerPlayer player) {
        if (INSTANCE == null) throw new IllegalStateException("Network channel not initialized");
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
