package com.extendedae_plus.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkDirection;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket;

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
    }

    private static int nextId() { return id++; }
}
