package com.extendedae_plus.util.wireless;

import com.extendedae_plus.compat.ftbteams.FTBTeamsCompat;
import com.extendedae_plus.util.ModCheckUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 无线收发器队伍工具类
 * 实现 FTB Teams 软依赖：有 FTB Teams 时使用队伍 UUID，没有时使用玩家 UUID
 */
public class WirelessTeamUtil {

    private static final boolean FTB_TEAMS_LOADED = ModCheckUtils.isLoaded(ModCheckUtils.MODID_FTB_TEAMS);

    /**
     * 获取用于无线网络隔离的 UUID
     * - 如果安装了 FTB Teams 且玩家在队伍中，返回队伍 UUID（同队玩家共享）
     * - 否则返回玩家自己的 UUID（独立网络）
     *
     * @param level      服务端世界
     * @param playerUUID 玩家 UUID
     * @return 网络所有者 UUID
     */
    public static UUID getNetworkOwnerUUID(@Nullable ServerLevel level, UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }

        if (!FTB_TEAMS_LOADED || level == null) {
            return playerUUID;
        }

        UUID teamId = FTBTeamsCompat.getTeamId(playerUUID);
        return teamId != null ? teamId : playerUUID;
    }

    /**
     * 获取网络所有者的显示名称（用于 UI 显示）
     *
     * @param level      服务端世界
     * @param playerUUID 玩家 UUID
     * @return 显示名称
     */
    public static Component getNetworkOwnerName(@Nullable ServerLevel level, UUID playerUUID) {
        if (FTB_TEAMS_LOADED && level != null) {
            Component teamName = FTBTeamsCompat.getTeamName(playerUUID);
            if (teamName != null) {
                return teamName;
            }
        }

        // 尝试获取玩家名称
        if (level != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                return player.getName();
            }
        }

        return Component.literal(playerUUID.toString());
    }

    /**
     * 检查网络所有者是否有效（玩家在线或队伍存在）
     *
     * @param level      服务端世界
     * @param playerUUID 玩家 UUID
     * @return 是否有效
     */
    public static boolean hasNetworkOwner(@Nullable ServerLevel level, UUID playerUUID) {
        if (FTB_TEAMS_LOADED && level != null && FTBTeamsCompat.hasTeam(playerUUID)) {
            return true;
        }

        // 检查玩家是否在线
        return level != null && level.getServer().getPlayerList().getPlayer(playerUUID) != null;
    }
}
