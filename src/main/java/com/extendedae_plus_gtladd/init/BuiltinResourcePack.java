package com.extendedae_plus_gtladd.init;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.nio.file.Path;

@EventBusSubscriber(
    modid = "extendedae_plus_gtladd",
    bus = Bus.MOD,
    value = {Dist.CLIENT}
)
public class BuiltinResourcePack {
    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            Path resourcePath = ModList.get().getModFileById("extendedae_plus_gtladd").getFile().findResource(new String[]{"pack"});
            if (resourcePath != null) {
                PathPackResources pack = new PathPackResources(ModList.get().getModFileById("extendedae_plus_gtladd").getFile().getFileName() + ":" + resourcePath, resourcePath, true);
                PackMetadataSection metadata = new PackMetadataSection(
                        Component.translatable("ExtendedAE_Plus Light Mode Texture Pack(1.21) §eBY C-H716, _leng, fish_旦"),
                        SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
                event.addRepositorySource(source -> source.accept(Pack.create(
                        "builtin/pack",
                        Component.translatable("[EAEP]LightMode V1.3"),
                        false,
                        string -> pack,
                        new Pack.Info(
                                metadata.getDescription(),
                                metadata.getPackFormat(PackType.SERVER_DATA),
                                metadata.getPackFormat(PackType.CLIENT_RESOURCES),
                                FeatureFlagSet.of(),
                                pack.isHidden()),
                        PackType.CLIENT_RESOURCES,
                        Position.TOP,
                        false,
                        PackSource.BUILT_IN)));
            }

        }
    }
}
