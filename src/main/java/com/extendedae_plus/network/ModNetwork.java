package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    // 在 Mod 构造中通过 modEventBus.addListener(ModNetwork::registerPayloadHandlers) 注册
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ExtendedAEPlus.MODID);
        registrar.playToServer(ToggleAdvancedBlockingC2SPacket.TYPE, ToggleAdvancedBlockingC2SPacket.STREAM_CODEC, ToggleAdvancedBlockingC2SPacket::handle);
        registrar.playToServer(ToggleSmartDoublingC2SPacket.TYPE, ToggleSmartDoublingC2SPacket.STREAM_CODEC, ToggleSmartDoublingC2SPacket::handle);
        registrar.playToServer(ScalePatternsC2SPacket.TYPE, ScalePatternsC2SPacket.STREAM_CODEC, ScalePatternsC2SPacket::handle);
        registrar.playToClient(SetPatternHighlightS2CPacket.TYPE, SetPatternHighlightS2CPacket.STREAM_CODEC, SetPatternHighlightS2CPacket::handle);
    }
}
