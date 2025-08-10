package com.extendedae_plus.network;

import com.extendedae_plus.util.PatternProviderDataUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.me.common.MEStorageMenu;

import java.util.function.Supplier;

/**
 * 样板缩放网络包
 * 用于从客户端发送样板倍增/除法请求到服务器
 */
public class PatternScalingPacket {
    
    public enum ScalingType {
        MULTIPLY,  // 倍增
        DIVIDE     // 除法
    }
    
    private final ScalingType scalingType;
    private final double scaleFactor;
    
    public PatternScalingPacket(ScalingType scalingType, double scaleFactor) {
        this.scalingType = scalingType;
        this.scaleFactor = scaleFactor;
    }
    
    /**
     * 编码数据包
     */
    public static void encode(PatternScalingPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.scalingType);
        buffer.writeDouble(packet.scaleFactor);
    }
    
    /**
     * 解码数据包
     */
    public static PatternScalingPacket decode(FriendlyByteBuf buffer) {
        ScalingType scalingType = buffer.readEnum(ScalingType.class);
        double scaleFactor = buffer.readDouble();
        return new PatternScalingPacket(scalingType, scaleFactor);
    }
    
    /**
     * 处理数据包（服务器端）
     */
    public static void handle(PatternScalingPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            
            try {
                // 获取玩家当前打开的样板供应器菜单
                PatternProviderLogic patternProvider = getCurrentPatternProvider(player);
                if (patternProvider == null) {
                    System.out.println("ExtendedAE Plus: 玩家 " + player.getName().getString() + " 没有打开样板供应器界面");
                    return;
                }
                
                // 在服务器端执行样板缩放操作
                PatternProviderDataUtil.PatternScalingResult result;
                if (packet.scalingType == ScalingType.MULTIPLY) {
                    result = PatternProviderDataUtil.duplicatePatternAmountsExtendedAEStyle(patternProvider, packet.scaleFactor);
                } else {
                    result = PatternProviderDataUtil.dividePatternAmounts(patternProvider, packet.scaleFactor);
                }
                
                // 发送结果回客户端
                if (result != null) {
                    PatternScalingResultPacket resultPacket = new PatternScalingResultPacket(
                        result.isSuccessful(),
                        result.getScaledPatterns(),
                        result.getFailedPatterns(),
                        result.getTotalPatterns(),
                        String.join("; ", result.getErrors())
                    );
                    NetworkHandler.sendToClient(resultPacket, player);
                    
                    System.out.println("ExtendedAE Plus: 服务器端样板缩放完成 - 成功: " + result.getScaledPatterns() + ", 失败: " + result.getFailedPatterns());
                }
                
            } catch (Exception e) {
                System.out.println("ExtendedAE Plus: 服务器端处理样板缩放时发生错误: " + e.getMessage());
                e.printStackTrace();
                
                // 发送错误结果回客户端
                PatternScalingResultPacket errorPacket = new PatternScalingResultPacket(
                    false, 0, 0, 0, "服务器端处理错误: " + e.getMessage()
                );
                NetworkHandler.sendToClient(errorPacket, player);
            }
        });
        context.setPacketHandled(true);
    }
    
    /**
     * 获取玩家当前打开的样板供应器逻辑
     */
    private static PatternProviderLogic getCurrentPatternProvider(ServerPlayer player) {
        try {
            if (player.containerMenu != null) {
                // 检查是否是样板供应器菜单
                String menuClassName = player.containerMenu.getClass().getSimpleName();
                if (menuClassName.contains("PatternProvider")) {
                    // 通过反射获取PatternProviderLogic
                    var logicField = player.containerMenu.getClass().getDeclaredField("logic");
                    logicField.setAccessible(true);
                    Object logic = logicField.get(player.containerMenu);
                    
                    if (logic instanceof PatternProviderLogic) {
                        return (PatternProviderLogic) logic;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("ExtendedAE Plus: 获取样板供应器逻辑时发生错误: " + e.getMessage());
        }
        return null;
    }
}
