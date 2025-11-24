package com.extendedae_plus.util.wireless;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 无线收发器队伍工具类
 * 实现FTBTeams软依赖：有FTBTeams时使用队伍UUID，没有时使用玩家UUID
 */
public class WirelessTeamUtil {

    private static final boolean FTB_TEAMS_LOADED = ModList.get().isLoaded("ftbteams");

    /**
     * 获取用于无线网络隔离的UUID
     * - 如果安装了FTBTeams且玩家在队伍中，返回队伍UUID（同队玩家共享）
     * - 否则返回玩家自己的UUID（独立网络）
     *
     * @param level      服务端世界
     * @param playerUUID 玩家UUID
     * @return 网络所有者UUID
     */
    public static UUID getNetworkOwnerUUID(@Nullable ServerLevel level, UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        
        if (!FTB_TEAMS_LOADED) {
            return playerUUID;
        }
        
        if (level == null) {
            return playerUUID;
        }
        
        try {
            return getTeamUUID(level, playerUUID);
        } catch (Exception e) {
            // 如果FTBTeams API调用失败，回退到玩家UUID
            return playerUUID;
        }
    }

    /**
     * 获取网络所有者的显示名称（用于UI显示）
     *
     * @param level      服务端世界
     * @param playerUUID 玩家UUID
     * @return 显示名称
     */
    public static Component getNetworkOwnerName(@Nullable ServerLevel level, UUID playerUUID) {
        if (FTB_TEAMS_LOADED && level != null) {
            try {
                return getTeamName(level, playerUUID);
            } catch (Exception ignored) {
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
     * @param playerUUID 玩家UUID
     * @return 是否有效
     */
    public static boolean hasNetworkOwner(@Nullable ServerLevel level, UUID playerUUID) {
        if (FTB_TEAMS_LOADED && level != null) {
            try {
                return hasTeamOwner(level, playerUUID);
            } catch (Exception ignored) {
            }
        }

        // 检查玩家是否在线
        if (level != null) {
            return level.getServer().getPlayerList().getPlayer(playerUUID) != null;
        }

        return false;
    }

    // ==================== FTBTeams 集成（通过反射调用避免硬依赖）====================

    private static UUID getTeamUUID(ServerLevel level, UUID playerUUID) {
        try {
            // 使用FTBTeams API
            var apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            var api = apiClass.getMethod("api").invoke(null);  // 静态方法，返回API实例
            
            // 检查Manager是否已加载（在api实例上调用）
            Boolean isLoaded = (Boolean) api.getClass().getMethod("isManagerLoaded").invoke(api);
            
            if (!isLoaded) {
                return playerUUID;
            }
            
            var getManager = api.getClass().getMethod("getManager").invoke(api);

            if (getManager == null) {
                return playerUUID;
            }

            var managerClass = getManager.getClass();
            var getTeamForPlayer = managerClass.getMethod("getTeamForPlayerID", UUID.class);
            var teamOptional = getTeamForPlayer.invoke(getManager, playerUUID);

            if (teamOptional != null) {
                var optionalClass = teamOptional.getClass();
                var isPresent = (boolean) optionalClass.getMethod("isPresent").invoke(teamOptional);

                if (isPresent) {
                    var team = optionalClass.getMethod("get").invoke(teamOptional);
                    var teamClass = team.getClass();
                    return (UUID) teamClass.getMethod("getTeamId").invoke(team);
                }
            }
        } catch (Exception e) {
            // 反射调用失败，回退
        }

        return playerUUID;
    }

    private static Component getTeamName(ServerLevel level, UUID playerUUID) {
        try {
            var apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            var api = apiClass.getMethod("api").invoke(null);
            
            // 检查Manager是否已加载
            Boolean isLoaded = (Boolean) api.getClass().getMethod("isManagerLoaded").invoke(api);
            if (!isLoaded) {
                // Manager未加载，回退到玩家名称
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    return player.getName();
                }
                return Component.literal(playerUUID.toString());
            }
            
            var getManager = api.getClass().getMethod("getManager").invoke(api);

            if (getManager == null) {
                return Component.literal(playerUUID.toString());
            }

            var managerClass = getManager.getClass();
            var getTeamForPlayer = managerClass.getMethod("getTeamForPlayerID", UUID.class);
            var teamOptional = getTeamForPlayer.invoke(getManager, playerUUID);

            if (teamOptional != null) {
                var optionalClass = teamOptional.getClass();
                var isPresent = (boolean) optionalClass.getMethod("isPresent").invoke(teamOptional);

                if (isPresent) {
                    var team = optionalClass.getMethod("get").invoke(teamOptional);
                    var teamClass = team.getClass();
                    return (Component) teamClass.getMethod("getName").invoke(team);
                }
            }
        } catch (Exception e) {
            // 反射调用失败，回退
        }

        // 回退到玩家名称
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
        if (player != null) {
            return player.getName();
        }

        return Component.literal(playerUUID.toString());
    }

    private static boolean hasTeamOwner(ServerLevel level, UUID playerUUID) {
        try {
            var apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            var api = apiClass.getMethod("api").invoke(null);
            
            // 检查Manager是否已加载
            Boolean isLoaded = (Boolean) api.getClass().getMethod("isManagerLoaded").invoke(api);
            if (!isLoaded) {
                return level.getServer().getPlayerList().getPlayer(playerUUID) != null;
            }
            
            var getManager = api.getClass().getMethod("getManager").invoke(api);

            if (getManager == null) {
                return level.getServer().getPlayerList().getPlayer(playerUUID) != null;
            }

            var managerClass = getManager.getClass();
            var getTeamForPlayer = managerClass.getMethod("getTeamForPlayerID", UUID.class);
            var teamOptional = getTeamForPlayer.invoke(getManager, playerUUID);

            if (teamOptional != null) {
                var optionalClass = teamOptional.getClass();
                return (boolean) optionalClass.getMethod("isPresent").invoke(teamOptional);
            }
        } catch (Exception e) {
            // 反射调用失败，回退
        }

        return level.getServer().getPlayerList().getPlayer(playerUUID) != null;
    }
}

