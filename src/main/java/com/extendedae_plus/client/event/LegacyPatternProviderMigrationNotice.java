package com.extendedae_plus.client.event;

import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.util.Logger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 在客户端配置目录中一次性显示旧倍率页样板的升级提示。 */
@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyPatternProviderMigrationNotice {
    private static final String FLAG_FILE_NAME = "extendedae_plus_pattern_provider_migration_notice_v1.flag";

    private LegacyPatternProviderMigrationNotice() {
    }

    @SubscribeEvent
    public static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LocalPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        Path flagFile = FMLPaths.CONFIGDIR.get().resolve(FLAG_FILE_NAME);
        try {
            if (Files.exists(flagFile)) {
                return;
            }

            player.displayClientMessage(Component.translatable(
                    "extendedae_plus.message.pattern_provider.legacy_migration_notice").withStyle(ChatFormatting.RED), false);
            Files.createDirectories(flagFile.getParent());
            Files.writeString(flagFile, "legacy pattern provider migration notice acknowledged\n");
        } catch (IOException e) {
            Logger.EAP$LOGGER.warn("无法写入客户端样板供应器迁移提示标志文件: {}", flagFile, e);
        }
    }
}
