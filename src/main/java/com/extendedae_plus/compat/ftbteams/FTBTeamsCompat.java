package com.extendedae_plus.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * FTB Teams 的可选兼容层；禁止在非兼容代码中直接引用 FTB Teams 类型。
 * <p>
 * 本类直接引用 FTB Teams API 类型，加载即需要 FTB Teams 在场，
 * 因此只能在 {@link com.extendedae_plus.util.ModCheckUtils} 守卫通过后调用。
 */
public final class FTBTeamsCompat {
    private FTBTeamsCompat() {
    }

    /**
     * 服务端队伍管理器是否已加载。
     */
    public static boolean isManagerLoaded() {
        var api = FTBTeamsAPI.api();
        return api != null && api.isManagerLoaded();
    }

    @Nullable
    private static TeamManager manager() {
        var api = FTBTeamsAPI.api();
        return api != null && api.isManagerLoaded() ? api.getManager() : null;
    }

    /**
     * 玩家所在队伍的共享 UUID；无队伍或管理器未加载时返回 {@code null}。
     */
    @Nullable
    public static UUID getTeamId(UUID playerUUID) {
        TeamManager manager = manager();
        if (manager == null) {
            return null;
        }
        Optional<Team> team = manager.getTeamForPlayerID(playerUUID);
        return team.map(Team::getTeamId).orElse(null);
    }

    /**
     * 玩家所在队伍的显示名；无队伍或管理器未加载时返回 {@code null}。
     */
    @Nullable
    public static Component getTeamName(UUID playerUUID) {
        TeamManager manager = manager();
        if (manager == null) {
            return null;
        }
        return manager.getTeamForPlayerID(playerUUID).map(Team::getName).orElse(null);
    }

    /**
     * 玩家是否归属于某个队伍。
     */
    public static boolean hasTeam(UUID playerUUID) {
        TeamManager manager = manager();
        return manager != null && manager.getTeamForPlayerID(playerUUID).isPresent();
    }
}
