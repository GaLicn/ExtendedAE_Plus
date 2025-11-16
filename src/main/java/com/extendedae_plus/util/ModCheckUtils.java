package com.extendedae_plus.util;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

/**
 * Forge 加载阶段的 Mod 检测工具
 * 适用于 MixinConfigPlugin 阶段使用
 * 包含版本比较工具
 */
public class ModCheckUtils {

    private static final LoadingModList MOD_LIST = LoadingModList.get();

    public static final String
            MODID_JEI = "jei",
            MODID_EMI = "emi",
            MODID_AE = "ae2",
            MODID_AAE = "advanced_ae",
            MODID_AE2WTLIB = "ae2wtlib",
            MODID_FTB_TEAMS = "ftbteams",
            MODID_APPFLUX = "appflux",
            MODID_GUIDEME = "guideme",
            MODID_MAE2 = "mae2",
            MODID_MEGA = "megacells";

    /**
     * 检查指定模组是否存在
     */
    public static boolean isLoaded(String modid) {
        return MOD_LIST != null && MOD_LIST.getModFileById(modid) != null;
    }

    /**
     * 获取模组版本号（x.x.x），若不存在则返回 "0.0.0"
     */
    public static String getVersion(String modid) {
        if (MOD_LIST == null) return "0.0.0";
        ModFileInfo file = MOD_LIST.getModFileById(modid);
        if (file == null || file.getMods().isEmpty()) return "0.0.0";
        return file.getMods().get(0).getVersion().toString();
    }

    /**
     * 检查模组是否存在且版本低于指定版本
     */
    public static boolean isLoadedAndLowerThan(String modid, String targetVersion) {
        if (!isLoaded(modid)) return false;
        return isVersionLower(getVersion(modid), targetVersion);
    }

    /**
     * 比较两个版本号
     *
     * @param current 当前版本号，格式 x.x.x
     * @param target  目标版本号，格式 x.x.x
     * @return true 如果 current < target，否则 false
     */
    public static boolean isVersionLower(String current, String target) {
        if (current == null || target == null) return false;

        String[] curParts = current.split("\\.");
        String[] tarParts = target.split("\\.");

        for (int i = 0; i < 3; i++) {
            int curNum = i < curParts.length ? parse(curParts[i]) : 0;
            int tarNum = i < tarParts.length ? parse(tarParts[i]) : 0;

            if (curNum < tarNum) return true;
            if (curNum > tarNum) return false;
        }
        return false; // 相等则不小于
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean isAppfluxLoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_APPFLUX);
    }

    public static boolean isAAELoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_AAE);
    }
}
