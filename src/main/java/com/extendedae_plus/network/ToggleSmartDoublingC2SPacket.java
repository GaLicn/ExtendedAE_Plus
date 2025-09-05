package com.extendedae_plus.network;

import appeng.menu.implementations.PatternProviderMenu;
import com.extendedae_plus.api.SmartDoublingHolder;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAdvancedAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S：切换智能翻倍启用状态。
 * 不含额外负载，基于玩家当前打开的 PatternProviderMenu 进行切换。
 */
public class ToggleSmartDoublingC2SPacket {
    public ToggleSmartDoublingC2SPacket() {}

    public static void encode(ToggleSmartDoublingC2SPacket msg, FriendlyByteBuf buf) {}

    public static ToggleSmartDoublingC2SPacket decode(FriendlyByteBuf buf) {
        return new ToggleSmartDoublingC2SPacket();
    }
}
