package com.extendedae_plus.compat;

import net.neoforged.fml.ModList;

public final class UpgradeSlotCompat {
    private static Boolean APPFLUX_LOADED;
    private static Boolean APPFLUX_PATTERN_PROVIDER_MIXIN_ACTIVE;

    private UpgradeSlotCompat() {}

    private static boolean isAppfluxLoaded() {
        if (APPFLUX_LOADED == null) {
            try {
                APPFLUX_LOADED = ModList.get().isLoaded("appflux");
            } catch (Throwable t) {
                // 早期阶段或运行环境差异
                APPFLUX_LOADED = false;
            }
        }
        return APPFLUX_LOADED;
    }

    /**
     * 检测AppliedFlux的PatternProviderScreen mixin是否活跃
     * 这比简单检查模组是否加载更准确
     */
    private static boolean isAppfluxPatternProviderMixinActive() {
        if (APPFLUX_PATTERN_PROVIDER_MIXIN_ACTIVE == null) {
            try {
                if (!isAppfluxLoaded()) {
                    APPFLUX_PATTERN_PROVIDER_MIXIN_ACTIVE = false;
                } else {
                    // 尝试检测AppliedFlux的mixin类是否存在
                    Class.forName("com.glodblock.github.appflux.mixins.MixinPatternProviderScreen");
                    APPFLUX_PATTERN_PROVIDER_MIXIN_ACTIVE = true;
                }
            } catch (Throwable t) {
                APPFLUX_PATTERN_PROVIDER_MIXIN_ACTIVE = false;
            }
        }
        return APPFLUX_PATTERN_PROVIDER_MIXIN_ACTIVE;
    }

    // 是否由我们提供升级槽（当未安装 appflux 时）
    public static boolean shouldEnableUpgradeSlots() {
        return !isAppfluxLoaded();
    }

    // 是否启用频道卡支持（两种情况下都启用）
    public static boolean shouldEnableChannelCard() {
        return true;
    }

    // 客户端界面是否需要显示升级面板
    // 如果AppliedFlux的mixin活跃，我们降低优先级让它处理
    public static boolean shouldAddUpgradePanelToScreen() {
        return true; // 总是尝试添加，但在代码中检测冲突
    }

    // 是否应该使用低优先级模式（当AppliedFlux存在时）
    public static boolean shouldUseLowPriorityMode() {
        return isAppfluxPatternProviderMixinActive();
    }

    // 获取推荐的mixin优先级
    public static int getRecommendedMixinPriority() {
        return isAppfluxPatternProviderMixinActive() ? 1500 : 2000;
    }
}
