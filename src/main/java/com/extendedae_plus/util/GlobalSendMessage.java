package com.extendedae_plus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GlobalSendMessage {
    /**
     * 简化发送玩家消息的辅助方法
     */
    public static void sendPlayerMessage(Component msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(msg);
        }
    }
}
