package com.extendedae_plus.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.AdvancedBlockingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraft.server.level.ServerPlayer;

/**
 * C2S：切换高级阻挡模式。
 * 不含额外负载，直接基于玩家当前打开的 PatternProviderMenu 进行切换。
 */
public class ToggleAdvancedBlockingC2SPacket {
    public ToggleAdvancedBlockingC2SPacket() {}

    public static void encode(ToggleAdvancedBlockingC2SPacket msg, FriendlyByteBuf buf) {}

    public static ToggleAdvancedBlockingC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleAdvancedBlockingC2SPacket();
    }
}
