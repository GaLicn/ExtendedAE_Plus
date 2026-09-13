package com.extendedae_plus_gtladd.init;

import com.extendedae_plus_gtladd.network.CraftingMonitorOpenGTMProviderC2SPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry.ChannelBuilder;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(new ResourceLocation("extendedae_plus_gtladd", "main")).networkProtocolVersion(() -> "1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(CraftingMonitorOpenGTMProviderC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER).encoder(CraftingMonitorOpenGTMProviderC2SPacket::encode).decoder(CraftingMonitorOpenGTMProviderC2SPacket::decode).consumerNetworkThread(CraftingMonitorOpenGTMProviderC2SPacket::handle).add();
    }

    private static int nextId() {
        return id++;
    }
}