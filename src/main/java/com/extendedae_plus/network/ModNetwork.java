package com.extendedae_plus.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkDirection;

import com.extendedae_plus.ExtendedAEPlus;

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
    }

    private static int nextId() { return id++; }
}
