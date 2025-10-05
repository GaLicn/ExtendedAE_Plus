package com.extendedae_plus.init;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.network.*;
import com.extendedae_plus.network.crafting.CraftingMonitorJumpC2SPacket;
import com.extendedae_plus.network.crafting.CraftingMonitorOpenProviderC2SPacket;
import com.extendedae_plus.network.crafting.OpenCraftFromJeiC2SPacket;
import com.extendedae_plus.network.meInterface.InterfaceAdjustConfigAmountC2SPacket;
import com.extendedae_plus.network.provider.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ExtendedAEPlus.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(OpenProviderUiC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenProviderUiC2SPacket::encode)
                .decoder(OpenProviderUiC2SPacket::decode)
                .consumerNetworkThread(OpenProviderUiC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(PickFromWirelessC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PickFromWirelessC2SPacket::encode)
                .decoder(PickFromWirelessC2SPacket::decode)
                .consumerNetworkThread(PickFromWirelessC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenCraftFromJeiC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenCraftFromJeiC2SPacket::encode)
                .decoder(OpenCraftFromJeiC2SPacket::decode)
                .consumerNetworkThread(OpenCraftFromJeiC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(PullFromJeiOrCraftC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PullFromJeiOrCraftC2SPacket::encode)
                .decoder(PullFromJeiOrCraftC2SPacket::decode)
                .consumerNetworkThread(PullFromJeiOrCraftC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(UploadEncodedPatternToProviderC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(UploadEncodedPatternToProviderC2SPacket::encode)
                .decoder(UploadEncodedPatternToProviderC2SPacket::decode)
                .consumerNetworkThread(UploadEncodedPatternToProviderC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestProvidersListC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestProvidersListC2SPacket::encode)
                .decoder(RequestProvidersListC2SPacket::decode)
                .consumerNetworkThread(RequestProvidersListC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(ProvidersListS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProvidersListS2CPacket::encode)
                .decoder(ProvidersListS2CPacket::decode)
                .consumerNetworkThread(ProvidersListS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetPatternHighlightS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SetPatternHighlightS2CPacket::encode)
                .decoder(SetPatternHighlightS2CPacket::decode)
                .consumerNetworkThread(SetPatternHighlightS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetProviderPageS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SetProviderPageS2CPacket::encode)
                .decoder(SetProviderPageS2CPacket::decode)
                .consumerNetworkThread(SetProviderPageS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(ToggleAdvancedBlockingC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleAdvancedBlockingC2SPacket::encode)
                .decoder(ToggleAdvancedBlockingC2SPacket::decode)
                .consumerNetworkThread(ToggleAdvancedBlockingC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(ToggleSmartDoublingC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleSmartDoublingC2SPacket::encode)
                .decoder(ToggleSmartDoublingC2SPacket::decode)
                .consumerNetworkThread(ToggleSmartDoublingC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(GlobalToggleProviderModesC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(GlobalToggleProviderModesC2SPacket::encode)
                .decoder(GlobalToggleProviderModesC2SPacket::decode)
                .consumerNetworkThread(GlobalToggleProviderModesC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(ToggleEntityTickerC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleEntityTickerC2SPacket::encode)
                .decoder(ToggleEntityTickerC2SPacket::decode)
                .consumerNetworkThread(ToggleEntityTickerC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(InterfaceAdjustConfigAmountC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(InterfaceAdjustConfigAmountC2SPacket::encode)
                .decoder(InterfaceAdjustConfigAmountC2SPacket::decode)
                .consumerNetworkThread(InterfaceAdjustConfigAmountC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(AdvancedBlockingSyncS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AdvancedBlockingSyncS2CPacket::encode)
                .decoder(AdvancedBlockingSyncS2CPacket::decode)
                .consumerNetworkThread(AdvancedBlockingSyncS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(CraftingMonitorJumpC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(CraftingMonitorJumpC2SPacket::encode)
                .decoder(CraftingMonitorJumpC2SPacket::decode)
                .consumerNetworkThread(CraftingMonitorJumpC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(CraftingMonitorOpenProviderC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(CraftingMonitorOpenProviderC2SPacket::encode)
                .decoder(CraftingMonitorOpenProviderC2SPacket::decode)
                .consumerNetworkThread(CraftingMonitorOpenProviderC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChannelCardBindPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChannelCardBindPacket::encode)
                .decoder(ChannelCardBindPacket::decode)
                .consumerNetworkThread(ChannelCardBindPacket::handle)
                .add();
    }

    private static int nextId() { return id++; }
}
