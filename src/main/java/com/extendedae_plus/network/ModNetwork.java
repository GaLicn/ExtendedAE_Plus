package com.extendedae_plus.network;

import com.extendedae_plus.ExtendedAEPlus;
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

        CHANNEL.messageBuilder(ToggleAdvancedBlockingC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleAdvancedBlockingC2SPacket::encode)
                .decoder(ToggleAdvancedBlockingC2SPacket::decode)
                .consumerNetworkThread(ToggleAdvancedBlockingC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(AdvancedBlockingSyncS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AdvancedBlockingSyncS2CPacket::encode)
                .decoder(AdvancedBlockingSyncS2CPacket::decode)
                .consumerNetworkThread(AdvancedBlockingSyncS2CPacket::handle)
                .add();
    }

    private static int nextId() { return id++; }
}
