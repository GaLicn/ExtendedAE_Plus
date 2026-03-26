package com.extendedae_plus.compat;

import net.neoforged.fml.ModList;

/**
 * 升级卡槽兼容性管理类
 * 统一管理：
 * 1. 是否由我们自己提供升级槽
 * 2. appflux存在时是否复用其升级槽
 */
public final class UpgradeSlotCompat {
    private static final String APPFLUX_MOD_ID = "appflux";
    private static final String APPFLUX_SCREEN_MIXIN = "com.glodblock.github.appflux.mixins.MixinPatternProviderScreen";
    private static final int LOCAL_PATTERN_PROVIDER_UPGRADE_SLOTS = 2;
    private static final int APPFLUX_PATTERN_PROVIDER_UPGRADE_SLOTS = 2;

    private static Boolean appfluxLoaded;
    private static Boolean appfluxPatternProviderMixinActive;

    private UpgradeSlotCompat() {
    }

    /**
     * 检测 Applied Flux 模组是否存在
     */
    public static boolean isAppfluxPresent() {
        if (appfluxLoaded == null) {
            try {
                appfluxLoaded = ModList.get().isLoaded(APPFLUX_MOD_ID);
            } catch (Throwable t) {
                appfluxLoaded = false;
            }
        }
        return appfluxLoaded;
    }

    /**
     * 是否由我们自己提供升级槽实现。
     */
    public static boolean usesDedicatedUpgradeSlots() {
        return !isAppfluxPresent();
    }

    /**
     * 是否应当复用 appflux 注入到 PatternProviderLogic 上的升级槽。
     */
    public static boolean usesAppfluxUpgradeSlots() {
        return isAppfluxPresent();
    }

    /**
     * 检测是否应该启用我们的升级卡槽功能
     */
    public static boolean shouldEnableUpgradeSlots() {
        return usesDedicatedUpgradeSlots();
    }

    /**
     * 是否需要持久化和管理我们本地创建的升级槽。
     */
    public static boolean shouldManageLocalUpgradeInventory() {
        return usesDedicatedUpgradeSlots();
    }

    /**
     * 频道卡是我们独有的功能，即使 appflux 存在也应该启用。
     */
    public static boolean shouldEnableChannelCard() {
        return true;
    }

    /**
     * appflux 存在时，我们仍然需要监听其升级槽变化来驱动额外的兼容逻辑。
     */
    public static boolean shouldListenToAppfluxUpgrades() {
        return usesAppfluxUpgradeSlots();
    }

    /**
     * 客户端界面是否需要显示升级面板。
     */
    public static boolean shouldAddUpgradePanelToScreen() {
        return usesDedicatedUpgradeSlots();
    }

    public static int getPatternProviderLocalUpgradeSlots() {
        return LOCAL_PATTERN_PROVIDER_UPGRADE_SLOTS;
    }

    public static int getPatternProviderAppfluxUpgradeSlots() {
        return APPFLUX_PATTERN_PROVIDER_UPGRADE_SLOTS;
    }

    /**
     * 兼容主工程现有调用：当 appflux 的 PatternProvider Screen mixin 会接管 UI 时，返回低优先级模式。
     */
    public static boolean shouldUseLowPriorityMode() {
        return isAppfluxPatternProviderMixinActive();
    }

    /**
     * 兼容主工程现有调用：保留旧的优先级推荐接口。
     */
    public static int getRecommendedMixinPriority() {
        return shouldUseLowPriorityMode() ? 1500 : 2000;
    }

    private static boolean isAppfluxPatternProviderMixinActive() {
        if (appfluxPatternProviderMixinActive == null) {
            try {
                if (!isAppfluxPresent()) {
                    appfluxPatternProviderMixinActive = false;
                } else {
                    Class.forName(APPFLUX_SCREEN_MIXIN);
                    appfluxPatternProviderMixinActive = true;
                }
            } catch (Throwable t) {
                appfluxPatternProviderMixinActive = false;
            }
        }
        return appfluxPatternProviderMixinActive;
    }
}
