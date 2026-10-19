package com.extendedae_plus.util;


import net.neoforged.fml.loading.LoadingModList;

/**
 * Forge 加载阶段的 Mod 检测工具
 * 适用于 MixinConfigPlugin 阶段使用
 * 包含版本比较工具
 */
public class ModCheckUtils {

    private static final LoadingModList MOD_LIST = LoadingModList.get();

    public static final String
            MODID_JEI = "jei",
            MODID_TMRV = "toomanyrecipeviewers",
            MODID_EMI = "emi",
            MODID_AE = "ae2",
            MODID_AAE = "advanced_ae",
            MODID_AE2WTLIB = "ae2wtlib",
            MODID_FTB_TEAMS = "ftbteams",
            MODID_APPFLUX = "appflux",
            MODID_GUIDEME = "guideme",
            MODID_MAE2 = "mae2",
            MODID_MEGA = "megacells",
            MODID_MEKANISM = "mekanism",
            MODID_APPMEK = "appmek",
            MODID_EPA = "expandedae",
            MODID_ECO = "neoecoae",
            MODID_JEC = "jecharacters";

    /**
     * 检查指定模组是否存在
     */
    public static boolean isLoaded(String modid) {
        return MOD_LIST != null && MOD_LIST.getModFileById(modid) != null;
    }

    /**
     * 真正的 JEI 是否在场。
     *
     * <p>TooManyRecipeViewers 会额外注册一个 modId 为 {@code jei} 的 stub，
     * 但类路径上没有 {@code mezz.jei.gui.*} 等实现类。凡是会触碰 JEI 实现类
     * （书签、覆盖层、ImmutableRect2i 等）的判断都必须用本方法，而不是
     * {@code isLoaded(MODID_JEI)}。</p>
     */
    public static boolean isRealJeiLoaded() {
        return isLoaded(MODID_JEI) && !isLoaded(MODID_TMRV);
    }

    public static boolean isAppfluxLoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_APPFLUX);
    }

    public static boolean isAAELoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_AAE);
    }

    /**
     * Applied Mekanistics 是否可用。
     * 化学品相关的桥接类（{@code AppliedMekanisticsCompat}）直接引用 mekanism / appmek 的类，
     * 只装 Mekanism 不装 Applied Mekanistics 的环境会解析失败，所以两个都要在场才算可用。
     */
    public static boolean isAppMekLoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_MEKANISM)
                && ModCheckUtils.isLoaded(ModCheckUtils.MODID_APPMEK);
    }

    public static boolean isECOLoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_ECO);
    }

    /**
     * Just Enough Characters 是否可用。
     * 拼音匹配桥接类（{@code JecharactersCompat}）直接引用 {@code me.towdium.jecharacters.utils.Match}，
     * 只应在该守卫通过后调用。
     *
     * <p>最低版本要求（4.5.24，1.21.1 线首个版本）由 {@code neoforge.mods.toml} 中
     * {@code jecharacters} 依赖的 {@code versionRange} 声明，版本不足时由 FML 在加载阶段直接拒绝，
     * 此处无需重复比较。</p>
     */
    public static boolean isJecharactersLoading() {
        return ModCheckUtils.isLoaded(ModCheckUtils.MODID_JEC);
    }
}
