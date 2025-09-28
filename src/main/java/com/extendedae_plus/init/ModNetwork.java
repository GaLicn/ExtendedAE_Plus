package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    // 在 Mod 构造中通过 modEventBus.addListener(ModNetwork::registerPayloadHandlers) 注册
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ExtendedAEPlus.MODID);
        registrar.playToServer(ToggleAdvancedBlockingC2SPacket.TYPE, ToggleAdvancedBlockingC2SPacket.STREAM_CODEC, ToggleAdvancedBlockingC2SPacket::handle);
        registrar.playToServer(ToggleSmartDoublingC2SPacket.TYPE, ToggleSmartDoublingC2SPacket.STREAM_CODEC, ToggleSmartDoublingC2SPacket::handle);
        registrar.playToServer(ScalePatternsC2SPacket.TYPE, ScalePatternsC2SPacket.STREAM_CODEC, ScalePatternsC2SPacket::handle);
        registrar.playToServer(InterfaceAdjustConfigAmountC2SPacket.TYPE, InterfaceAdjustConfigAmountC2SPacket.STREAM_CODEC, InterfaceAdjustConfigAmountC2SPacket::handle);
        registrar.playToClient(SetPatternHighlightS2CPacket.TYPE, SetPatternHighlightS2CPacket.STREAM_CODEC, SetPatternHighlightS2CPacket::handle);
        registrar.playToClient(AdvancedBlockingSyncS2CPacket.TYPE, AdvancedBlockingSyncS2CPacket.STREAM_CODEC, AdvancedBlockingSyncS2CPacket::handle);
        registrar.playToClient(ProvidersListS2CPacket.TYPE, ProvidersListS2CPacket.STREAM_CODEC, ProvidersListS2CPacket::handle);
        registrar.playToServer(RequestUploadingC2SPacket.TYPE, RequestUploadingC2SPacket.STREAM_CODEC, RequestUploadingC2SPacket::handle);
        registrar.playToClient(SetProviderPageS2CPacket.TYPE, SetProviderPageS2CPacket.STREAM_CODEC, SetProviderPageS2CPacket::handle);
        registrar.playToServer(GlobalToggleProviderModesC2SPacket.TYPE, GlobalToggleProviderModesC2SPacket.STREAM_CODEC, GlobalToggleProviderModesC2SPacket::handle);
        registrar.playToServer(CraftingMonitorJumpC2SPacket.TYPE, CraftingMonitorJumpC2SPacket.STREAM_CODEC, CraftingMonitorJumpC2SPacket::handle);
        registrar.playToServer(CraftingMonitorOpenProviderC2SPacket.TYPE, CraftingMonitorOpenProviderC2SPacket.STREAM_CODEC, CraftingMonitorOpenProviderC2SPacket::handle);
        registrar.playToServer(OpenProviderUiC2SPacket.TYPE, OpenProviderUiC2SPacket.STREAM_CODEC, OpenProviderUiC2SPacket::handle);
        registrar.playToServer(UploadEncodedPatternToProviderC2SPacket.TYPE, UploadEncodedPatternToProviderC2SPacket.STREAM_CODEC, UploadEncodedPatternToProviderC2SPacket::handle);
        registrar.playToServer(UploadInventoryPatternToProviderC2SPacket.TYPE, UploadInventoryPatternToProviderC2SPacket.STREAM_CODEC, UploadInventoryPatternToProviderC2SPacket::handle);
        // 新增：JEI 中键打开合成界面 & 无线终端拾取方块物品
        registrar.playToServer(OpenCraftFromJeiC2SPacket.TYPE,
                OpenCraftFromJeiC2SPacket.STREAM_CODEC,
                OpenCraftFromJeiC2SPacket::handle);
        registrar.playToServer(PickFromWirelessC2SPacket.TYPE,
                PickFromWirelessC2SPacket.STREAM_CODEC,
                PickFromWirelessC2SPacket::handle);
        registrar.playToServer(PullFromJeiOrCraftC2SPacket.TYPE,
                PullFromJeiOrCraftC2SPacket.STREAM_CODEC,
                PullFromJeiOrCraftC2SPacket::handle);

        registrar.playToClient(C2SPacketEncodeFinished.TYPE,
                C2SPacketEncodeFinished.STREAM_CODEC,
                C2SPacketEncodeFinished::handle);
        registrar.playToServer(C2SPacketTargetKeyTriggered.TYPE,
                C2SPacketTargetKeyTriggered.STREAM_CODEC,
                C2SPacketTargetKeyTriggered::handle);
        registrar.playToServer(C2SPacketStoneCuttingID.TYPE,
                C2SPacketStoneCuttingID.STREAM_CODEC,
                C2SPacketStoneCuttingID::handle);
    }
}
