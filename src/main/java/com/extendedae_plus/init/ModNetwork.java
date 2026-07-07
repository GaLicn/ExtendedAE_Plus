package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.network.CancelPendingPatternC2SPacket;
import com.extendedae_plus.network.CraftingMonitorJumpC2SPacket;
import com.extendedae_plus.network.CraftingMonitorOpenProviderC2SPacket;
import com.extendedae_plus.network.CreateAndUploadPatternC2SPacket;
import com.extendedae_plus.network.CreateCtrlQPatternC2SPacket;
import com.extendedae_plus.network.GlobalToggleProviderModesC2SPacket;
import com.extendedae_plus.network.InterfaceAdjustConfigAmountC2SPacket;
import com.extendedae_plus.network.OpenProviderUiC2SPacket;
import com.extendedae_plus.network.ProvidersListS2CPacket;
import com.extendedae_plus.network.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.ReturnLastPatternC2SPacket;
import com.extendedae_plus.network.ScaleEncodingPatternC2SPacket;
import com.extendedae_plus.network.ScalePatternsC2SPacket;
import com.extendedae_plus.network.SetPatternHighlightS2CPacket;
import com.extendedae_plus.network.SetPerProviderScalingLimitC2SPacket;
import com.extendedae_plus.network.SetGlobalScalingLimitC2SPacket;
import com.extendedae_plus.network.SetProviderPageS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixActionC2SPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixStatsS2CPacket;
import com.extendedae_plus.network.SuperAssemblerMatrixUpdateS2CPacket;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;
import com.extendedae_plus.network.UploadInventoryPatternToProviderC2SPacket;
import com.extendedae_plus.network.crafting.ForceCraftStartFlagC2SPacket;
import com.extendedae_plus.network.crafting.ManualCraftingStatusS2CPacket;
import com.extendedae_plus.network.packet.EAPConfigButtonPacket;
import com.extendedae_plus.network.upload.EncodeWithShiftFlagC2SPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    // 在 Mod 构造中通过 modEventBus.addListener(ModNetwork::registerPayloadHandlers) 注册
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ExtendedAEPlus.MODID);
        registrar.playToServer(ScalePatternsC2SPacket.TYPE, ScalePatternsC2SPacket.STREAM_CODEC, ScalePatternsC2SPacket::handle);
        registrar.playToServer(ScaleEncodingPatternC2SPacket.TYPE, ScaleEncodingPatternC2SPacket.STREAM_CODEC,
                ScaleEncodingPatternC2SPacket::handle);
        registrar.playToServer(InterfaceAdjustConfigAmountC2SPacket.TYPE, InterfaceAdjustConfigAmountC2SPacket.STREAM_CODEC, InterfaceAdjustConfigAmountC2SPacket::handle);
        registrar.playToClient(SetPatternHighlightS2CPacket.TYPE, SetPatternHighlightS2CPacket.STREAM_CODEC, SetPatternHighlightS2CPacket::handle);
        registrar.playToClient(ProvidersListS2CPacket.TYPE, ProvidersListS2CPacket.STREAM_CODEC, ProvidersListS2CPacket::handle);
        registrar.playToServer(RequestProvidersListC2SPacket.TYPE, RequestProvidersListC2SPacket.STREAM_CODEC, RequestProvidersListC2SPacket::handle);
        registrar.playToClient(SetProviderPageS2CPacket.TYPE, SetProviderPageS2CPacket.STREAM_CODEC, SetProviderPageS2CPacket::handle);
        registrar.playToServer(GlobalToggleProviderModesC2SPacket.TYPE, GlobalToggleProviderModesC2SPacket.STREAM_CODEC, GlobalToggleProviderModesC2SPacket::handle);
        registrar.playToServer(CraftingMonitorJumpC2SPacket.TYPE, CraftingMonitorJumpC2SPacket.STREAM_CODEC, CraftingMonitorJumpC2SPacket::handle);
        registrar.playToServer(CraftingMonitorOpenProviderC2SPacket.TYPE, CraftingMonitorOpenProviderC2SPacket.STREAM_CODEC, CraftingMonitorOpenProviderC2SPacket::handle);
        registrar.playToServer(OpenProviderUiC2SPacket.TYPE, OpenProviderUiC2SPacket.STREAM_CODEC, OpenProviderUiC2SPacket::handle);
        registrar.playToServer(UploadEncodedPatternToProviderC2SPacket.TYPE, UploadEncodedPatternToProviderC2SPacket.STREAM_CODEC, UploadEncodedPatternToProviderC2SPacket::handle);
        registrar.playToServer(ReturnLastPatternC2SPacket.TYPE, ReturnLastPatternC2SPacket.STREAM_CODEC, ReturnLastPatternC2SPacket::handle);
        registrar.playToServer(UploadInventoryPatternToProviderC2SPacket.TYPE, UploadInventoryPatternToProviderC2SPacket.STREAM_CODEC, UploadInventoryPatternToProviderC2SPacket::handle);
        registrar.playToServer(CreateCtrlQPatternC2SPacket.TYPE, CreateCtrlQPatternC2SPacket.STREAM_CODEC, CreateCtrlQPatternC2SPacket::handle);
        registrar.playToServer(CreateAndUploadPatternC2SPacket.TYPE, CreateAndUploadPatternC2SPacket.STREAM_CODEC, CreateAndUploadPatternC2SPacket::handle);
        registrar.playToServer(CancelPendingPatternC2SPacket.TYPE,CancelPendingPatternC2SPacket.STREAM_CODEC,CancelPendingPatternC2SPacket::handle);
        registrar.playToServer(EncodeWithShiftFlagC2SPacket.TYPE, EncodeWithShiftFlagC2SPacket.STREAM_CODEC, EncodeWithShiftFlagC2SPacket::handle);
        // 新增：JEI 中键打开合成界面 & 无线终端拾取方块物品
        registrar.playToServer(com.extendedae_plus.network.OpenCraftFromJeiC2SPacket.TYPE,
                com.extendedae_plus.network.OpenCraftFromJeiC2SPacket.STREAM_CODEC,
                com.extendedae_plus.network.OpenCraftFromJeiC2SPacket::handle);
        registrar.playToServer(com.extendedae_plus.network.PickFromWirelessC2SPacket.TYPE,
                com.extendedae_plus.network.PickFromWirelessC2SPacket.STREAM_CODEC,
                com.extendedae_plus.network.PickFromWirelessC2SPacket::handle);
        registrar.playToServer(com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket.TYPE,
                com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket.STREAM_CODEC,
                com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket::handle);
        // 频道卡绑定
        registrar.playToServer(com.extendedae_plus.network.ChannelCardBindPacket.TYPE,
                com.extendedae_plus.network.ChannelCardBindPacket.STREAM_CODEC,
                com.extendedae_plus.network.ChannelCardBindPacket::handle);
        // 无线收发器频率设置
        registrar.playToServer(com.extendedae_plus.network.SetWirelessFrequencyC2SPacket.TYPE,
                com.extendedae_plus.network.SetWirelessFrequencyC2SPacket.STREAM_CODEC,
                com.extendedae_plus.network.SetWirelessFrequencyC2SPacket::handle);

        // 标签无线收发器：请求列表 / 操作 / 列表下发
        registrar.playToServer(com.extendedae_plus.network.LabelNetworkListC2SPacket.TYPE,
                com.extendedae_plus.network.LabelNetworkListC2SPacket.STREAM_CODEC,
                com.extendedae_plus.network.LabelNetworkListC2SPacket::handle);
        registrar.playToServer(com.extendedae_plus.network.LabelNetworkActionC2SPacket.TYPE,
                com.extendedae_plus.network.LabelNetworkActionC2SPacket.STREAM_CODEC,
                com.extendedae_plus.network.LabelNetworkActionC2SPacket::handle);
        registrar.playToClient(com.extendedae_plus.network.LabelNetworkListS2CPacket.TYPE,
                com.extendedae_plus.network.LabelNetworkListS2CPacket.STREAM_CODEC,
                com.extendedae_plus.network.LabelNetworkListS2CPacket::handle);
        registrar.playToServer(ForceCraftStartFlagC2SPacket.TYPE,
                ForceCraftStartFlagC2SPacket.STREAM_CODEC,
                ForceCraftStartFlagC2SPacket::handle);
        registrar.playToClient(ManualCraftingStatusS2CPacket.TYPE,
                ManualCraftingStatusS2CPacket.STREAM_CODEC,
                ManualCraftingStatusS2CPacket::handle);

        registrar.playToServer(EAPConfigButtonPacket.TYPE, EAPConfigButtonPacket.STREAM_CODEC, EAPConfigButtonPacket::handleOnServer);
        registrar.playToServer(SetPerProviderScalingLimitC2SPacket.TYPE, SetPerProviderScalingLimitC2SPacket.STREAM_CODEC, SetPerProviderScalingLimitC2SPacket::handle);
        registrar.playToServer(SetGlobalScalingLimitC2SPacket.TYPE, SetGlobalScalingLimitC2SPacket.STREAM_CODEC, SetGlobalScalingLimitC2SPacket::handle);
        registrar.playToClient(SuperAssemblerMatrixUpdateS2CPacket.TYPE,
                SuperAssemblerMatrixUpdateS2CPacket.STREAM_CODEC,
                SuperAssemblerMatrixUpdateS2CPacket::handle);
        registrar.playToClient(SuperAssemblerMatrixStatsS2CPacket.TYPE,
                SuperAssemblerMatrixStatsS2CPacket.STREAM_CODEC,
                SuperAssemblerMatrixStatsS2CPacket::handle);
        registrar.playToServer(SuperAssemblerMatrixActionC2SPacket.TYPE,
                SuperAssemblerMatrixActionC2SPacket.STREAM_CODEC,
                SuperAssemblerMatrixActionC2SPacket::handle);
    }
}
